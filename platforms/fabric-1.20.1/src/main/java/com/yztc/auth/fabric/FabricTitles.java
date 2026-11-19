package com.yztc.auth.fabric;

import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.title.TitleService;
import com.yztc.auth.title.TitleStore;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FabricTitles {
    // 消息格式化也可能读玩家名。只在服务端线程查存储并发布已提交的前缀。
    private static final Map<UUID, Component> PREFIXES = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerTeam> NAMEPLATES = new HashMap<>();
    private static final Scoreboard DISPLAY_BOARD = new Scoreboard();
    private static int nextTeamId;

    private FabricTitles() {}

    public static Component display(ServerPlayer player, Component original) {
        Component prefix = PREFIXES.get(player.getUUID());
        if (prefix == null) return null;
        Component name = original != null ? original : PlayerTeam.formatNameForTeam(player.getTeam(), player.getName());
        return Component.empty().append(prefix).append(name);
    }

    public static void refreshDisplay(ServerPlayer player) {
        if (player != null) syncNameplate(player);
    }

    static void syncNameplate(ServerPlayer player) {
        UUID id = player.getUUID();
        TitleStore store = StoreHolder.titles();
        Component prefix = null;
        if (!FabricEvents.locked(player) && store != null) {
            String selected = store.selectedOf(TitleService.normalizeName(player.getGameProfile().getName()));
            if (store.def(selected) != null) {
                Component value = TitleService.prefixFor(selected, store.definitions());
                if (!value.getString().isEmpty()) prefix = value;
            }
        }

        Component previous = prefix == null ? PREFIXES.remove(id) : PREFIXES.put(id, prefix);
        if (!Objects.equals(prefix, previous) && !FabricEvents.locked(player)) {
            player.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
        }

        // 同一玩家在原版客户端只能归属一个队伍。已有真实队伍时保留其头顶、配色和碰撞规则。
        if (prefix == null || player.getTeam() != null) {
            discardTeam(player);
            return;
        }
        PlayerTeam team = NAMEPLATES.get(id);
        if (team == null || player.getScoreboard().getPlayerTeam(team.getName()) != null) {
            String teamName;
            do {
                teamName = "yzts." + Integer.toUnsignedString(nextTeamId++, 36);
            } while (player.getScoreboard().getPlayerTeam(teamName) != null);
            team = new PlayerTeam(DISPLAY_BOARD, teamName);
            team.getPlayers().add(player.getScoreboardName());
            team.setPlayerPrefix(prefix);
            NAMEPLATES.put(id, team);
            player.server.getPlayerList().broadcastAll(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        } else if (!Objects.equals(team.getPlayerPrefix(), prefix)) {
            team.setPlayerPrefix(prefix);
            player.server.getPlayerList().broadcastAll(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, false));
        }
    }

    private static void discardTeam(ServerPlayer player) {
        PlayerTeam team = NAMEPLATES.remove(player.getUUID());
        if (team != null && player.getScoreboard().getPlayerTeam(team.getName()) == null) {
            player.server.getPlayerList().broadcastAll(ClientboundSetPlayerTeamPacket.createRemovePacket(team));
        }
    }

    public static void sendNameplatesTo(ServerPlayer viewer) {
        for (PlayerTeam team : NAMEPLATES.values()) {
            if (viewer.getScoreboard().getPlayerTeam(team.getName()) == null) {
                viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
            }
        }
    }

    public static void removeNameplate(ServerPlayer player) {
        PREFIXES.remove(player.getUUID());
        discardTeam(player);
    }

    static void clear() {
        PREFIXES.clear();
        NAMEPLATES.clear();
        nextTeamId = 0;
    }
}
