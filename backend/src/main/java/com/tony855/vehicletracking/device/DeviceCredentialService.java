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

    private record CredentialRecord(UUID deviceId, UUID tenantId, String credentialHash) {}

    public record AuthenticatedDevice(UUID deviceId, UUID tenantId, String deviceUid) {}
}