package com.yztc.auth.selftest;

import com.yztc.auth.auth.RateLimiter;
import com.yztc.auth.data.Account;
import com.yztc.auth.data.AccountStore;
import com.yztc.auth.data.Gate;
import com.yztc.auth.security.PasswordHasher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class SelfTestMain {
    public static void main(String[] args) {
        Assert.section("自测框架自检");
        Assert.that(true, "自测框架可运行");

        loadDefaultConfig();
        testConfig();
        testPasswordHasher();
        testAccountStore();
        testRateLimiter();
        testMigrateService();
        testLang();
        testLangKeysReferenced();
        testGateManager();
        testPasswordUtil();
        testUiSession();
        testChatPrompt();
        testBookGuide();
        testVerifyPassword();
        testGateRequired();
        testGateAttempt();
        testLogoutThrottle();
        testGateChangePolicy();
        testAllowedWhileLocked();
        testPromptStyle();
        testFlashCadence();
        testModsToml();
        testWelcome();
        testPasswordStrength();
        testAuditLog();
        testMailStore();
        testLastSeenStore();
        testAuthPure();
        testTabHider();
        testLocationPure();
        testStatusRender();
        testMailCommand();

        System.exit(Assert.summaryAndExit());
    }

    // 闸门决策：未过闸者不得进入登录/注册（否则全服闸门形同虚设）
    private static void testGateRequired() {
        Assert.section("AuthManager.gateRequired");
        Assert.that(!com.yztc.auth.auth.AuthManager.gateRequired(false, false, false),
                "未设全服闸门 → 无需过闸");
        Assert.that(com.yztc.auth.auth.AuthManager.gateRequired(true, false, false),
                "已设闸门 + 未登录 + 未过闸 → 必须先过闸");
        Assert.that(!com.yztc.auth.auth.AuthManager.gateRequired(true, false, true),
                "已过闸（gatePassed 被读取）→ 不再要求过闸");
        Assert.that(!com.yztc.auth.auth.AuthManager.gateRequired(true, true, false),
                "已登录 → 不因闸门受阻");
        Assert.that(!com.yztc.auth.auth.AuthManager.gateRequired(true, true, true),
                "已登录且已过闸 → 不要求过闸");
        Assert.that(!com.yztc.auth.auth.AuthManager.gateRequired(false, true, false),
                "未设闸门且已登录 → 不要求过闸");
    }

    // 闸门验密必须与登录共享 IP 限流，否则可无限暴力猜全服密码
    private static void testGateAttempt() {
        Assert.section("GateManager.attempt（限流+验密）");
        java.util.function.Supplier<com.yztc.auth.auth.GateManager.GateResult> yes =
                () -> com.yztc.auth.auth.GateManager.GateResult.OK;
        java.util.function.Supplier<com.yztc.auth.auth.GateManager.GateResult> no =
                () -> com.yztc.auth.auth.GateManager.GateResult.BAD;

        var rl1 = new RateLimiter(1, 60_000);
        Assert.eq(com.yztc.auth.auth.GateManager.attempt(rl1, "ip", 0, false, yes).name(),
                "NOT_SET", "未设闸门 → NOT_SET");
        Assert.eq(com.yztc.auth.auth.GateManager.attempt(rl1, "ip", 0, true, no).name(),
                "BAD", "未设闸门的尝试不消耗额度");
        Assert.eq(com.yztc.auth.auth.GateManager.attempt(rl1, "ip", 0, true, no).name(),
                "RATE_LIMITED", "额度用尽后闸门尝试被 IP 限流");

        var rl2 = new RateLimiter(2, 60_000);
        Assert.eq(com.yztc.auth.auth.GateManager.attempt(rl2, "ip", 0, true, no).name(),
                "BAD", "第 1 次错误密码 → BAD");
        Assert.eq(com.yztc.auth.auth.GateManager.attempt(rl2, "ip", 1, true, no).name(),
                "BAD", "第 2 次错误密码 → BAD");
        Assert.eq(com.yztc.auth.auth.GateManager.attempt(rl2, "ip", 2, true, no).name(),
                "RATE_LIMITED", "第 3 次 → RATE_LIMITED");

        var rl3 = new RateLimiter(2, 60_000);
        Assert.eq(com.yztc.auth.auth.GateManager.attempt(rl3, "ip", 0, true, yes).name(),
                "OK", "正确密码 → OK");
        Assert.eq(com.yztc.auth.auth.GateManager.attempt(rl3, "ip", 1, true, yes).name(),
                "OK", "成功后重置额度：第 2 次仍 OK");
        Assert.eq(com.yztc.auth.auth.GateManager.attempt(rl3, "ip", 2, true, yes).name(),
                "OK", "成功后重置额度：第 3 次仍 OK");
        Assert.eq(com.yztc.auth.auth.GateManager.attempt(rl3, "other", 2, true, no).name(),
                "BAD", "换 IP 不受影响");
    }

    // disconnect-throttle 必须是「限时」禁重连，而非永久封禁
    private static void testLogoutThrottle() {
        Assert.section("LogoutThrottle");
        var t = new com.yztc.auth.auth.LogoutThrottle();
        var id = java.util.UUID.randomUUID();
        var other = java.util.UUID.randomUUID();
        Assert.that(!t.blocked(id, 1000), "未标记时放行");
        t.mark(id, 1000, 10); // 禁至 11000
        Assert.that(t.blocked(id, 1000), "刚登出即被禁重连");
        Assert.that(t.blocked(id, 10_999), "未到期仍被禁");
        Assert.that(!t.blocked(id, 11_000), "到期即解禁（不是永久封禁）");
        Assert.that(!t.blocked(id, 11_000), "到期条目已淘汰，重复查询仍放行");
        Assert.eq(t.trackedKeys(), 0, "到期条目被淘汰，不无限增长");
        t.mark(other, 0, 10);
        Assert.that(t.blocked(other, 5000), "另一个 UUID 独立计时");
        Assert.that(!t.blocked(id, 5000), "不同 UUID 互不影响");
        t.clear();
        Assert.that(!t.blocked(other, 5000), "clear 后解禁");
        t.mark(other, 0, 0);
        Assert.that(!t.blocked(other, 0), "时长 0 视为不禁止（可关闭该模式）");
    }

    // 全服闸门改动：控制台免二次验密，游戏内必须验操作者自己的密码
    private static void testGateChangePolicy() {
        Assert.section("AdminCommands.gateChangeAllowed");
        Assert.that(com.yztc.auth.command.AdminCommands.gateChangeAllowed(false, false),
                "控制台 → 免二次验密");
        Assert.that(!com.yztc.auth.command.AdminCommands.gateChangeAllowed(true, false),
                "游戏内未验密 → 拒绝改闸门");
        Assert.that(com.yztc.auth.command.AdminCommands.gateChangeAllowed(true, true),
                "游戏内验密通过 → 放行");
    }

    private static void testVerifyPassword() {
        Assert.section("AuthManager.verifyPassword");
        try {
            Path dir = Files.createTempDirectory("yztauth-vpw");
            AccountStore store = new AccountStore(dir.resolve("users.json"));
            store.load();
            var auth = new com.yztc.auth.auth.AuthManager(
                    store, new com.yztc.auth.auth.GateManager(store));
            auth.load();

            Account pbk = new Account();
            com.yztc.auth.data.PasswordUtil.assign(pbk, "correct-horse", 1000);
            store.put("alice", pbk);
            Assert.that(auth.verifyPassword("alice", "correct-horse"), "PBKDF2 正确密码通过");
            Assert.that(!auth.verifyPassword("alice", "wrong"), "PBKDF2 错误密码拒绝");

            Account leg = new Account();
            leg.algo = PasswordHasher.ALGO_LEGACY;
            leg.hash = PasswordHasher.legacySha256Hex("oldpass".toCharArray());
            store.put("bob", leg);
            Assert.that(auth.verifyPassword("bob", "oldpass"), "legacy 正确密码通过（hex 大小写不敏感）");
            Assert.that(!auth.verifyPassword("bob", "nope"), "legacy 错误密码拒绝");

            Assert.that(!auth.verifyPassword("ghost", "whatever"), "不存在的账号返回 false");
        } catch (Exception e) {
            Assert.that(false, "verifyPassword 测试异常: " + e);
        }
    }

    private static void testChatPrompt() {
        Assert.section("ChatPrompt");
        var btn = com.yztc.auth.ui.ChatPrompt.button(
                net.minecraft.network.chat.Component.literal("▶ 点这里登录"), "/yztauth login ");
        var style = btn.getStyle();
        Assert.that(style.getClickEvent() != null, "按钮带 clickEvent");
        Assert.eq(style.getClickEvent().getAction(),
                net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, "clickEvent 动作为 suggest_command");
        Assert.eq(style.getClickEvent().getValue(), "/yztauth login ", "clickEvent 值为建议命令");
        Assert.that(style.getHoverEvent() != null, "按钮带 hoverEvent");
        Assert.eq(style.getHoverEvent().getAction(),
                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, "hoverEvent 动作为 show_text");
        Assert.eq(btn.getString(), "▶ 点这里登录", "按钮文本保留");
        Assert.eq(style.getColor().getValue(),
                net.minecraft.ChatFormatting.GREEN.getColor().intValue(), "按钮为绿色");
        Assert.that(style.isUnderlined(), "按钮带下划线");

        var plain = net.minecraft.network.chat.Component.literal("x");
        var styled = com.yztc.auth.ui.ChatPrompt.button(plain, "/yztauth help");
        Assert.that(plain.getStyle().getClickEvent() == null, "原组件不被就地修改（copy 后再套样式）");
        Assert.that(styled.getStyle().getClickEvent() != null, "样式只作用于副本");
    }

    // 未登录玩家只放行认证入口指令，其余一律拦截（安全漏洞修复）
    private static void testAllowedWhileLocked() {
        Assert.section("LockdownHandler.allowedWhileLocked");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/login secret"), "裸 /login 放行");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/register secret"), "裸 /register 放行");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("login secret"), "无斜杠 login 也放行");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yztauth gate secret"), "/yztauth gate 放行");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yztauth login secret"), "/yztauth login 放行");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yztauth register secret"), "/yztauth register 放行");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yztauth help"), "/yztauth help 放行");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("minecraft:login secret"), "带命名空间的 login 放行");

        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yztauth logout"), "登出被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yztauth changepassword a b"), "改密被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yztauth admin"), "管理面板被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yztauth serverpassword set x"), "改闸门被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yztauth op delete alice"), "管理操作被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/gamemode creative"), "原版指令被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/op alice"), "OP 被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/say hi"), "聊天类指令被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yztauth"), "裸 /yztauth 被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked(""), "空串被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("   "), "纯空白被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked(null), "null 被拦");
    }

    // 提示样式：金加粗标题 / 黄副标题 / 绿下划线可点按钮，且聊天栏按钮文本 != 副标题
    private static void testPromptStyle() {
        Assert.section("WelcomeSync 提示样式");
        int gold = net.minecraft.ChatFormatting.GOLD.getColor();
        int yellow = net.minecraft.ChatFormatting.YELLOW.getColor();
        int green = net.minecraft.ChatFormatting.GREEN.getColor();

        var loginTitle = com.yztc.auth.ui.WelcomeSync.title(false);
        Assert.eq(loginTitle.getStyle().getColor().getValue(), gold, "登录标题为金色");
        Assert.that(loginTitle.getStyle().isBold(), "登录标题加粗");
        var gateTitle = com.yztc.auth.ui.WelcomeSync.title(true);
        Assert.eq(gateTitle.getStyle().getColor().getValue(), gold, "闸门标题为金色");

        var loginSub = com.yztc.auth.ui.WelcomeSync.sub(true);
        Assert.eq(loginSub.getStyle().getColor().getValue(), yellow, "登录副标题为黄色");
        var regSub = com.yztc.auth.ui.WelcomeSync.sub(false);
        Assert.eq(regSub.getStyle().getColor().getValue(), yellow, "注册副标题为黄色");
        var gateSub = com.yztc.auth.ui.WelcomeSync.gateSub();
        Assert.eq(gateSub.getStyle().getColor().getValue(), yellow, "闸门副标题为黄色");

        var loginBtn = com.yztc.auth.ui.WelcomeSync.button(false, true);
        Assert.eq(loginBtn.getStyle().getColor().getValue(), green, "登录按钮为绿色");
        Assert.that(loginBtn.getStyle().isUnderlined(), "登录按钮带下划线");
        Assert.that(loginBtn.getStyle().getClickEvent() != null, "登录按钮可点击");
        Assert.eq(loginBtn.getStyle().getClickEvent().getValue(), "/yztauth login ", "登录按钮建议命令正确");

        var regBtn = com.yztc.auth.ui.WelcomeSync.button(false, false);
        Assert.eq(regBtn.getStyle().getClickEvent().getValue(), "/yztauth register ", "注册按钮建议命令正确");
        var gateBtn = com.yztc.auth.ui.WelcomeSync.button(true, false);
        Assert.eq(gateBtn.getStyle().getClickEvent().getValue(), "/yztauth gate ", "闸门按钮建议命令正确");

        // 修复点：聊天栏按钮文本必须与副标题不同（此前副标题被错当聊天栏内容）
        Assert.that(!loginBtn.getString().equals(loginSub.getString()),
                "登录聊天栏按钮文本 != 副标题文本");
        Assert.that(!gateBtn.getString().equals(gateSub.getString()),
                "闸门聊天栏按钮文本 != 副标题文本");
    }

    // 标题持续闪烁：按固定间隔重发，间隔外不重发
    private static void testFlashCadence() {
        Assert.section("AuthManager.flashDue / 闪烁间隔");
        long iv = com.yztc.auth.auth.AuthManager.TITLE_FLASH_INTERVAL_MS;
        Assert.that(iv > 0 && iv <= 3000, "闪烁间隔在合理区间（实得 " + iv + "ms）");
        Assert.that(!com.yztc.auth.auth.AuthManager.flashDue(1000, 1000, iv), "刚到不闪");
        Assert.that(!com.yztc.auth.auth.AuthManager.flashDue(1000 + iv - 1, 1000, iv), "差 1ms 不闪");
        Assert.that(com.yztc.auth.auth.AuthManager.flashDue(1000 + iv, 1000, iv), "到达间隔即闪");
        Assert.that(com.yztc.auth.auth.AuthManager.flashDue(1000 + iv * 3, 1000, iv), "超过间隔仍闪");
    }

    // mods.toml 必须显式要求客户端装有本 mod（版本匹配），否则裸指令/翻译会缺失
    private static void testModsToml() {
        Assert.section("mods.toml displayTest");
        try {
            java.nio.file.Path toml = java.nio.file.Path.of("src", "main", "resources", "META-INF", "mods.toml");
            Assert.that(java.nio.file.Files.isRegularFile(toml), "mods.toml 存在：" + toml.toAbsolutePath());
            String text = java.nio.file.Files.readString(toml, java.nio.charset.StandardCharsets.UTF_8);
            Assert.that(text.contains("displayTest=\"MATCH_VERSION\""),
                    "mods.toml 显式声明 displayTest=\"MATCH_VERSION\"（客户端缺少本 mod 即拒绝登入）");
            Assert.that(text.contains("logoFile="), "mods.toml 声明 logoFile（PCL2 列表图标）");
            Assert.that(text.contains("authors="), "mods.toml 声明 authors");
            Assert.that(java.nio.file.Files.isRegularFile(
                            java.nio.file.Path.of("src", "main", "resources", "logo.png")),
                    "resources/logo.png 存在");
        } catch (Exception e) {
            Assert.that(false, "mods.toml 测试异常: " + e);
        }
    }

    // 登录成功后要有欢迎语（金标题 + 黄副标题）
    private static void testWelcome() {
        Assert.section("WelcomeSync 欢迎语");
        int gold = net.minecraft.ChatFormatting.GOLD.getColor();
        int yellow = net.minecraft.ChatFormatting.YELLOW.getColor();
        var t = com.yztc.auth.ui.WelcomeSync.welcomeTitle();
        Assert.eq(t.getStyle().getColor().getValue(), gold, "欢迎标题为金色");
        Assert.that(t.getStyle().isBold(), "欢迎标题加粗");
        var s = com.yztc.auth.ui.WelcomeSync.welcomeSub("Alice");
        Assert.eq(s.getStyle().getColor().getValue(), yellow, "欢迎副标题为黄色");
        var contents = (net.minecraft.network.chat.contents.TranslatableContents) s.getContents();
        Assert.eq(contents.getKey(), "yztauth.msg.welcome.sub", "欢迎副标题键正确");
        Assert.that(java.util.Arrays.asList(contents.getArgs()).contains("Alice"), "欢迎副标题带上玩家名");

        var suffixC = (net.minecraft.network.chat.contents.TranslatableContents)
                com.yztc.auth.ui.WelcomeSync.countdownSuffix(123).getContents();
        Assert.eq(suffixC.getKey(), "yztauth.msg.prompt_countdown", "倒计时后缀键");
        Assert.that(java.util.Arrays.asList(suffixC.getArgs()).contains(123L), "倒计时带秒数");

        var fj = com.yztc.auth.ui.WelcomeSync.firstJoinMessage("Bob");
        var fjc = (net.minecraft.network.chat.contents.TranslatableContents) fj.getContents();
        Assert.eq(fjc.getKey(), "yztauth.msg.first_join_broadcast", "首进广播键");
        Assert.that(java.util.Arrays.asList(fjc.getArgs()).contains("Bob"), "首进广播带名字");

        var wt = com.yztc.auth.ui.WelcomeSync.resolveWelcomeTitle("自定义标题");
        Assert.eq(wt.getString(), "自定义标题", "自定义标题生效");
        Assert.eq(wt.getStyle().getColor().getValue(), gold, "自定义标题仍为金色");
        var wtDefault = com.yztc.auth.ui.WelcomeSync.resolveWelcomeTitle("");
        var wtDefaultC = (net.minecraft.network.chat.contents.TranslatableContents) wtDefault.getContents();
        Assert.eq(wtDefaultC.getKey(), "yztauth.msg.welcome.title", "空配置回退语言键");
        var ws = com.yztc.auth.ui.WelcomeSync.resolveWelcomeSub("你好 %s", "Amy");
        Assert.that(ws.getString().contains("Amy"), "自定义副标题替换 %s");
    }

    private static void testPasswordStrength() {
        Assert.section("PasswordStrength");
        Assert.eq(com.yztc.auth.data.PasswordStrength.of("abc").name(), "WEAK", "短字母→弱");
        Assert.eq(com.yztc.auth.data.PasswordStrength.of("abcdef12").name(), "MEDIUM", "6位两字符类→中");
        Assert.eq(com.yztc.auth.data.PasswordStrength.of("Abcdef1!x").name(), "STRONG", "8位三字符类→强");
        Assert.eq(com.yztc.auth.data.PasswordStrength.of("").name(), "WEAK", "空→弱");
        Assert.eq(com.yztc.auth.data.PasswordStrength.of("aaaaaaaa").name(), "WEAK", "8位单一类→弱");
        Assert.eq(com.yztc.auth.data.PasswordStrength.of("Ab1!Ab1!Ab1!").name(), "STRONG", "长四类→强");
    }

    private static void testAuditLog() {
        Assert.section("AuditLog");
        String ln = com.yztc.auth.audit.AuditLog.line("2026-10-09 01:02:03", "alice", "1.2.3.4", "LOGIN_FAIL");
        Assert.eq(ln, "2026-10-09 01:02:03 | alice | 1.2.3.4 | LOGIN_FAIL", "审计行格式");
        Assert.that(!ln.contains("hunter2"), "审计行不含明文");
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("yztauth-audit");
            java.nio.file.Path f = dir.resolve("audit.log");
            var a = new com.yztc.auth.audit.AuditLog(f);
            a.append(ln);
            a.append(ln);
            var lines = java.nio.file.Files.readAllLines(f);
            Assert.eq(lines.size(), 2, "两次追写两行");
            Assert.eq(lines.get(0), ln, "首行内容一致");
        } catch (Exception e) {
            Assert.that(false, "AuditLog 落盘测试异常: " + e);
        }
    }

    private static com.yztc.auth.data.Mail mkMail(String f, String t, String txt, boolean pub, long at) {
        var m = new com.yztc.auth.data.Mail();
        m.from = f; m.to = t; m.text = txt; m.isPublic = pub; m.createdAt = at;
        return m;
    }

    private static void testMailStore() {
        Assert.section("MailStore");
        Assert.that(com.yztc.auth.data.MailStore.canReceive(19, 20), "未满可收");
        Assert.that(!com.yztc.auth.data.MailStore.canReceive(20, 20), "满则拒收");
        Assert.that(!com.yztc.auth.data.MailStore.expired(1000, 1000 + 30L * 86400_000 - 1, 30), "差 1ms 未过期");
        Assert.that(com.yztc.auth.data.MailStore.expired(1000, 1000 + 30L * 86400_000, 30), "到期即过期");
        // M4：配额按“未过期”计数，超期条目不该占额度
        long nowMs = System.currentTimeMillis();
        var liveList = new java.util.ArrayList<com.yztc.auth.data.Mail>();
        liveList.add(mkMail("a", "bob", "新", false, nowMs));
        liveList.add(mkMail("a", "bob", "陈年", false, nowMs - 31L * 86400_000));
        Assert.eq(com.yztc.auth.data.MailStore.liveCount(liveList, nowMs, 30), 1, "liveCount 只数未过期");
        Assert.eq(com.yztc.auth.data.MailStore.liveCount(new java.util.ArrayList<>(), nowMs, 30), 0, "空收件箱 0");
        var m = new com.yztc.auth.data.Mail();
        m.from = "alice"; m.to = "bob"; m.isPublic = false;
        Assert.that(com.yztc.auth.data.MailStore.canDelete(m, "bob", false), "收件人可删私信");
        Assert.that(com.yztc.auth.data.MailStore.canDelete(m, "alice", false), "发件人可删私信");
        Assert.that(!com.yztc.auth.data.MailStore.canDelete(m, "carol", false), "第三者不可删私信");
        Assert.that(com.yztc.auth.data.MailStore.canDelete(m, "carol", true), "管理员可删");
        var pub = new com.yztc.auth.data.Mail(); pub.from = "alice"; pub.isPublic = true;
        Assert.that(com.yztc.auth.data.MailStore.canDelete(pub, "alice", false), "作者可删公开");
        Assert.that(!com.yztc.auth.data.MailStore.canDelete(pub, "bob", false), "非作者不可删公开");
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("yztauth-mail");
            long now = System.currentTimeMillis();
            var s = new com.yztc.auth.data.MailStore(dir.resolve("mail.json"));
            s.load();
            String id1 = s.add(mkMail("alice", "bob", "你好", false, now));
            s.add(mkMail("alice", "", "公告一条", true, now + 1));
            s.add(mkMail("alice", "bob", "陈年私信", false, now - 31L * 86400_000));
            s.pruneExpired(now);
            Assert.eq(s.inboxFor("bob").size(), 1, "超期私信被清理，bob 收件箱 1 条");
            Assert.eq(s.unreadFor("bob"), 1, "未读 1");
            s.markRead(id1);
            Assert.eq(s.unreadFor("bob"), 0, "标记后未读 0");
            Assert.eq(s.board().size(), 1, "公开板 1 条");
            Assert.that(s.board().get(0).isPublic, "公开板条目为 public");
            s.save();
            var s2 = new com.yztc.auth.data.MailStore(dir.resolve("mail.json")); s2.load();
            Assert.eq(s2.all().size(), 2, "重载 2 条");
            Assert.that(s2.remove(id1), "删除成功");
            Assert.that(!s2.remove("nosuch"), "删不存在返回 false");
            java.util.Set<String> ids = new java.util.HashSet<>();
            var s3 = new com.yztc.auth.data.MailStore(dir.resolve("m3.json")); s3.load();
            for (int i = 0; i < 200; i++) ids.add(s3.add(mkMail("a", "b", "x", false, now)));
            Assert.eq(ids.size(), 200, "200 条 id 全唯一");
        } catch (Exception e) {
            Assert.that(false, "MailStore 测试异常: " + e);
        }
    }

    private static void testLastSeenStore() {
        Assert.section("LastSeenStore");
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("yztauth-ls");
            var f = dir.resolve("lastseen.json");
            var s = new com.yztc.auth.data.LastSeenStore(f);
            s.load();
            Assert.that(s.get("alice") == null, "初始无记录");
            var a = new com.yztc.auth.data.LastSeen();
            a.name = "Alice"; a.dimension = "minecraft:overworld"; a.x = 1.5; a.y = 64; a.z = -3.5;
            a.yaw = 90; a.pitch = 10; a.at = 111L;
            s.put(a);
            Assert.eq(s.get("alice").dimension, "minecraft:overworld", "小写键可查");
            s.save();
            var s2 = new com.yztc.auth.data.LastSeenStore(f); s2.load();
            Assert.eq(s2.get("alice").x, 1.5, "往返 x");
            Assert.eq(s2.get("alice").at, 111L, "往返 at");
            var b = new com.yztc.auth.data.LastSeen();
            b.name = "alice"; b.dimension = "minecraft:the_nether"; b.x = 9; b.z = 9; b.at = 222L;
            s2.put(b);
            Assert.eq(s2.get("alice").dimension, "minecraft:the_nether", "同名覆盖");
            Assert.eq(s2.size(), 1, "一人一条");
        } catch (Exception e) {
            Assert.that(false, "LastSeenStore 测试异常: " + e);
        }
    }

    private static void testAuthPure() {
        Assert.section("AuthManager 纯逻辑");
        Assert.eq(com.yztc.auth.auth.AuthManager.remainingAttempts(0, 4), 4, "未失败剩 4");
        Assert.eq(com.yztc.auth.auth.AuthManager.remainingAttempts(3, 4), 1, "失败 3 剩 1");
        Assert.eq(com.yztc.auth.auth.AuthManager.remainingAttempts(4, 4), 0, "达阈值剩 0");
        Assert.eq(com.yztc.auth.auth.AuthManager.remainingAttempts(9, 4), 0, "不返回负数");
        Assert.that(!com.yztc.auth.auth.AuthManager.afkExpired(1000, 99999, 60000, false), "关闭时永不触发");
        Assert.that(!com.yztc.auth.auth.AuthManager.afkExpired(1000, 60099, 60000, true), "差 1ms 不触发");
        Assert.that(com.yztc.auth.auth.AuthManager.afkExpired(1000, 61000, 60000, true), "到点触发");
        Assert.that(!com.yztc.auth.auth.AuthManager.ipChanged(null, "1.1.1.1"), "首次登录不算变更");
        Assert.that(!com.yztc.auth.auth.AuthManager.ipChanged("1.1.1.1", "1.1.1.1"), "同 IP 不算");
        Assert.that(com.yztc.auth.auth.AuthManager.ipChanged("1.1.1.1", "2.2.2.2"), "换 IP 算变更");
        // C2：位置或朝向变化都算“活动”，避免站着不动被误判 AFK
        double[] snap = {10.0, 64.0, -3.0, 90.0f, 0.0f};
        Assert.that(com.yztc.auth.auth.AuthManager.viewChanged(null, 10, 64, -3, 90, 0), "无快照视为有活动（首次）");
        Assert.that(!com.yztc.auth.auth.AuthManager.viewChanged(snap, 10, 64, -3, 90, 0), "位置朝向全同不算活动");
        Assert.that(com.yztc.auth.auth.AuthManager.viewChanged(snap, 10.5, 64, -3, 90, 0), "位置移动算活动");
        Assert.that(com.yztc.auth.auth.AuthManager.viewChanged(snap, 10, 64, -3, 120, 0), "仅转向也算活动");
        Assert.that(com.yztc.auth.auth.AuthManager.viewChanged(snap, 10, 64, -3, 90, 15), "仅俯仰也算活动");
        Assert.that(!com.yztc.auth.auth.AuthManager.viewChanged(snap, 10.005, 64, -3, 90, 0), "微动在阈值内不算");
        // B4：登出后仍保持连接的模式（lock）必须重新从他人 Tab 隐藏
        Assert.that(com.yztc.auth.auth.AuthManager.staysConnectedOnLogout("lock"), "lock 模式保持连接");
        Assert.that(!com.yztc.auth.auth.AuthManager.staysConnectedOnLogout("disconnect"), "disconnect 断开连接");
        Assert.that(!com.yztc.auth.auth.AuthManager.staysConnectedOnLogout("disconnect-throttle"), "throttle 断开连接");
    }

    private static void testTabHider() {
        Assert.section("TabHider.hiddenFor");
        var a = java.util.UUID.randomUUID(); var b = java.util.UUID.randomUUID();
        var pending = java.util.List.of(a, b);
        Assert.eq(com.yztc.auth.ui.TabHider.hiddenFor(pending, a), java.util.List.of(b), "自己不在隐藏列表");
        Assert.eq(com.yztc.auth.ui.TabHider.hiddenFor(pending, java.util.UUID.randomUUID()).size(), 2, "旁观者隐藏两人");
        Assert.eq(com.yztc.auth.ui.TabHider.hiddenFor(java.util.List.of(), a).size(), 0, "无 pending 则空");
    }

    private static void testLocationPure() {
        Assert.section("LocationCommand");
        Assert.that(!com.yztc.auth.command.LocationCommand.canTp(false, true), "控制台不可 tp");
        Assert.that(!com.yztc.auth.command.LocationCommand.canTp(true, false), "非管理不可 tp");
        Assert.that(com.yztc.auth.command.LocationCommand.canTp(true, true), "管理玩家可 tp");
        var s = new com.yztc.auth.data.LastSeen();
        s.name = "alice"; s.dimension = "minecraft:overworld"; s.x = 1.5; s.y = 64; s.z = -2.5; s.at = 0;
        var tc = (net.minecraft.network.chat.contents.TranslatableContents)
                com.yztc.auth.command.LocationCommand.whereText(s, "alice").getContents();
        Assert.eq(tc.getKey(), "yztauth.msg.where_result", "where 文本键");
        Assert.that(java.util.Arrays.asList(tc.getArgs()).contains("1.5"), "where 文本含坐标");
        // M2：查无记录时回显玩家真正输入的名字，而不是占位符
        var none = (net.minecraft.network.chat.contents.TranslatableContents)
                com.yztc.auth.command.LocationCommand.whereText(null, "bob").getContents();
        Assert.eq(none.getKey(), "yztauth.msg.where_none", "无记录用 where_none");
        Assert.that(java.util.Arrays.asList(none.getArgs()).contains("bob"), "where_none 回显查询真名");
        // M3：记录存在但维度不可用时，用专门的 tp 文案，而非复用 where_none
        var badDim = (net.minecraft.network.chat.contents.TranslatableContents)
                com.yztc.auth.command.LocationCommand.tpFailText(s, "alice").getContents();
        Assert.eq(badDim.getKey(), "yztauth.msg.tp_bad_dimension", "维度不可用专用文案");
        var missing = (net.minecraft.network.chat.contents.TranslatableContents)
                com.yztc.auth.command.LocationCommand.tpFailText(null, "alice").getContents();
        Assert.eq(missing.getKey(), "yztauth.msg.where_none", "tp 记录缺失仍用 where_none");
    }

    private static void testStatusRender() {
        Assert.section("StatusCommand");
        var c = (net.minecraft.network.chat.contents.TranslatableContents)
                com.yztc.auth.command.StatusCommand.render(new String[]{"alice", "bob"}, 5).getContents();
        Assert.eq(c.getKey(), "yztauth.msg.status_line", "状态键");
        var args = java.util.Arrays.asList(c.getArgs());
        Assert.that(args.contains("5"), "含在线人数");
        Assert.that(args.stream().anyMatch(a -> String.valueOf(a).contains("alice")), "含未登录名单");
        var c2 = (net.minecraft.network.chat.contents.TranslatableContents)
                com.yztc.auth.command.StatusCommand.render(new String[0], 0).getContents();
        Assert.eq(c2.getKey(), "yztauth.msg.status_line", "空名单也给文本");
    }

    private static void testMailCommand() {
        Assert.section("MailCommand.validRecipient");
        Assert.that(com.yztc.auth.command.MailCommand.validRecipient("bob", "alice"), "正常收件人");
        Assert.that(!com.yztc.auth.command.MailCommand.validRecipient("alice", "alice"), "不能发给自己");
        Assert.that(!com.yztc.auth.command.MailCommand.validRecipient("", "alice"), "空名不合法");
        Assert.that(!com.yztc.auth.command.MailCommand.validRecipient("abcdefghijklmnopq", "alice"), "超 16 不合法");
        Assert.that(com.yztc.auth.command.MailCommand.validRecipient("Bob", "alice"), "大小写不敏感");
        Assert.that(!com.yztc.auth.command.MailCommand.validRecipient("Alice", "alice"), "自己大小写变体也拒");
    }

    private static void testBookGuide() {
        Assert.section("BookGuide");
        var tag = com.yztc.auth.ui.BookGuide.bookTag();
        Assert.eq(tag.getString("title"), "yztauth 指南", "书名");
        Assert.eq(tag.getString("author"), "服务器", "作者");
        var pages = tag.getList("pages", net.minecraft.nbt.Tag.TAG_STRING);
        Assert.that(pages.size() >= 3, "至少 3 页（实得 " + pages.size() + "）");
        int clickable = 0;
        for (int i = 0; i < pages.size(); i++) {
            String raw = pages.getString(i);
            try {
                var o = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
                Assert.that(o.has("text"), "第 " + (i + 1) + " 页含 text 字段");
                if (o.has("clickEvent")) {
                    clickable++;
                    Assert.eq(o.getAsJsonObject("clickEvent").get("action").getAsString(), "suggest_command",
                            "第 " + (i + 1) + " 页 clickEvent 为 suggest_command");
                }
            } catch (Exception e) {
                Assert.that(false, "第 " + (i + 1) + " 页不是合法 JSON: " + e);
            }
        }
        Assert.that(clickable >= 2, "至少 2 页带可点击命令（实得 " + clickable + "）");
    }

    private static void testUiSession() {
        Assert.section("UiSession");
        java.util.UUID owner = java.util.UUID.randomUUID();
        java.util.List<String> commits = new java.util.ArrayList<>();
        int[] cancels = {0};
        var s = com.yztc.auth.ui.UiSession.open(owner, commits::add, () -> cancels[0]++);

        Assert.that(com.yztc.auth.ui.UiSession.isActive(owner), "open 后会话活跃");
        Assert.that(com.yztc.auth.ui.UiSession.get(owner) == s, "get 返回同一会话");
        s.commit("hello");
        Assert.eq(commits.toString(), "[hello]", "commit 回调一次且带值");
        Assert.that(com.yztc.auth.ui.UiSession.isActive(owner), "commit 本身不清注册（由调用方 forget）");
        s.commit("again");
        Assert.eq(commits.toString(), "[hello]", "重复 commit 不再回调");
        s.cancel();
        Assert.eq(cancels[0], 0, "已提交后 cancel 不再回调");

        java.util.UUID owner2 = java.util.UUID.randomUUID();
        java.util.List<String> commits2 = new java.util.ArrayList<>();
        int[] cancels2 = {0};
        var s2 = com.yztc.auth.ui.UiSession.open(owner2, commits2::add, () -> cancels2[0]++);
        s2.cancel();
        Assert.eq(cancels2[0], 1, "cancel 回调一次");
        s2.cancel();
        Assert.eq(cancels2[0], 1, "重复 cancel 不再回调");
        s2.commit("late");
        Assert.eq(commits2.size(), 0, "已取消后 commit 不回调 onCommit");

        com.yztc.auth.ui.UiSession.forget(owner);
        Assert.that(!com.yztc.auth.ui.UiSession.isActive(owner), "forget 后不活跃");
        com.yztc.auth.ui.UiSession.forget(owner2);
        Assert.that(!com.yztc.auth.ui.UiSession.isActive(owner2), "forget 后不活跃（第二个）");
    }

    // 扫描主源码里 Lang.msg("...") / Lang.key("...") 的字面量 id，保证都已在 zh_cn 声明
    private static void testLangKeysReferenced() {
        Assert.section("Lang 键引用");
        try {
            java.nio.file.Path srcRoot = java.nio.file.Path.of("src", "main", "java");
            Assert.that(java.nio.file.Files.isDirectory(srcRoot), "源码目录存在：" + srcRoot.toAbsolutePath());
            java.util.Set<String> declared = langKeys("/assets/yztauth/lang/zh_cn.json");
            java.util.Set<String> used = new java.util.TreeSet<>();
            try (var walk = java.nio.file.Files.walk(srcRoot)) {
                for (java.nio.file.Path f : walk.filter(x -> x.toString().endsWith(".java")).toList()) {
                    collectLangIds(java.nio.file.Files.readString(f, java.nio.charset.StandardCharsets.UTF_8), used);
                }
            }
            Assert.that(used.size() >= 10, "扫描到足够多的语言键，防空转（实得 " + used.size() + "）");
            java.util.List<String> missing = new java.util.ArrayList<>();
            for (String id : used) if (!declared.contains("yztauth.msg." + id)) missing.add(id);
            Assert.eq(missing.toString(), "[]", "源码引用的语言键都已在 zh_cn 声明");
        } catch (Exception e) {
            Assert.that(false, "Lang 键引用测试异常: " + e);
        }
    }

    private static void collectLangIds(String text, java.util.Set<String> out) {
        int i = 0;
        while (true) {
            int a = text.indexOf("Lang.msg(", i), b = text.indexOf("Lang.key(", i);
            int at = (a < 0) ? b : (b < 0 ? a : Math.min(a, b));
            if (at < 0) return;
            int depth = 0, j = at + "Lang.msg".length();
            StringBuilder lit = null;
            for (; j < text.length(); j++) {
                char c = text.charAt(j);
                if (lit != null) {
                    if (c == '\\') { j++; continue; }
                    if (c == '"') { out.add(lit.toString()); lit = null; } else lit.append(c);
                } else if (c == '"') {
                    lit = new StringBuilder();
                } else if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    if (--depth == 0) break;
                }
            }
            i = j + 1;
        }
    }

    private static void testPasswordUtil() {
        Assert.section("PasswordUtil");
        var a = new com.yztc.auth.data.Account();
        a.algo = com.yztc.auth.security.PasswordHasher.ALGO_LEGACY;
        a.hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        a.failedAttempts = 3;
        a.lockedUntil = System.currentTimeMillis() + 60_000;

        com.yztc.auth.data.PasswordUtil.assign(a, "newpass", 1000);
        Assert.eq(a.algo, "pbkdf2-sha256", "重置后算法为 pbkdf2");
        Assert.eq(a.iterations, 1000, "重置后迭代数=入参");
        Assert.eq(a.failedAttempts, 0, "重置清空失败计数");
        Assert.eq(a.lockedUntil, 0, "重置解除锁定");
        var salt = java.util.Base64.getDecoder().decode(a.salt);
        var hash = java.util.Base64.getDecoder().decode(a.hash);
        Assert.that(com.yztc.auth.security.PasswordHasher.verifyPbkdf2(
                "newpass".toCharArray(), a.iterations, salt, hash), "重置后新密码校验通过");
        Assert.that(!com.yztc.auth.security.PasswordHasher.verifyPbkdf2(
                "oldpass".toCharArray(), a.iterations, salt, hash), "重置后旧密码不再通过");
    }

    // Forge 未加载时 AuthConfig 取值抛 IllegalStateException；自测内手工装载一份默认配置
    private static void loadDefaultConfig() {
        com.electronwill.nightconfig.core.CommentedConfig cfg =
                com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        com.yztc.auth.config.AuthConfig.SPEC.correct(cfg);
        com.yztc.auth.config.AuthConfig.SPEC.setConfig(cfg);
    }

    private static void testConfig() {
        Assert.section("AuthConfig");
        Assert.eq(com.yztc.auth.config.AuthConfig.pbkdf2Iterations(), 120000, "pbkdf2Iterations 默认 120000");
        Assert.eq(com.yztc.auth.config.AuthConfig.minPasswordLength(), 5, "minPasswordLength 默认 5");
        Assert.eq(com.yztc.auth.config.AuthConfig.maxLoginAttempts(), 4, "maxLoginAttempts 默认 4");
        Assert.eq(com.yztc.auth.config.AuthConfig.ipRateLimitCount(), 12, "ipRateLimitCount 默认 12");
        Assert.eq(com.yztc.auth.config.AuthConfig.loginTimeoutSeconds(), 450, "loginTimeoutSeconds 默认 450");
        Assert.eq(com.yztc.auth.config.AuthConfig.logoutMode(), "disconnect", "logoutMode 默认 disconnect");
        Assert.eq(com.yztc.auth.config.AuthConfig.mailMaxPerPlayer(), 20, "mailMaxPerPlayer 默认 20");
        Assert.eq(com.yztc.auth.config.AuthConfig.mailExpireDays(), 30, "mailExpireDays 默认 30");
        Assert.that(com.yztc.auth.config.AuthConfig.auditLogEnabled(), "auditLogEnabled 默认 true");
        Assert.that(com.yztc.auth.config.AuthConfig.notifyAdminOnLogin(), "notifyAdminOnLogin 默认 true");
        Assert.that(!com.yztc.auth.config.AuthConfig.afkLogoutEnabled(), "afkLogoutEnabled 默认 false");
        Assert.eq(com.yztc.auth.config.AuthConfig.afkLogoutSeconds(), 600, "afkLogoutSeconds 默认 600");
        Assert.eq(com.yztc.auth.config.AuthConfig.welcomeTitleText(), "", "welcomeTitleText 默认空");
        Assert.eq(com.yztc.auth.config.AuthConfig.welcomeSubText(), "", "welcomeSubText 默认空");
    }

    private static void testGateManager() {
        Assert.section("GateManager");
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("yztauth-gate");
            java.nio.file.Path f = dir.resolve("users.json");
            var store = new com.yztc.auth.data.AccountStore(f);
            store.load();
            var gm = new com.yztc.auth.auth.GateManager(store);

            Assert.that(!gm.isGateSet(), "初始未设闸门");
            // 未设闸门时 verify 在任何玩家对象被触碰前即返回，故传 null 安全
            Assert.eq(gm.verify(null, "whatever").name(), "NOT_SET", "未设闸门时 verify=NOT_SET");

            gm.set("s3cret-gate");
            Assert.that(gm.isGateSet(), "set 后闸门已设");
            var g = store.getGate();
            Assert.eq(g.algo, "pbkdf2-sha256", "闸门算法 pbkdf2");
            Assert.eq(g.iterations, 120000, "闸门迭代数取配置默认");
            var salt = java.util.Base64.getDecoder().decode(g.salt);
            var hash = java.util.Base64.getDecoder().decode(g.hash);
            Assert.eq(salt.length, 16, "闸门盐 16 字节");
            Assert.eq(hash.length, 32, "闸门哈希 32 字节");
            Assert.that(com.yztc.auth.security.PasswordHasher.verifyPbkdf2(
                    "s3cret-gate".toCharArray(), g.iterations, salt, hash), "闸门哈希接受正确密码");
            Assert.that(!com.yztc.auth.security.PasswordHasher.verifyPbkdf2(
                    "wrong".toCharArray(), g.iterations, salt, hash), "闸门哈希拒绝错误密码");

            var store2 = new com.yztc.auth.data.AccountStore(f);
            store2.load();
            Assert.that(new com.yztc.auth.auth.GateManager(store2).isGateSet(), "闸门落盘后可重载");

            gm.clear();
            Assert.that(!gm.isGateSet(), "clear 后无闸门");
            var store3 = new com.yztc.auth.data.AccountStore(f);
            store3.load();
            Assert.that(store3.getGate() == null, "清闸门已落盘");
        } catch (Exception e) {
            Assert.that(false, "GateManager 测试异常: " + e);
        }
    }

    private static void testLang() {
        Assert.section("Lang");
        try {
            java.util.Set<String> zh = langKeys("/assets/yztauth/lang/zh_cn.json");
            java.util.Set<String> en = langKeys("/assets/yztauth/lang/en_us.json");
            Assert.that(!zh.isEmpty(), "zh_cn 非空");
            Assert.that(zh.equals(en), "zh_cn 与 en_us 键集合一致");
            Assert.that(zh.stream().allMatch(k -> k.startsWith("yztauth.msg.")), "所有键前缀为 yztauth.msg.");
            Assert.eq(com.yztc.auth.lang.Lang.key("login_ok"), "yztauth.msg.login_ok", "Lang.key 拼接前缀");
        } catch (Exception e) {
            Assert.that(false, "Lang 测试异常: " + e);
        }
    }

    private static java.util.Set<String> langKeys(String resource) throws java.io.IOException {
        try (java.io.InputStream in = SelfTestMain.class.getResourceAsStream(resource)) {
            if (in == null) throw new java.io.IOException("资源缺失: " + resource);
            String raw = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonObject o =
                    com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
            return new java.util.TreeSet<>(o.keySet());
        }
    }

    private static void testMigrateService() {
        Assert.section("MigrateService");
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("yztauth-mig");
            var store = new com.yztc.auth.data.AccountStore(dir.resolve("users.json"));
            store.load();
            var existing = new com.yztc.auth.data.Account();
            existing.algo = "pbkdf2-sha256"; existing.hash = "X"; existing.salt = "Y"; existing.iterations = 1;
            store.put("existing", existing);

            var legacy = new java.util.LinkedHashMap<String, String>();
            String good = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
            legacy.put("Alice", good);      // 正常，键需归一化为小写
            legacy.put("existing", good);   // 与新库重名 → 跳过
            legacy.put("badhash", "zzzz");  // 非法 hex → 失败
            legacy.put("", good);           // 空键 → 失败
            legacy.put("toolongname1234567", good); // 超长 → 失败

            var rep = com.yztc.auth.command.MigrateService.migrate(legacy, store, false, 123L);
            Assert.eq(rep.total(), 5, "total=5");
            Assert.eq(rep.imported(), 1, "imported=1");
            Assert.eq(rep.skipped(), 1, "skipped=1");
            Assert.eq(rep.failed(), 3, "failed=3");
            Assert.that(store.contains("alice"), "Alice 归一化为小写 alice 入库");
            Assert.that(store.get("alice").isLegacy(), "导入记录标为 legacy");
            Assert.eq(store.get("alice").hash, good, "hash 原样保留");

            // dry-run 不落库
            var store2 = new com.yztc.auth.data.AccountStore(dir.resolve("u2.json")); store2.load();
            var rep2 = com.yztc.auth.command.MigrateService.migrate(legacy, store2, true, 1L);
            Assert.eq(rep2.imported(), 2, "dry-run 对空目标库计数 imported=2（Alice+existing）");
            Assert.eq(store2.size(), 0, "dry-run 不写库");

            // 幂等：再跑一次全跳过
            var rep3 = com.yztc.auth.command.MigrateService.migrate(legacy, store, false, 1L);
            Assert.eq(rep3.imported(), 0, "二次执行 imported=0");
            Assert.eq(rep3.skipped(), 2, "二次执行 skipped=2");
        } catch (Exception e) {
            Assert.that(false, "MigrateService 测试异常: " + e);
        }
    }

    private static void testRateLimiter() {
        Assert.section("RateLimiter");
        RateLimiter rl = new RateLimiter(3, 60_000);
        Assert.that(rl.tryAcquire("1.2.3.4", 0), "第1次放行");
        Assert.that(rl.tryAcquire("1.2.3.4", 10), "第2次放行");
        Assert.that(rl.tryAcquire("1.2.3.4", 20), "第3次放行");
        Assert.that(!rl.tryAcquire("1.2.3.4", 30), "第4次被限");
        Assert.that(rl.tryAcquire("5.6.7.8", 30), "换个 IP 不受影响");
        Assert.that(rl.retryAfterMillis("1.2.3.4", 30) > 0, "限流中 retryAfter>0");
        Assert.that(rl.tryAcquire("1.2.3.4", 60_001), "窗口滑过后恢复");
        rl.reset("1.2.3.4");
        Assert.eq(rl.trackedKeys(), 1, "reset 只留另一个 key");
    }

    private static void testPasswordHasher() {
        Assert.section("PasswordHasher");
        PasswordHasher.Hashed h = PasswordHasher.pbkdf2("hunter2".toCharArray());
        Assert.that(PasswordHasher.verifyPbkdf2(
                "hunter2".toCharArray(), h.iterations(), h.salt(), h.hash()), "PBKDF2 正确密码通过");
        Assert.that(!PasswordHasher.verifyPbkdf2(
                "hunter3".toCharArray(), h.iterations(), h.salt(), h.hash()), "PBKDF2 错误密码拒绝");
        PasswordHasher.Hashed h2 = PasswordHasher.pbkdf2("hunter2".toCharArray());
        Assert.that(!Arrays.equals(h.salt(), h2.salt()), "同密码两次盐不同");
        Assert.eq(PasswordHasher.legacySha256Hex("abc".toCharArray()),
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "SHA-256('abc') 小写 hex");
        Assert.that(PasswordHasher.verifyLegacy(
                "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), "legacy 大小写不敏感");
        Assert.that(!PasswordHasher.verifyLegacy(null, "x"), "legacy null 不抛异常");
        PasswordHasher.Hashed uni = PasswordHasher.pbkdf2("中文密码🔒".toCharArray());
        Assert.that(PasswordHasher.verifyPbkdf2(
                "中文密码🔒".toCharArray(), uni.iterations(), uni.salt(), uni.hash()), "Unicode 密码往返一致");
    }

    private static void testAccountStore() {
        Assert.section("AccountStore");
        try {
            Path dir = Files.createTempDirectory("yztauth-test");
            Path f = dir.resolve("users.json");
            AccountStore store = new AccountStore(f);
            store.load();
            Assert.eq(store.size(), 0, "空库 size=0");

            Account acc = new Account();
            acc.algo = PasswordHasher.ALGO_PBKDF2;
            acc.hash = "AAAA";
            acc.salt = "BBBB";
            acc.iterations = 1000;
            store.put("steve", acc);
            store.save();
            Assert.that(Files.exists(f), "save 落盘");
            Assert.that(!Files.exists(dir.resolve("users.json.bak")), "首次 save 无旧文件，不产生 .bak");

            store.put("alex", acc);
            store.save();
            Assert.that(Files.exists(dir.resolve("users.json.bak")), "第二次 save 前留备份");

            AccountStore store2 = new AccountStore(f);
            store2.load();
            Assert.eq(store2.size(), 2, "重载 size=2");
            Assert.eq(store2.get("steve").hash, "AAAA", "重载字段一致");

            Gate g = new Gate();
            g.algo = "pbkdf2-sha256";
            g.hash = "GG";
            g.salt = "SS";
            g.iterations = 1000;
            store2.setGate(g);
            store2.save();
            AccountStore store3 = new AccountStore(f);
            store3.load();
            Assert.eq(store3.getGate().hash, "GG", "gate 往返");
            store3.clearGate();
            store3.save();
            AccountStore store4 = new AccountStore(f);
            store4.load();
            Assert.that(store4.getGate() == null, "clearGate 后无 gate");

            Path empty = dir.resolve("empty.json");
            Files.writeString(empty, "");
            AccountStore s5 = new AccountStore(empty);
            s5.load();
            Assert.eq(s5.size(), 0, "空文件不抛、视为空库");

            Path bad = dir.resolve("bad.json");
            Files.writeString(bad, "{ not json");
            Assert.throwsAny(() -> {
                try {
                    new AccountStore(bad).load();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, "损坏 JSON 抛异常");

            Path weird = dir.resolve("weird.json");
            Files.writeString(weird, "{\"accounts\":{\"x\":42,\"y\":\"str\"}}");
            AccountStore s6 = new AccountStore(weird);
            s6.load();
            Assert.eq(s6.size(), 0, "非法 account 条目被跳过而非崩溃");
        } catch (Exception e) {
            Assert.that(false, "AccountStore 测试异常: " + e);
        }
    }
}
