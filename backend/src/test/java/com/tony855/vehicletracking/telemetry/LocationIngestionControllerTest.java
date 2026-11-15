package com.tony855.vehicletracking.telemetry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LocationIngestionController.class)
class LocationIngestionControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean LocationIngestionService ingestionService;

    @Test
    void requiresBearerCredential() throws Exception {
        mockMvc.perform(post("/api/v1/telemetry/locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.result").value("unauthorized"));
    }

    @Test
    void acceptsAuthenticatedTelemetry() throws Exception {
        UUID eventId = UUID.fromString("b6f9f1aa-7d84-4c48-9d55-6e70b6ddf2c2");
        when(ingestionService.ingest(eq("device-secret"), any(LocationTelemetryRequest.class)))
            .thenReturn(new LocationIngestionResponse("accepted", eventId));
        mockMvc.perform(post("/api/v1/telemetry/locations")
                .header("Authorization", "Bearer device-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("accepted"))
            .andExpect(jsonPath("$.eventId").value(eventId.toString()));
        verify(ingestionService).ingest(eq("device-secret"), any(LocationTelemetryRequest.class));
    }

    private String payload() {
        return """
            {
              "schema_version": 1,
              "event_id": "b6f9f1aa-7d84-4c48-9d55-6e70b6ddf2c2",
              "device_uid": "GPS-UNIT-00001",
              "recorded_at": "2026-01-15T10:20:30Z",
              "latitude": 11.5564,
              "longitude": 104.9282,
              "speed_kmh": 42.5,
              "heading_degrees": 180.0,
              "altitude_m": 12.4,
              "satellites": 17,
              "accuracy_m": 3.2,
              "ignition_on": true
            }
            """;
    }
}