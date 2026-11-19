package com.yztc.auth.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 高风险操作库（config/yzt_super/risk.json）。写盘方式与 MailStore 一致：.tmp 原子落盘 + .bak 备份。 */
public final class RiskStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** 记录上限，超出丢弃最早条目（已处理优先丢弃）。 */
    public static final int MAX = 500;

    private final JsonStoreFile file;
    private final List<Risk> entries = new ArrayList<>();

    public RiskStore(Path file) {
        this.file = new JsonStoreFile(file);
    }

    public void load() throws IOException {
        file.load(this::decode);
    }

    private void decode(JsonObject root) {
        List<Risk> loaded = new ArrayList<>();
        if (root.has("entries")) {
            for (JsonElement e : root.getAsJsonArray("entries")) {
                if (e.isJsonObject()) loaded.add(GSON.fromJson(e, Risk.class));
            }
        }
        entries.clear();
        entries.addAll(loaded);
    }

    public void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray arr = new JsonArray();
        for (Risk r : entries) arr.add(GSON.toJsonTree(r));
        root.add("entries", arr);

        file.save(GSON, root);
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

    public void addAndSave(Risk r) throws IOException {
        // add may evict an older entry when the cap is reached.
        List<Risk> previous = new ArrayList<>(entries);
        add(r);
        try {
            save();
        } catch (IOException ex) {
            entries.clear();
            entries.addAll(previous);
            throw ex;
        }
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

    public boolean markHandledAndSave(String id, String by, long now) throws IOException {
        Risk r = byId(id);
        if (r == null || r.handled) return false;
        String previousBy = r.handledBy;
        long previousAt = r.handledAt;
        markHandled(id, by, now);
        try {
            save();
        } catch (IOException ex) {
            r.handled = false;
            r.handledBy = previousBy;
            r.handledAt = previousAt;
            throw ex;
        }
        return true;
    }

    public int size() { return entries.size(); }
}
