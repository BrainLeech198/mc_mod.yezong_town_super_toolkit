package com.yztc.auth.risk;

import com.yztc.auth.data.Risk;
import com.yztc.auth.data.RiskStore;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.lang.ServerLang;
import com.yztc.auth.ui.ChatPrompt;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;

/**
 * 高风险操作记录：把管理类危险操作（重置密码/删号/强制登录登出/踢人/闸门增删/迁移/传送）
 * 落成可查、可标记处理的台账。记录只含 时间/操作者/动作/目标，<b>绝不含明文密码</b>。
 */
public final class RiskLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("yzt_super");

    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String ACCOUNT_DELETE = "ACCOUNT_DELETE";
    public static final String FORCE_LOGIN = "FORCE_LOGIN";
    public static final String FORCE_LOGOUT = "FORCE_LOGOUT";
    public static final String KICK = "KICK";
    public static final String GATE_SET = "GATE_SET";
    public static final String GATE_CLEAR = "GATE_CLEAR";
    public static final String MIGRATE = "MIGRATE";
    public static final String TELEPORT = "TELEPORT";

    private RiskLog() {}

    /** 纯逻辑：动作词 → 语言表显示名（未知动作原样返回）。 */
    public static String display(String action) {
        if (action == null) return "";
        return switch (action) {
            case PASSWORD_RESET -> ServerLang.template("risk_action_password_reset");
            case ACCOUNT_DELETE -> ServerLang.template("risk_action_account_delete");
            case FORCE_LOGIN -> ServerLang.template("risk_action_force_login");
            case FORCE_LOGOUT -> ServerLang.template("risk_action_force_logout");
            case KICK -> ServerLang.template("risk_action_kick");
            case GATE_SET -> ServerLang.template("risk_action_gate_set");
            case GATE_CLEAR -> ServerLang.template("risk_action_gate_clear");
            case MIGRATE -> ServerLang.template("risk_action_migrate");
            case TELEPORT -> ServerLang.template("risk_action_teleport");
            default -> action;
        };
    }

    /** 纯逻辑：单条记录的单行文本（不含明文）。 */
    public static String line(Risk r) {
        StringBuilder sb = new StringBuilder("#").append(r.id).append(" ").append(display(r.action));
        if (r.actor != null && !r.actor.isBlank()) sb.append(ServerLang.template("risk_by")).append(r.actor);
        if (r.target != null && !r.target.isBlank()) sb.append(" → ").append(r.target);
        return sb.toString();
    }

    /** 从指令来源取操作者名（控制台 → "(console)"）。 */
    public static String actorOf(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        return p == null ? "(console)" : p.getGameProfile().getName().toLowerCase(Locale.ROOT);
    }

    /** 记一条并落盘（写盘失败只记日志、不抛，避免拖垮原操作）。 */
    public static void record(String action, String actor, String target, String detail) {
        RiskStore store = StoreHolder.risk();
        if (store == null) return;
        Risk r = new Risk();
        r.at = System.currentTimeMillis();
        r.actor = (actor == null || actor.isBlank()) ? "(console)" : actor;
        r.action = action;
        r.target = target;
        r.detail = detail;
        store.add(r);
        try {
            store.save();
        } catch (IOException e) {
            LOGGER.warn("[yzt_super] risk.json 写入失败：{}", e.toString());
        }
    }

    /** 管理员登录后：若有未处理记录，发一个套框提醒（数量标题 + 「查看」按钮）。 */
    public static void notifyIfUnhandled(ServerPlayer p) {
        RiskStore store = StoreHolder.risk();
        if (store == null) return;
        int n = store.pendingCount();
        if (n <= 0) return;
        Component view = ChatPrompt.runButton(Lang.msg("btn_view"), "/yzts admin risk");
        for (Component c : com.yztc.auth.ui.ChatBox.box(
                Lang.msg("risk_alert", String.valueOf(n)), java.util.List.of(view))) {
            p.sendSystemMessage(c);
        }
    }
}
