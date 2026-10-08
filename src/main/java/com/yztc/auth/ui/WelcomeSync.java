package com.yztc.auth.ui;

import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.lang.Lang;
import net.minecraft.server.level.ServerPlayer;

public final class WelcomeSync {
    private WelcomeSync() {}

    public static void promptLogin(ServerPlayer p, AuthManager auth) {
        TitleSink.send(p, Lang.msg("prompt_login.title"), Lang.msg("prompt_login.sub"));
        p.sendSystemMessage(Lang.msg("prompt_login.sub"));
    }
}
