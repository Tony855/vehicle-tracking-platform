package com.tony855.vehicletracking.telemetry;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fleet")
public class FleetLocationQueryController {
    private final FleetLocationQueryService locations;

    public FleetLocationQueryController(FleetLocationQueryService locations) {
        this.locations=locations;
    }

    @GetMapping("/locations/latest")
    public List<FleetLocationQueryService.FleetLocationPoint> latest(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue="500") @Min(1) @Max(1000) int limit) {
        requireFleetReader(jwt);
        return locations.latestForTenant(tenant(jwt),limit);
    }

    @GetMapping("/vehicles/{vehicleId}/locations")
    public ResponseEntity<?> history(@AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID vehicleId,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue="500") @Min(1) @Max(1000) int limit,
        @RequestParam(defaultValue="0") @Min(0) int offset) {
        requireFleetReader(jwt);
        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().body(Map.of("error","invalid_time_range"));
        }
        return ResponseEntity.ok(locations.history(tenant(jwt),vehicleId,from,to,limit,offset));
    }

    private void requireFleetReader(Jwt jwt) {
        if (jwt==null) throw new AccessDeniedException("Authentication required");
        String role=jwt.getClaimAsString("role");
        if (!List.of("OWNER","ADMIN","DISPATCHER","VIEWER").contains(role)) {
            throw new AccessDeniedException("Fleet read permission required");
        }
    }

    private UUID tenant(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("tenant_id"));
    }
}
