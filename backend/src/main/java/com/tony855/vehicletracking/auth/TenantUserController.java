package com.tony855.vehicletracking.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class TenantUserController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    public TenantUserController(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    @GetMapping
    public List<UserView> list(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return jdbc.query("""
            SELECT id,email,display_name,role,status,created_at
            FROM tenant_users WHERE tenant_id=? ORDER BY created_at
            """, (rs,row) -> new UserView(rs.getObject("id",UUID.class),rs.getString("email"),
                rs.getString("display_name"),rs.getString("role"),rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()), tenant(jwt));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateUserRequest request) {
        requireAdmin(jwt);
        if ("OWNER".equals(request.role())) {
            return ResponseEntity.badRequest().body(Map.of("error","owner_role_cannot_be_assigned"));
        }
        try {
            UUID id=jdbc.queryForObject("""
                INSERT INTO tenant_users(tenant_id,email,password_hash,display_name,role)
                VALUES (?,lower(?),?,?,?) RETURNING id
                """,UUID.class,tenant(jwt),request.email(),passwords.encode(request.password()),
                request.displayName(),request.role());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("userId",id,"status","created"));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","email_already_registered"));
        }
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<?> setStatus(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID userId,
        @Valid @RequestBody StatusRequest request) {
        requireAdmin(jwt);
        if (userId.toString().equals(jwt.getSubject())) {
            return ResponseEntity.badRequest().body(Map.of("error","cannot_change_own_status"));
        }
        int changed=jdbc.update("""
            UPDATE tenant_users SET status=?,updated_at=now()
            WHERE id=? AND tenant_id=? AND role<>'OWNER'
            """,request.status(),userId,tenant(jwt));
        return changed==1 ? ResponseEntity.ok(Map.of("status",request.status()))
            : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","user_not_found"));
    }

    private void requireAdmin(Jwt jwt) {
        String role=jwt.getClaimAsString("role");
        if (!"OWNER".equals(role) && !"ADMIN".equals(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Administrator role required");
        }
    }
    private UUID tenant(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("tenant_id")); }

    public record CreateUserRequest(@Email @NotBlank String email,
        @NotBlank @Size(min=12,max=128) String password,@NotBlank @Size(max=120) String displayName,
        @jakarta.validation.constraints.Pattern(regexp="ADMIN|DISPATCHER|VIEWER") String role) {}
    public record StatusRequest(@jakarta.validation.constraints.Pattern(regexp="ACTIVE|DISABLED") String status) {}
    public record UserView(UUID userId,String email,String displayName,String role,String status,
        java.time.Instant createdAt) {}
}
