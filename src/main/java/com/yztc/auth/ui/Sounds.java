package com.yztc.auth.ui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * 操作反馈音效，一律用原版音效（不附带任何自定义音频）：
 *
 * <ul>
 *   <li>成功（登录/注册/过闸/改密）→ {@code levelup}（{@code ENTITY_PLAYER_LEVELUP}）</li>
 *   <li>失败（密码错/未注册/锁定/限流）→ {@code note.bass}（低沉的短板音）</li>
 *   <li>弹出输入框 → {@code note.pling}</li>
 *   <li>点击箱子面板按钮 → {@code ui.button.click}</li>
 *   <li>挂机玩家返回 → {@code entity.experience_orb.pickup}</li>
 * </ul>
 *
 * <p>用 {@code playNotifySound} 只发给该玩家本人，不惊动旁人。
 */
public final class Sounds {
    private Sounds() {}

    /** 成功：升到下一级的清脆音。 */
    public static void ok(ServerPlayer p) {
        play(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.0f);
    }

    /** 失败/拒绝：低沉短板音。 */
    public static void fail(ServerPlayer p) {
        play(p, SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.7f);
    }

    /** 弹出输入框的提示音。 */
    public static void prompt(ServerPlayer p) {
        play(p, SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f, 1.4f);
    }

    /** 按钮点击音。 */
    public static void click(ServerPlayer p) {
        play(p, SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
    }

    /** 挂机返回提示音：经验球拾取的清脆「叮」，比升级音更轻巧。 */
    public static void welcomeBack(ServerPlayer p) {
        play(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    private static void play(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        if (p == null || p.server == null) return;
        p.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }
}
