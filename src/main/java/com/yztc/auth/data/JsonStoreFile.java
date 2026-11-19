package com.yztc.auth.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/** One store's UTF-8 JSON file and rolling backup. A failed load blocks writes until a successful reload. */
public final class JsonStoreFile {
    private final Path path;
    private boolean loadFailed;

    public JsonStoreFile(Path path) {
        this.path = path;
    }

    public Path path() { return path; }

    public boolean loadFailed() { return loadFailed; }

    public void load(Consumer<JsonObject> decode) throws IOException {
        loadFailed = true;
        String raw = Files.notExists(path) ? "" : Files.readString(path, StandardCharsets.UTF_8);
        try {
            decode.accept(raw.isBlank() ? new JsonObject() : JsonParser.parseString(raw).getAsJsonObject());
        } catch (RuntimeException ex) {
            throw new IOException(path.getFileName() + " 内容无法解析，保留原件并暂停写入", ex);
        }
        loadFailed = false;
    }

    public void save(Gson gson, JsonObject root) throws IOException {
        if (loadFailed) throw new IOException(path.getFileName() + " 尚未成功加载，保留原文件；请修复后重新加载");
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, gson.toJson(root), StandardCharsets.UTF_8);
        if (Files.exists(path)) {
            Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
