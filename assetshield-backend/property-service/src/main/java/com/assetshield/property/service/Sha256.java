package com.assetshield.property.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 over raw bytes, lowercase hex — the evidence integrity primitive. */
public final class Sha256 {

    private Sha256() {
    }

    public static String hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Case-insensitive comparison against a client-declared hash. */
    public static boolean matches(byte[] bytes, String declaredHex) {
        return hex(bytes).equalsIgnoreCase(declaredHex);
    }
}
