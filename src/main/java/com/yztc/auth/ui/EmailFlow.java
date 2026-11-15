package com.yztc.auth.ui;

import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.data.Emails;
import com.yztc.auth.lang.Lang;
import net.minecraft.server.level.ServerPlayer;

/**
 * 邮箱绑定/改绑流程。
 *
 * <p>规则：邮箱**只能设一次**。首次 {@code /yzts account email <地址>} 直接绑定；已绑定后要改，
 * 必须先在铁砧里输「当前邮箱」、再输「账户密码」，两项都对才换成新地址。
 */
public final class EmailFlow {
    private EmailFlow() {}

    /** 账户面板入口：先让玩家在铁砧里输入（新）邮箱地址，再交给 {@link #apply}。 */
    public static void promptAddress(ServerPlayer p, AuthManager auth) {
        AnvilInput.openFlow(p, Lang.msg("account_email"), Lang.msg("email_prompt_new"),
                (player, addr) -> addressStep(p, auth, addr), () -> {});
    }

    /** 绑定或改绑：地址格式不合法直接拒绝；否则按「是否已绑定」分流到直连绑定或验证链。 */
    public static void apply(ServerPlayer p, AuthManager auth, String addr) {
        String a = addr == null ? "" : addr.trim();
        if (!Emails.looksValid(a)) { p.sendSystemMessage(Lang.msg("email_bad")); return; }
        switch (auth.bindEmail(p, a)) {
            case SET -> {
                p.sendSystemMessage(Lang.msg("email_set"));
                Sounds.ok(p);
            }
            case NO_ACCOUNT -> {
                p.sendSystemMessage(Lang.msg("not_registered"));
                Sounds.fail(p);
            }
            case ALREADY_SET -> change(p, auth, a);
            default -> {
                p.sendSystemMessage(Lang.msg("email_change_fail"));
                Sounds.fail(p);
            }
        }
    }

    /** 改绑验证链：铁砧① 旧邮箱 → 铁砧② 账户密码 → 一致才把邮箱改成 {@code newAddr}。 */
    private static void change(ServerPlayer p, AuthManager auth, String newAddr) {
        AnvilInput.openFlow(p, Lang.msg("account_email"), Lang.msg("email_prompt_old"),
                (player, oldEmail) -> oldEmailStep(p, auth, newAddr, oldEmail), () -> {});
    }

    private static AnvilInput.Response addressStep(ServerPlayer p, AuthManager auth, String addr) {
        String normalized = addr == null ? "" : addr.trim();
        if (!Emails.looksValid(normalized)) {
            p.sendSystemMessage(Lang.msg("email_bad"));
            Sounds.fail(p);
            return AnvilInput.Response.retry(Lang.msg("email_prompt_new"));
        }
        return switch (auth.bindEmail(p, normalized)) {
            case SET -> {
                p.sendSystemMessage(Lang.msg("email_set"));
                Sounds.ok(p);
                yield AnvilInput.Response.close();
            }
            case NO_ACCOUNT -> {
                p.sendSystemMessage(Lang.msg("not_registered"));
                Sounds.fail(p);
                yield AnvilInput.Response.close();
            }
            case ALREADY_SET -> AnvilInput.Response.next(Lang.msg("email_prompt_old"),
                    (player, oldEmail) -> oldEmailStep(p, auth, normalized, oldEmail));
            default -> {
                p.sendSystemMessage(Lang.msg("email_change_fail"));
                Sounds.fail(p);
                yield AnvilInput.Response.retry(Lang.msg("email_prompt_new"));
            }
        };
    }

    private static AnvilInput.Response oldEmailStep(ServerPlayer p, AuthManager auth, String newAddr, String oldEmail) {
        return AnvilInput.Response.next(Lang.msg("email_prompt_pw"), (player, oldPw) -> {
            AuthManager.EmailResult result = auth.changeEmail(p, oldEmail, oldPw, newAddr);
            switch (result) {
                case SET -> {
                    p.sendSystemMessage(Lang.msg("email_changed"));
                    Sounds.ok(p);
                    return AnvilInput.Response.close();
                }
                case BAD_OLD_EMAIL -> {
                    p.sendSystemMessage(Lang.msg("email_bad_old"));
                    Sounds.fail(p);
                    return AnvilInput.Response.next(Lang.msg("email_prompt_old"),
                            (ignoredPlayer, retryOld) -> oldEmailStep(p, auth, newAddr, retryOld));
                }
                case BAD_PASSWORD -> {
                    p.sendSystemMessage(Lang.msg("email_bad_pw"));
                    Sounds.fail(p);
                    return AnvilInput.Response.retry(Lang.msg("email_prompt_pw"));
                }
                default -> {
                    p.sendSystemMessage(Lang.msg("email_change_fail"));
                    Sounds.fail(p);
                    return AnvilInput.Response.close();
                }
            }
        });
    }
}
