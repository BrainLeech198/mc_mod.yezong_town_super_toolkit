package com.yztc.auth.selftest;

public final class SelfTestMain {
    public static void main(String[] args) {
        Assert.section("自测框架自检");
        Assert.that(true, "自测框架可运行");
        System.exit(Assert.summaryAndExit());
    }
}
