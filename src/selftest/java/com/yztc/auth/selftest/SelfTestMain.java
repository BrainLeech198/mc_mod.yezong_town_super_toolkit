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
