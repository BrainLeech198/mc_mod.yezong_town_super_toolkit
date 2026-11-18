package com.yztc.auth.ui;

import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.data.MailStore;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lang.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 登录成功后的聊天栏面板：三段式，各段以分隔线标题打头，段内内容统一缩进对齐。
 *
 * <ul>
 *   <li>登录时间 —— 当前时刻</li>
 *   <li>邮箱 —— 未读条数（有则发）+ 查看/写信/邮箱管理</li>
 *   <li>账号 —— 账户/指南</li>
 * </ul>
 *
 * <p>纯构建部分（时间格式化、按钮表、段标题）可自测；{@link #send} 负责取未读与发送。
 */
public final class LoginBoard {
    private static final DateTimeFormatter ZH =
            DateTimeFormatter.ofPattern("M月d日 EEEE HH:mm", Locale.CHINA);
    private static final DateTimeFormatter EN =
            DateTimeFormatter.ofPattern("MMM d, EEE HH:mm", Locale.ENGLISH);

    /** 段内缩进，让内容视觉上归属其标题。 */
    private static final String INDENT = "  ";
    /** 同一排按钮之间的间距。 */
    private static final String GAP = "    ";

    /** 一个文字按钮：语言键 + 命令；run=true 点击即执行（打开 GUI），否则只填入输入框。 */
    public record Btn(String labelKey, String command, boolean run) {}

    private LoginBoard() {}

    /** 邮箱段按钮：查看 / 写信 / 邮箱管理。 */
    public static List<Btn> mailButtons() {
        return List.of(
                new Btn("btn_view", "/yzts mail list", false),
                new Btn("btn_write", "/yzts mail send", true),
                new Btn("btn_manage", "/yzts mail manage", true));
    }

    /** 账号段按钮：账户 / 指南。 */
    public static List<Btn> accountButtons() {
        return List.of(
                new Btn("btn_account", "/yzts account", true),
                new Btn("btn_guide", "/yzts help", false),
                new Btn("btn_home", "/yzts menu", true));
    }

    /** 全部按钮（邮箱段 + 账号段，按显示顺序）。 */
    public static List<Btn> buttons() {
        List<Btn> all = new ArrayList<>(mailButtons());
        all.addAll(accountButtons());
        return all;
    }

    /** 把时间戳按给定时区格式化（中文/英文）。 */
    public static String dateTimeText(long epochMillis, ZoneId zone, boolean english) {
        return (english ? EN : ZH).format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    /** 段标题：青色加粗（正文时间行同为青色但不加粗，靠加粗与分隔线区分主次）。 */
    public static Component sectionHeader(String labelKey) {
        return Lang.msg(labelKey).copy().withStyle(ChatFormatting.BOLD);
    }

    /** 登录成功后发送面板：整块套一个双线外框，三个小节标题作为框内行。 */
    public static void send(ServerPlayer p, String nameLower) {
        boolean english = "en_us".equalsIgnoreCase(AuthConfig.language());
        String dt = dateTimeText(System.currentTimeMillis(), ZoneId.systemDefault(), english);

        List<Component> lines = new ArrayList<>();
        lines.add(sectionHeader("login_board_sec_time"));
        lines.add(indent(Lang.msg("login_board_time", dt)));

        MailStore mail = StoreHolder.mail();
        int unread = mail == null ? 0 : mail.unreadFor(nameLower);
        lines.add(sectionHeader("login_board_sec_mail"));
        if (unread > 0) lines.add(indent(Lang.msg("login_board_unread", String.valueOf(unread))));
        lines.add(buttonRow(mailButtons()));

        lines.add(sectionHeader("login_board_sec_account"));
        lines.add(buttonRow(accountButtons()));

        for (Component c : ChatBox.box(Lang.msg("login_board_title"), lines)) p.sendSystemMessage(c);
    }

    /** 给内容加段内缩进。 */
    private static Component indent(Component c) {
        return Component.literal(INDENT).append(c);
    }

    /** 一排按钮（段内缩进 + 固定间距；GUI 类点击即开，其余只填入命令）。 */
    public static Component buttonRow(List<Btn> bs) {
        MutableComponent row = Component.literal(INDENT);
        for (int i = 0; i < bs.size(); i++) {
            if (i > 0) row.append(Component.literal(GAP));
            Btn b = bs.get(i);
            Component label = Lang.msg(b.labelKey());
            row.append(b.run() ? ChatPrompt.runButton(label, b.command())
                    : ChatPrompt.button(label, b.command()));
        }
        return row;
    }
}
