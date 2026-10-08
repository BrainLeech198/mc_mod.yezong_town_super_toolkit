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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class AccountStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private Gate gate;
    private int version = 1;

    public AccountStore(Path file) {
        this.file = file;
    }

    public void load() throws IOException {
        accounts.clear();
        gate = null;
        version = 1;
        if (!Files.exists(file)) return;
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        if (raw.isBlank()) return; // 空文件视为无账号

        JsonObject root;
        try {
            root = JsonParser.parseString(raw).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("users.json 解析失败: " + e.getMessage(), e);
        }
        if (root.has("version") && root.get("version").isJsonPrimitive()) {
            version = root.get("version").getAsInt();
        }
        if (root.has("gate") && root.get("gate").isJsonObject()) {
            gate = GSON.fromJson(root.get("gate"), Gate.class);
        }
        if (root.has("accounts") && root.get("accounts").isJsonObject()) {
            JsonObject acc = root.getAsJsonObject("accounts");
            for (Map.Entry<String, JsonElement> e : acc.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                accounts.put(e.getKey(), GSON.fromJson(e.getValue(), Account.class));
            }
        }
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

    public Account get(String nameLower) { return accounts.get(nameLower); }
    public boolean contains(String nameLower) { return accounts.containsKey(nameLower); }
    public void put(String nameLower, Account a) { accounts.put(nameLower, a); }
    public boolean remove(String nameLower) { return accounts.remove(nameLower) != null; }
    public int size() { return accounts.size(); }
    public Set<String> names() { return Collections.unmodifiableSet(accounts.keySet()); }
    public Gate getGate() { return gate; }
    public void setGate(Gate g) { this.gate = g; }
    public void clearGate() { this.gate = null; }
    public Path file() { return file; }
}
