package com.tony855.vehicletracking.vehicle;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {
    private final JdbcTemplate jdbc;

    public VehicleController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody VehicleRequest request) {
        requireFleetWrite(jwt);
        UUID tenantId = tenantId(jwt);
        UUID id = jdbc.queryForObject("""
            INSERT INTO vehicles(tenant_id,plate_number,display_name,make,model,year,color,notes)
            VALUES (?,?,?,?,?,?,?,?) RETURNING id
            """, UUID.class, tenantId, request.plateNumber().trim().toUpperCase(),
            request.displayName(), request.make(), request.model(), request.year(),
            request.color(), request.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("vehicleId", id, "status", "created"));
    }

    @GetMapping
    public List<VehicleView> list(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantId(jwt);
        return jdbc.query("""
            SELECT v.id,v.plate_number,v.display_name,v.make,v.model,v.year,v.color,v.status,
                   d.id AS device_id,d.device_uid,d.hardware_type
            FROM vehicles v
            LEFT JOIN device_bindings b ON b.vehicle_id=v.id AND b.tenant_id=v.tenant_id AND b.unbound_at IS NULL
            LEFT JOIN devices d ON d.id=b.device_id AND d.tenant_id=b.tenant_id
            WHERE v.tenant_id=? ORDER BY v.created_at DESC
            """, (rs, row) -> {
                int yearValue = rs.getInt("year");
                Integer year = rs.wasNull() ? null : yearValue;
                return new VehicleView(rs.getObject("id", UUID.class), rs.getString("plate_number"),
                    rs.getString("display_name"), rs.getString("make"), rs.getString("model"),
                    year, rs.getString("color"), rs.getString("status"),
                    rs.getObject("device_id", UUID.class), rs.getString("device_uid"),
                    rs.getString("hardware_type"));
            }, tenantId);
    }

    @PostMapping("/{vehicleId}/device")
    @Transactional
    public ResponseEntity<?> bind(@AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID vehicleId, @Valid @RequestBody BindDeviceRequest request) {
        requireFleetWrite(jwt);
        UUID tenantId = tenantId(jwt);
        Integer vehicleCount = jdbc.queryForObject(
            "SELECT count(*) FROM vehicles WHERE id=? AND tenant_id=? AND status='ACTIVE'",
            Integer.class, vehicleId, tenantId);
        Integer deviceCount = jdbc.queryForObject(
            "SELECT count(*) FROM devices WHERE id=? AND tenant_id=? AND status='ACTIVE'",
            Integer.class, request.deviceId(), tenantId);
        if (vehicleCount == null || vehicleCount != 1 || deviceCount == null || deviceCount != 1) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "vehicle_or_device_not_found"));
        }
        List<UUID> ids = jdbc.query("""
            INSERT INTO device_bindings(tenant_id,device_id,vehicle_id)
            VALUES (?,?,?) ON CONFLICT DO NOTHING RETURNING id
            """, (rs, row) -> rs.getObject("id", UUID.class), tenantId, request.deviceId(), vehicleId);
        if (ids.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "vehicle_or_device_already_bound"));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("bindingId", ids.get(0), "status", "bound"));
    }

    @DeleteMapping("/{vehicleId}/device")
    @Transactional
    public ResponseEntity<?> unbind(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vehicleId) {
        requireFleetWrite(jwt);
        int changed = jdbc.update("""
            UPDATE device_bindings SET unbound_at=now()
            WHERE tenant_id=? AND vehicle_id=? AND unbound_at IS NULL
            """, tenantId(jwt), vehicleId);
        return changed == 0
            ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "active_binding_not_found"))
            : ResponseEntity.ok(Map.of("status", "unbound"));
    }

    private void requireFleetWrite(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (!"OWNER".equals(role) && !"ADMIN".equals(role) && !"DISPATCHER".equals(role)) {
            throw new AccessDeniedException("Fleet write permission required");
        }
    }

    private UUID tenantId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("tenant_id"));
    }

    public record VehicleRequest(@NotBlank @Size(max = 32) String plateNumber,
        @Size(max = 120) String displayName, @Size(max = 80) String make,
        @Size(max = 80) String model, @Min(1900) @Max(2200) Integer year,
        @Size(max = 40) String color, @Size(max = 1000) String notes) {}
    public record BindDeviceRequest(@NotNull UUID deviceId) {}
    public record VehicleView(UUID vehicleId, String plateNumber, String displayName,
        String make, String model, Integer year, String color, String status,
        UUID deviceId, String deviceUid, String hardwareType) {}
}
