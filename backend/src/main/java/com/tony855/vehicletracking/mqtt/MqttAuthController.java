package com.tony855.vehicletracking.mqtt;

import com.tony855.vehicletracking.device.DeviceCredentialService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/mqtt")
public class MqttAuthController {
    private final DeviceCredentialService credentials;
    private final JdbcTemplate jdbc;
    private final byte[] sharedSecret;

    public MqttAuthController(DeviceCredentialService credentials, JdbcTemplate jdbc,
        @Value("${MQTT_AUTH_SHARED_SECRET:}") String sharedSecret) {
        this.credentials = credentials;
        this.jdbc = jdbc;
        this.sharedSecret = sharedSecret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/auth")
    public ResponseEntity<Map<String, Object>> authenticate(
        @RequestHeader(value = "X-MQTT-Auth-Secret", required = false) String suppliedSecret,
        @Valid @RequestBody MqttAuthRequest request) {
        if (!authorizedBroker(suppliedSecret)) return denied();
        boolean valid = credentials.authenticate(request.username(), request.password()).isPresent();
        return valid
            ? ResponseEntity.ok(Map.of("result", "allow", "is_superuser", false))
            : denied();
    }

    @PostMapping("/acl")
    public ResponseEntity<Map<String, String>> authorize(
        @RequestHeader(value = "X-MQTT-Auth-Secret", required = false) String suppliedSecret,
        @Valid @RequestBody MqttAclRequest request) {
        if (!authorizedBroker(suppliedSecret)) return denied();
        var device = jdbc.query("""
            SELECT id, tenant_id FROM devices
            WHERE device_uid = ? AND status = 'ACTIVE'
            """, (rs, row) -> new DeviceIdentity(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class)),
            request.username()).stream().findFirst().orElse(null);
        if (device == null) return denied();

        String[] segments = request.topic().split("/", -1);
        if (segments.length != 6 || !"v1".equals(segments[0])
            || !"tenants".equals(segments[1]) || !"devices".equals(segments[3])) {
            return denied();
        }
        if (!device.tenantId().toString().equals(segments[2])
            || !device.deviceId().toString().equals(segments[4])) {
            return denied();
        }

        String channel = segments[5];
        boolean publish = "publish".equalsIgnoreCase(request.action());
        boolean subscribe = "subscribe".equalsIgnoreCase(request.action());
        boolean allowed = (publish && ("location".equals(channel)
                || "status".equals(channel) || "event".equals(channel)
                || "command-ack".equals(channel)))
            || (subscribe && "command".equals(channel));
        return allowed ? ResponseEntity.ok(Map.of("result", "allow")) : denied();
    }

    private boolean authorizedBroker(String supplied) {
        return sharedSecret.length >= 32 && supplied != null
            && MessageDigest.isEqual(sharedSecret, supplied.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<Map<String, String>> denied() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("result", "deny"));
    }

    private record DeviceIdentity(UUID deviceId, UUID tenantId) {}
}
