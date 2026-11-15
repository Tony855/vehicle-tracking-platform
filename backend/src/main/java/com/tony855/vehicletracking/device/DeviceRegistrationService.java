package com.tony855.vehicletracking.device;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceRegistrationService {
    private final JdbcTemplate jdbc;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeviceRegistrationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public DeviceRegistrationResponse register(DeviceRegistrationRequest request) {
        String enrollmentHash = DeviceCredentialHasher.hash(request.enrollmentToken());
        EnrollmentToken token;
        try {
            token = jdbc.queryForObject("""
                SELECT id, tenant_id, max_devices, used_count, expires_at, revoked_at
                FROM device_enrollment_tokens
                WHERE token_hash = ?
                FOR UPDATE
                """, (rs, row) -> new EnrollmentToken(
                    rs.getObject("id", UUID.class),
                    rs.getObject("tenant_id", UUID.class),
                    rs.getInt("max_devices"),
                    rs.getInt("used_count"),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()
                ), enrollmentHash);
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new InvalidEnrollmentTokenException();
        }

        Instant now = Instant.now();
        if (token.revokedAt() != null || !token.expiresAt().isAfter(now)
            || token.usedCount() >= token.maxDevices()) {
            throw new InvalidEnrollmentTokenException();
        }

        int consumed = jdbc.update("""
            UPDATE device_enrollment_tokens
            SET used_count = used_count + 1
            WHERE id = ? AND revoked_at IS NULL
              AND expires_at > now() AND used_count < max_devices
            """, token.id());
        if (consumed != 1) throw new InvalidEnrollmentTokenException();

        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);
        String credential = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        UUID deviceId;
        try {
            deviceId = jdbc.queryForObject("""
                INSERT INTO devices (tenant_id, device_uid, hardware_type, status, credential_hash)
                VALUES (?, ?, ?, 'ACTIVE', ?)
                RETURNING id
                """, UUID.class, token.tenantId(), request.deviceUid(),
                request.hardwareType().name(), DeviceCredentialHasher.hash(credential));
        } catch (DataIntegrityViolationException duplicate) {
            throw new DuplicateDeviceException();
        }

        return new DeviceRegistrationResponse(deviceId, request.deviceUid(),
            credential, "Bearer", now);
    }

    private record EnrollmentToken(UUID id, UUID tenantId, int maxDevices,
                                   int usedCount, Instant expiresAt, Instant revokedAt) {}

    public static class InvalidEnrollmentTokenException extends RuntimeException {}
    public static class DuplicateDeviceException extends RuntimeException {}
}
