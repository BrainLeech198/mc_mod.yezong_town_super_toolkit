package com.yztc.auth.title;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 称号纯逻辑：前缀组件、聊天/显示拼接、选用/发放校验、颜色映射。 */
public final class TitleService {
    private static final int ID_MAX = 16;

    private TitleService() {}

    /** 纯逻辑：称号 ID 是否合法（非空、≤16）。 */
    public static boolean validId(String id) {
        return id != null && !id.isBlank() && id.length() <= ID_MAX;
    }

    /** 纯逻辑：玩家能否选用该称号（须已定义且 owned 含 id）。 */
    public static boolean canSelect(String titleId, Set<String> owned, Map<String, TitleDef> defs) {
        return isDefined(titleId, defs) && owned != null && owned.contains(titleId);
    }

    /** 纯逻辑：能否发放（ID 合法且尚未拥有）。 */
    public static boolean canGive(String titleId, Set<String> owned) {
        return validId(titleId) && (owned == null || !owned.contains(titleId));
    }

    /** 纯逻辑：能否回收（ID 合法且已拥有）。 */
    public static boolean canTake(String titleId, Set<String> owned) {
        return validId(titleId) && owned != null && owned.contains(titleId);
    }

    /** 纯逻辑：颜色名 → ChatFormatting；非法名默认 GOLD。 */
    public static ChatFormatting colorOf(String colorName) {
        if (colorName == null) return ChatFormatting.GOLD;
        try {
            return ChatFormatting.valueOf(colorName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ChatFormatting.GOLD;
        }
    }

    /** 纯逻辑：前缀组件 `{color}{text}·`；无 selected 或未知 id 返回 empty。 */
    public static Component prefixFor(String selectedId, Map<String, TitleDef> defs) {
        if (selectedId == null || selectedId.isBlank()) return Component.empty();
        TitleDef d = defs == null ? null : defs.get(selectedId);
        if (d == null || d.text == null || d.text.isBlank()) return Component.empty();
        return Component.literal(d.text + "·").withStyle(colorOf(d.color));
    }

    /** 纯逻辑：聊天行 `{prefix}{name}: {rawText}`。 */
    public static Component formatChat(String playerName, String selectedId,
                                       Map<String, TitleDef> defs, String rawText) {
        MutableComponent c = Component.empty();
        c.append(prefixFor(selectedId, defs));
        c.append(Component.literal(playerName + ": " + rawText));
        return c;
    }

    /** 纯逻辑：头顶/Tab 显示 `{prefix}{name}`。 */
    public static Component formatDisplay(String playerName, String selectedId,
                                          Map<String, TitleDef> defs) {
        MutableComponent c = Component.empty();
        c.append(prefixFor(selectedId, defs));
        c.append(Component.literal(playerName));
        return c;
    }

    /** 纯逻辑：是否显示称号（须已登录且有 selected）。 */
    public static boolean shouldShowTitle(boolean authenticated, String selectedId) {
        return authenticated && selectedId != null && !selectedId.isBlank();
    }

    /** 纯逻辑：玩家名归一（去空白 + 小写，null 归空串）。 */
    public static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /** 纯逻辑：list 行 `id → text (color) ×n`。 */
    public static String listLine(TitleDef d, int ownedCount) {
        return "  " + d.id + " → " + d.text + " (" + d.color + ") ×" + ownedCount;
    }

    /** 纯逻辑：id 是否已在称号库定义（give 只收已定义 id）。 */
    public static boolean isDefined(String titleId, Map<String, TitleDef> defs) {
        return titleId != null && defs != null && defs.containsKey(titleId);
    }

    /** 纯逻辑：当前是否有选用的称号（clear 时区分「已取消」vs「本无称号」）。 */
    public static boolean hasSelected(String selected) {
        return selected != null && !selected.isBlank();
    }
}
