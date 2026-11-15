package com.tony855.vehicletracking.telemetry;

import com.tony855.vehicletracking.device.DeviceCredentialService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class DeviceLocationQueryService {
    private static final RowMapper<LocationPointResponse> LOCATION_MAPPER =
        DeviceLocationQueryService::mapLocation;

    private final JdbcTemplate jdbc;
    private final DeviceCredentialService credentials;

    public DeviceLocationQueryService(JdbcTemplate jdbc, DeviceCredentialService credentials) {
        this.jdbc = jdbc;
        this.credentials = credentials;
    }

    public LocationPointResponse latest(String deviceUid, String credential) {
        var device = credentials.authenticate(deviceUid, credential)
            .orElseThrow(UnauthenticatedDeviceException::new);
        List<LocationPointResponse> rows = jdbc.query("""
            SELECT event_id, recorded_at,
                   ST_Y(position::geometry) AS latitude,
                   ST_X(position::geometry) AS longitude,
                   speed_kmh, heading_degrees, altitude_m, satellites, accuracy_m, ignition_on
            FROM gps_locations
            WHERE tenant_id = ? AND device_id = ?
            ORDER BY recorded_at DESC, received_at DESC
            LIMIT 1
            """, LOCATION_MAPPER, device.tenantId(), device.deviceId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<LocationPointResponse> history(String deviceUid, String credential,
                                                Instant from, Instant to, int limit) {
        var device = credentials.authenticate(deviceUid, credential)
            .orElseThrow(UnauthenticatedDeviceException::new);
        return jdbc.query("""
            SELECT event_id, recorded_at,
                   ST_Y(position::geometry) AS latitude,
                   ST_X(position::geometry) AS longitude,
                   speed_kmh, heading_degrees, altitude_m, satellites, accuracy_m, ignition_on
            FROM gps_locations
            WHERE tenant_id = ? AND device_id = ?
              AND recorded_at >= ? AND recorded_at <= ?
            ORDER BY recorded_at DESC, received_at DESC
            LIMIT ?
            """, LOCATION_MAPPER, device.tenantId(), device.deviceId(), from, to,
            Math.min(Math.max(limit, 1), 1000));
    }

    public record DevicePresence(String deviceUid, Instant lastSeenAt, boolean online) {}

    public DevicePresence presence(String deviceUid, String credential) {
        var device = credentials.authenticate(deviceUid, credential)
            .orElseThrow(UnauthenticatedDeviceException::new);
        List<java.sql.Timestamp> seen = jdbc.query(
            "SELECT last_seen_at FROM devices WHERE id = ? AND tenant_id = ?",
            (rs, row) -> rs.getTimestamp("last_seen_at"), device.deviceId(), device.tenantId());
        Instant lastSeen = seen.isEmpty() || seen.get(0) == null ? null : seen.get(0).toInstant();
        boolean online = lastSeen != null && lastSeen.isAfter(Instant.now().minusSeconds(180));
        return new DevicePresence(deviceUid, lastSeen, online);
    }

    private static LocationPointResponse mapLocation(ResultSet rs, int row) throws SQLException {
        int satelliteCount = rs.getInt("satellites");
        Integer satellites = rs.wasNull() ? null : satelliteCount;
        return new LocationPointResponse(
            rs.getObject("event_id", java.util.UUID.class),
            rs.getTimestamp("recorded_at").toInstant(),
            rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"),
            rs.getBigDecimal("speed_kmh"), rs.getBigDecimal("heading_degrees"),
            rs.getBigDecimal("altitude_m"), satellites,
            rs.getBigDecimal("accuracy_m"), (Boolean) rs.getObject("ignition_on")
        );
    }

    public static class UnauthenticatedDeviceException extends RuntimeException {}
}
