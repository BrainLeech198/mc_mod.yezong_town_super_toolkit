package com.yztc.auth.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class AccountStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final JsonStoreFile file;
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private Gate gate;
    private int version = 1;

    public AccountStore(Path file) {
        this.file = new JsonStoreFile(file);
    }

    public void load() throws IOException {
        file.load(this::decode);
    }

    private void decode(JsonObject root) {
        Map<String, Account> loaded = new LinkedHashMap<>();
        int loadedVersion = root.has("version") ? root.get("version").getAsInt() : 1;
        Gate loadedGate = root.has("gate") && !root.get("gate").isJsonNull() ? GSON.fromJson(root.getAsJsonObject("gate"), Gate.class) : null;
        if (root.has("accounts")) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("accounts").entrySet()) {
                if (e.getValue().isJsonObject()) loaded.put(e.getKey(), GSON.fromJson(e.getValue(), Account.class));
            }
        }
        accounts.clear();
        accounts.putAll(loaded);
        gate = loadedGate;
        version = loadedVersion;
    }

    public void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", version);
        if (gate != null) root.add("gate", GSON.toJsonTree(gate));
        JsonObject acc = new JsonObject();
        for (Map.Entry<String, Account> e : accounts.entrySet()) {
            acc.add(e.getKey(), GSON.toJsonTree(e.getValue()));
        }
        root.add("accounts", acc);

        file.save(GSON, root);
    }

    /** Commit a detached replacement, keeping the previous account (or absence) on failure. */
    public void putAndSave(String name, Account replacement) throws IOException {
        Account previous = accounts.put(name, replacement);
        try {
            save();
        } catch (IOException ex) {
            if (previous == null) accounts.remove(name);
            else accounts.put(name, previous);
            throw ex;
        }
    }

    public Account get(String nameLower) { return accounts.get(nameLower); }
    public boolean contains(String nameLower) { return accounts.containsKey(nameLower); }
    public void put(String nameLower, Account a) { accounts.put(nameLower, a); }
    public boolean remove(String nameLower) { return accounts.remove(nameLower) != null; }
    public int size() { return accounts.size(); }
    public Set<String> names() { return Collections.unmodifiableSet(accounts.keySet()); }
    public Gate getGate() { return gate; }
    public void setGate(Gate g) { this.gate = g; }
    public void clearGate() { this.gate = null; }
    public Path file() { return file.path(); }
    public boolean loadFailed() { return file.loadFailed(); }
}
