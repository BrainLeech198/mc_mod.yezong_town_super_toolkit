package com.yztc.auth.lockdown;

import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.PendingState;
import com.yztc.auth.lang.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;
import java.util.Set;

public final class LockdownHandler {
    private static AuthManager auth;

    private LockdownHandler() {}

    public static void bind(AuthManager manager) { auth = manager; }

    private static final Set<String> ALLOWED_BARE = Set.of("login", "register");
    private static final Set<String> ALLOWED_SUB = Set.of("gate", "login", "register", "help");

    /** 纯逻辑：未登录玩家仅允许认证入口指令，其余一律拦截。 */
    public static boolean allowedWhileLocked(String rawCommand) {
        if (rawCommand == null) return false;
        String s = rawCommand.trim();
        if (s.startsWith("/")) s = s.substring(1).trim();
        if (s.isEmpty()) return false;
        String[] parts = s.split("\\s+");
        String head = parts[0].toLowerCase(Locale.ROOT);
        int colon = head.indexOf(':');
        if (colon >= 0) head = head.substring(colon + 1);
        if (ALLOWED_BARE.contains(head)) return true;
        if (head.equals("yztauth")) {
            if (parts.length < 2) return false;
            return ALLOWED_SUB.contains(parts[1].toLowerCase(Locale.ROOT));
        }
        return false;
    }

    private static boolean locked(ServerPlayer p) {
        return auth != null && auth.isPending(p);
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        CommandSourceStack src = event.getParseResults().getContext().getSource();
        ServerPlayer p = src.getPlayer();
        if (p == null || !locked(p)) return;
        String raw = event.getParseResults().getReader().getString();
        if (!allowedWhileLocked(raw)) {
            event.setCanceled(true);
            p.sendSystemMessage(Lang.msg("need_login"));
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer p)) return;
        PendingState st = PendingState.ALL.get(p.getUUID());
        if (st == null) return;
        auth.onTick(p);
        double dx = p.getX() - st.originX, dy = p.getY() - st.originY, dz = p.getZ() - st.originZ;
        if (dx * dx + dy * dy + dz * dz > 0.01) {
            p.teleportTo(st.originX, st.originY, st.originZ);
            p.setYRot(st.originYaw);
            p.setXRot(st.originPitch);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getEntity() instanceof ServerPlayer p && locked(p)) {
            e.setCanceled(true);
            p.sendSystemMessage(Lang.msg("need_login"));
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (e.getEntity() instanceof ServerPlayer p && locked(p)) {
            e.setCanceled(true);
            p.sendSystemMessage(Lang.msg("need_login"));
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p && locked(p)) {
            e.setCanceled(true);
            p.sendSystemMessage(Lang.msg("need_login"));
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && locked(p)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && locked(p)) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent e) {
        // 未登录玩家自身不可被伤害（防止旁人趁封锁期攻击）
        if (e.getEntity() instanceof ServerPlayer p && locked(p)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p && locked(p)) {
            e.setCanceled(true);
            p.getInventory().add(e.getEntity().getItem());
            LockdownSync.lock(p);
        }
    }

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && locked(p)) e.setCanceled(true);
    }
}
