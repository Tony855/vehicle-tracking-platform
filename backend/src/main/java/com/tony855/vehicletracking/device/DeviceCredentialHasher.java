package com.tony855.vehicletracking.device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class DeviceCredentialHasher {
    private DeviceCredentialHasher() {}

    public static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public static boolean matches(String secret, String expectedHexHash) {
        if (secret == null || expectedHexHash == null) return false;
        try {
            byte[] actual = HexFormat.of().parseHex(hash(secret));
            byte[] expected = HexFormat.of().parseHex(expectedHexHash);
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException invalidHash) {
            return false;
        }
    }
}
