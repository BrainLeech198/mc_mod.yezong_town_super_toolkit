package com.yztc.auth.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 下线快照库（config/yzt_super/lastseen.json）：每人保存最近位置与游戏模式。 */
public final class LastSeenStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final JsonStoreFile file;
    private final Map<String, LastSeen> players = new LinkedHashMap<>();

    public LastSeenStore(Path file) {
        this.file = new JsonStoreFile(file);
    }

    public void load() throws IOException {
        file.load(this::decode);
    }

    private void decode(JsonObject root) {
        Map<String, LastSeen> loaded = new LinkedHashMap<>();
        if (root.has("players")) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("players").entrySet()) {
                if (e.getValue().isJsonObject()) loaded.put(e.getKey().toLowerCase(Locale.ROOT), GSON.fromJson(e.getValue(), LastSeen.class));
            }
        }
        players.clear();
        players.putAll(loaded);
    }

    public void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 2);
        JsonObject arr = new JsonObject();
        for (Map.Entry<String, LastSeen> e : players.entrySet()) arr.add(e.getKey(), GSON.toJsonTree(e.getValue()));
        root.add("players", arr);

        file.save(GSON, root);
    }

    public void put(LastSeen s) {
        players.put(s.name.toLowerCase(Locale.ROOT), s);
    }

    public void putAndSave(LastSeen snapshot) throws IOException {
        String name = snapshot.name.toLowerCase(Locale.ROOT);
        LastSeen previous = players.put(name, snapshot);
        try {
            save();
        } catch (IOException ex) {
            if (previous == null) players.remove(name);
            else players.put(name, previous);
            throw ex;
        }
    }

    public LastSeen get(String nameLower) {
        return players.get(nameLower);
    }

    public int size() { return players.size(); }
}
