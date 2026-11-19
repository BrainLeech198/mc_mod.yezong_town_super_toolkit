package com.yztc.auth.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.data.AccountStore;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lang.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.io.IOException;
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
            src.sendFailure(Lang.msg("migrate_no_file", legacyPath.toString()));
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
            src.sendFailure(Lang.msg("migrate_parse_fail", e.getMessage()));
            return 0;
        }

        MigrateService.Report rep;
        long now = System.currentTimeMillis();
        try {
            rep = dryRun ? MigrateService.migrate(legacy, store, true, now) : MigrateService.migrateAndSave(legacy, store, now);
        } catch (IOException e) {
            src.sendFailure(Lang.msg("err_save", e.getMessage()));
            return 0;
        }
        if (!dryRun) {
            com.yztc.auth.risk.RiskLog.record(com.yztc.auth.risk.RiskLog.MIGRATE,
                    com.yztc.auth.risk.RiskLog.actorOf(src), null, rep.summary());
        }
        Component head = Component.literal(dryRun ? "[DRY-RUN] " : "").append(Lang.msg("migrate_done", rep.summary()));
        java.util.List<Component> lines = new java.util.ArrayList<>();
        for (MigrateService.EntryResult r : rep.details()) {
            if (r.status() == MigrateService.Status.FAILED) {
                lines.add(Lang.msg("migrate_fail_line", r.name(), r.reason()));
            } else if (r.status() == MigrateService.Status.SKIPPED) {
                lines.add(Lang.msg("migrate_skip_line", r.name(), r.reason()));
            }
        }
        java.util.List<Component> boxed = com.yztc.auth.ui.ChatBox.box(head, lines);
        for (int i = 0; i < boxed.size(); i++) {
            Component c = boxed.get(i);
            boolean bcast = i == 0;
            src.sendSuccess(() -> c, bcast);
        }
        return rep.failed() > 0 ? 1 : 1;
    }
}
