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
        var controller = new MqttAuthController(credentials, jdbc, "a".repeat(64));
        var response = controller.authenticate(null, new MqttAuthRequest("GPS-1", "secret"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void delegatesDevicePasswordValidationToCredentialService() {
        var controller = new MqttAuthController(credentials, jdbc, "a".repeat(64));
        when(credentials.authenticate("GPS-1", "device-password")).thenReturn(Optional.empty());
        var response = controller.authenticate("a".repeat(64),
            new MqttAuthRequest("GPS-1", "device-password"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void refusesWeakInternalSharedSecretConfiguration() {
        var controller = new MqttAuthController(credentials, jdbc, "too-short");
        var response = controller.authenticate("too-short",
            new MqttAuthRequest("GPS-1", "device-password"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
