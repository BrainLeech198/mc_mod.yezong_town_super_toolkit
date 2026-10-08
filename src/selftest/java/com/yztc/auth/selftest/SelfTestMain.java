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
        testGateManager();

        System.exit(Assert.summaryAndExit());
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
