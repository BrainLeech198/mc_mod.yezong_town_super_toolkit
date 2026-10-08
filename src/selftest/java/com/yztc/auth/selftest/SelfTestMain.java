package com.yztc.auth.selftest;

import com.yztc.auth.security.PasswordHasher;

import java.util.Arrays;

public final class SelfTestMain {
    public static void main(String[] args) {
        Assert.section("自测框架自检");
        Assert.that(true, "自测框架可运行");

        Assert.section("PasswordHasher");
        var h = PasswordHasher.pbkdf2("hunter2".toCharArray());
        Assert.that(PasswordHasher.verifyPbkdf2(
                "hunter2".toCharArray(), h.iterations(), h.salt(), h.hash()), "PBKDF2 正确密码通过");
        Assert.that(!PasswordHasher.verifyPbkdf2(
                "hunter3".toCharArray(), h.iterations(), h.salt(), h.hash()), "PBKDF2 错误密码拒绝");
        var h2 = PasswordHasher.pbkdf2("hunter2".toCharArray());
        Assert.that(!Arrays.equals(h.salt(), h2.salt()), "同密码两次盐不同");
        Assert.eq(PasswordHasher.legacySha256Hex("abc".toCharArray()),
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "SHA-256('abc') 小写 hex");
        Assert.that(PasswordHasher.verifyLegacy(
                "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), "legacy 大小写不敏感");
        Assert.that(!PasswordHasher.verifyLegacy(null, "x"), "legacy null 不抛异常");
        var uni = PasswordHasher.pbkdf2("中文密码🔒".toCharArray());
        Assert.that(PasswordHasher.verifyPbkdf2(
                "中文密码🔒".toCharArray(), uni.iterations(), uni.salt(), uni.hash()), "Unicode 密码往返一致");

        System.exit(Assert.summaryAndExit());
    }
}
