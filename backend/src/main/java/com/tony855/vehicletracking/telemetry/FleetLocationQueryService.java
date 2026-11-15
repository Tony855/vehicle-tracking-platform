package com.tony855.vehicletracking.telemetry;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class FleetLocationQueryService {
    private static final RowMapper<FleetLocationPoint> MAPPER = FleetLocationQueryService::map;
    private final JdbcTemplate jdbc;

    public FleetLocationQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FleetLocationPoint> latestForTenant(UUID tenantId, int limit) {
        return jdbc.query("""
            SELECT v.id AS vehicle_id, v.plate_number, v.display_name,
                   d.device_uid, d.hardware_type, g.event_id, g.recorded_at,
                   ST_Y(g.position::geometry) AS latitude,
                   ST_X(g.position::geometry) AS longitude,
                   g.speed_kmh, g.heading_degrees, g.altitude_m,
                   g.satellites, g.accuracy_m, g.ignition_on
            FROM vehicles v
            JOIN device_bindings b ON b.vehicle_id=v.id AND b.tenant_id=v.tenant_id
                AND b.unbound_at IS NULL
            JOIN devices d ON d.id=b.device_id AND d.tenant_id=b.tenant_id AND d.status='ACTIVE'
            JOIN LATERAL (
                SELECT * FROM gps_locations x
                WHERE x.tenant_id=v.tenant_id AND x.device_id=d.id
                ORDER BY x.recorded_at DESC, x.received_at DESC LIMIT 1
            ) g ON TRUE
            WHERE v.tenant_id=? AND v.status='ACTIVE'
            ORDER BY g.recorded_at DESC
            LIMIT ?
            """, MAPPER, tenantId, Math.min(Math.max(limit, 1), 1000));
    }

    public List<FleetLocationPoint> history(UUID tenantId, UUID vehicleId,
                                             Instant from, Instant to, int limit, int offset) {
        return jdbc.query("""
            SELECT v.id AS vehicle_id, v.plate_number, v.display_name,
                   d.device_uid, d.hardware_type, g.event_id, g.recorded_at,
                   ST_Y(g.position::geometry) AS latitude,
                   ST_X(g.position::geometry) AS longitude,
                   g.speed_kmh, g.heading_degrees, g.altitude_m,
                   g.satellites, g.accuracy_m, g.ignition_on
            FROM vehicles v
            JOIN device_bindings b ON b.vehicle_id=v.id AND b.tenant_id=v.tenant_id
            JOIN devices d ON d.id=b.device_id AND d.tenant_id=b.tenant_id
            JOIN gps_locations g ON g.device_id=d.id AND g.tenant_id=d.tenant_id
                AND g.recorded_at >= b.bound_at
                AND (b.unbound_at IS NULL OR g.recorded_at <= b.unbound_at)
            WHERE v.tenant_id=? AND v.id=? AND g.recorded_at>=? AND g.recorded_at<=?
            ORDER BY g.recorded_at DESC, g.received_at DESC
            LIMIT ? OFFSET ?
            """, MAPPER, tenantId, vehicleId, from, to,
            Math.min(Math.max(limit, 1), 1000), Math.max(offset, 0));
    }

    private static FleetLocationPoint map(ResultSet rs, int row) throws SQLException {
        int count=rs.getInt("satellites");
        Integer satellites=rs.wasNull()?null:count;
        return new FleetLocationPoint(
            rs.getObject("vehicle_id", UUID.class), rs.getString("plate_number"),
            rs.getString("display_name"), rs.getString("device_uid"), rs.getString("hardware_type"),
            rs.getObject("event_id", UUID.class), rs.getTimestamp("recorded_at").toInstant(),
            rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"),
            rs.getBigDecimal("speed_kmh"), rs.getBigDecimal("heading_degrees"),
            rs.getBigDecimal("altitude_m"), satellites, rs.getBigDecimal("accuracy_m"),
            (Boolean)rs.getObject("ignition_on"));
    }

    public record FleetLocationPoint(UUID vehicleId, String plateNumber, String displayName,
        String deviceUid, String hardwareType, UUID eventId, Instant recordedAt,
        BigDecimal latitude, BigDecimal longitude, BigDecimal speedKmh,
        BigDecimal headingDegrees, BigDecimal altitudeM, Integer satellites,
        BigDecimal accuracyM, Boolean ignitionOn) {}
}
