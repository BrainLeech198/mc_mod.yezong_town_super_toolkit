package com.yztc.auth.title;

import com.yztc.auth.auth.AuthHolder;
import com.yztc.auth.data.StoreHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;

/** 三个 Forge 钩子：聊天前缀 / 头顶名 / Tab 显示名。未登录玩家一律跳过。 */
public final class TitleEvents {
    private TitleEvents() {}

    @SubscribeEvent
    public static void onChat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        TitleStore store = StoreHolder.titles();
        if (store == null || !TitleService.shouldShowTitle(authed(p), store.selectedOf(key(p)))) return;
        Map<String, TitleDef> defs = store.definitions();
        e.setMessage(TitleService.formatChat(p.getGameProfile().getName(),
                store.selectedOf(key(p)), defs, e.getRawText()));
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        TitleStore store = StoreHolder.titles();
        if (store == null || !TitleService.shouldShowTitle(authed(p), store.selectedOf(key(p)))) return;
        e.setDisplayname(TitleService.formatDisplay(p.getGameProfile().getName(),
                store.selectedOf(key(p)), store.definitions()));
    }

    @SubscribeEvent
    public static void onTabList(PlayerEvent.TabListNameFormat e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        TitleStore store = StoreHolder.titles();
        if (store == null || !TitleService.shouldShowTitle(authed(p), store.selectedOf(key(p)))) return;
        e.setDisplayName(TitleService.formatDisplay(p.getGameProfile().getName(),
                store.selectedOf(key(p)), store.definitions()));
    }

    /** 称号变更/登录后重发玩家信息包，让 Tab 与头顶名立即刷新。 */
    public static void refreshDisplay(ServerPlayer p) {
        if (p == null || p.server == null) return;
        p.server.getPlayerList().sendAllPlayerInfo(p);
    }

    private static boolean authed(ServerPlayer p) {
        return AuthHolder.AUTH != null && AuthHolder.AUTH.isAuthenticated(p);
    }

    private static String key(ServerPlayer p) {
        return TitleService.normalizeName(p.getGameProfile().getName());
    }
}
