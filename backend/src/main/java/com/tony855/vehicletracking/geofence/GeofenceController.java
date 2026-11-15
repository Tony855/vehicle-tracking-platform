package com.tony855.vehicletracking.geofence;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/geofences")
public class GeofenceController {
    private final JdbcTemplate jdbc;

    public GeofenceController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody GeofenceRequest request) {
        requireOperator(jwt);
        if (!validPolygon(request.polygonWkt())) {
            return ResponseEntity.badRequest().body(Map.of("error","invalid_polygon_wkt"));
        }
        try {
            UUID id=jdbc.queryForObject("""
                INSERT INTO geofences(tenant_id,name,area,speed_limit_kmh)
                VALUES (?, ?, ST_GeomFromText(?,4326)::geography, ?) RETURNING id
                """,UUID.class,tenant(jwt),request.name().trim(),request.polygonWkt(),request.speedLimitKmh());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("geofenceId",id,"status","created"));
        } catch (DataAccessException e) {
            return ResponseEntity.badRequest().body(Map.of("error","invalid_polygon_geometry"));
        }
    }

    @GetMapping
    public List<GeofenceView> list(@AuthenticationPrincipal Jwt jwt) {
        requireReader(jwt);
        return jdbc.query("""
            SELECT id,name,ST_AsText(area::geometry) AS polygon_wkt,
                   speed_limit_kmh,active,created_at,updated_at
            FROM geofences WHERE tenant_id=? ORDER BY created_at DESC
            """,(rs,row)->new GeofenceView(rs.getObject("id",UUID.class),rs.getString("name"),
                rs.getString("polygon_wkt"),rs.getBigDecimal("speed_limit_kmh"),rs.getBoolean("active"),
                rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant()),tenant(jwt));
    }

    @DeleteMapping("/{geofenceId}")
    public ResponseEntity<?> disable(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID geofenceId) {
        requireOperator(jwt);
        int changed=jdbc.update("""
            UPDATE geofences SET active=false,updated_at=now()
            WHERE id=? AND tenant_id=? AND active=true
            """,geofenceId,tenant(jwt));
        return changed==1?ResponseEntity.ok(Map.of("status","disabled"))
            :ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","geofence_not_found"));
    }

    private boolean validPolygon(String wkt) {
        if (wkt==null || wkt.length()>20000 || !wkt.trim().toUpperCase().startsWith("POLYGON")) return false;
        try {
            Boolean valid=jdbc.queryForObject("""
                SELECT ST_IsValid(g) AND GeometryType(g)='POLYGON'
                    AND ST_XMin(g)>=-180 AND ST_XMax(g)<=180
                    AND ST_YMin(g)>=-90 AND ST_YMax(g)<=90
                FROM (SELECT ST_GeomFromText(?,4326) AS g) p
                """,Boolean.class,wkt);
            return Boolean.TRUE.equals(valid);
        } catch (DataAccessException e) { return false; }
    }

    private void requireReader(Jwt jwt) {
        if(jwt==null || !List.of("OWNER","ADMIN","DISPATCHER","VIEWER").contains(jwt.getClaimAsString("role")))
            throw new AccessDeniedException("Fleet read permission required");
    }
    private void requireOperator(Jwt jwt) {
        if(jwt==null || !List.of("OWNER","ADMIN","DISPATCHER").contains(jwt.getClaimAsString("role")))
            throw new AccessDeniedException("Fleet write permission required");
    }
    private UUID tenant(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("tenant_id")); }

    public record GeofenceRequest(@NotBlank @Size(max=120) String name,
        @NotBlank @Size(max=20000) String polygonWkt,
        @DecimalMin("1.0") @DecimalMax("300.0") BigDecimal speedLimitKmh) {}
    public record GeofenceView(UUID geofenceId,String name,String polygonWkt,
        BigDecimal speedLimitKmh,boolean active,Instant createdAt,Instant updatedAt) {}
}
