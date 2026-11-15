package com.tony855.vehicletracking.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwtEncoder;
    private final byte[] bootstrapSecret;

    public AuthController(JdbcTemplate jdbc, PasswordEncoder passwords, JwtEncoder jwtEncoder,
        @Value("${TENANT_BOOTSTRAP_SECRET:}") String bootstrapSecret) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.jwtEncoder = jwtEncoder;
        this.bootstrapSecret = bootstrapSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @PostMapping("/bootstrap/tenants")
    public ResponseEntity<?> bootstrap(
        @RequestHeader(value = "X-Bootstrap-Secret", required = false) String supplied,
        @Valid @RequestBody BootstrapRequest request) {
        if (!secretMatches(supplied)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }
        UUID tenantId = jdbc.queryForObject(
            "INSERT INTO tenants(name) VALUES (?) RETURNING id", UUID.class, request.tenantName());
        UUID userId = jdbc.queryForObject("""
            INSERT INTO tenant_users(tenant_id,email,password_hash,display_name,role)
            VALUES (?,lower(?),?,?,'OWNER') RETURNING id
            """, UUID.class, tenantId, request.ownerEmail(), passwords.encode(request.ownerPassword()),
            request.ownerName());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("tenantId", tenantId, "ownerUserId", userId, "status", "created"));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        var users = jdbc.query("""
            SELECT u.id, u.tenant_id, u.email, u.password_hash, u.display_name, u.role
            FROM tenant_users u JOIN tenants t ON t.id=u.tenant_id
            WHERE lower(u.email)=lower(?) AND u.status='ACTIVE' AND t.status='ACTIVE'
            ORDER BY u.created_at ASC LIMIT 2
            """, (rs, row) -> new UserRow(rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class), rs.getString("email"),
                rs.getString("password_hash"), rs.getString("display_name"), rs.getString("role")),
            request.email());
        if (users.size() != 1 || !passwords.matches(request.password(), users.get(0).passwordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_credentials"));
        }
        UserRow user = users.get(0);
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("vehicle-tracking-platform")
            .issuedAt(now).expiresAt(now.plusSeconds(3600)).subject(user.id().toString())
            .claim("tenant_id", user.tenantId().toString()).claim("role", user.role())
            .claim("email", user.email()).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", 3600,
            user.id(), user.tenantId(), user.email(), user.displayName(), user.role()));
    }

    private boolean secretMatches(String supplied) {
        return bootstrapSecret.length >= 32 && supplied != null
            && java.security.MessageDigest.isEqual(bootstrapSecret,
                supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public record BootstrapRequest(@NotBlank @Size(max=160) String tenantName,
        @Email @NotBlank String ownerEmail, @NotBlank @Size(min=12,max=128) String ownerPassword,
        @NotBlank @Size(max=120) String ownerName) {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record LoginResponse(String accessToken, String tokenType, long expiresIn,
        UUID userId, UUID tenantId, String email, String displayName, String role) {}
    private record UserRow(UUID id, UUID tenantId, String email, String passwordHash,
        String displayName, String role) {}
}
