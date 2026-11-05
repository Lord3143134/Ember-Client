package com.ember.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Hides the SECRET category behind a 5 character code. Only a salted SHA-256 hash is
 * stored. This keeps the menu out of sight; it is not real security.
 */
public final class CodeLock {
    public static final int LENGTH = 5;
    private static boolean unlocked;

    private CodeLock() {}

    public static boolean isUnlocked() { return unlocked; }
    public static boolean hasCode() { return !Config.codeHash.isEmpty(); }
    public static void lock() { unlocked = false; }

    public static boolean tryUnlock(String code) {
        if (!hasCode()) return false;
        byte[] a = hash(Config.salt, code).getBytes(StandardCharsets.UTF_8);
        byte[] b = Config.codeHash.getBytes(StandardCharsets.UTF_8);
        if (MessageDigest.isEqual(a, b)) { unlocked = true; return true; }
        return false;
    }

    /** First-run: choose the code. */
    public static void setCode(String code) {
        byte[] s = new byte[12];
        new SecureRandom().nextBytes(s);
        Config.salt = HexFormat.of().formatHex(s);
        Config.codeHash = hash(Config.salt, code);
        unlocked = true;
        Config.save();
    }

    private static String hash(String salt, String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((salt + ":" + code.toUpperCase()).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
