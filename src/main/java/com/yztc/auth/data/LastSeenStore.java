package com.yztc.auth.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 下线位置库（config/yztauth/lastseen.json）：一人一条，覆盖式更新。 */
public final class LastSeenStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Map<String, LastSeen> players = new LinkedHashMap<>();

    public LastSeenStore(Path file) {
        this.file = file;
    }

    public void load() throws IOException {
        players.clear();
        if (!Files.exists(file)) return;
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        if (raw.isBlank()) return;
        JsonObject root;
        try {
            root = JsonParser.parseString(raw).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("lastseen.json 解析失败: " + e.getMessage(), e);
        }
        if (root.has("players") && root.get("players").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("players").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                players.put(e.getKey().toLowerCase(Locale.ROOT), GSON.fromJson(e.getValue(), LastSeen.class));
            }
        }
    }

    public void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject arr = new JsonObject();
        for (Map.Entry<String, LastSeen> e : players.entrySet()) arr.add(e.getKey(), GSON.toJsonTree(e.getValue()));
        root.add("players", arr);

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

    public void put(LastSeen s) {
        players.put(s.name.toLowerCase(Locale.ROOT), s);
    }

    public LastSeen get(String nameLower) {
        return players.get(nameLower);
    }

    public int size() { return players.size(); }
}
