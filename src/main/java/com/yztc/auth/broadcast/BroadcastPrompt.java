package com.yztc.auth.broadcast;

import com.yztc.auth.lang.Lang;
import com.yztc.auth.ui.InputPrompt;
import net.minecraft.server.level.ServerPlayer;

/** 大喇叭的输入入口：管理员发公告、玩家喊话。先二选一（铁砧/命令），不直接弹铁砧。 */
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
        InputPrompt.choose(p,
                Lang.msg("input_choose"),
                Lang.msg("broadcast_shout"),
                text -> BroadcastCommand.performShout(p, text),
                "/yzts shout ");
    }
}
