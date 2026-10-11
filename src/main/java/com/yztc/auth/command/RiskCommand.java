package com.yztc.auth.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.data.Risk;
import com.yztc.auth.data.RiskStore;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.lang.ServerLang;
import com.yztc.auth.perm.Permissions;
import com.yztc.auth.risk.RiskLog;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** `/yzts admin risk`：高风险记录的开面板 / 列表 / 标记已处理（管理员）。 */
public final class RiskCommand {
    private RiskCommand() {}

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root, AuthManager auth) {
        root.then(Commands.literal("risk")
                .requires(s -> s.getPlayer() == null || Permissions.isAdmin(s.getPlayer()))
                .executes(ctx -> openGui(ctx.getSource(), auth))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), auth)))
                .then(Commands.literal("done")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> done(ctx.getSource(), auth, StringArgumentType.getString(ctx, "id"))))));
    }

    private static int openGui(CommandSourceStack src, AuthManager auth) {
        if (gatedOut(src, auth)) return 0;
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Lang.msg("in_game_only")); return 0; }
        com.yztc.auth.ui.RiskPanel.open(p);
        return 1;
    }

    private static int list(CommandSourceStack src, AuthManager auth) {
        if (gatedOut(src, auth)) return 0;
        RiskStore store = StoreHolder.risk();
        if (store == null) { src.sendFailure(Lang.msg("risk_none")); return 0; }
        List<Risk> all = store.all();

        List<Component> lines = new java.util.ArrayList<>();
        if (all.isEmpty()) {
            lines.add(Lang.msg("risk_none"));
        } else {
            for (Risk r : all) {
                lines.add(Component.literal(
                        RiskLog.line(r) + (r.handled ? ServerLang.template("risk_handled_tag") : "")));
            }
        }
        for (Component c : com.yztc.auth.ui.ChatBox.box(Lang.msg("risk_all_header"), lines)) {
            src.sendSuccess(() -> c, false);
        }
        return 1;
    }

    private static int done(CommandSourceStack src, AuthManager auth, String id) {
        if (gatedOut(src, auth)) return 0;
        RiskStore store = StoreHolder.risk();
        if (store == null) { src.sendFailure(Lang.msg("risk_done_none")); return 0; }
        ServerPlayer p = src.getPlayer();
        String by = p == null ? "(console)" : p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        if (!store.markHandled(id, by, System.currentTimeMillis())) {
            src.sendFailure(Lang.msg("risk_done_none"));
            return 0;
        }
        try {
            store.save();
        } catch (IOException e) {
            src.sendFailure(Lang.msg("err_save", e.getMessage()));
            return 0;
        }
        src.sendSuccess(() -> Lang.msg("risk_done_ok"), false);
        return 1;
    }

    /** 未过闸者一律拒绝（含管理员），与其它管理指令一致。 */
    private static boolean gatedOut(CommandSourceStack src, AuthManager auth) {
        ServerPlayer p = src.getPlayer();
        return p != null && RootCommand.gateBlocked(p, auth);
    }
}
