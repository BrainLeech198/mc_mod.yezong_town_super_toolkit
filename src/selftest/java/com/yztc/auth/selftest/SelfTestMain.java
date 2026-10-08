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

        testPasswordHasher();
        testAccountStore();
        testRateLimiter();

        System.exit(Assert.summaryAndExit());
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
