package com.yztc.auth.title;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 称号库（config/yzt_super/titles.json）。写盘与 MailStore 一致：.tmp 原子落盘 + .bak 备份。 */
public final class TitleStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Map<String, TitleDef> definitions = new LinkedHashMap<>();
    private final Map<String, TitleRecord> players = new HashMap<>();

    public TitleStore(Path file) {
        this.file = file;
    }

    public void load() throws IOException {
        definitions.clear();
        players.clear();
        if (!Files.exists(file)) return;
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        if (raw.isBlank()) return;
        JsonObject root;
        try {
            root = JsonParser.parseString(raw).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("titles.json 解析失败: " + e.getMessage(), e);
        }
        if (root.has("definitions") && root.get("definitions").isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray("definitions")) {
                if (!e.isJsonObject()) continue;
                TitleDef d = GSON.fromJson(e, TitleDef.class);
                if (d != null && d.id != null) definitions.put(d.id, d);
            }
        }
        if (root.has("players") && root.get("players").isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : root.getAsJsonObject("players").entrySet()) {
                if (!en.getValue().isJsonObject()) continue;
                TitleRecord r = GSON.fromJson(en.getValue(), TitleRecord.class);
                if (r != null) players.put(en.getKey(), r);
            }
        }
    }

    public void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray defs = new JsonArray();
        for (TitleDef d : definitions.values()) defs.add(GSON.toJsonTree(d));
        root.add("definitions", defs);
        JsonObject pl = new JsonObject();
        for (Map.Entry<String, TitleRecord> en : players.entrySet()) {
            pl.add(en.getKey(), GSON.toJsonTree(en.getValue()));
        }
        root.add("players", pl);

        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        if (Files.exists(file)) {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Map<String, TitleDef> definitions() { return definitions; }
    public Map<String, TitleRecord> players() { return players; }

    public TitleDef def(String id) { return definitions.get(id); }

    public void define(String id, String text, String color) {
        TitleDef d = new TitleDef();
        d.id = id;
        d.text = text;
        d.color = color;
        definitions.put(id, d);
    }

    public void undefine(String id) { definitions.remove(id); }

    public void give(String playerLower, String titleId) {
        TitleRecord r = players.computeIfAbsent(playerLower, k -> new TitleRecord());
        if (!r.owned.contains(titleId)) r.owned.add(titleId);
    }

    public void take(String playerLower, String titleId) {
        TitleRecord r = players.get(playerLower);
        if (r == null) return;
        r.owned.remove(titleId);
        if (titleId.equals(r.selected)) r.selected = "";
    }

    public void select(String playerLower, String titleId) {
        TitleRecord r = players.computeIfAbsent(playerLower, k -> new TitleRecord());
        r.selected = titleId;
    }

    public void clearSelected(String playerLower) {
        TitleRecord r = players.get(playerLower);
        if (r != null) r.selected = "";
    }

    public Set<String> ownedOf(String playerLower) {
        TitleRecord r = players.get(playerLower);
        return r == null ? Set.of() : new LinkedHashSet<>(r.owned);
    }

    public String selectedOf(String playerLower) {
        TitleRecord r = players.get(playerLower);
        return r == null ? "" : r.selected;
    }
}
