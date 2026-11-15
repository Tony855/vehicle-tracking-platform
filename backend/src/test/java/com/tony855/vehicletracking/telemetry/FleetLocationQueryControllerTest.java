package com.tony855.vehicletracking.telemetry;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters=false)
@WebMvcTest(FleetLocationQueryController.class)
class FleetLocationQueryControllerTest {
    @Autowired MockMvc mvc;
    @MockBean FleetLocationQueryService service;

    @Test
    void returnsTenantFleetLatestLocations() throws Exception {
        UUID tenant=UUID.fromString("8c1901f0-9c61-4f1d-9bcb-7c76b2e91a01");
        UUID vehicle=UUID.fromString("a4d219b0-6a55-4a12-b7b0-6d01f8c4f881");
        when(service.latestForTenant(tenant,500)).thenReturn(List.of(
            new FleetLocationQueryService.FleetLocationPoint(vehicle,"PP-001","Truck 1",
                "headunit-001","ANDROID_HEADUNIT",UUID.randomUUID(),
                Instant.parse("2026-11-15T10:00:00Z"),new BigDecimal("11.5564"),
                new BigDecimal("104.9282"),new BigDecimal("45.0"),new BigDecimal("180.0"),
                null,18,new BigDecimal("4.0"),true)));
        mvc.perform(get("/api/v1/fleet/locations/latest")
                .with(jwt().jwt(j->j.claim("tenant_id",tenant.toString()).claim("role","VIEWER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].vehicleId").value(vehicle.toString()))
            .andExpect(jsonPath("$[0].hardwareType").value("ANDROID_HEADUNIT"))
            .andExpect(jsonPath("$[0].latitude").value(11.5564));
    }

    @Test
    void rejectsInvertedHistoryRange() throws Exception {
        UUID tenant=UUID.fromString("8c1901f0-9c61-4f1d-9bcb-7c76b2e91a01");
        mvc.perform(get("/api/v1/fleet/vehicles/"+UUID.randomUUID()+"/locations")
                .param("from","2026-11-16T00:00:00Z")
                .param("to","2026-11-15T00:00:00Z")
                .with(jwt().jwt(j->j.claim("tenant_id",tenant.toString()).claim("role","ADMIN"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_time_range"));
    }
}
