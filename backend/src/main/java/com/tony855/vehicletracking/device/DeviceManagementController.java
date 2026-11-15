package com.tony855.vehicletracking.device;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
@RequestMapping("/api/v1/devices")
public class DeviceManagementController {
    private final JdbcTemplate jdbc;

    public DeviceManagementController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<DeviceView> list(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        UUID tenantId=tenant(jwt);
        return jdbc.query("""
            SELECT d.id,d.device_uid,d.hardware_type,d.status,d.created_at,d.last_seen_at,
                   v.id AS vehicle_id,v.plate_number
            FROM devices d
            LEFT JOIN device_bindings b ON b.device_id=d.id AND b.tenant_id=d.tenant_id AND b.unbound_at IS NULL
            LEFT JOIN vehicles v ON v.id=b.vehicle_id AND v.tenant_id=b.tenant_id
            WHERE d.tenant_id=? ORDER BY d.created_at DESC
            """,(rs,row)->new DeviceView(rs.getObject("id",UUID.class),rs.getString("device_uid"),
                rs.getString("hardware_type"),rs.getString("status"),rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_seen_at")==null?null:rs.getTimestamp("last_seen_at").toInstant(),
                rs.getObject("vehicle_id",UUID.class),rs.getString("plate_number")),tenantId);
    }

    @PatchMapping("/{deviceId}/status")
    public ResponseEntity<?> setStatus(@AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID deviceId,@Valid @RequestBody DeviceStatusRequest request) {
        requireAdmin(jwt);
        int changed=jdbc.update("""
            UPDATE devices SET status=?
            WHERE id=? AND tenant_id=?
            """,request.status(),deviceId,tenant(jwt));
        return changed==1 ? ResponseEntity.ok(Map.of("status",request.status()))
            : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","device_not_found"));
    }

    private void requireAdmin(Jwt jwt) {
        String role=jwt.getClaimAsString("role");
        if (!"OWNER".equals(role) && !"ADMIN".equals(role)) {
            throw new AccessDeniedException("Administrator role required");
        }
    }
    private UUID tenant(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("tenant_id")); }

    public record DeviceStatusRequest(@NotBlank @Pattern(regexp="ACTIVE|DISABLED|REVOKED") String status) {}
    public record DeviceView(UUID deviceId,String deviceUid,String hardwareType,String status,
        Instant createdAt,Instant lastSeenAt,UUID vehicleId,String plateNumber) {}
}
