package com.tony855.vehicletracking.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tony855.vehicletracking.device.DeviceCredentialService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class MqttAuthControllerTest {
    private final DeviceCredentialService credentials = mock(DeviceCredentialService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    void rejectsRequestsWithoutBrokerSharedSecret() {
        var controller = new MqttAuthController(credentials, jdbc, "a".repeat(64), "b".repeat(64));
        var response = controller.authenticate(null, new MqttAuthRequest("GPS-1", "secret"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void delegatesDevicePasswordValidationToCredentialService() {
        var controller = new MqttAuthController(credentials, jdbc, "a".repeat(64), "b".repeat(64));
        when(credentials.authenticate("GPS-1", "device-password")).thenReturn(Optional.empty());
        var response = controller.authenticate("a".repeat(64),
            new MqttAuthRequest("GPS-1", "device-password"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }


    @Test
    void permitsInternalConsumerOnlyWithItsSeparateLongCredential() {
        var controller = new MqttAuthController(credentials, jdbc, "a".repeat(64), "b".repeat(64));
        var response = controller.authenticate("a".repeat(64),
            new MqttAuthRequest("internal-gps-ingestor", "b".repeat(64)));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void restrictsInternalConsumerToLocationSubscriptionFilter() {
        var controller = new MqttAuthController(credentials, jdbc, "a".repeat(64), "b".repeat(64));
        var allowed = controller.authorize("a".repeat(64),
            new MqttAclRequest("internal-gps-ingestor",
                "v1/tenants/+/devices/+/location", "subscribe"));
        var denied = controller.authorize("a".repeat(64),
            new MqttAclRequest("internal-gps-ingestor",
                "v1/tenants/+/devices/+/command", "subscribe"));
        assertEquals(HttpStatus.OK, allowed.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());
    }

    @Test
    void refusesWeakInternalSharedSecretConfiguration() {
        var controller = new MqttAuthController(credentials, jdbc, "too-short", "b".repeat(64));
        var response = controller.authenticate("too-short",
            new MqttAuthRequest("GPS-1", "device-password"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
