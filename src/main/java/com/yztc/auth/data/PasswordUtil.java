package com.yztc.auth.data;

import com.yztc.auth.security.PasswordHasher;

import java.util.Base64;

public final class PasswordUtil {
    private static final Base64.Encoder ENC = Base64.getEncoder();
    private PasswordUtil() {}

    public static void assign(Account a, String plain, int iterations) {
        PasswordHasher.Hashed h = PasswordHasher.pbkdf2(plain.toCharArray(), iterations, 16);
        a.algo = PasswordHasher.ALGO_PBKDF2;
        a.iterations = h.iterations();
        a.salt = ENC.encodeToString(h.salt());
        a.hash = ENC.encodeToString(h.hash());
        a.failedAttempts = 0;
        a.lockedUntil = 0;
    }
}
