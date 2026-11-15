package com.tony855.vehicletracking.telemetry;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeviceLocationQueryController.class)
class DeviceLocationQueryControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean DeviceLocationQueryService queryService;

    @Test
    void rejectsRequestsWithoutDeviceCredential() throws Exception {
        mockMvc.perform(get("/api/v1/devices/GPS-001/presence"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_device_credentials"));
    }

    @Test
    void returnsLatestLocationForAuthenticatedDevice() throws Exception {
        var eventId = UUID.fromString("b6f9f1aa-7d84-4c48-9d55-6e70b6ddf2c2");
        when(queryService.latest("GPS-001", "device-secret")).thenReturn(
            new LocationPointResponse(eventId, Instant.parse("2026-01-15T10:20:30Z"),
                new BigDecimal("11.5564"), new BigDecimal("104.9282"),
                new BigDecimal("42.5"), new BigDecimal("180.0"),
                null, 17, new BigDecimal("3.2"), true));
        mockMvc.perform(get("/api/v1/devices/GPS-001/locations/latest")
                .header("Authorization", "Bearer device-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latitude").value(11.5564))
            .andExpect(jsonPath("$.longitude").value(104.9282))
            .andExpect(jsonPath("$.eventId").value(eventId.toString()));
    }

    @Test
    void reportsPresenceFromAuthenticatedHeartbeat() throws Exception {
        when(queryService.presence("GPS-001", "device-secret"))
            .thenReturn(new DeviceLocationQueryService.DevicePresence(
                "GPS-001", Instant.parse("2026-01-15T10:20:30Z"), true));
        mockMvc.perform(get("/api/v1/devices/GPS-001/presence")
                .header("Authorization", "Bearer device-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.online").value(true));
    }
}
