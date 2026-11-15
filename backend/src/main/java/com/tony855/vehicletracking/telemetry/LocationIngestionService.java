package com.tony855.vehicletracking.telemetry;

import com.tony855.vehicletracking.device.DeviceCredentialService;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationIngestionService {
    private final JdbcTemplate jdbc;
    private final DeviceCredentialService credentials;

    public LocationIngestionService(JdbcTemplate jdbc, DeviceCredentialService credentials) {
        this.jdbc = jdbc;
        this.credentials = credentials;
    }

    @Transactional
    public LocationIngestionResponse ingest(String deviceCredential, LocationTelemetryRequest request) {
        var principal = credentials.authenticate(request.deviceUid(), deviceCredential)
            .orElseThrow(UnauthenticatedDeviceException::new);
        return ingestAuthenticated(principal, request);
    }

    /** Shared persistence path for HTTPS and broker-authenticated MQTT messages. */
    @Transactional
    public LocationIngestionResponse ingestAuthenticated(
        DeviceCredentialService.AuthenticatedDevice principal, LocationTelemetryRequest request) {
        if (!principal.deviceUid().equals(request.deviceUid())) {
            throw new UnauthenticatedDeviceException();
        }
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
        if (inserted == 1 && request.speedKmh() != null) {
            jdbc.update("""
                INSERT INTO alert_events(
                    tenant_id,vehicle_id,device_id,geofence_id,source_event_id,
                    alert_type,message,observed_speed_kmh,speed_limit_kmh,occurred_at
                )
                SELECT ?,b.vehicle_id,?,g.id,?,'GEOFENCE_SPEEDING',
                    'Vehicle speed exceeded the geofence speed limit',
                    ?,g.speed_limit_kmh,?
                FROM geofences g
                LEFT JOIN device_bindings b ON b.device_id=? AND b.tenant_id=g.tenant_id
                    AND b.unbound_at IS NULL
                WHERE g.tenant_id=? AND g.active=true AND g.speed_limit_kmh IS NOT NULL
                    AND ?>g.speed_limit_kmh
                    AND ST_Covers(g.area::geometry,ST_SetSRID(ST_MakePoint(?,?),4326))
                ON CONFLICT DO NOTHING
                """,principal.tenantId(),principal.deviceId(),request.eventId(),
                request.speedKmh(),request.recordedAt(),principal.deviceId(),principal.tenantId(),
                request.speedKmh(),request.longitude(),request.latitude());
        }
        jdbc.update("UPDATE devices SET last_seen_at = now() WHERE id = ? AND tenant_id = ?",
            principal.deviceId(), principal.tenantId());
        return new LocationIngestionResponse(inserted == 1 ? "accepted" : "duplicate", request.eventId());
    }

    public static class UnauthenticatedDeviceException extends RuntimeException {}
}
