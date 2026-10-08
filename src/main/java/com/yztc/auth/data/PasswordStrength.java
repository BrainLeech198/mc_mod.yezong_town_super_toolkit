package com.yztc.auth.data;

/** 纯逻辑：按长度与字符种类粗分密码强度，仅用于注册后的友好提示。 */
public final class PasswordStrength {
    private PasswordStrength() {}

    public enum Level { WEAK, MEDIUM, STRONG }

    public static Level of(String pw) {
        if (pw == null) return Level.WEAK;
        int lower = 0, upper = 0, digit = 0, other = 0;
        for (int i = 0; i < pw.length(); i++) {
            char c = pw.charAt(i);
            if (Character.isLowerCase(c)) lower = 1;
            else if (Character.isUpperCase(c)) upper = 1;
            else if (Character.isDigit(c)) digit = 1;
            else other = 1;
        }
        int classes = lower + upper + digit + other;
        if (pw.length() >= 8 && classes >= 3) return Level.STRONG;
        if (pw.length() >= 6 && classes >= 2) return Level.MEDIUM;
        return Level.WEAK;
    }
}
