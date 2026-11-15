package com.tony855.vehicletracking.telemetry;

import com.tony855.vehicletracking.device.DeviceCredentialService;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LocationIngestionService {
    private final JdbcTemplate jdbc;
    private final DeviceCredentialService credentials;

    public LocationIngestionService(JdbcTemplate jdbc, DeviceCredentialService credentials) {
        this.jdbc = jdbc;
        this.credentials = credentials;
    }

    public LocationIngestionResponse ingest(String deviceCredential, LocationTelemetryRequest request) {
        var principal = credentials.authenticate(request.deviceUid(), deviceCredential)
            .orElseThrow(UnauthenticatedDeviceException::new);

        int inserted = jdbc.update("""
            INSERT INTO gps_locations (
                tenant_id, device_id, event_id, recorded_at, position, speed_kmh,
                heading_degrees, altitude_m, satellites, accuracy_m, ignition_on
            ) VALUES (?, ?, ?, ?,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, ?, ?, ?
            ) ON CONFLICT (device_id, event_id) DO NOTHING
            """,
            principal.tenantId(), principal.deviceId(), request.eventId(), request.recordedAt(),
            request.longitude(), request.latitude(), request.speedKmh(), request.headingDegrees(),
            request.altitudeM(), request.satellites(), request.accuracyM(), request.ignitionOn()
        );
        jdbc.update("UPDATE devices SET last_seen_at = now() WHERE id = ? AND tenant_id = ?",
            principal.deviceId(), principal.tenantId());
        return new LocationIngestionResponse(inserted == 1 ? "accepted" : "duplicate", request.eventId());
    }

    public static class UnauthenticatedDeviceException extends RuntimeException {}
}