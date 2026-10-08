package com.yztc.auth.selftest;

public final class Assert {
    private static int passed = 0;
    private static int failed = 0;

    private Assert() {}

    public static void that(boolean cond, String msg) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL: " + msg);
        }
    }

    public static void eq(Object actual, Object expected, String msg) {
        boolean ok;
        if (actual == null || expected == null) {
            ok = actual == expected;
        } else if (actual instanceof Number na && expected instanceof Number ne) {
            ok = na.doubleValue() == ne.doubleValue(); // Long(0) 与 Integer(0) 也应相等
        } else {
            ok = actual.equals(expected);
        }
        that(ok, msg + " (期望=" + expected + " 实际=" + actual + ")");
    }

    public static void throwsAny(Runnable r, String msg) {
        try {
            r.run();
            that(false, msg + " (未抛异常)");
        } catch (Throwable t) {
            that(true, msg);
        }
    }

    public static void section(String name) {
        System.out.println("== " + name + " ==");
    }

    public static int summaryAndExit() {
        System.out.println("通过=" + passed + " 失败=" + failed);
        return failed == 0 ? 0 : 1;
    }
}
