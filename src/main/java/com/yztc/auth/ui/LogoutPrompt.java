package com.yztc.auth.ui;

import com.yztc.auth.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 登出二选一：退出服务器 / 仅登出账号（留在服里，回到未登入锁定态）。
 *
 * <p>裸 {@code /yzts logout} 与账户面板的登出按钮共用此提示；带参数（{@code quit}/{@code stay}）
 * 则跳过提示直接执行。
 */
public final class LogoutPrompt {
    private LogoutPrompt() {}

    public static void send(ServerPlayer p) {
        p.closeContainer();
        Component buttons = Component.empty()
                .append(ChatPrompt.runButton(Lang.msg("logout_quit"), "/yzts logout quit"))
                .append(Component.literal("   "))
                .append(ChatPrompt.runButton(Lang.msg("logout_stay"), "/yzts logout stay"));
        for (Component c : ChatBox.box(Lang.msg("logout_choose"), java.util.List.of(buttons))) {
            p.sendSystemMessage(c);
        }
    }
}
