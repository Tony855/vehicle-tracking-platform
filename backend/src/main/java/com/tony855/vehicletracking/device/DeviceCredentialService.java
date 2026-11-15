package com.tony855.vehicletracking.device;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DeviceCredentialService {
    private final JdbcTemplate jdbc;

    public DeviceCredentialService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns an authenticated device identity; never returns the stored credential hash. */
    public Optional<AuthenticatedDevice> authenticate(String deviceUid, String presentedSecret) {
        if (deviceUid == null || deviceUid.isBlank() || presentedSecret == null || presentedSecret.isBlank()) {
            return Optional.empty();
        }
        List<CredentialRecord> records = jdbc.query(
            "SELECT id, tenant_id, credential_hash FROM devices WHERE device_uid = ? AND status = 'ACTIVE'",
            (rs, rowNum) -> new CredentialRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("credential_hash")
            ), deviceUid
        );
        return records.stream()
            .filter(record -> DeviceCredentialHasher.matches(presentedSecret, record.credentialHash()))
            .findFirst()
            .map(record -> new AuthenticatedDevice(record.deviceId(), record.tenantId(), deviceUid));
    }

    /** Resolve a broker-authenticated device by the tenant and device IDs embedded in its authorized topic. */
    public Optional<AuthenticatedDevice> findActiveDevice(UUID tenantId, UUID deviceId) {
        List<String> deviceUids = jdbc.query(
            "SELECT device_uid FROM devices WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'",
            (rs, rowNum) -> rs.getString("device_uid"), deviceId, tenantId);
        return deviceUids.stream().findFirst()
            .map(uid -> new AuthenticatedDevice(deviceId, tenantId, uid));
    }

    private record CredentialRecord(UUID deviceId, UUID tenantId, String credentialHash) {}

    public record AuthenticatedDevice(UUID deviceId, UUID tenantId, String deviceUid) {}
}