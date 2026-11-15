package com.yztc.auth.lang;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;
import java.util.Set;

/**
 * 语言键 → 组件，并在同一处统一上色。
 *
 * <p>配色语义（与 WelcomeSync 的「金标题 / 黄副标题 / 绿按钮」一致）：
 * <ul>
 *   <li>绿 {@link ChatFormatting#GREEN} — 成功、已完成</li>
 *   <li>红 {@link ChatFormatting#RED} — 失败、被拒、无权限、未登录拦截</li>
 *   <li>金 {@link ChatFormatting#GOLD} — 需注意：安全提醒、未读、危险操作</li>
 *   <li>黄 {@link ChatFormatting#YELLOW} — 引导与轻提醒</li>
 *   <li>青 {@link ChatFormatting#AQUA} — 中性信息、查询结果、状态行</li>
 *   <li>灰 {@link ChatFormatting#GRAY} — 次要信息、空态</li>
 * </ul>
 * 未列入调色板的键保持默认（白）；标题/副标题/按钮由调用方另行套样式，故不入表。
 */
public final class Lang {
    private static final String PREFIX = "yzt_super.msg.";

    private static final Map<String, ChatFormatting> PALETTE = Map.ofEntries(
            // 成功
            Map.entry("gate_ok", ChatFormatting.GREEN),
            Map.entry("login_ok", ChatFormatting.GREEN),
            Map.entry("register_ok", ChatFormatting.GREEN),
            Map.entry("logout_ok", ChatFormatting.GREEN),
            Map.entry("changepw_ok", ChatFormatting.GREEN),
            Map.entry("email_set", ChatFormatting.GREEN),
            Map.entry("email_changed", ChatFormatting.GREEN),
            Map.entry("confirm_ok", ChatFormatting.GREEN),
            Map.entry("anvil_submit_hint", ChatFormatting.GREEN),
            Map.entry("mail_sent", ChatFormatting.GREEN),
            Map.entry("mail_posted", ChatFormatting.GREEN),
            Map.entry("mail_del_ok", ChatFormatting.GREEN),
            Map.entry("risk_done_ok", ChatFormatting.GREEN),
            Map.entry("title_set_ok", ChatFormatting.GREEN),
            Map.entry("title_clear_ok", ChatFormatting.GREEN),
            Map.entry("title_gave", ChatFormatting.GREEN),
            Map.entry("title_taken", ChatFormatting.GREEN),
            Map.entry("title_defined", ChatFormatting.GREEN),
            Map.entry("title_undefined", ChatFormatting.GREEN),
            Map.entry("tp_result", ChatFormatting.GREEN),
            Map.entry("admin_force_login", ChatFormatting.GREEN),
            Map.entry("strength_strong", ChatFormatting.GREEN),
            Map.entry("gate_set_ok", ChatFormatting.GREEN),
            Map.entry("gate_clear_ok", ChatFormatting.GREEN),
            Map.entry("admin_reset_ok", ChatFormatting.GREEN),
            Map.entry("admin_delete_ok", ChatFormatting.GREEN),
            Map.entry("admin_force_login_ok", ChatFormatting.GREEN),
            Map.entry("admin_kick_ok", ChatFormatting.GREEN),
            Map.entry("migrate_done", ChatFormatting.GREEN),
            // 失败 / 被拒
            Map.entry("gate_bad", ChatFormatting.RED),
            Map.entry("login_bad", ChatFormatting.RED),
            Map.entry("login_locked", ChatFormatting.RED),
            Map.entry("login_rate_limited", ChatFormatting.RED),
            Map.entry("login_bad_remaining", ChatFormatting.RED),
            Map.entry("register_too_short", ChatFormatting.RED),
            Map.entry("changepw_bad", ChatFormatting.RED),
            Map.entry("email_bad", ChatFormatting.RED),
            Map.entry("email_bad_old", ChatFormatting.RED),
            Map.entry("email_bad_pw", ChatFormatting.RED),
            Map.entry("email_change_fail", ChatFormatting.RED),
            Map.entry("confirm_bad", ChatFormatting.RED),
            Map.entry("risk_done_none", ChatFormatting.RED),
            Map.entry("no_permission", ChatFormatting.RED),
            Map.entry("need_login", ChatFormatting.RED),
            Map.entry("timeout", ChatFormatting.RED),
            Map.entry("admin_kick", ChatFormatting.RED),
            Map.entry("admin_delete_kick", ChatFormatting.RED),
            Map.entry("mail_empty", ChatFormatting.RED),
            Map.entry("mail_no_recipient", ChatFormatting.RED),
            Map.entry("mail_full", ChatFormatting.RED),
            Map.entry("mail_del_none", ChatFormatting.RED),
            Map.entry("mail_del_denied", ChatFormatting.RED),
            Map.entry("tp_bad_dimension", ChatFormatting.RED),
            Map.entry("strength_weak", ChatFormatting.RED),
            Map.entry("err_save", ChatFormatting.RED),
            Map.entry("title_bad_id", ChatFormatting.RED),
            Map.entry("title_bad_color", ChatFormatting.RED),
            Map.entry("title_not_owned", ChatFormatting.RED),
            Map.entry("anvil_cancelled", ChatFormatting.RED),
            Map.entry("err_mail_save", ChatFormatting.RED),
            Map.entry("admin_no_account", ChatFormatting.RED),
            Map.entry("admin_no_online_player", ChatFormatting.RED),
            Map.entry("in_game_only", ChatFormatting.RED),
            Map.entry("migrate_no_file", ChatFormatting.RED),
            Map.entry("migrate_parse_fail", ChatFormatting.RED),
            Map.entry("migrate_fail_line", ChatFormatting.RED),
            // 需注意
            Map.entry("confirm_prompt", ChatFormatting.GOLD),
            Map.entry("login_new_ip", ChatFormatting.GOLD),
            Map.entry("mail_unread_notice", ChatFormatting.GOLD),
            Map.entry("mail_notify", ChatFormatting.GOLD),
            Map.entry("admin_login_notice", ChatFormatting.GOLD),
            Map.entry("password_strength", ChatFormatting.GOLD),
            Map.entry("strength_mid", ChatFormatting.GOLD),
            Map.entry("risk_alert", ChatFormatting.GOLD),
            Map.entry("risk_detail_title", ChatFormatting.AQUA),
            Map.entry("risk_row_time", ChatFormatting.AQUA),
            Map.entry("risk_row_action", ChatFormatting.AQUA),
            Map.entry("risk_row_actor", ChatFormatting.AQUA),
            Map.entry("risk_row_target", ChatFormatting.AQUA),
            Map.entry("risk_row_detail", ChatFormatting.AQUA),
            Map.entry("risk_detail_none", ChatFormatting.GRAY),
            Map.entry("btn_mark_handled", ChatFormatting.GREEN),
            Map.entry("btn_back", ChatFormatting.YELLOW),
            Map.entry("admin_confirm_view_mail", ChatFormatting.GOLD),
            Map.entry("admin_confirm_reset_pw", ChatFormatting.GOLD),
            Map.entry("admin_confirm_force_login", ChatFormatting.GOLD),
            Map.entry("admin_confirm_force_logout", ChatFormatting.GOLD),
            Map.entry("admin_confirm_kick", ChatFormatting.GOLD),
            Map.entry("admin_confirm_delete", ChatFormatting.GOLD),
            Map.entry("gate_confirm_set", ChatFormatting.GOLD),
            Map.entry("gate_confirm_clear", ChatFormatting.GOLD),
            Map.entry("title_confirm_give", ChatFormatting.GOLD),
            Map.entry("title_confirm_take", ChatFormatting.GOLD),
            // 引导 / 轻提醒
            Map.entry("gate_not_set", ChatFormatting.YELLOW),
            Map.entry("anvil_howto", ChatFormatting.YELLOW),
            Map.entry("anvil_input_hint", ChatFormatting.AQUA),
            Map.entry("gate_required", ChatFormatting.YELLOW),
            Map.entry("logout_choose", ChatFormatting.YELLOW),
            Map.entry("not_registered", ChatFormatting.YELLOW),
            Map.entry("already_logged_in", ChatFormatting.YELLOW),
            Map.entry("register_exists", ChatFormatting.YELLOW),
            Map.entry("title_already_owned", ChatFormatting.YELLOW),
            Map.entry("afk_logout", ChatFormatting.YELLOW),
            Map.entry("admin_force_logout", ChatFormatting.YELLOW),
            Map.entry("mail_compose_manual", ChatFormatting.YELLOW),
            Map.entry("broadcast_say", ChatFormatting.AQUA),
            Map.entry("broadcast_shout", ChatFormatting.AQUA),
            Map.entry("broadcast_cooldown", ChatFormatting.YELLOW),
            Map.entry("broadcast_empty", ChatFormatting.RED),
            Map.entry("broadcast_too_long", ChatFormatting.RED),
            Map.entry("broadcast_sent", ChatFormatting.GREEN),
            Map.entry("tp_console", ChatFormatting.YELLOW),
            // 中性信息
            Map.entry("status_line", ChatFormatting.AQUA),
            Map.entry("where_result", ChatFormatting.AQUA),
            Map.entry("first_join_broadcast", ChatFormatting.AQUA),
            Map.entry("mail_list_header", ChatFormatting.AQUA),
            Map.entry("mail_board_header", ChatFormatting.AQUA),
            Map.entry("manage_header", ChatFormatting.AQUA),
            Map.entry("account_title", ChatFormatting.AQUA),
            Map.entry("mail_compose_title", ChatFormatting.AQUA),
            Map.entry("mail_compose_manual_title", ChatFormatting.AQUA),
            Map.entry("mail_detail_title", ChatFormatting.AQUA),
            Map.entry("mail_manage_title", ChatFormatting.AQUA),
            Map.entry("risk_title", ChatFormatting.AQUA),
            Map.entry("title_list_header", ChatFormatting.AQUA),
            Map.entry("title_panel_title", ChatFormatting.AQUA),
            Map.entry("risk_all_header", ChatFormatting.AQUA),
            Map.entry("login_board_sec_time", ChatFormatting.AQUA),
            Map.entry("login_board_sec_mail", ChatFormatting.AQUA),
            Map.entry("login_board_sec_account", ChatFormatting.AQUA),
            Map.entry("login_board_time", ChatFormatting.AQUA),
            Map.entry("login_board_unread", ChatFormatting.GOLD),
            Map.entry("admin_panel_title", ChatFormatting.AQUA),
            Map.entry("admin_page_indicator", ChatFormatting.AQUA),
            Map.entry("admin_btn_prev", ChatFormatting.AQUA),
            Map.entry("admin_btn_next", ChatFormatting.AQUA),
            Map.entry("btn_prev", ChatFormatting.AQUA),
            Map.entry("btn_next", ChatFormatting.AQUA),
            Map.entry("hub_registered", ChatFormatting.AQUA),
            Map.entry("hub_last_login", ChatFormatting.AQUA),
            Map.entry("hub_last_ip", ChatFormatting.AQUA),
            Map.entry("mail_row_from", ChatFormatting.AQUA),
            Map.entry("mail_row_to", ChatFormatting.AQUA),
            Map.entry("mail_row_time", ChatFormatting.AQUA),
            Map.entry("mail_row_text", ChatFormatting.AQUA),
            Map.entry("email_status", ChatFormatting.AQUA),
            Map.entry("admin_mail_is", ChatFormatting.AQUA),
            Map.entry("migrate_skip_line", ChatFormatting.AQUA),
            // 次要 / 空态
            Map.entry("mail_none", ChatFormatting.GRAY),
            Map.entry("title_none", ChatFormatting.GRAY),
            Map.entry("title_no_owned", ChatFormatting.GRAY),
            Map.entry("title_no_display", ChatFormatting.GRAY),
            Map.entry("risk_none", ChatFormatting.GRAY),
            Map.entry("risk_handled_tag", ChatFormatting.GRAY),
            Map.entry("where_none", ChatFormatting.GRAY),
            Map.entry("help", ChatFormatting.GRAY),
            Map.entry("prompt_countdown", ChatFormatting.GRAY),
            Map.entry("admin_no_online", ChatFormatting.GRAY),
            Map.entry("admin_no_accounts", ChatFormatting.GRAY),
            Map.entry("admin_email_unset", ChatFormatting.GRAY),
            Map.entry("email_unbound", ChatFormatting.GRAY)
    );

    private Lang() {}

    public static String key(String id) {
        return PREFIX + id;
    }

    /** 供自测校验：调色板涉及的全部消息 id。 */
    public static Set<String> paletteKeys() {
        return PALETTE.keySet();
    }

    /** 取消息组件；命中调色板的会套上对应颜色。原版客户端无本 mod 语言键，退回到服务端兜底模板。 */
    public static Component msg(String id, Object... args) {
        MutableComponent c = Component.translatableWithFallback(PREFIX + id, ServerLang.template(id), args);
        ChatFormatting color = PALETTE.get(id);
        return color == null ? c : c.withStyle(color);
    }
}
