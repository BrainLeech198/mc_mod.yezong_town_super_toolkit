package com.yztc.auth.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.data.AccountStore;
import com.yztc.auth.data.StoreHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MigrateCommand {
    private MigrateCommand() {}

    public static int run(CommandSourceStack src, boolean dryRun) {
        AccountStore store = StoreHolder.get();
        Path legacyPath = Path.of(AuthConfig.legacyUsersFile());
        if (!Files.exists(legacyPath)) {
            src.sendFailure(Component.literal("找不到 SAuth 文件：" + legacyPath));
            return 0;
        }
        Map<String, String> legacy = new LinkedHashMap<>();
        try {
            String raw = Files.readString(legacyPath, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                legacy.put(e.getKey(), e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : null);
            }
        } catch (Exception e) {
            src.sendFailure(Component.literal("SAuth 文件解析失败：" + e.getMessage()));
            return 0;
        }

        MigrateService.Report rep = MigrateService.migrate(legacy, store, dryRun, System.currentTimeMillis());
        if (!dryRun) {
            try { store.save(); } catch (Exception e) {
                src.sendFailure(Component.literal("写盘失败：" + e.getMessage()));
                return 0;
            }
        }
        String head = (dryRun ? "[DRY-RUN] " : "") + "迁移完成：" + rep.summary();
        src.sendSuccess(() -> Component.literal(head), true);
        for (MigrateService.EntryResult r : rep.details()) {
            if (r.status() == MigrateService.Status.FAILED) {
                src.sendSuccess(() -> Component.literal("  失败 " + r.name() + "：" + r.reason()), false);
            } else if (r.status() == MigrateService.Status.SKIPPED) {
                src.sendSuccess(() -> Component.literal("  跳过 " + r.name() + "：" + r.reason()), false);
            }
        }
        return rep.failed() > 0 ? 1 : 1;
    }
}
