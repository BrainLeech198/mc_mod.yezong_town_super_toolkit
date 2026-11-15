package com.yztc.auth.broadcast;

import com.yztc.auth.lang.Lang;
import com.yztc.auth.perm.Permissions;
import com.yztc.auth.ui.AnvilInput;
import com.yztc.auth.ui.InputPrompt;
import net.minecraft.server.level.ServerPlayer;

/** 大喇叭的输入入口：管理员公告保留输入方式选择，玩家喊话直接打开铁砧输入。 */
public final class BroadcastPrompt {
    private BroadcastPrompt() {}

    public static void openSay(ServerPlayer p) {
        InputPrompt.choose(p,
                Lang.msg("input_choose"),
                Lang.msg("broadcast_say"),
                text -> BroadcastCommand.performSay(p, text),
                "/yzts say ");
    }

    public static void openShout(ServerPlayer p) {
        long wait = Broadcaster.remainingFor(p.getUUID(), System.currentTimeMillis(), Permissions.isAdmin(p));
        if (wait > 0) {
            p.sendSystemMessage(Lang.msg("broadcast_cooldown", wait));
            return;
        }
        AnvilInput.openFlow(p, Lang.msg("broadcast_shout"), Lang.msg("broadcast_shout_prompt"), (player, text) -> {
            int result = BroadcastCommand.performShout(player, text);
            return result > 0 ? AnvilInput.Response.close() : AnvilInput.Response.keep(null);
        }, () -> {});
    }
}
