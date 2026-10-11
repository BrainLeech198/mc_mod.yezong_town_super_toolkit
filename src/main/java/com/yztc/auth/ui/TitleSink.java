package com.yztc.auth.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public final class TitleSink {
    private TitleSink() {}

    /** 淡入 5t / 停留 10t / 淡出 5t（共 1s），配合每 1.5s 重发形成持续闪烁。 */
    public static void send(ServerPlayer p, Component title, Component subtitle) {
        send(p, title, subtitle, 5, 10, 5);
    }

    /** 自定义淡入/停留/淡出时长（tick，20t=1s）。 */
    public static void send(ServerPlayer p, Component title, Component subtitle,
                            int fadeIn, int stay, int fadeOut) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }
}
