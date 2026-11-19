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
        String selected = visibleTitle(p, store);
        if (selected == null) return;
        Map<String, TitleDef> defs = store.definitions();
        e.setMessage(TitleService.formatChat(p.getGameProfile().getName(),
                selected, defs, e.getRawText()));
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        TitleStore store = StoreHolder.titles();
        String selected = visibleTitle(p, store);
        if (selected == null) return;
        e.setDisplayname(TitleService.formatDisplay(p.getGameProfile().getName(),
                selected, store.definitions()));
    }

    @SubscribeEvent
    public static void onTabList(PlayerEvent.TabListNameFormat e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        TitleStore store = StoreHolder.titles();
        String selected = visibleTitle(p, store);
        if (selected == null) return;
        e.setDisplayName(TitleService.formatDisplay(p.getGameProfile().getName(),
                selected, store.definitions()));
    }

    /** 重算 Forge 缓存的显示名，并向在线观察者同步已认证玩家的 Tab 名称变更。 */
    public static void refreshDisplay(ServerPlayer p) {
        if (p == null || p.server == null || !authed(p)) return;
        p.refreshDisplayName();
        p.refreshTabListName();
    }

    private static boolean authed(ServerPlayer p) {
        return AuthHolder.AUTH != null && AuthHolder.AUTH.isAuthenticated(p);
    }

    private static String visibleTitle(ServerPlayer p, TitleStore store) {
        if (store == null) return null;
        String selected = store.selectedOf(TitleService.normalizeName(p.getGameProfile().getName()));
        return TitleService.shouldShowTitle(authed(p), selected) && store.def(selected) != null ? selected : null;
    }
}
