package com.finpilot.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30 s step) with RFC 4648 base32 secrets. */
public final class Totp {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int STEP_SECONDS = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public static String otpauthUri(String issuer, String account, String secret) {
        String label = java.net.URLEncoder.encode(issuer + ":" + account, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer="
                + java.net.URLEncoder.encode(issuer, java.nio.charset.StandardCharsets.UTF_8) + "&digits=6&period=30";
    }

    /** Verifies a code allowing +/- one time step of clock drift. */
    public static boolean verify(String secret, String code, Clock clock) {
        return match(secret, code, clock).isPresent();
    }

    /** Returns the time-step counter the code belongs to (within +/- one step), for replay protection. */
    public static java.util.OptionalLong match(String secret, String code, Clock clock) {
        if (secret == null || code == null || !code.matches("\\d{6}")) {
            return java.util.OptionalLong.empty();
        }
        long counter = clock.instant().getEpochSecond() / STEP_SECONDS;
        for (long c = counter - 1; c <= counter + 1; c++) {
            if (constantTimeEquals(generate(secret, c), code)) {
                return java.util.OptionalLong.of(c);
            }
        }
        return java.util.OptionalLong.empty();
    }

    public static String codeAt(String secret, long epochSecond) {
        return generate(secret, epochSecond / STEP_SECONDS);
    }

    static String generate(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    static byte[] base32Decode(String s) {
        String clean = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char ch : clean.toCharArray()) {
            int val = ALPHABET.indexOf(ch);
            if (val < 0) {
                throw new IllegalArgumentException("invalid base32 character");
            }
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }
}
