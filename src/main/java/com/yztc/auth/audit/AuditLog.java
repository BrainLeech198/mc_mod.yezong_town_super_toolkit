package com.yztc.auth.audit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 追加式审计日志：只记 时间 / 名字 / IP / 结果词，绝不记明文密码。失败静默，不影响登录。 */
public final class AuditLog {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path file;

    public AuditLog(Path file) {
        this.file = file;
    }

    public void append(String line) {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // 审计失败不得影响登录流程
        }
    }

    public static String line(String timeIso, String name, String ip, String event) {
        return String.join(" | ", timeIso, name, ip, event);
    }

    public static String nowIso() {
        return LocalDateTime.now().format(FMT);
    }
}
