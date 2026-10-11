package com.yztc.auth.data;

/** 邮箱显示与校验的纯工具。 */
public final class Emails {
    private Emails() {}

    /** 是否像一个邮箱地址：非空、含 @、@ 不在首尾。 */
    public static boolean looksValid(String addr) {
        if (addr == null) return false;
        String a = addr.trim();
        int at = a.indexOf('@');
        return at > 0 && at < a.length() - 1;
    }

    /** 打码显示：保留本地部分首字符与域名，如 alice@x.com → a***@x.com。 */
    public static String mask(String email) {
        if (email == null || email.isBlank()) return "";
        String e = email.trim();
        int at = e.indexOf('@');
        if (at <= 0) return e.charAt(0) + "***";
        return e.charAt(0) + "***" + e.substring(at);
    }
}
