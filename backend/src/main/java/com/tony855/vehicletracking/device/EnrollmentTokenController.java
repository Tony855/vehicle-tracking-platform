package com.tony855.vehicletracking.device;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
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
@RequestMapping("/api/v1/device-enrollment-tokens")
public class EnrollmentTokenController {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public EnrollmentTokenController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateTokenRequest request) {
        requireAdmin(jwt);
        Instant now=Instant.now();
        if (!request.expiresAt().isAfter(now) || request.expiresAt().isAfter(now.plusSeconds(31536000))) {
            return ResponseEntity.badRequest().body(Map.of("error","expiry_must_be_within_one_year"));
        }
        byte[] bytes=new byte[32];
        random.nextBytes(bytes);
        String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id=jdbc.queryForObject("""
            INSERT INTO device_enrollment_tokens(tenant_id,token_hash,label,max_devices,expires_at)
            VALUES (?,?,?,?,?) RETURNING id
            """,UUID.class,tenant(jwt),DeviceCredentialHasher.hash(raw),request.label(),
            request.maxDevices(),request.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new CreatedToken(id,raw,request.label(),request.maxDevices(),request.expiresAt()));
    }

    @GetMapping
    public List<TokenView> list(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return jdbc.query("""
            SELECT id,label,max_devices,used_count,expires_at,revoked_at,created_at
            FROM device_enrollment_tokens WHERE tenant_id=? ORDER BY created_at DESC
            """,(rs,row)->new TokenView(rs.getObject("id",UUID.class),rs.getString("label"),
                rs.getInt("max_devices"),rs.getInt("used_count"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("revoked_at")==null?null:rs.getTimestamp("revoked_at").toInstant(),
                rs.getTimestamp("created_at").toInstant()),tenant(jwt));
    }

    @PostMapping("/{tokenId}/revoke")
    public ResponseEntity<?> revoke(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID tokenId) {
        requireAdmin(jwt);
        int changed=jdbc.update("""
            UPDATE device_enrollment_tokens SET revoked_at=now()
            WHERE id=? AND tenant_id=? AND revoked_at IS NULL
            """,tokenId,tenant(jwt));
        return changed==1 ? ResponseEntity.ok(Map.of("status","revoked"))
            : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","token_not_found_or_revoked"));
    }

    private void requireAdmin(Jwt jwt) {
        String role=jwt.getClaimAsString("role");
        if (!"OWNER".equals(role) && !"ADMIN".equals(role)) {
            throw new AccessDeniedException("Administrator role required");
        }
    }
    private UUID tenant(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("tenant_id")); }

    public record CreateTokenRequest(@NotBlank @Size(max=120) String label,
        @Min(1) @Max(10000) int maxDevices,@NotNull Instant expiresAt) {}
    public record CreatedToken(UUID tokenId,String enrollmentToken,String label,
        int maxDevices,Instant expiresAt) {}
    public record TokenView(UUID tokenId,String label,int maxDevices,int usedCount,
        Instant expiresAt,Instant revokedAt,Instant createdAt) {}
}
