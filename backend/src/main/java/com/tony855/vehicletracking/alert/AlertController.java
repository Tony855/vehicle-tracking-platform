package com.tony855.vehicletracking.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {
    private final JdbcTemplate jdbc;

    public AlertController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @GetMapping
    public List<AlertView> list(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue="false") boolean unacknowledgedOnly,
        @RequestParam(defaultValue="100") int limit,
        @RequestParam(defaultValue="0") int offset) {
        requireReader(jwt);
        return jdbc.query("""
            SELECT a.id,a.alert_type,a.message,a.vehicle_id,v.plate_number,
                   a.device_id,d.device_uid,d.hardware_type,a.geofence_id,g.name AS geofence_name,
                   a.observed_speed_kmh,a.speed_limit_kmh,a.occurred_at,a.acknowledged_at
            FROM alert_events a
            LEFT JOIN vehicles v ON v.id=a.vehicle_id AND v.tenant_id=a.tenant_id
            JOIN devices d ON d.id=a.device_id AND d.tenant_id=a.tenant_id
            LEFT JOIN geofences g ON g.id=a.geofence_id AND g.tenant_id=a.tenant_id
            WHERE a.tenant_id=? AND (?=false OR a.acknowledged_at IS NULL)
            ORDER BY a.occurred_at DESC
            LIMIT ? OFFSET ?
            """,(rs,row)->new AlertView(rs.getObject("id",UUID.class),rs.getString("alert_type"),
                rs.getString("message"),rs.getObject("vehicle_id",UUID.class),rs.getString("plate_number"),
                rs.getObject("device_id",UUID.class),rs.getString("device_uid"),rs.getString("hardware_type"),
                rs.getObject("geofence_id",UUID.class),rs.getString("geofence_name"),
                rs.getBigDecimal("observed_speed_kmh"),rs.getBigDecimal("speed_limit_kmh"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getTimestamp("acknowledged_at")==null?null:rs.getTimestamp("acknowledged_at").toInstant()),
            tenant(jwt),unacknowledgedOnly,Math.min(Math.max(limit,1),500),Math.max(offset,0));
    }

    @PostMapping("/{alertId}/acknowledge")
    public ResponseEntity<?> acknowledge(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID alertId) {
        requireOperator(jwt);
        int changed=jdbc.update("""
            UPDATE alert_events SET acknowledged_at=now()
            WHERE id=? AND tenant_id=? AND acknowledged_at IS NULL
            """,alertId,tenant(jwt));
        return changed==1?ResponseEntity.ok(Map.of("status","acknowledged"))
            :ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","unacknowledged_alert_not_found"));
    }

    private void requireReader(Jwt jwt) {
        if(jwt==null || !List.of("OWNER","ADMIN","DISPATCHER","VIEWER").contains(jwt.getClaimAsString("role")))
            throw new AccessDeniedException("Fleet read permission required");
    }
    private void requireOperator(Jwt jwt) {
        if(jwt==null || !List.of("OWNER","ADMIN","DISPATCHER").contains(jwt.getClaimAsString("role")))
            throw new AccessDeniedException("Alert acknowledgement permission required");
    }
    private UUID tenant(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("tenant_id")); }

    public record AlertView(UUID alertId,String alertType,String message,UUID vehicleId,
        String plateNumber,UUID deviceId,String deviceUid,String hardwareType,UUID geofenceId,
        String geofenceName,BigDecimal observedSpeedKmh,BigDecimal speedLimitKmh,
        Instant occurredAt,Instant acknowledgedAt) {}
}
