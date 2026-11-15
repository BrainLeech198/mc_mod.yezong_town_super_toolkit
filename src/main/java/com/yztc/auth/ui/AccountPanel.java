package com.yztc.auth.ui;

import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.broadcast.BroadcastPrompt;
import com.yztc.auth.command.MailCommand;
import com.yztc.auth.data.Account;
import com.yztc.auth.data.Emails;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.title.TitlePanel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 个人中心（GUI）：上方只读资料行（注册时间 / 上次登录 / 上次登录 IP / 当前邮箱打码），
 * 下方各入口按钮（修改密码 / 改绑邮箱 / 我的留言 / 写信 / 邮箱管理 / 指南 / 登出）。
 * 只聚合现有能力；删除账号仅限管理员，此处不提供。
 */
public final class AccountPanel {
    private AccountPanel() {}

    /** 账号字段缺失时的占位（如 legacy 老号尚未回填）。 */
    private static final String UNSET = "—";

    public static void open(ServerPlayer viewer, AuthManager auth) {
        List<ButtonPanel.Btn> bs = new ArrayList<>();
        List<Component> info = profileInfo(account(viewer, auth), ZoneId.systemDefault());
        for (int i = 0; i < info.size(); i++) {
            bs.add(new ButtonPanel.Btn(Items.PAPER, info.get(i), null, i));
        }
        bs.add(new ButtonPanel.Btn(Items.NAME_TAG, Lang.msg("account_changepw"),
                v -> changePassword(v, auth), 9));
        bs.add(new ButtonPanel.Btn(Items.MAP, Lang.msg("account_email"),
                v -> EmailFlow.promptAddress(v, auth), 10));
        bs.add(new ButtonPanel.Btn(Items.WRITABLE_BOOK, Lang.msg("hub_mymail"),
                v -> MailCommand.showList(v), 11));
        bs.add(new ButtonPanel.Btn(Items.FEATHER, Lang.msg("btn_write"),
                MailPanel::openCompose, 12));
        bs.add(new ButtonPanel.Btn(Items.CHEST, Lang.msg("btn_manage"),
                MailPanel::openManage, 13));
        bs.add(new ButtonPanel.Btn(Items.KNOWLEDGE_BOOK, Lang.msg("btn_guide"),
                BookGuide::give, 14));
        bs.add(new ButtonPanel.Btn(Items.IRON_DOOR, Lang.msg("account_logout"),
                LogoutPrompt::send, 15));
        bs.add(new ButtonPanel.Btn(Items.BELL, Lang.msg("broadcast_shout"),
                BroadcastPrompt::openShout, 16));
        bs.add(new ButtonPanel.Btn(Items.NAME_TAG, Lang.msg("title_panel_title"),
                TitlePanel::open, 17));
        ButtonPanel.open(viewer, Lang.msg("account_title"), bs);
    }

    private static Account account(ServerPlayer v, AuthManager auth) {
        return auth.accountOf(v.getGameProfile().getName().toLowerCase(Locale.ROOT));
    }

    /** 纯逻辑：一行邮箱状态（未绑则提示未绑定，否则给打码地址）。 */
    public static Component emailLine(String email) {
        if (email == null || email.isBlank()) return Lang.msg("email_unbound");
        return Lang.msg("email_status", Emails.mask(email));
    }

    /** 纯逻辑：账号 → 四行只读资料（注册时间 / 上次登录 / 上次登录 IP / 当前邮箱）。 */
    public static List<Component> profileInfo(Account a, ZoneId zone) {
        List<Component> out = new ArrayList<>();
        out.add(Lang.msg("hub_registered",
                a == null || a.registeredAt == null ? UNSET : RiskPanel.timeText(a.registeredAt, zone)));
        out.add(Lang.msg("hub_last_login",
                a == null || a.lastLogin == null ? UNSET : RiskPanel.timeText(a.lastLogin, zone)));
        out.add(Lang.msg("hub_last_ip",
                a == null || a.lastIp == null || a.lastIp.isBlank() ? UNSET : a.lastIp));
        out.add(emailLine(a == null ? null : a.email));
        return out;
    }

    /** 先输当前密码，再输新密码，两次都经铁砧捕获后调用 changePassword。 */
    private static void changePassword(ServerPlayer v, AuthManager auth) {
        AnvilInput.openFlow(v, Lang.msg("account_changepw"), Lang.msg("changepw_prompt_old"), (player, oldPw) -> {
            if (!auth.verifyOwnPassword(v, oldPw)) {
                v.sendSystemMessage(Lang.msg("changepw_bad"));
                Sounds.fail(v);
                return AnvilInput.Response.retry(Lang.msg("changepw_prompt_old"));
            }
            return AnvilInput.Response.next(Lang.msg("changepw_prompt_new"), (ignoredPlayer, newPw) -> {
                boolean ok = auth.changePassword(v, oldPw, newPw);
                v.sendSystemMessage(Lang.msg(ok ? "changepw_ok" : "changepw_bad"));
                if (ok) Sounds.ok(v); else Sounds.fail(v);
                return ok ? AnvilInput.Response.close()
                        : AnvilInput.Response.retry(Lang.msg("changepw_prompt_new"));
            });
        }, () -> {});
    }
}
