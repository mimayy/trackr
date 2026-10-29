package com.uniace.trackr.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Random secret generation and hashing for activation and refresh tokens. */
public final class TokenUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private TokenUtil() {
    }

    /** A 256-bit URL-safe random string. */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * SHA-256, base64url encoded. Used for tokens that are high entropy random
     * values - a slow KDF adds nothing when there is nothing to guess, and these
     * are verified on every refresh.
     */
    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return URL_ENCODER.encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    public static byte[] sha256Bytes(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    public static String base64Url(byte[] bytes) {
        return URL_ENCODER.encodeToString(bytes);
    }

    /**
     * Decodes base64url. Browsers are inconsistent about padding and some
     * libraries hand back standard base64, so both are accepted.
     */
    public static byte[] fromBase64Url(String value) {
        String normalised = value.trim()
                .replace('+', '-')
                .replace('/', '_')
                .replace("=", "");
        return URL_DECODER.decode(normalised);
    }
}
