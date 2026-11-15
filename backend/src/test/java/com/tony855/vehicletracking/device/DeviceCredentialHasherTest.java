package com.tony855.vehicletracking.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeviceCredentialHasherTest {
    @Test
    void hashesDeterministicallyAndVerifiesSecret() {
        String hash = DeviceCredentialHasher.hash("a-long-random-device-secret");
        assertEquals(64, hash.length());
        assertTrue(DeviceCredentialHasher.matches("a-long-random-device-secret", hash));
        assertFalse(DeviceCredentialHasher.matches("wrong-secret", hash));
    }

    @Test
    void rejectsMalformedStoredHash() {
        assertFalse(DeviceCredentialHasher.matches("secret", "not-a-hash"));
    }
}
