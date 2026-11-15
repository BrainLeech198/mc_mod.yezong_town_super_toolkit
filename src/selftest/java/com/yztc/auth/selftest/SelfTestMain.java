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
        testServerLang();
        testMessageColors();
        testLangKeysReferenced();
        testServerLangReferences();
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
        testTitleStore();
        testTitleService();
        testLastSeenStore();
        testAuthPure();
        testTabHider();
        testLocationPure();
        testStatusRender();
        testMailCommand();
        testBanner();
        testChatBox();
        testLoginBoard();
        testButtonPanel();
        testTitlePanel();
        testAdminPanel();
        testAccountPanel();
        testMailPanel();
        testBroadcaster();
        testAfkBroadcast();
        testRiskStore();
        testRiskLog();
        testRiskPanelDetail();

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
            Path dir = Files.createTempDirectory("yzt_super-vpw");
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
                net.minecraft.network.chat.Component.literal("▶ 点这里登录"), "/yzts login ");
        var style = btn.getStyle();
        Assert.that(style.getClickEvent() != null, "按钮带 clickEvent");
        Assert.eq(style.getClickEvent().getAction(),
                net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, "clickEvent 动作为 suggest_command");
        Assert.eq(style.getClickEvent().getValue(), "/yzts login ", "clickEvent 值为建议命令");
        Assert.that(style.getHoverEvent() != null, "按钮带 hoverEvent");
        Assert.eq(style.getHoverEvent().getAction(),
                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, "hoverEvent 动作为 show_text");
        Assert.eq(btn.getString(), "[▶ 点这里登录]", "按钮文本用方括号括出");
        Assert.eq(style.getColor().getValue(),
                net.minecraft.ChatFormatting.GREEN.getColor().intValue(), "按钮为绿色");
        Assert.that(style.isUnderlined(), "按钮带下划线");

        // 回归：标签须保留原始组件（translatable-with-fallback），不得在服务端 getString() 压平。
        // 专用服务器由 Forge 预载本 mod 的 en_us，压平即恒为英文——既非玩家语言，也非服务端兜底语言。
        var trLabel = net.minecraft.network.chat.Component.translatableWithFallback(
                "yzt_super.msg.btn_view", "View");
        var trBtn = com.yztc.auth.ui.ChatPrompt.button(trLabel, "/yzts mail list");
        Assert.eq(trBtn.getString(), "[View]", "压平兜底：自测环境无语言键，getString 取 fallback");
        Assert.that(trBtn.getSiblings().stream().anyMatch(
                        c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents),
                "按钮保留可翻译组件（交由客户端本地化，不服务端压平）");

        var plain = net.minecraft.network.chat.Component.literal("x");
        var styled = com.yztc.auth.ui.ChatPrompt.button(plain, "/yzts help");
        Assert.that(plain.getStyle().getClickEvent() == null, "原组件不被就地修改（copy 后再套样式）");
        Assert.that(styled.getStyle().getClickEvent() != null, "样式只作用于副本");

        // runButton：点击即执行（用于打开 GUI），动作为 run_command
        var run = com.yztc.auth.ui.ChatPrompt.runButton(
                net.minecraft.network.chat.Component.literal("写信"), "/yzts mail send");
        Assert.eq(run.getStyle().getClickEvent().getAction(),
                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "runButton 动作为 run_command");
        Assert.eq(run.getStyle().getClickEvent().getValue(), "/yzts mail send", "runButton 值为执行命令");
        Assert.eq(run.getStyle().getColor().getValue(),
                net.minecraft.ChatFormatting.GREEN.getColor().intValue(), "runButton 为绿色");
        Assert.that(run.getStyle().isUnderlined(), "runButton 带下划线");
    }

    // 未登录玩家只放行认证入口指令，其余一律拦截（安全漏洞修复）
    private static void testAllowedWhileLocked() {
        Assert.section("LockdownHandler.allowedWhileLocked");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/login secret"), "裸 /login 放行");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/register secret"), "裸 /register 放行");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("login secret"), "无斜杠 login 也放行");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yzts gate secret"), "/yzts gate 放行");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yzts login secret"), "/yzts login 放行");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yzts register secret"), "/yzts register 放行");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yzts help"), "/yzts help 放行");
        Assert.that(com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("minecraft:login secret"), "带命名空间的 login 放行");

        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yzts logout"), "登出被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yzts account changepassword a b"), "改密被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yzts admin"), "管理面板被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yzts admin serverpassword set x"), "改闸门被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yzts admin delete alice"), "管理操作被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/gamemode creative"), "原版指令被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/op alice"), "OP 被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/say hi"), "聊天类指令被拦");
        Assert.that(!com.yztc.auth.lockdown.LockdownHandler.allowedWhileLocked("/yzts"), "裸 /yzts 被拦");
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
        Assert.that(loginBtn.getString().contains("需要登录"), "登录提示说明需要登录");
        Assert.that(loginBtn.getString().contains("【重新输入】"), "登录提示只有一个重新输入按钮");
        Assert.eq(loginBtn.getSiblings().size(), 1, "登录提示仅一个可点入口");
        var guiSeg = loginBtn.getSiblings().get(0);
        Assert.eq(guiSeg.getStyle().getClickEvent().getAction(),
                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                "重新输入按钮点击即打开铁砧框");
        Assert.eq(guiSeg.getStyle().getClickEvent().getValue(), "/yzts login",
                "登录重开命令正确");

        var regBtn = com.yztc.auth.ui.WelcomeSync.button(false, false);
        Assert.that(regBtn.getString().contains("第一次来"), "注册提示说明首次进入");
        Assert.eq(regBtn.getSiblings().get(0).getStyle().getClickEvent().getValue(), "/yzts register",
                "注册重开命令正确");
        var gateBtn = com.yztc.auth.ui.WelcomeSync.button(true, false);
        Assert.that(gateBtn.getString().contains("需要服务器通行密码"), "闸门提示说明需要服务器通行密码");
        Assert.eq(gateBtn.getSiblings().get(0).getStyle().getClickEvent().getValue(), "/yzts gate",
                "闸门重开命令正确");

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

    // mods.toml 声明仅服务端 mod：未装本 mod 的原版客户端也能进服（提示走服务端兜底文本）
    private static void testModsToml() {
        Assert.section("mods.toml displayTest");
        try {
            java.nio.file.Path toml = java.nio.file.Path.of("src", "main", "resources", "META-INF", "mods.toml");
            Assert.that(java.nio.file.Files.isRegularFile(toml), "mods.toml 存在：" + toml.toAbsolutePath());
            String text = java.nio.file.Files.readString(toml, java.nio.charset.StandardCharsets.UTF_8);
            Assert.that(text.contains("displayTest=\"IGNORE_SERVER_VERSION\""),
                    "mods.toml 声明 displayTest=\"IGNORE_SERVER_VERSION\"（原版客户端可不装本 mod）");
            Assert.that(!text.contains("displayTest=\"MATCH_VERSION\""),
                    "mods.toml 不再强制版本匹配（否则原版客户端被拒）");
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
        Assert.eq(contents.getKey(), "yzt_super.msg.welcome.sub", "欢迎副标题键正确");
        Assert.that(java.util.Arrays.asList(contents.getArgs()).contains("Alice"), "欢迎副标题带上玩家名");

        var suffixC = (net.minecraft.network.chat.contents.TranslatableContents)
                com.yztc.auth.ui.WelcomeSync.countdownSuffix(123).getContents();
        Assert.eq(suffixC.getKey(), "yzt_super.msg.prompt_countdown", "倒计时后缀键");
        Assert.that(java.util.Arrays.asList(suffixC.getArgs()).contains(123L), "倒计时带秒数");

        // #36：倒计时挂在主标题之后，而非副标题
        var titled = com.yztc.auth.ui.WelcomeSync.titledWithCountdown(
                com.yztc.auth.ui.WelcomeSync.title(false), 45);
        var titledRoot = (net.minecraft.network.chat.contents.TranslatableContents) titled.getContents();
        Assert.eq(titledRoot.getKey(), "yzt_super.msg.prompt_login.title", "主标题仍是登录标题");
        Assert.that(!titled.getSiblings().isEmpty(), "倒计时作为子组件挂到主标题");
        var childC = (net.minecraft.network.chat.contents.TranslatableContents)
                titled.getSiblings().get(0).getContents();
        Assert.eq(childC.getKey(), "yzt_super.msg.prompt_countdown", "子组件为倒计时键");

        var fj = com.yztc.auth.ui.WelcomeSync.firstJoinMessage("Bob");
        var fjc = (net.minecraft.network.chat.contents.TranslatableContents) fj.getContents();
        Assert.eq(fjc.getKey(), "yzt_super.msg.first_join_broadcast", "首进广播键");
        Assert.that(java.util.Arrays.asList(fjc.getArgs()).contains("Bob"), "首进广播带名字");

        var wt = com.yztc.auth.ui.WelcomeSync.resolveWelcomeTitle("自定义标题");
        Assert.eq(wt.getString(), "自定义标题", "自定义标题生效");
        Assert.eq(wt.getStyle().getColor().getValue(), gold, "自定义标题仍为金色");
        var wtDefault = com.yztc.auth.ui.WelcomeSync.resolveWelcomeTitle("");
        var wtDefaultC = (net.minecraft.network.chat.contents.TranslatableContents) wtDefault.getContents();
        Assert.eq(wtDefaultC.getKey(), "yzt_super.msg.welcome.title", "空配置回退语言键");
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
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("yzt_super-audit");
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
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("yzt_super-mail");
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

    // 称号库：定义增删、owned 增减、selected 切换、回收清 selected、落盘 round-trip
    private static void testTitleStore() {
        Assert.section("TitleStore");
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("yzt_super-title");
            var s = new com.yztc.auth.title.TitleStore(dir.resolve("titles.json"));
            s.load();

            // define / def
            s.define("elder", "长老", "GOLD");
            s.define("newbie", "新人", "GRAY");
            Assert.eq(s.definitions().size(), 2, "两条定义");
            Assert.eq(s.def("elder").text, "长老", "elder 文字");
            Assert.eq(s.def("elder").color, "GOLD", "elder 颜色");
            Assert.eq(s.def("nosuch"), null, "未知 id 返回 null");

            // give / ownedOf（大小写归一由调用方负责，store 收到什么存什么）
            s.give("steve", "elder");
            s.give("steve", "newbie");
            s.give("steve", "elder"); // 幂等
            Assert.eq(s.ownedOf("steve").size(), 2, "重复 give 不重复计");
            Assert.that(s.ownedOf("steve").contains("elder"), "steve 有 elder");
            Assert.that(s.ownedOf("steve").contains("newbie"), "steve 有 newbie");
            Assert.eq(s.ownedOf("alex").size(), 0, "未给玩家空集合");

            // select / selectedOf
            s.select("steve", "elder");
            Assert.eq(s.selectedOf("steve"), "elder", "选中 elder");
            s.clearSelected("steve");
            Assert.eq(s.selectedOf("steve"), "", "清空后为空串");

            // take：回收正在显示的称号时自动清 selected（Review Focus #2）
            s.select("steve", "elder");
            s.take("steve", "elder");
            Assert.that(!s.ownedOf("steve").contains("elder"), "回收后 owned 不含 elder");
            Assert.eq(s.selectedOf("steve"), "", "回收 selected 称号后 selected 清空");
            Assert.that(s.ownedOf("steve").contains("newbie"), "newbie 不受影响");

            // undefine
            s.undefine("newbie");
            Assert.eq(s.def("newbie"), null, "undefine 后 def 为 null");
            Assert.that(s.ownedOf("steve").contains("newbie"), "undefine 不改 owned（显示时跳过）");

            // round-trip
            s.define("elder", "长老", "GOLD");
            s.give("steve", "elder");
            s.select("steve", "elder");
            s.save();
            var s2 = new com.yztc.auth.title.TitleStore(dir.resolve("titles.json"));
            s2.load();
            Assert.eq(s2.definitions().size(), 1, "重载 1 条定义");
            Assert.eq(s2.def("elder").text, "长老", "重载文字对");
            Assert.that(s2.ownedOf("steve").contains("elder"), "重载 owned 对");
            Assert.eq(s2.selectedOf("steve"), "elder", "重载 selected 对");
        } catch (Exception e) {
            Assert.that(false, "TitleStore 测试异常: " + e);
        }
    }

    // 称号纯逻辑：前缀拼接、选用/发放校验、颜色映射、显示策略
    private static void testTitleService() {
        Assert.section("TitleService");
        var defs = new java.util.HashMap<String, com.yztc.auth.title.TitleDef>();
        var elder = new com.yztc.auth.title.TitleDef();
        elder.id = "elder"; elder.text = "长老"; elder.color = "GOLD";
        defs.put("elder", elder);
        var newbie = new com.yztc.auth.title.TitleDef();
        newbie.id = "newbie"; newbie.text = "新人"; newbie.color = "GRAY";
        defs.put("newbie", newbie);

        // validId
        Assert.that(com.yztc.auth.title.TitleService.validId("elder"), "合法 id");
        Assert.that(!com.yztc.auth.title.TitleService.validId(""), "空 id 非法");
        Assert.that(!com.yztc.auth.title.TitleService.validId(null), "null id 非法");
        Assert.that(!com.yztc.auth.title.TitleService.validId("abcdefghijklmnopq"), "超 16 非法");

        // canSelect / canGive / canTake
        var owned = java.util.Set.of("elder", "newbie");
        Assert.that(com.yztc.auth.title.TitleService.canSelect("elder", owned, defs), "owned 含 id 可选");
        Assert.that(!com.yztc.auth.title.TitleService.canSelect("gone", owned, defs), "owned 不含不可选");
        Assert.that(com.yztc.auth.title.TitleService.canGive("arch", owned), "未拥有可发放");
        Assert.that(!com.yztc.auth.title.TitleService.canGive("elder", owned), "已拥有不可再发");
        Assert.that(com.yztc.auth.title.TitleService.canTake("elder", owned), "已拥有可回收");
        Assert.that(!com.yztc.auth.title.TitleService.canTake("gone", owned), "未拥有不可回收");
        Assert.that(!com.yztc.auth.title.TitleService.canGive("", owned), "空 id 不可发");

        // colorOf
        Assert.eq(com.yztc.auth.title.TitleService.colorOf("GOLD"), net.minecraft.ChatFormatting.GOLD, "GOLD");
        Assert.eq(com.yztc.auth.title.TitleService.colorOf("red"), net.minecraft.ChatFormatting.RED, "小写也认");
        Assert.eq(com.yztc.auth.title.TitleService.colorOf("bogus"), net.minecraft.ChatFormatting.GOLD,
                "非法颜色名默认 GOLD（Review Focus #5）");
        Assert.eq(com.yztc.auth.title.TitleService.colorOf(null), net.minecraft.ChatFormatting.GOLD, "null 默认 GOLD");

        // prefixFor：有 selected → text· 组件；无/未知 → empty
        var p = com.yztc.auth.title.TitleService.prefixFor("elder", defs);
        Assert.eq(p.getString(), "长老·", "前缀 = 文字 + 间隔号");
        Assert.eq(p.getSiblings().size(), 0, "前缀无多余兄弟");
        Assert.eq(com.yztc.auth.title.TitleService.prefixFor("", defs).getString(), "", "空 selected 空前缀");
        Assert.eq(com.yztc.auth.title.TitleService.prefixFor(null, defs).getString(), "", "null selected 空前缀");
        Assert.eq(com.yztc.auth.title.TitleService.prefixFor("gone", defs).getString(), "",
                "未知 id 空前缀（Review Focus #4）");

        // formatChat：prefix + name + ": " + text
        var chat = com.yztc.auth.title.TitleService.formatChat("Steve", "elder", defs, "大家好");
        Assert.eq(chat.getString(), "长老·Steve: 大家好", "聊天行拼接");
        var chatNone = com.yztc.auth.title.TitleService.formatChat("Steve", "", defs, "hi");
        Assert.eq(chatNone.getString(), "Steve: hi", "无称号聊天行");

        // formatDisplay：prefix + name
        var disp = com.yztc.auth.title.TitleService.formatDisplay("Steve", "elder", defs);
        Assert.eq(disp.getString(), "长老·Steve", "头顶/Tab 显示");

        // shouldShowTitle：未登录不显示（Review Focus #1）
        Assert.that(com.yztc.auth.title.TitleService.shouldShowTitle(true, "elder"), "已登录有称号显示");
        Assert.that(!com.yztc.auth.title.TitleService.shouldShowTitle(false, "elder"), "未登录不显示");
        Assert.that(!com.yztc.auth.title.TitleService.shouldShowTitle(true, ""), "无称号不显示");
        Assert.that(!com.yztc.auth.title.TitleService.shouldShowTitle(true, null), "null 称号不显示");

        // normalizeName（Review Focus #3）
        Assert.eq(com.yztc.auth.title.TitleService.normalizeName("Steve"), "steve", "归一小写");
        Assert.eq(com.yztc.auth.title.TitleService.normalizeName("  Alex "), "alex", "去空白 + 小写");
        Assert.eq(com.yztc.auth.title.TitleService.normalizeName(null), "", "null 归空串");

        // 命令边界：define 时颜色校验
        Assert.that(com.yztc.auth.title.TitleService.validId("elder"), "define id 合法");
        Assert.that(!com.yztc.auth.title.TitleService.validId("this_is_way_too_long_for_an_id"), "define id 超长拒");
        // give/take 的目标名归一
        Assert.eq(com.yztc.auth.title.TitleService.normalizeName("Steve"), "steve", "give 目标归一");

        // list 行：id / 文字 / 颜色 / 已获人数（spec §6）
        var line = com.yztc.auth.title.TitleService.listLine(elder, 3);
        Assert.that(line.contains("elder"), "list 行含 id");
        Assert.that(line.contains("长老"), "list 行含文字");
        Assert.that(line.contains("GOLD"), "list 行含颜色名");
        Assert.that(line.contains("3"), "list 行含已获人数");

        // isDefined：give 只收已定义 id
        Assert.that(com.yztc.auth.title.TitleService.isDefined("elder", defs), "已定义 id");
        Assert.that(!com.yztc.auth.title.TitleService.isDefined("ghost", defs), "未定义 id 不可发");
        Assert.that(!com.yztc.auth.title.TitleService.isDefined(null, defs), "null id 不可发");

        // canSelect 须同时已定义：undefine 后 owned 残留 id 不可选（set 拒绝）
        Assert.that(com.yztc.auth.title.TitleService.canSelect("elder", owned, defs), "owned+defined 可选");
        Assert.that(!com.yztc.auth.title.TitleService.canSelect("newbie", java.util.Set.of("newbie"), java.util.Map.of("elder", elder)),
                "owned 但未定义不可选");
        Assert.that(!com.yztc.auth.title.TitleService.canSelect("ghost", owned, defs), "未拥有且未定义不可选");

        // hasSelected：clear 未选中时提示「当前没有称号」而非「已取消」
        Assert.that(com.yztc.auth.title.TitleService.hasSelected("elder"), "有选中");
        Assert.that(!com.yztc.auth.title.TitleService.hasSelected(""), "空串无选中");
        Assert.that(!com.yztc.auth.title.TitleService.hasSelected(null), "null 无选中");
        Assert.that(!com.yztc.auth.title.TitleService.hasSelected("   "), "空白无选中");
    }

    private static void testLastSeenStore() {
        Assert.section("LastSeenStore");
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("yzt_super-ls");
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
        // C2：只有真实操作（转向 / 行走级位移 / 载具操控）算活动；被推动等被动位移不算
        Assert.that(!com.yztc.auth.auth.AuthManager.walkedTick(0, 0), "不动不算行走");
        Assert.that(!com.yztc.auth.auth.AuthManager.walkedTick(0.05, 0), "被推动单次冲量不算行走");
        Assert.that(!com.yztc.auth.auth.AuthManager.walkedTick(0.11, -0.02), "被推动连续推挤稳态不算行走");
        Assert.that(com.yztc.auth.auth.AuthManager.walkedTick(0.216, 0), "行走位移算行走");
        Assert.that(com.yztc.auth.auth.AuthManager.walkedTick(-0.2, 0.12), "斜向快移也算行走");
        Assert.that(!com.yztc.auth.auth.AuthManager.turnedTick(0, 0), "朝向不变不算转向");
        Assert.that(!com.yztc.auth.auth.AuthManager.turnedTick(0.05f, -0.05f), "微小抖动不算转向");
        Assert.that(com.yztc.auth.auth.AuthManager.turnedTick(3f, 0), "偏航算转向");
        Assert.that(com.yztc.auth.auth.AuthManager.turnedTick(0, -12f), "俯仰算转向");
        Assert.that(!com.yztc.auth.auth.AuthManager.realActivity(false, false, false), "纯被动位移不算操作（被推动）");
        Assert.that(com.yztc.auth.auth.AuthManager.realActivity(true, false, false), "转向算操作");
        Assert.that(com.yztc.auth.auth.AuthManager.realActivity(false, true, false), "行走算操作");
        Assert.that(com.yztc.auth.auth.AuthManager.realActivity(false, false, true), "载具操控算操作");
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
        Assert.eq(tc.getKey(), "yzt_super.msg.where_result", "where 文本键");
        Assert.that(java.util.Arrays.asList(tc.getArgs()).contains("1.5"), "where 文本含坐标");
        // M2：查无记录时回显玩家真正输入的名字，而不是占位符
        var none = (net.minecraft.network.chat.contents.TranslatableContents)
                com.yztc.auth.command.LocationCommand.whereText(null, "bob").getContents();
        Assert.eq(none.getKey(), "yzt_super.msg.where_none", "无记录用 where_none");
        Assert.that(java.util.Arrays.asList(none.getArgs()).contains("bob"), "where_none 回显查询真名");
        // M3：记录存在但维度不可用时，用专门的 tp 文案，而非复用 where_none
        var badDim = (net.minecraft.network.chat.contents.TranslatableContents)
                com.yztc.auth.command.LocationCommand.tpFailText(s, "alice").getContents();
        Assert.eq(badDim.getKey(), "yzt_super.msg.tp_bad_dimension", "维度不可用专用文案");
        var missing = (net.minecraft.network.chat.contents.TranslatableContents)
                com.yztc.auth.command.LocationCommand.tpFailText(null, "alice").getContents();
        Assert.eq(missing.getKey(), "yzt_super.msg.where_none", "tp 记录缺失仍用 where_none");
    }

    private static void testStatusRender() {
        Assert.section("StatusCommand");
        var c = (net.minecraft.network.chat.contents.TranslatableContents)
                com.yztc.auth.command.StatusCommand.render(new String[]{"alice", "bob"}, 5).getContents();
        Assert.eq(c.getKey(), "yzt_super.msg.status_line", "状态键");
        var args = java.util.Arrays.asList(c.getArgs());
        Assert.that(args.contains("5"), "含在线人数");
        Assert.that(args.stream().anyMatch(a -> String.valueOf(a).contains("alice")), "含未登录名单");
        var c2 = (net.minecraft.network.chat.contents.TranslatableContents)
                com.yztc.auth.command.StatusCommand.render(new String[0], 0).getContents();
        Assert.eq(c2.getKey(), "yzt_super.msg.status_line", "空名单也给文本");
    }

    private static void testMailCommand() {
        Assert.section("MailCommand.validRecipient");
        var reg = java.util.Set.of("bob", "carol");
        Assert.that(com.yztc.auth.command.MailCommand.validRecipient("bob", "alice", reg), "已注册收件人");
        Assert.that(!com.yztc.auth.command.MailCommand.validRecipient("dave", "alice", reg), "未注册收件人被拒");
        Assert.that(!com.yztc.auth.command.MailCommand.validRecipient("alice", "alice", reg), "不能发给自己");
        Assert.that(!com.yztc.auth.command.MailCommand.validRecipient("", "alice", reg), "空名不合法");
        Assert.that(!com.yztc.auth.command.MailCommand.validRecipient("abcdefghijklmnopq", "alice", reg), "超 16 不合法");
        Assert.that(com.yztc.auth.command.MailCommand.validRecipient("Bob", "alice", reg), "大小写不敏感");
        Assert.that(!com.yztc.auth.command.MailCommand.validRecipient("Alice", "alice", reg), "自己大小写变体也拒");
    }

    private static void testBanner() {
        Assert.section("Banner");
        // 宽度：全角算 2，半角算 1，框线算 1
        Assert.eq(com.yztc.auth.ui.Banner.displayWidth("abc"), 3, "ASCII 宽 3");
        Assert.eq(com.yztc.auth.ui.Banner.displayWidth("野棕乡"), 6, "中文宽 6");
        Assert.eq(com.yztc.auth.ui.Banner.displayWidth("YztAuth"), 7, "英文宽 7");
        Assert.eq(com.yztc.auth.ui.Banner.displayWidth("║ ═ ╗"), 5, "框线按半角算");
        Assert.eq(com.yztc.auth.ui.Banner.displayWidth(""), 0, "空串宽 0");
        // 补齐
        Assert.eq(com.yztc.auth.ui.Banner.padRight("ab", 5), "ab   ", "半角右补空格");
        Assert.eq(com.yztc.auth.ui.Banner.padRight("野棕乡", 8), "野棕乡  ", "全角按宽补齐");
        Assert.eq(com.yztc.auth.ui.Banner.padRight("abcd", 3), "abcd", "超宽不截断");
        // 信息行（标签由语言表读入；此处内置一份，聚焦排版）
        var labels = new com.yztc.auth.ui.Banner.Labels(
                "登录认证组件", "Mod 版本", "Minecraft", "Forge", "作者", "启动成功");
        String mod = "野粽乡登入mod";
        var noName = com.yztc.auth.ui.Banner.infoLines("", mod, "1.0.0", "BrainLeech198", "1.20.1", "47.4.0", labels);
        Assert.that(noName.get(0).text().contains(mod), "无服名时首行用 mod 名");
        Assert.that(noName.stream().anyMatch(l -> l.text().contains("BrainLeech198")), "含作者");
        Assert.that(noName.stream().anyMatch(l -> l.text().contains("1.0.0")), "含 mod 版本");
        Assert.that(noName.stream().anyMatch(l -> l.text().contains("47.4.0")), "含 Forge 版本");
        Assert.that(noName.stream().anyMatch(l -> l.text().contains("1.20.1")), "含 MC 版本");
        Assert.eq(noName.stream().filter(com.yztc.auth.ui.Banner.Line::accent).count(), 1L, "仅状态行 accent");
        var withName = com.yztc.auth.ui.Banner.infoLines("我的世界", mod, "1.0.0", "BrainLeech198", "1.20.1", "47.4.0", labels);
        Assert.that(withName.get(0).text().contains("我的世界"), "有服名时首行含服名");
        Assert.that(withName.get(0).text().contains(mod), "有服名时首行仍含 mod 名");
        // 控制台行：顶底框 + 等宽 + 颜色码
        var cl = com.yztc.auth.ui.Banner.consoleLines("", mod, "1.0.0", "BrainLeech198", "1.20.1", "47.4.0", labels);
        Assert.that(cl.get(0).contains("╔") && cl.get(0).contains("╗"), "顶框");
        Assert.that(cl.get(cl.size() - 1).contains("╚") && cl.get(cl.size() - 1).contains("╝"), "底框");
        Assert.that(cl.get(0).startsWith(com.yztc.auth.ui.Banner.GOLD), "顶框金色前缀");
        Assert.that(cl.stream().anyMatch(l -> l.contains(com.yztc.auth.ui.Banner.GREEN)), "状态行含绿色");
        int wid = com.yztc.auth.ui.Banner.displayWidth(stripAnsi(cl.get(0)));
        Assert.that(cl.stream().allMatch(l -> com.yztc.auth.ui.Banner.displayWidth(stripAnsi(l)) == wid),
                "所有控制台行等宽（实得首行 " + wid + "）");
        // 游戏内行：组件数量与纯文本行数一致
        var ch = com.yztc.auth.ui.Banner.chatLines("", mod, "1.0.0", "BrainLeech198", "1.20.1", "47.4.0", labels);
        Assert.eq(ch.size(), cl.size(), "聊天行数与控制台行数一致");
        Assert.that(ch.get(0).getString().contains("╔"), "聊天顶框含字符");
    }

    private static void testChatBox() {
        Assert.section("ChatBox 双线方框");
        // TextWidth 共享工具：与 Banner 同模型
        Assert.eq(com.yztc.auth.ui.TextWidth.displayWidth("野棕乡"), 6, "全角宽 6");
        Assert.eq(com.yztc.auth.ui.TextWidth.displayWidth("║ ═ ╗"), 5, "框线半角");
        Assert.eq(com.yztc.auth.ui.Banner.displayWidth("野棕乡"),
                com.yztc.auth.ui.TextWidth.displayWidth("野棕乡"), "Banner 委托 TextWidth");

        var lines = com.yztc.auth.ui.ChatBox.boxText("标题", java.util.List.of("abc", "汉字行"));
        Assert.eq(lines.size(), 4, "标题+2 行+底框 = 4 行");
        Assert.eq(lines.get(0), "╔═ 标题 ═", "顶框含标题（无右边框）");
        Assert.eq(lines.get(1), "║ abc", "内容行 1");
        Assert.eq(lines.get(2), "║ 汉字行", "内容行 2");
        Assert.eq(lines.get(3), "╚════════", "底框（无右边框）");
        int w = com.yztc.auth.ui.TextWidth.displayWidth(lines.get(0));
        Assert.eq(com.yztc.auth.ui.TextWidth.displayWidth(lines.get(3)), w, "上下横线等宽");
        Assert.that(lines.stream().noneMatch(l -> l.endsWith("╗") || l.endsWith("║") || l.endsWith("╝")),
                "无右边框字符");

        // 标题比内容还长时，顶框仍要留至少一格 ═
        var wide = com.yztc.auth.ui.ChatBox.boxText("一个很长很长的标题啊", java.util.List.of("x"));
        int w2 = com.yztc.auth.ui.TextWidth.displayWidth(wide.get(0));
        Assert.eq(com.yztc.auth.ui.TextWidth.displayWidth(wide.get(wide.size() - 1)), w2,
                "长标题时上下横线仍等宽");
        Assert.that(wide.get(0).endsWith("═"), "长标题时顶框仍以 ═ 收尾");

        // 组件版：内容行的样式/点击事件被保留（不重建成纯文本）
        var clicky = net.minecraft.network.chat.Component.literal("点我")
                .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/yzts")));
        var boxed = com.yztc.auth.ui.ChatBox.box(
                net.minecraft.network.chat.Component.literal("列表"), java.util.List.of(clicky));
        Assert.eq(boxed.size(), 3, "组件版：标题+1 行+底框 = 3");
        Assert.that(boxed.get(0).getString().contains("列表"), "组件版顶框含标题");
        boolean hasClick = boxed.get(1).getSiblings().stream().anyMatch(c -> c.getStyle().getClickEvent() != null);
        Assert.that(hasClick, "内容行的点击事件被保留");
        Assert.that(boxed.get(2).getString().startsWith("╚"), "组件版底框");
    }

    private static String stripAnsi(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 27) {
                int m = s.indexOf('m', i);
                if (m < 0) break;
                i = m;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static void testBookGuide() {
        Assert.section("BookGuide");
        var tag = com.yztc.auth.ui.BookGuide.bookTag();
        Assert.eq(tag.getString("title"), "超级工具箱指南", "书名");
        Assert.eq(tag.getString("author"), "服务器", "作者");
        var pages = tag.getList("pages", net.minecraft.nbt.Tag.TAG_STRING);
        Assert.that(pages.size() >= 3, "至少 3 页（实得 " + pages.size() + "）");
        for (int i = 0; i < pages.size(); i++) {
            String raw = pages.getString(i);
            try {
                var o = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
                Assert.that(o.has("text"), "第 " + (i + 1) + " 页含 text 字段");
                Assert.that(!o.has("clickEvent"), "第 " + (i + 1) + " 页不带点击（1.20.1 书内点击为空实现）");
            } catch (Exception e) {
                Assert.that(false, "第 " + (i + 1) + " 页不是合法 JSON: " + e);
            }
        }
        // 热栏选格：优先选中格，其次任一空格，全满 -1
        Assert.eq(com.yztc.auth.ui.BookGuide.hotbarSlotFor(0, i -> true), 0, "选中格为空则用选中格");
        Assert.eq(com.yztc.auth.ui.BookGuide.hotbarSlotFor(0, i -> i != 0), 1, "选中格被占则用下一空格");
        Assert.eq(com.yztc.auth.ui.BookGuide.hotbarSlotFor(5, i -> i == 9), -1, "热栏全满返回 -1");
    }

    private static void testUiSession() {
        Assert.section("UiSession");
        Assert.that(com.yztc.auth.ui.AnvilInput.shouldCommit("hello"), "有内容即提交");
        Assert.that(com.yztc.auth.ui.AnvilInput.shouldCommit("  x  "), "带空白的内容仍提交");
        Assert.that(!com.yztc.auth.ui.AnvilInput.shouldCommit(null), "null 不提交");
        Assert.that(!com.yztc.auth.ui.AnvilInput.shouldCommit(""), "空串不提交");
        Assert.that(!com.yztc.auth.ui.AnvilInput.shouldCommit("   "), "全空白不提交");

        int[] fired = {0};
        String token = com.yztc.auth.ui.InputPrompt.register(() -> fired[0]++);
        Assert.that(token != null && !token.isBlank(), "register 返回令牌");
        Assert.that(com.yztc.auth.ui.InputPrompt.fire(token), "fire 命中已注册令牌");
        Assert.eq(fired[0], 1, "fire 执行回调一次");
        Assert.that(!com.yztc.auth.ui.InputPrompt.fire(token), "重复 fire 失败（一次性）");
        Assert.eq(fired[0], 1, "重复 fire 不再执行");
        Assert.that(!com.yztc.auth.ui.InputPrompt.fire("nope"), "未知令牌 fire 失败");

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
            java.util.Set<String> declared = langKeys("/assets/yzt_super/lang/zh_cn.json");
            java.util.Set<String> used = new java.util.TreeSet<>();
            try (var walk = java.nio.file.Files.walk(srcRoot)) {
                for (java.nio.file.Path f : walk.filter(x -> x.toString().endsWith(".java")).toList()) {
                    collectLangIds(java.nio.file.Files.readString(f, java.nio.charset.StandardCharsets.UTF_8), used);
                }
            }
            Assert.that(used.size() >= 10, "扫描到足够多的语言键，防空转（实得 " + used.size() + "）");
            java.util.List<String> missing = new java.util.ArrayList<>();
            for (String id : used) if (!declared.contains("yzt_super.msg." + id)) missing.add(id);
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
        Assert.that(com.yztc.auth.config.AuthConfig.afkAnnounceEnabled(), "afkAnnounceEnabled 默认 true");
        Assert.eq(com.yztc.auth.config.AuthConfig.afkAnnounceSeconds(), 180, "afkAnnounceSeconds 默认 180");
        Assert.eq(com.yztc.auth.config.AuthConfig.welcomeTitleText(), "", "welcomeTitleText 默认空");
        Assert.eq(com.yztc.auth.config.AuthConfig.welcomeSubText(), "", "welcomeSubText 默认空");
        Assert.eq(com.yztc.auth.config.AuthConfig.language(), "zh_cn", "language 默认 zh_cn");
        Assert.eq(com.yztc.auth.config.AuthConfig.broadcastCooldownSeconds(), 60, "broadcastCooldownSeconds 默认 60");
        Assert.that(!com.yztc.auth.config.AuthConfig.broadcastAllowPlayerColors(), "玩家颜色默认不允许");
        Assert.eq(com.yztc.auth.config.AuthConfig.broadcastPrefix(), "[公告]", "前缀默认 [公告]");
        Assert.that(com.yztc.auth.config.AuthConfig.broadcastChannelChat(), "聊天通道默认开");
        Assert.that(com.yztc.auth.config.AuthConfig.broadcastChannelTitle(), "标题通道默认开");
        Assert.that(!com.yztc.auth.config.AuthConfig.broadcastChannelSubtitle(), "副标题默认关");
        Assert.that(!com.yztc.auth.config.AuthConfig.broadcastChannelActionbar(), "actionbar 默认关");
        Assert.eq(com.yztc.auth.config.AuthConfig.broadcastMaxLength(), 128, "内容上限默认 128");
    }

    private static void testGateManager() {
        Assert.section("GateManager");
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("yzt_super-gate");
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

    // 聊天栏消息配色：成功=绿、失败=红、提醒=金、信息=青、次要=灰、注意=黄
    private static void testMessageColors() {
        Assert.section("Lang 消息配色");
        int green = net.minecraft.ChatFormatting.GREEN.getColor();
        int red = net.minecraft.ChatFormatting.RED.getColor();
        int gold = net.minecraft.ChatFormatting.GOLD.getColor();
        int yellow = net.minecraft.ChatFormatting.YELLOW.getColor();
        int aqua = net.minecraft.ChatFormatting.AQUA.getColor();
        int gray = net.minecraft.ChatFormatting.GRAY.getColor();

        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("login_ok", "alice")), green, "登录成功=绿");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("register_ok")), green, "注册成功=绿");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("mail_sent")), green, "留言已发=绿");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("login_bad")), red, "密码错误=红");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("no_permission")), red, "无权限=红");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("login_new_ip", "1.2.3.4")), gold, "异地登录提醒=金");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("mail_unread_notice", 2)), gold, "未读留言提醒=金");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("where_result", "a", "b", "c", "d", "e")), aqua, "位置结果=青");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("status_line", 1, 0, "")), aqua, "状态行=青");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("mail_none")), gray, "无留言=灰");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("not_registered")), yellow, "未注册提示=黄");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("confirm_prompt")), gold, "危险操作提示=金");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("risk_alert", 3)), gold, "高风险提醒=金");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("risk_done_ok")), green, "标记处理成功=绿");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("title_set_ok")), green, "切换称号成功=绿");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("title_bad_id")), red, "称号 ID 非法=红");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("title_confirm_give")), gold, "发放确认=金");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("title_list_header")), aqua, "称号库标题=青");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("title_none")), gray, "无称号=灰");
        // 提示文本配色补正：取消输入/收到留言/已有称号/已处理标记
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("anvil_cancelled")), red, "取消输入=红");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("mail_notify", "alice")), gold, "收到新留言=金");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("title_already_owned")), yellow, "已有称号=黄");
        Assert.eq(colorOf(com.yztc.auth.lang.Lang.msg("risk_handled_tag")), gray, "已处理标记=灰");

        try {
            java.util.Set<String> declared = langKeys("/assets/yzt_super/lang/zh_cn.json");
            var palette = com.yztc.auth.lang.Lang.paletteKeys();
            Assert.that(palette.size() >= 40, "调色板覆盖足够多的键，防空转（实得 " + palette.size() + "）");
            for (String id : palette) {
                Assert.that(declared.contains("yzt_super.msg." + id), "调色板键已在 zh_cn 声明：" + id);
            }
        } catch (Exception e) {
            Assert.that(false, "配色键覆盖测试异常: " + e);
        }
    }

    private static int colorOf(net.minecraft.network.chat.Component c) {
        var col = c.getStyle().getColor();
        return col == null ? -1 : col.getValue();
    }

    // 登录成功面板：日期时间行 + 未读行 + 一排文字按钮（查看/写信/管理/指南）
    private static void testLoginBoard() {
        Assert.section("登录面板");
        var zone = java.time.ZoneId.of("Asia/Shanghai");
        long ms = java.time.ZonedDateTime.of(2026, 10, 9, 21, 30, 0, 0, zone)
                .toInstant().toEpochMilli();

        String zh = com.yztc.auth.ui.LoginBoard.dateTimeText(ms, zone, false);
        Assert.that(zh.contains("10月9日"), "中文日期含 10月9日：" + zh);
        Assert.that(zh.contains("21:30"), "中文时间含 21:30：" + zh);
        String en = com.yztc.auth.ui.LoginBoard.dateTimeText(ms, zone, true);
        Assert.that(en.contains("Oct"), "英文日期含 Oct：" + en);
        Assert.that(en.contains("21:30"), "英文时间含 21:30：" + en);

        // 邮箱段：查看 / 写信 / 邮箱管理
        var mail = com.yztc.auth.ui.LoginBoard.mailButtons();
        Assert.eq(mail.size(), 3, "邮箱段三个按钮");
        Assert.eq(mail.get(0).labelKey(), "btn_view", "邮箱1=查看");
        Assert.eq(mail.get(0).command(), "/yzts mail list", "查看命令=留言列表");
        Assert.that(!mail.get(0).run(), "查看=只填命令（非 GUI）");
        Assert.eq(mail.get(1).labelKey(), "btn_write", "邮箱2=写信");
        Assert.eq(mail.get(1).command(), "/yzts mail send", "写信命令=写信 GUI");
        Assert.that(mail.get(1).run(), "写信=点击即开 GUI");
        Assert.eq(mail.get(2).labelKey(), "btn_manage", "邮箱3=管理");
        Assert.eq(mail.get(2).command(), "/yzts mail manage", "管理命令=管理 GUI");
        Assert.that(mail.get(2).run(), "管理=点击即开 GUI");

        // 账号段：账户 / 指南
        var acc = com.yztc.auth.ui.LoginBoard.accountButtons();
        Assert.eq(acc.size(), 2, "账号段两个按钮");
        Assert.eq(acc.get(0).labelKey(), "btn_account", "账号1=账户");
        Assert.eq(acc.get(0).command(), "/yzts account", "账户命令=账户 GUI");
        Assert.that(acc.get(0).run(), "账户=点击即开 GUI");
        Assert.eq(acc.get(1).labelKey(), "btn_guide", "账号2=指南");
        Assert.eq(acc.get(1).command(), "/yzts help", "指南命令=帮助");
        Assert.that(!acc.get(1).run(), "指南=只填命令（非 GUI）");

        // 合并表：邮箱段 + 账号段
        var btns = com.yztc.auth.ui.LoginBoard.buttons();
        Assert.eq(btns.size(), 5, "共五个文字按钮");
        Assert.eq(btns.get(3).labelKey(), "btn_account", "合并表按钮4=账户");
        Assert.eq(btns.get(4).labelKey(), "btn_guide", "合并表按钮5=指南");

        // 段标题：中文含分隔符与段名
        String hdr = com.yztc.auth.ui.LoginBoard.sectionHeader("login_board_sec_mail").getString();
        Assert.that(hdr.contains("邮箱"), "段标题含段名：邮箱 -> " + hdr);
        Assert.that(hdr.contains("━"), "段标题含分隔线：" + hdr);
    }

    // 通用按钮面板：按钮数 → 箱子行数（1..6，超出封顶）
    private static void testButtonPanel() {
        Assert.section("按钮面板行数");
        Assert.eq(com.yztc.auth.ui.ButtonPanel.rowsFor(0), 1, "0 个按钮 → 1 行");
        Assert.eq(com.yztc.auth.ui.ButtonPanel.rowsFor(1), 1, "1 个按钮 → 1 行");
        Assert.eq(com.yztc.auth.ui.ButtonPanel.rowsFor(9), 1, "9 个按钮 → 1 行");
        Assert.eq(com.yztc.auth.ui.ButtonPanel.rowsFor(10), 2, "10 个按钮 → 2 行");
        Assert.eq(com.yztc.auth.ui.ButtonPanel.rowsFor(20), 3, "20 个按钮 → 3 行");
        Assert.eq(com.yztc.auth.ui.ButtonPanel.rowsFor(54), 6, "54 个按钮 → 6 行");
        Assert.eq(com.yztc.auth.ui.ButtonPanel.rowsFor(100), 6, "超出 → 封顶 6 行");
    }

    // 管理面板分页：每页容量 → 总页数（至少 1 页，整除不多算一页）
    // 选称号面板：ButtonPanel 的 glint 字段 = 当前 selected 高亮
    private static void testTitlePanel() {
        Assert.section("TitlePanel");
        // 纯逻辑已在 TitleService 覆盖；GUI 观感标注「待实测」
        var dSel = new com.yztc.auth.title.TitleDef();
        dSel.id = "elder"; dSel.text = "长老"; dSel.color = "GOLD";
        Assert.that(com.yztc.auth.title.TitleService.canSelect("elder", java.util.Set.of("elder"),
                        java.util.Map.of("elder", dSel)),
                "面板可选项判定");

        // 槽位布局：标题占 0..n-1，「不显示」接在后面；无标题时提示占 0、不显示占 1（不得撞槽）
        Assert.eq(com.yztc.auth.title.TitlePanel.slotsFor(0), java.util.List.of(1),
                "无标题：不显示独占槽 1，槽 0 留给提示");
        Assert.eq(com.yztc.auth.title.TitlePanel.slotsFor(1), java.util.List.of(0, 1),
                "1 个标题 + 不显示");
        Assert.eq(com.yztc.auth.title.TitlePanel.slotsFor(2), java.util.List.of(0, 1, 2),
                "2 个标题 + 不显示");

        // 已定义标题数：未定义 id 不占槽（Review #3）
        var defs = new java.util.HashMap<String, com.yztc.auth.title.TitleDef>();
        var elder = new com.yztc.auth.title.TitleDef();
        elder.id = "elder"; elder.text = "长老"; elder.color = "GOLD";
        defs.put("elder", elder);
        Assert.eq(com.yztc.auth.title.TitlePanel.definedCount(java.util.Set.of(), defs), 0, "空 owned");
        Assert.eq(com.yztc.auth.title.TitlePanel.definedCount(java.util.Set.of("elder"), defs), 1, "1 条有定义");
        Assert.eq(com.yztc.auth.title.TitlePanel.definedCount(java.util.Set.of("elder", "gone"), defs), 1,
                "未定义 id 不占槽");
        Assert.eq(com.yztc.auth.title.TitlePanel.definedCount(java.util.Set.of("gone"), defs), 0, "全未定义");
    }

    private static void testAdminPanel() {
        Assert.section("AdminPanel 分页");
        Assert.eq(com.yztc.auth.ui.AdminPanel.pageCount(0, com.yztc.auth.ui.AdminPanel.PER_PAGE), 1,
                "0 个账号 → 1 页");
        Assert.eq(com.yztc.auth.ui.AdminPanel.pageCount(1, com.yztc.auth.ui.AdminPanel.PER_PAGE), 1,
                "1 个账号 → 1 页");
        Assert.eq(com.yztc.auth.ui.AdminPanel.pageCount(
                com.yztc.auth.ui.AdminPanel.PER_PAGE, com.yztc.auth.ui.AdminPanel.PER_PAGE), 1,
                "正好满一页 → 1 页");
        Assert.eq(com.yztc.auth.ui.AdminPanel.pageCount(
                com.yztc.auth.ui.AdminPanel.PER_PAGE + 1, com.yztc.auth.ui.AdminPanel.PER_PAGE), 2,
                "多 1 个 → 2 页");
        Assert.eq(com.yztc.auth.ui.AdminPanel.pageCount(
                2 * com.yztc.auth.ui.AdminPanel.PER_PAGE, com.yztc.auth.ui.AdminPanel.PER_PAGE), 2,
                "正好两页 → 2 页");
        Assert.eq(com.yztc.auth.ui.AdminPanel.pageCount(
                3 * com.yztc.auth.ui.AdminPanel.PER_PAGE + 1, com.yztc.auth.ui.AdminPanel.PER_PAGE), 4,
                "两页多 1 个 → 4 页");
        Assert.eq(com.yztc.auth.ui.AdminPanel.pageCount(5, 0), 1, "容量为 0 → 兜底 1 页");

        Assert.section("AdminPanel.slotToIndex");
        Assert.eq(com.yztc.auth.ui.AdminPanel.slotToIndex(9, 0), 0, "槽 9 第 0 页 → 0");
        Assert.eq(com.yztc.auth.ui.AdminPanel.slotToIndex(10, 0), 1, "槽 10 第 0 页 → 1");
        Assert.eq(com.yztc.auth.ui.AdminPanel.slotToIndex(44, 0), 35, "槽 44 第 0 页 → 35");
        Assert.eq(com.yztc.auth.ui.AdminPanel.slotToIndex(9, 1), 36, "槽 9 第 1 页 → 36");
        Assert.eq(com.yztc.auth.ui.AdminPanel.slotToIndex(15, 2), 78, "槽 15 第 2 页 → 78");
        Assert.eq(com.yztc.auth.ui.AdminPanel.slotToIndex(8, 0), -1, "顶行槽 8 不在列表区");
    }

    // 个人中心资料行：注册时间/上次登录/上次登录 IP/邮箱（打码）；空字段回退
    private static void testAccountPanel() {
        Assert.section("AccountPanel 资料行");
        var zone = java.time.ZoneId.of("UTC");
        var a = new com.yztc.auth.data.Account();
        a.registeredAt = 1_700_000_000_000L; a.lastLogin = 1_700_000_060_000L; a.lastIp = "1.2.3.4";
        a.email = "alice@example.com";
        var rows = com.yztc.auth.ui.AccountPanel.profileInfo(a, zone);
        Assert.eq(rows.size(), 4, "资料共 4 行");
        Assert.that(rows.get(0).getString().contains("2023-11-14 22:13:20"),
                "第 1 行=注册时间：" + rows.get(0).getString());
        Assert.that(rows.get(1).getString().contains("2023-11-14 22:14:20"),
                "第 2 行=上次登录：" + rows.get(1).getString());
        Assert.that(rows.get(2).getString().endsWith("1.2.3.4"),
                "第 3 行=上次登录 IP：" + rows.get(2).getString());
        Assert.eq(rows.get(3).getString(), "当前邮箱：a***@example.com", "第 4 行=打码邮箱");

        var blank = new com.yztc.auth.data.Account();
        var rows2 = com.yztc.auth.ui.AccountPanel.profileInfo(blank, zone);
        Assert.that(rows2.get(0).getString().endsWith("—"), "空注册时间回退 —");
        Assert.that(rows2.get(2).getString().endsWith("—"), "空 IP 回退 —");
        Assert.eq(rows2.get(3).getString(),
                com.yztc.auth.lang.Lang.msg("email_unbound").getString(), "未绑邮箱提示");
        Assert.eq(com.yztc.auth.ui.AccountPanel.emailLine(null).getString(),
                com.yztc.auth.lang.Lang.msg("email_unbound").getString(), "emailLine(null)=未绑定");
        Assert.eq(com.yztc.auth.ui.AccountPanel.emailLine(" bob@x.com ").getString(),
                "当前邮箱：b***@x.com", "emailLine 打码");
    }

    // 邮箱详情页与列表标签：发件人/收件人/时间/内容；未读前置 [未读]；公开留言收件人占位
    private static void testMailPanel() {
        Assert.section("MailPanel 详情与列表标签");
        var zone = java.time.ZoneId.of("UTC");
        var m = new com.yztc.auth.data.Mail();
        m.id = "ab12cd"; m.from = "alice"; m.to = "bob";
        m.text = "hello"; m.createdAt = 1_700_000_000_000L; m.isPublic = false; m.readAt = 0;
        var rows = com.yztc.auth.ui.MailPanel.detailInfo(m, zone);
        Assert.eq(rows.size(), 4, "详情共 4 行");
        Assert.that(rows.get(0).getString().endsWith("alice"), "第 1 行=发件人");
        Assert.that(rows.get(1).getString().endsWith("bob"), "第 2 行=收件人");
        Assert.that(rows.get(2).getString().contains("2023-11-14 22:13:20"), "第 3 行=时间");
        Assert.that(rows.get(3).getString().endsWith("hello"), "第 4 行=内容");

        String unread = com.yztc.auth.ui.MailPanel.listLabel(m, true).getString();
        Assert.that(unread.contains("未读"), "未读标签含 [未读]：" + unread);
        Assert.that(unread.contains("#ab12cd") && unread.contains("alice"), "标签含 id 与发件人");
        String read = com.yztc.auth.ui.MailPanel.listLabel(m, false).getString();
        Assert.that(!read.contains("未读"), "已读无未读标记");
        Assert.eq(com.yztc.auth.ui.MailPanel.listLabel(m, true).getSiblings().get(0)
                        .getStyle().getColor().getValue(),
                net.minecraft.ChatFormatting.GOLD.getColor(), "未读标记=金");

        var pub = new com.yztc.auth.data.Mail();
        pub.id = "ffee00"; pub.from = "carol"; pub.to = ""; pub.text = "hi all";
        pub.createdAt = 1_700_000_000_000L; pub.isPublic = true;
        var rows2 = com.yztc.auth.ui.MailPanel.detailInfo(pub, zone);
        Assert.that(rows2.get(1).getString().endsWith("—"), "公开留言收件人占位 —");

        Assert.that(com.yztc.auth.ui.MailPanel.PER_PAGE > 0, "PER_PAGE 为正");
        Assert.eq(com.yztc.auth.ui.AdminPanel.pageCount(
                com.yztc.auth.ui.MailPanel.PER_PAGE + 1, com.yztc.auth.ui.MailPanel.PER_PAGE), 2,
                "写信列表分页复用 pageCount");
    }

    // 大喇叭纯逻辑：通道选择、冷却、内容净化、行文本、标题分段
    private static void testBroadcaster() {
        Assert.section("Broadcaster 纯逻辑");
        var B = com.yztc.auth.broadcast.Broadcaster.class;

        // 通道：玩家固定仅聊天栏；管理员按开关
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.channelsFor(false, true, true, true, true),
                java.util.EnumSet.of(com.yztc.auth.broadcast.Broadcaster.Channel.CHAT),
                "玩家恒为仅聊天栏");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.channelsFor(true, true, false, false, true),
                java.util.EnumSet.of(com.yztc.auth.broadcast.Broadcaster.Channel.CHAT,
                        com.yztc.auth.broadcast.Broadcaster.Channel.ACTIONBAR),
                "管理员=已开启通道集合");
        Assert.that(com.yztc.auth.broadcast.Broadcaster.channelsFor(true, false, false, false, false).isEmpty(),
                "管理员全关=空集");

        // 冷却：向上取整、就绪归零、无冷却
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.cooldownRemaining(1_000_000L, 1_000_000L, 60), 60L, "刚喊完=60");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.cooldownRemaining(1_000_000L, 1_030_000L, 60), 30L, "过半=30");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.cooldownRemaining(1_000_000L, 1_030_500L, 60), 30L, "向上取整=30");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.cooldownRemaining(1_000_000L, 1_060_000L, 60), 0L, "到点=就绪");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.cooldownRemaining(1_000_000L, 1_100_000L, 0), 0L, "无冷却=0");

        // 净化：去空白、剥颜色、截断
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.sanitize(" hi ", false, 128), "hi", "去首尾空白");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.sanitize("&ahello", false, 128), "hello", "剥 &a");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.sanitize("&Khello", false, 128), "hello", "剥大写码");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.sanitize("&ahello", true, 128), "§ahello", "允许颜色：&a 转 §a");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.sanitize("§bhi", true, 128), "§bhi", "已是 § 则原样保留");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.sanitize("abcdef", false, 3), "abc", "超长截断");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.sanitize(null, false, 128), "", "null → 空");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.stripColors("a&x&b"), "a&x", "&x 非码则保留，&b 剥掉");

        // 行文本
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.line("[公告]", null, "hi").getString(),
                "[公告] hi", "无名字");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.line("[公告]", "Steve", "hi").getString(),
                "[公告] Steve: hi", "带名字");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.line("", null, "hi").getString(), "hi", "空前缀");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.line("", "Steve", "hi").getString(),
                "Steve: hi", "空前缀带名字");

        // 标题分段
        Assert.eq(java.util.Arrays.toString(com.yztc.auth.broadcast.Broadcaster.titleParts(true, false, "hi", "[公告]")),
                "[hi, ]", "仅标题");
        Assert.eq(java.util.Arrays.toString(com.yztc.auth.broadcast.Broadcaster.titleParts(false, true, "hi", "[公告]")),
                "[, hi]", "仅副标题");
        Assert.eq(java.util.Arrays.toString(com.yztc.auth.broadcast.Broadcaster.titleParts(true, true, "hi", "[公告]")),
                "[hi, [公告]]", "标题+副标题");

        // 冷却按角色：玩家受限、管理员豁免
        java.util.UUID u = java.util.UUID.randomUUID();
        com.yztc.auth.broadcast.Broadcaster.markShout(u, 1_000_000L);
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.remainingFor(u, 1_000_000L, false), 60L, "普通玩家有冷却");
        Assert.eq(com.yztc.auth.broadcast.Broadcaster.remainingFor(u, 1_000_000L, true), 0L, "管理员免冷却");

        // 发送前置：空/超长/净化统一判定（命令与 GUI 共用）
        Assert.eq(com.yztc.auth.broadcast.BroadcastCommand.prepare("", false, 128).errorKey(),
                "broadcast_empty", "空 → empty");
        Assert.eq(com.yztc.auth.broadcast.BroadcastCommand.prepare("   ", false, 128).errorKey(),
                "broadcast_empty", "纯空白 → empty");
        Assert.eq(com.yztc.auth.broadcast.BroadcastCommand.prepare("abcdef", false, 3).errorKey(),
                "broadcast_too_long", "超长 → too_long");
        Assert.that(com.yztc.auth.broadcast.BroadcastCommand.prepare("ok", false, 128).errorKey() == null,
                "正常 → 无错误");
        Assert.eq(com.yztc.auth.broadcast.BroadcastCommand.prepare(" hi ", false, 128).content(),
                "hi", "通过 → 净化去空白");
        Assert.eq(com.yztc.auth.broadcast.BroadcastCommand.prepare("&ahello", false, 128).content(),
                "hello", "玩家剥色");
        Assert.eq(com.yztc.auth.broadcast.BroadcastCommand.prepare("&ahello", true, 128).content(),
                "§ahello", "管理员转 §");
    }

    // 挂机公告：状态机、变体键、消息颜色
    private static void testAfkBroadcast() {
        Assert.section("AfkBroadcast 挂机公告");
        // 状态机纯逻辑
        Assert.that(!com.yztc.auth.broadcast.AfkBroadcast.shouldEnterAway(false, 179_999, 180_000),
                "差 1ms 不进入挂机");
        Assert.that(com.yztc.auth.broadcast.AfkBroadcast.shouldEnterAway(false, 180_000, 180_000),
                "到点进入挂机");
        Assert.that(!com.yztc.auth.broadcast.AfkBroadcast.shouldEnterAway(true, 9_999_999, 180_000),
                "已挂机不重复触发");
        Assert.that(com.yztc.auth.broadcast.AfkBroadcast.shouldReturn(true, true), "挂机中有操作=返回");
        Assert.that(!com.yztc.auth.broadcast.AfkBroadcast.shouldReturn(false, true), "未挂机有操作=不播报返回");
        Assert.that(!com.yztc.auth.broadcast.AfkBroadcast.shouldReturn(true, false), "仍无操作=不播报返回");

        // 变体序号与语言键
        Assert.eq(com.yztc.auth.broadcast.AfkBroadcast.awayKey(0), "afk_away_1", "离开键 1");
        Assert.eq(com.yztc.auth.broadcast.AfkBroadcast.backKey(5), "afk_back_6", "返回键 6");
        Assert.eq(com.yztc.auth.broadcast.AfkBroadcast.pickVariant(0, new java.util.Random(1)), 0, "空集退回 0");
        java.util.Random r = new java.util.Random(42);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) seen.add(com.yztc.auth.broadcast.AfkBroadcast.pickVariant(6, r));
        Assert.that(seen.stream().allMatch(v -> v >= 0 && v < 6), "随机序号均落在 [0,6)");
        Assert.eq(seen.size(), 6, "多次抽样能覆盖全部 6 个变体");

        // 每个变体键都在中英语言表声明
        try {
            var zh = langValues("/assets/yzt_super/lang/zh_cn.json");
            var en = langValues("/assets/yzt_super/lang/en_us.json");
            for (int i = 0; i < com.yztc.auth.broadcast.AfkBroadcast.AWAY_VARIANTS; i++) {
                String k = "yzt_super.msg." + com.yztc.auth.broadcast.AfkBroadcast.awayKey(i);
                Assert.that(zh.containsKey(k) && en.containsKey(k), "离开文案键已声明：" + k);
            }
            for (int i = 0; i < com.yztc.auth.broadcast.AfkBroadcast.BACK_VARIANTS; i++) {
                String k = "yzt_super.msg." + com.yztc.auth.broadcast.AfkBroadcast.backKey(i);
                Assert.that(zh.containsKey(k) && en.containsKey(k), "返回文案键已声明：" + k);
            }
            Assert.that(zh.containsKey("yzt_super.msg.afk_welcome")
                    && en.containsKey("yzt_super.msg.afk_welcome"), "私人欢迎语键已声明");
        } catch (Exception e) {
            Assert.that(false, "挂机公告语言键测试异常: " + e);
        }

        // 消息构建：名字高亮 + 文案单独着色
        var away = com.yztc.auth.broadcast.AfkBroadcast.awayMessage("Steve", 0);
        Assert.that(away.getString().startsWith("Steve"), "离开消息以玩家名开头：" + away.getString());
        Assert.eq(colorOf(away), net.minecraft.ChatFormatting.GOLD.getColor(), "离开名字=金色");
        Assert.eq(colorOf(away.getSiblings().get(1)), net.minecraft.ChatFormatting.GRAY.getColor(), "离开文案=灰色");
        var back = com.yztc.auth.broadcast.AfkBroadcast.backMessage("Steve", 0);
        Assert.eq(colorOf(back), net.minecraft.ChatFormatting.GREEN.getColor(), "返回名字=绿色");
        Assert.eq(colorOf(back.getSiblings().get(1)), net.minecraft.ChatFormatting.YELLOW.getColor(), "返回文案=黄色");
        Assert.eq(colorOf(com.yztc.auth.broadcast.AfkBroadcast.welcomeMessage()),
                net.minecraft.ChatFormatting.GREEN.getColor(), "私人欢迎语=绿色");
    }

    // 高风险记录库：id 分配、待处理计数、标记处理、重复标记、上限裁剪
    private static void testRiskStore() {
        Assert.section("RiskStore");
        var store = new com.yztc.auth.data.RiskStore(java.nio.file.Path.of("build", "risk-test.json"));
        var r = new com.yztc.auth.data.Risk();
        r.at = 1000; r.actor = "alice"; r.action = com.yztc.auth.risk.RiskLog.KICK; r.target = "bob";
        String id = store.add(r);
        Assert.that(id != null && !id.isBlank(), "add 分配 id");
        Assert.eq(store.size(), 1, "size=1");
        Assert.eq(store.pendingCount(), 1, "待处理=1");
        Assert.that(store.byId(id) == r, "byId 命中");
        Assert.eq(store.pending().size(), 1, "pending 列表=1");
        Assert.that(store.markHandled(id, "op", 2000), "标记处理成功");
        Assert.eq(store.pendingCount(), 0, "标记后待处理=0");
        Assert.that(store.byId(id).handled, "handled 置位");
        Assert.that(!store.markHandled(id, "op", 3000), "重复标记返回 false");
        Assert.eq(store.pending().size(), 0, "pending 列表已空");

        var store2 = new com.yztc.auth.data.RiskStore(java.nio.file.Path.of("build", "risk-test2.json"));
        for (int i = 0; i < com.yztc.auth.data.RiskStore.MAX + 5; i++) {
            var x = new com.yztc.auth.data.Risk();
            x.at = i; x.action = com.yztc.auth.risk.RiskLog.KICK;
            store2.add(x);
        }
        Assert.eq(store2.size(), com.yztc.auth.data.RiskStore.MAX, "超出上限裁剪到 MAX");
    }

    // 高风险记录文本：动作词中文映射、单行格式、未知动作回退
    private static void testRiskLog() {
        Assert.section("RiskLog");
        Assert.eq(com.yztc.auth.risk.RiskLog.display(com.yztc.auth.risk.RiskLog.PASSWORD_RESET), "重置密码", "动作=重置密码");
        Assert.eq(com.yztc.auth.risk.RiskLog.display(com.yztc.auth.risk.RiskLog.ACCOUNT_DELETE), "删除账号", "动作=删除账号");
        Assert.eq(com.yztc.auth.risk.RiskLog.display(com.yztc.auth.risk.RiskLog.FORCE_LOGIN), "强制登录", "动作=强制登录");
        Assert.eq(com.yztc.auth.risk.RiskLog.display(com.yztc.auth.risk.RiskLog.FORCE_LOGOUT), "强制登出", "动作=强制登出");
        Assert.eq(com.yztc.auth.risk.RiskLog.display(com.yztc.auth.risk.RiskLog.KICK), "踢出", "动作=踢出");
        Assert.eq(com.yztc.auth.risk.RiskLog.display(com.yztc.auth.risk.RiskLog.GATE_SET), "设置闸门", "动作=设置闸门");
        Assert.eq(com.yztc.auth.risk.RiskLog.display(com.yztc.auth.risk.RiskLog.GATE_CLEAR), "关闭闸门", "动作=关闭闸门");
        Assert.eq(com.yztc.auth.risk.RiskLog.display(com.yztc.auth.risk.RiskLog.MIGRATE), "数据迁移", "动作=数据迁移");
        Assert.eq(com.yztc.auth.risk.RiskLog.display(com.yztc.auth.risk.RiskLog.TELEPORT), "传送", "动作=传送");
        Assert.eq(com.yztc.auth.risk.RiskLog.display("UNKNOWN_X"), "UNKNOWN_X", "未知动作原样返回");

        var r = new com.yztc.auth.data.Risk();
        r.id = "ab12cd"; r.action = com.yztc.auth.risk.RiskLog.KICK; r.actor = "op"; r.target = "bob";
        String line = com.yztc.auth.risk.RiskLog.line(r);
        Assert.that(line.startsWith("#ab12cd"), "行含 id：" + line);
        Assert.that(line.contains("踢出"), "行含动作名：" + line);
        Assert.that(line.contains("op") && line.contains("bob"), "行含操作者与目标：" + line);

        var noTarget = new com.yztc.auth.data.Risk();
        noTarget.id = "ff00ff"; noTarget.action = com.yztc.auth.risk.RiskLog.MIGRATE; noTarget.actor = "(console)";
        Assert.that(com.yztc.auth.risk.RiskLog.line(noTarget).contains("(console)"), "控制台操作者显示");
        Assert.that(!com.yztc.auth.risk.RiskLog.line(noTarget).contains("→"), "无目标不显示箭头");
    }

    // 高风险详情页：点开单条记录要能看出 时间/操作/操作者/目标/说明
    private static void testRiskPanelDetail() {
        Assert.section("RiskPanel 详情");
        java.time.ZoneId utc = java.time.ZoneId.of("UTC");
        Assert.eq(com.yztc.auth.ui.RiskPanel.timeText(1_700_000_000_000L, utc),
                "2023-11-14 22:13:20", "时间戳格式化");

        var r = new com.yztc.auth.data.Risk();
        r.id = "ab12cd"; r.at = 1_700_000_000_000L;
        r.action = com.yztc.auth.risk.RiskLog.PASSWORD_RESET;
        r.actor = "admin"; r.target = "steve"; r.detail = "按管理员要求";
        java.util.List<net.minecraft.network.chat.Component> rows =
                com.yztc.auth.ui.RiskPanel.detailInfo(r, utc);
        Assert.eq(rows.size(), 5, "详情共 5 行");
        Assert.eq(rows.get(0).getString(), "时间：2023-11-14 22:13:20", "第 1 行=时间");
        Assert.eq(rows.get(1).getString(), "操作：重置密码", "第 2 行=操作");
        Assert.eq(rows.get(2).getString(), "操作者：admin", "第 3 行=操作者");
        Assert.eq(rows.get(3).getString(), "目标：steve", "第 4 行=目标");
        Assert.eq(rows.get(4).getString(), "说明：按管理员要求", "第 5 行=说明");

        var blank = new com.yztc.auth.data.Risk();
        blank.id = "0ff00f"; blank.at = 0;
        blank.action = com.yztc.auth.risk.RiskLog.GATE_CLEAR; blank.actor = "(console)";
        java.util.List<net.minecraft.network.chat.Component> rows2 =
                com.yztc.auth.ui.RiskPanel.detailInfo(blank, utc);
        Assert.that(rows2.get(3).getString().endsWith("(无)"), "无目标显示 (无)");
        Assert.that(rows2.get(4).getString().endsWith("(无)"), "无说明显示 (无)");

        // ButtonPanel：显式槽位按最大槽位算行数；只读项（action=null）也算占位
        var auto1 = new com.yztc.auth.ui.ButtonPanel.Btn(
                null, net.minecraft.network.chat.Component.literal("x"), null,
                com.yztc.auth.ui.ButtonPanel.AUTO);
        Assert.eq(com.yztc.auth.ui.ButtonPanel.requiredRows(java.util.List.of(auto1)), 1,
                "单个自动槽 → 1 行");
        var explicit11 = new com.yztc.auth.ui.ButtonPanel.Btn(
                null, net.minecraft.network.chat.Component.literal("x"), null, 11);
        Assert.eq(com.yztc.auth.ui.ButtonPanel.requiredRows(java.util.List.of(explicit11)), 2,
                "显式槽 11 → 2 行");
        var explicit20 = new com.yztc.auth.ui.ButtonPanel.Btn(
                null, net.minecraft.network.chat.Component.literal("x"), null, 20);
        Assert.eq(com.yztc.auth.ui.ButtonPanel.requiredRows(java.util.List.of(explicit20)), 3,
                "显式槽 20 → 3 行");
        // 混合：4 个自动槽（占 0..3）+ 1 个显式槽 11 → 需要 2 行
        var mixed = java.util.List.of(
                new com.yztc.auth.ui.ButtonPanel.Btn(null, net.minecraft.network.chat.Component.literal("a"), null),
                new com.yztc.auth.ui.ButtonPanel.Btn(null, net.minecraft.network.chat.Component.literal("b"), null),
                new com.yztc.auth.ui.ButtonPanel.Btn(null, net.minecraft.network.chat.Component.literal("c"), null),
                new com.yztc.auth.ui.ButtonPanel.Btn(null, net.minecraft.network.chat.Component.literal("d"), null),
                new com.yztc.auth.ui.ButtonPanel.Btn(null, net.minecraft.network.chat.Component.literal("e"), null, 11));
        Assert.eq(com.yztc.auth.ui.ButtonPanel.requiredRows(mixed), 2, "自动+显式混合 → 2 行");
    }

    private static void testLang() {
        Assert.section("Lang");
        try {
            java.util.Set<String> zh = langKeys("/assets/yzt_super/lang/zh_cn.json");
            java.util.Set<String> en = langKeys("/assets/yzt_super/lang/en_us.json");
            Assert.that(!zh.isEmpty(), "zh_cn 非空");
            Assert.that(zh.equals(en), "zh_cn 与 en_us 键集合一致");
            Assert.that(zh.stream().allMatch(k -> k.startsWith("yzt_super.msg.")), "所有键前缀为 yzt_super.msg.");
            Assert.eq(com.yztc.auth.lang.Lang.key("login_ok"), "yzt_super.msg.login_ok", "Lang.key 拼接前缀");
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

    // 服务端兜底文本：原版客户端无本 mod 语言键，靠 ServerLang 提供的模板渲染
    private static void testServerLang() {
        Assert.section("ServerLang 兜底文本");
        try {
            java.util.Map<String, String> zh = langValues("/assets/yzt_super/lang/zh_cn.json");
            java.util.Map<String, String> en = langValues("/assets/yzt_super/lang/en_us.json");
            String p = "yzt_super.msg.";
            for (var e : zh.entrySet()) {
                String id = e.getKey().substring(p.length());
                Assert.eq(com.yztc.auth.lang.ServerLang.templateFor("zh_cn", id), e.getValue(),
                        "zh 模板一致: " + id);
            }
            for (var e : en.entrySet()) {
                String id = e.getKey().substring(p.length());
                Assert.eq(com.yztc.auth.lang.ServerLang.templateFor("en_us", id), e.getValue(),
                        "en 模板一致: " + id);
            }
            Assert.that(com.yztc.auth.lang.ServerLang.templateFor("zh_cn", "no_such_key") == null,
                    "未知 id 返回 null");
            Assert.eq(com.yztc.auth.lang.ServerLang.templateFor("fr_fr", "login_ok"),
                    zh.get(p + "login_ok"), "未知语言回退 zh_cn");

            // 配置语言为默认 zh_cn 时，template 取 zh 模板；Lang.msg 把它挂成 fallback
            Assert.eq(com.yztc.auth.lang.ServerLang.template("login_ok"),
                    zh.get(p + "login_ok"), "template 按配置语言取模板");
            var c = com.yztc.auth.lang.Lang.msg("login_ok", "Tom");
            var tc = (net.minecraft.network.chat.contents.TranslatableContents) c.getContents();
            Assert.eq(tc.getKey(), p + "login_ok", "键不变（装了 mod 的客户端仍走本地翻译）");
            Assert.eq(tc.getFallback(), zh.get(p + "login_ok"), "无 mod 客户端走 zh 兜底模板");
        } catch (Exception e) {
            Assert.that(false, "ServerLang 测试异常: " + e);
        }
    }

    // ServerLang.template 直接引用的兜底键不经 Lang.msg，故 testLangKeysReferenced 扫不到；此处逐一核对已声明
    private static void testServerLangReferences() {
        Assert.section("ServerLang 引用键");
        try {
            java.util.Map<String, String> zh = langValues("/assets/yzt_super/lang/zh_cn.json");
            java.util.Map<String, String> en = langValues("/assets/yzt_super/lang/en_us.json");
            String[] ids = {
                    "banner_mod_tag", "banner_version", "banner_minecraft", "banner_forge",
                    "banner_author", "banner_status",
                    "admin_email_unset",
                    "migrate_summary", "migrate_reason_empty_name", "migrate_reason_bad_name",
                    "migrate_reason_bad_hash", "migrate_reason_exists",
                    "risk_action_password_reset", "risk_action_account_delete", "risk_action_force_login",
                    "risk_action_force_logout", "risk_action_kick", "risk_action_gate_set",
                    "risk_action_gate_clear", "risk_action_migrate", "risk_action_teleport", "risk_by",
                    "risk_handled_tag", "risk_detail_none",
                    "book_title", "book_author", "book_p1", "book_p2", "book_p3",
                    "broadcast_say", "broadcast_shout", "broadcast_cooldown",
                    "broadcast_empty", "broadcast_too_long", "broadcast_sent"
            };
            for (String id : ids) {
                String k = "yzt_super.msg." + id;
                Assert.that(zh.containsKey(k), "zh 含键: " + id);
                Assert.that(en.containsKey(k), "en 含键: " + id);
            }
        } catch (Exception e) {
            Assert.that(false, "ServerLang 引用键测试异常: " + e);
        }
    }

    private static java.util.Map<String, String> langValues(String resource) throws java.io.IOException {
        try (java.io.InputStream in = SelfTestMain.class.getResourceAsStream(resource)) {
            if (in == null) throw new java.io.IOException("资源缺失: " + resource);
            String raw = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonObject o =
                    com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
            java.util.Map<String, String> m = new java.util.HashMap<>();
            for (var e : o.entrySet()) m.put(e.getKey(), e.getValue().getAsString());
            return m;
        }
    }

    private static void testMigrateService() {
        Assert.section("MigrateService");
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("yzt_super-mig");
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
            Path dir = Files.createTempDirectory("yzt_super-test");
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
