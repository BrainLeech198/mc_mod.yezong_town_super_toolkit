package com.yztc.auth.command;

import com.yztc.auth.data.Account;
import com.yztc.auth.data.AccountStore;
import com.yztc.auth.security.PasswordHasher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class MigrateService {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern HEX64 = Pattern.compile("[0-9a-fA-F]{64}");

    public enum Status { IMPORTED, SKIPPED, FAILED }

    public record EntryResult(String name, Status status, String reason) {}

    public record Report(int total, int imported, int skipped, int failed, List<EntryResult> details) {
        public String summary() {
            return "总计=" + total + " 导入=" + imported + " 跳过=" + skipped + " 失败=" + failed;
        }
    }

    private MigrateService() {}

    public static Report migrate(Map<String, String> legacy, AccountStore target, boolean dryRun, long now) {
        int imported = 0, skipped = 0, failed = 0;
        List<EntryResult> details = new ArrayList<>();
        for (Map.Entry<String, String> e : legacy.entrySet()) {
            String rawName = e.getKey();
            String value = e.getValue();

            if (rawName == null || rawName.isBlank()) {
                failed++;
                details.add(new EntryResult(String.valueOf(rawName), Status.FAILED, "玩家名为空"));
                continue;
            }
            String name = rawName.toLowerCase(Locale.ROOT);
            if (!NAME.matcher(name).matches()) {
                failed++;
                details.add(new EntryResult(name, Status.FAILED, "玩家名非法：" + rawName));
                continue;
            }
            if (value == null || !HEX64.matcher(value).matches()) {
                failed++;
                details.add(new EntryResult(name, Status.FAILED, "哈希非法（需 64 位十六进制）"));
                continue;
            }
            if (target.contains(name)) {
                skipped++;
                details.add(new EntryResult(name, Status.SKIPPED, "目标库已存在同名账号"));
                continue;
            }

            Account a = new Account();
            a.algo = PasswordHasher.ALGO_LEGACY;
            a.hash = value.toLowerCase(Locale.ROOT);
            a.migratedAt = now;
            if (!dryRun) target.put(name, a);
            imported++;
            details.add(new EntryResult(name, Status.IMPORTED, null));
        }
        return new Report(legacy.size(), imported, skipped, failed, details);
    }
}
