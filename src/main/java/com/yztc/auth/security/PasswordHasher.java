package com.yztc.auth.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Locale;
import java.util.Base64;

public final class PasswordHasher {
    public static final String ALGO_PBKDF2 = "pbkdf2-sha256";
    public static final String ALGO_LEGACY = "sha256-legacy";
    public static final int DEFAULT_ITERATIONS = 120000;
    public static final int SALT_BYTES = 16;
    public static final int KEY_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    public record Hashed(byte[] salt, byte[] hash, int iterations) {}

    public static Hashed pbkdf2(char[] password) {
        return pbkdf2(password, DEFAULT_ITERATIONS, SALT_BYTES);
    }

    public static Hashed pbkdf2(char[] password, int iterations, int saltBytes) {
        byte[] salt = new byte[saltBytes];
        RANDOM.nextBytes(salt);
        return new Hashed(salt, derive(password, salt, iterations), iterations);
    }

    public static boolean verifyPbkdf2(char[] password, int iterations, byte[] salt, byte[] expected) {
        if (password == null || salt == null || expected == null
                || iterations < 1 || iterations > 2_000_000) return false;
        try {
            return MessageDigest.isEqual(derive(password, salt, iterations), expected);
        } catch (IllegalArgumentException e) {
            // Corrupt persisted parameters must fail closed rather than escape into a server command/tick.
            return false;
        }
    }

    /** Verify a persisted PBKDF2 record. Invalid Base64 or missing fields are rejected without throwing. */
    public static boolean verifyPbkdf2Encoded(char[] password, Integer iterations,
                                               String salt, String expected) {
        if (password == null || iterations == null || salt == null || expected == null) return false;
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            return verifyPbkdf2(password, iterations, decoder.decode(salt), decoder.decode(expected));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 不可用", e);
        }
    }

    public static String legacySha256Hex(char[] password) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(new String(password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public static boolean verifyLegacy(String inputHex, String storedHex) {
        if (inputHex == null || storedHex == null) return false;
        return MessageDigest.isEqual(
                inputHex.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                storedHex.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }
}
