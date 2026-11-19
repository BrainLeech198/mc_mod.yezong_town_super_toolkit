package com.yztc.auth.title;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lang.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/** `/yzts title`（玩家自助）与 `/yzts admin title`（管理）。 */
public final class TitleCommand {
    private TitleCommand() {}

    /** 玩家侧：`/yzts title [set <id> | clear]`。 */
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root, AuthManager auth) {
        root.then(Commands.literal("title")
                .executes(ctx -> openGui(ctx.getSource()))
                .then(Commands.literal("set")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> set(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("clear")
                        .executes(ctx -> clear(ctx.getSource()))));
    }

    /** 管理侧：挂进 AdminCommands 既有 `admin` 节点，不新建同名 literal。 */
    public static void attachAdmin(LiteralArgumentBuilder<CommandSourceStack> admin, AuthManager auth) {
        LiteralArgumentBuilder<CommandSourceStack> adminTitle = Commands.literal("title");

        adminTitle.then(Commands.literal("give")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> give(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "id"))))));

        adminTitle.then(Commands.literal("take")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> take(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "id"))))));

        // text 用 word()：greedyString 会吞掉后续 color 参数
        adminTitle.then(Commands.literal("define")
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("text", StringArgumentType.word())
                                .executes(ctx -> define(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        StringArgumentType.getString(ctx, "text"),
                                        "GOLD"))
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .executes(ctx -> define(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "text"),
                                                StringArgumentType.getString(ctx, "color")))))));

        adminTitle.then(Commands.literal("undefine")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> undefine(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        adminTitle.then(Commands.literal("list")
                .executes(ctx -> list(ctx.getSource())));

        admin.then(adminTitle);
    }

    // ---- 玩家 ----

    private static int openGui(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Lang.msg("in_game_only")); return 0; }
        TitlePanel.open(p);
        return 1;
    }

    private static int set(CommandSourceStack src, String id) {
        ServerPlayer p = src.getPlayer();
        TitleStore store = StoreHolder.titles();
        if (p == null || store == null) return 0;
        String name = TitleService.normalizeName(p.getGameProfile().getName());
        if (!TitleService.isDefined(id, store.definitions())) {
            p.sendSystemMessage(Lang.msg("title_bad_id"));
            return 0;
        }
        if (!TitleService.canSelect(id, store.ownedOf(name), store.definitions())) {
            p.sendSystemMessage(Lang.msg("title_not_owned"));
            return 0;
        }
        String previous = store.selectedOf(name);
        store.select(name, id);
        if (!save(p, store)) {
            store.select(name, previous);
            return 0;
        }
        p.sendSystemMessage(Lang.msg("title_set_ok", store.def(id) == null ? id : store.def(id).text));
        TitleEvents.refreshDisplay(p);
        return 1;
    }

    private static int clear(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        TitleStore store = StoreHolder.titles();
        if (p == null || store == null) return 0;
        String name = TitleService.normalizeName(p.getGameProfile().getName());
        if (!TitleService.hasSelected(store.selectedOf(name))) {
            p.sendSystemMessage(Lang.msg("title_none"));
            return 1;
        }
        String previous = store.selectedOf(name);
        store.clearSelected(name);
        if (!save(p, store)) { store.select(name, previous); return 0; }
        p.sendSystemMessage(Lang.msg("title_clear_ok"));
        TitleEvents.refreshDisplay(p);
        return 1;
    }

    // ---- 管理 ----

    private static int give(CommandSourceStack src, String player, String id) {
        TitleStore store = StoreHolder.titles();
        if (store == null) return 0;
        String target = TitleService.normalizeName(player);
        if (!TitleService.validId(id)) { src.sendFailure(Lang.msg("title_bad_id")); return 0; }
        if (!TitleService.isDefined(id, store.definitions())) {
            src.sendFailure(Lang.msg("title_bad_id"));
            return 0;
        }
        if (!TitleService.canGive(id, store.ownedOf(target))) {
            src.sendFailure(Lang.msg("title_already_owned"));
            return 0;
        }
        try {
            store.setOwnershipAndSave(target, id, true);
        } catch (IOException ex) {
            src.sendFailure(Lang.msg("err_save", ex.getMessage()));
            return 0;
        }
        src.sendSuccess(() -> Lang.msg("title_gave", player, id), true);
        refreshIfOnline(src, target);
        return 1;
    }

    private static int take(CommandSourceStack src, String player, String id) {
        TitleStore store = StoreHolder.titles();
        if (store == null) return 0;
        String target = TitleService.normalizeName(player);
        if (!TitleService.canTake(id, store.ownedOf(target))) {
            src.sendFailure(Lang.msg("title_not_owned"));
            return 0;
        }
        try {
            store.setOwnershipAndSave(target, id, false);
        } catch (IOException ex) {
            src.sendFailure(Lang.msg("err_save", ex.getMessage()));
            return 0;
        }
        src.sendSuccess(() -> Lang.msg("title_taken", player, id), true);
        refreshIfOnline(src, target);
        return 1;
    }

    private static int define(CommandSourceStack src, String id, String text, String color) {
        TitleStore store = StoreHolder.titles();
        if (store == null) return 0;
        if (!TitleService.validId(id)) { src.sendFailure(Lang.msg("title_bad_id")); return 0; }
        if (text == null || text.isBlank()) { src.sendFailure(Lang.msg("title_bad_id")); return 0; }
        String c = color == null ? "GOLD" : color.toUpperCase(Locale.ROOT);
        if (!isColor(c)) { src.sendFailure(Lang.msg("title_bad_color")); return 0; }
        TitleDef previous = store.def(id);
        store.define(id, text, c);
        if (!save(src, store)) {
            if (previous == null) store.undefine(id);
            else store.definitions().put(id, previous);
            return 0;
        }
        refreshWearers(src, store, id);
        src.sendSuccess(() -> Lang.msg("title_defined", id), true);
        return 1;
    }

    private static int undefine(CommandSourceStack src, String id) {
        TitleStore store = StoreHolder.titles();
        if (store == null) return 0;
        TitleDef previous = store.def(id);
        store.undefine(id);
        if (!save(src, store)) {
            if (previous != null) store.definitions().put(id, previous);
            return 0;
        }
        refreshWearers(src, store, id);
        src.sendSuccess(() -> Lang.msg("title_undefined", id), true);
        return 1;
    }

    private static int list(CommandSourceStack src) {
        TitleStore store = StoreHolder.titles();
        if (store == null) return 0;
        Map<String, TitleDef> defs = store.definitions();
        Map<String, Integer> counts = ownedCounts(store);
        src.sendSuccess(() -> Lang.msg("title_list_header"), false);
        for (TitleDef d : defs.values()) {
            Component line = Component.literal(TitleService.listLine(d, counts.getOrDefault(d.id, 0)))
                    .withStyle(TitleService.colorOf(d.color));
            src.sendSuccess(() -> line, false);
        }
        return 1;
    }

    /** 每个定义 id 的已获人数（players() 遍历 owned 统计）。 */
    private static Map<String, Integer> ownedCounts(TitleStore store) {
        Map<String, Integer> out = new java.util.HashMap<>();
        for (String p : store.players().keySet()) {
            for (String id : store.ownedOf(p)) out.merge(id, 1, Integer::sum);
        }
        return out;
    }

    private static void refreshWearers(CommandSourceStack src, TitleStore store, String id) {
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            String name = TitleService.normalizeName(player.getGameProfile().getName());
            if (id.equals(store.selectedOf(name))) TitleEvents.refreshDisplay(player);
        }
    }

    /** give/take 的目标在线时同步称号。 */
    private static void refreshIfOnline(CommandSourceStack src, String normalized) {
        if (src.getServer() == null) return;
        for (ServerPlayer v : src.getServer().getPlayerList().getPlayers()) {
            if (TitleService.normalizeName(v.getGameProfile().getName()).equals(normalized)) {
                TitleEvents.refreshDisplay(v);
                return;
            }
        }
    }

    // ---- 工具 ----

    private static boolean isColor(String name) {
        try {
            net.minecraft.ChatFormatting.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean save(ServerPlayer p, TitleStore store) {
        try {
            store.save();
            return true;
        } catch (IOException e) {
            p.sendSystemMessage(Lang.msg("err_save", e.getMessage()));
            return false;
        }
    }

    private static boolean save(CommandSourceStack src, TitleStore store) {
        try {
            store.save();
            return true;
        } catch (IOException e) {
            src.sendFailure(Lang.msg("err_save", e.getMessage()));
            return false;
        }
    }
}
