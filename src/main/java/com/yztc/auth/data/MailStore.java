package com.yztc.auth.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yztc.auth.config.AuthConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 留言库（config/yzt_super/mail.json）。写盘方式与 AccountStore 一致：.tmp 原子落盘 + .bak 备份。 */
public final class MailStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("yzt_super");

    private final Path file;
    private final List<Mail> mails = new ArrayList<>();

    public MailStore(Path file) {
        this.file = file;
    }

    public void load() throws IOException {
        mails.clear();
        if (!Files.exists(file)) return;
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        if (raw.isBlank()) return;
        JsonObject root;
        try {
            root = JsonParser.parseString(raw).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("mail.json 解析失败: " + e.getMessage(), e);
        }
        if (root.has("mails") && root.get("mails").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("mails");
            for (int i = 0; i < arr.size(); i++) {
                JsonElement e = arr.get(i);
                if (!e.isJsonObject()) continue;
                try {
                    Mail mail = GSON.fromJson(e, Mail.class);
                    if (!normalizeLoadedMail(mail)) {
                        LOGGER.warn("[yzt_super] mail.json 第 {} 条记录无效，已跳过", i + 1);
                        continue;
                    }
                    if (byId(mail.id) != null) mail.id = null;
                    add(mail);
                } catch (RuntimeException ex) {
                    LOGGER.warn("[yzt_super] mail.json 第 {} 条记录无法读取，已跳过：{}", i + 1, ex.toString());
                }
            }
        }
        pruneExpired(System.currentTimeMillis());
    }

    /** 兼容历史大小写差异，并隔离字段缺失或损坏的单条记录。 */
    private static boolean normalizeLoadedMail(Mail mail) {
        if (mail == null || mail.from == null || mail.from.isBlank() || mail.text == null) return false;
        mail.from = normalizeName(mail.from);
        if (mail.isPublic) {
            mail.to = "";
        } else {
            if (mail.to == null || mail.to.isBlank()) return false;
            mail.to = normalizeName(mail.to);
        }
        return true;
    }

    public void save() throws IOException {
        pruneExpired(System.currentTimeMillis());
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray arr = new JsonArray();
        for (Mail m : mails) arr.add(GSON.toJsonTree(m));
        root.add("mails", arr);

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

    /** 丢弃超期条目；先判超期，避免把过期条目算进配额。 */
    public void pruneExpired(long now) {
        int days = AuthConfig.mailExpireDays();
        mails.removeIf(m -> expired(m.createdAt, now, days));
    }

    public String add(Mail m) {
        if (m.id == null || m.id.isBlank()) {
            String id;
            do {
                id = String.format("%06x", ThreadLocalRandom.current().nextInt(0x1000000));
            } while (byId(id) != null);
            m.id = id;
        }
        if (m.isPublic) m.to = "";
        mails.add(m);
        return m.id;
    }

    public boolean remove(String id) {
        return mails.removeIf(m -> m.id != null && m.id.equals(id));
    }

    public List<Mail> all() {
        return List.copyOf(mails);
    }

    public Mail byId(String id) {
        if (id == null) return null;
        for (Mail m : mails) if (id.equals(m.id)) return m;
        return null;
    }

    public List<Mail> inboxFor(String nameLower) {
        String recipient = normalizeName(nameLower);
        return mails.stream()
                .filter(m -> !m.isPublic && recipient.equals(normalizeName(m.to)))
                .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt))
                .toList();
    }

    public List<Mail> board() {
        return mails.stream()
                .filter(m -> m.isPublic)
                .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt))
                .toList();
    }

    public int unreadFor(String nameLower) {
        String recipient = normalizeName(nameLower);
        int n = 0;
        for (Mail m : mails) if (!m.isPublic && recipient.equals(normalizeName(m.to)) && m.readAt == 0) n++;
        return n;
    }

    public void markRead(String id) {
        Mail m = byId(id);
        if (m != null && !m.isPublic && m.readAt == 0) m.readAt = System.currentTimeMillis();
    }

    public int size() { return mails.size(); }

    private static String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    // ---- 纯逻辑 ----

    public static boolean canReceive(int inboxCount, int cap) {
        return inboxCount < cap;
    }

    /** 纯逻辑：收件箱中仍有效的私信数（超期条目不占配额）。 */
    public static int liveCount(List<Mail> inbox, long now, int days) {
        int n = 0;
        for (Mail m : inbox) if (!expired(m.createdAt, now, days)) n++;
        return n;
    }

    public static boolean expired(long createdAt, long now, int days) {
        return now - createdAt >= days * 86400_000L;
    }

    public static boolean canDelete(Mail m, String actorLower, boolean admin) {
        if (m == null) return false;
        if (admin) return true;
        if (actorLower == null) return false;
        if (actorLower.equals(m.from)) return true;
        return !m.isPublic && actorLower.equals(m.to);
    }
}
