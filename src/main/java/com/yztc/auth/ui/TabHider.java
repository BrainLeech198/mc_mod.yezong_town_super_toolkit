package com.yztc.auth.ui;

import com.yztc.auth.auth.PendingState;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 未登录玩家在他人 Tab 列表中的隐藏与揭示。 */
public final class TabHider {
    private TabHider() {}

    /** 纯逻辑：viewer 需要隐藏的 pending 名单（不含自己，自己始终看得见自己的 Tab 项）。 */
    public static List<UUID> hiddenFor(Collection<UUID> pendingIds, UUID viewer) {
        return pendingIds.stream().filter(id -> !id.equals(viewer)).toList();
    }

    /** 让其他在线玩家看不到 pending 的 Tab 项。 */
    public static void hideFromOthers(ServerPlayer pending) {
        MinecraftServer server = pending.server;
        if (server == null) return;
        ClientboundPlayerInfoRemovePacket pk =
                new ClientboundPlayerInfoRemovePacket(List.of(pending.getUUID()));
        for (ServerPlayer v : server.getPlayerList().getPlayers()) {
            if (!v.getUUID().equals(pending.getUUID())) v.connection.send(pk);
        }
    }

    /** 让 viewer 看不到所有未登录者的 Tab 项。 */
    public static void hidePendingFrom(ServerPlayer viewer) {
        List<UUID> hide = hiddenFor(PendingState.ALL.keySet(), viewer.getUUID());
        if (hide.isEmpty()) return;
        viewer.connection.send(new ClientboundPlayerInfoRemovePacket(hide));
    }

    /** 认证成功后，让其他玩家重新看到该玩家的 Tab 项。 */
    public static void reveal(ServerPlayer authed) {
        MinecraftServer server = authed.server;
        if (server == null) return;
        ClientboundPlayerInfoUpdatePacket pk =
                ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(authed));
        for (ServerPlayer v : server.getPlayerList().getPlayers()) {
            if (!v.getUUID().equals(authed.getUUID())) v.connection.send(pk);
        }
    }
}
