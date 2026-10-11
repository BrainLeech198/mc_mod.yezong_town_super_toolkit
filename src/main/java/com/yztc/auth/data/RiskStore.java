package com.yztc.auth.data;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 高风险操作库（config/yzt_super/risk.json）。写盘方式与 MailStore 一致：.tmp 原子落盘 + .bak 备份。 */
public final class RiskStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** 记录上限，超出丢弃最早条目（已处理优先丢弃）。 */
    public static final int MAX = 500;

    private final Path file;
    private final List<Risk> entries = new ArrayList<>();

    public RiskStore(Path file) {
        this.file = file;
    }

    public void load() throws IOException {
        entries.clear();
        if (!Files.exists(file)) return;
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        if (raw.isBlank()) return;
        JsonObject root;
        try {
            root = JsonParser.parseString(raw).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("risk.json 解析失败: " + e.getMessage(), e);
        }
        if (root.has("entries") && root.get("entries").isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray("entries")) {
                if (!e.isJsonObject()) continue;
                entries.add(GSON.fromJson(e, Risk.class));
            }
        }
    }

    public void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray arr = new JsonArray();
        for (Risk r : entries) arr.add(GSON.toJsonTree(r));
        root.add("entries", arr);

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

    public String add(Risk r) {
        if (r.id == null || r.id.isBlank()) {
            String id;
            do {
                id = String.format("%06x", ThreadLocalRandom.current().nextInt(0x1000000));
            } while (byId(id) != null);
            r.id = id;
        }
        entries.add(r);
        prune();
        return r.id;
    }

    /** 超上限时按时间丢最早条目（已处理者优先）。 */
    private void prune() {
        if (entries.size() <= MAX) return;
        entries.sort(Comparator.comparingLong(r -> r.at));
        while (entries.size() > MAX) entries.remove(0);
    }

    /** 全部记录，按时间倒序（新→旧）。 */
    public List<Risk> all() {
        List<Risk> out = new ArrayList<>(entries);
        out.sort((a, b) -> Long.compare(b.at, a.at));
        return out;
    }

    /** 未处理记录，按时间倒序。 */
    public List<Risk> pending() {
        List<Risk> out = new ArrayList<>();
        for (Risk r : all()) if (!r.handled) out.add(r);
        return out;
    }

    public int pendingCount() {
        int n = 0;
        for (Risk r : entries) if (!r.handled) n++;
        return n;
    }

    public Risk byId(String id) {
        if (id == null) return null;
        for (Risk r : entries) if (id.equals(r.id)) return r;
        return null;
    }

    /** 标记为已处理；已处理或不存在返回 false。 */
    public boolean markHandled(String id, String by, long now) {
        Risk r = byId(id);
        if (r == null || r.handled) return false;
        r.handled = true;
        r.handledBy = by;
        r.handledAt = now;
        return true;
    }

    public int size() { return entries.size(); }
}
