package com.yztc.auth.title;

import com.yztc.auth.data.JsonStoreFile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 称号库（config/yzt_super/titles.json）。写盘与 MailStore 一致：.tmp 原子落盘 + .bak 备份。 */
public final class TitleStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final JsonStoreFile file;
    private final Map<String, TitleDef> definitions = new LinkedHashMap<>();
    private final Map<String, TitleRecord> players = new HashMap<>();

    public TitleStore(Path file) {
        this.file = new JsonStoreFile(file);
    }

    public void load() throws IOException {
        file.load(this::decode);
    }

    private void decode(JsonObject root) {
        Map<String, TitleDef> defs = new LinkedHashMap<>();
        Map<String, TitleRecord> records = new HashMap<>();
        if (root.has("definitions")) {
            for (JsonElement e : root.getAsJsonArray("definitions")) {
                if (!e.isJsonObject()) continue;
                TitleDef d = GSON.fromJson(e, TitleDef.class);
                if (d != null && d.id != null) defs.put(d.id, d);
            }
        }
        if (root.has("players")) {
            for (Map.Entry<String, JsonElement> en : root.getAsJsonObject("players").entrySet()) {
                if (!en.getValue().isJsonObject()) continue;
                TitleRecord r = GSON.fromJson(en.getValue(), TitleRecord.class);
                if (r != null) records.put(en.getKey(), r);
            }
        }
        definitions.clear();
        definitions.putAll(defs);
        players.clear();
        players.putAll(records);
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

        file.save(GSON, root);
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

    /** Preserve both ownership order and absence of a player record when storage is unavailable. */
    public void setOwnershipAndSave(String playerLower, String titleId, boolean owned) throws IOException {
        TitleRecord previous = players.get(playerLower);
        if (previous != null) {
            TitleRecord updated = new TitleRecord();
            updated.owned.addAll(previous.owned);
            updated.selected = previous.selected;
            players.put(playerLower, updated);
        }
        if (owned) give(playerLower, titleId);
        else take(playerLower, titleId);
        try {
            save();
        } catch (IOException ex) {
            if (previous == null) players.remove(playerLower);
            else players.put(playerLower, previous);
            throw ex;
        }
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
