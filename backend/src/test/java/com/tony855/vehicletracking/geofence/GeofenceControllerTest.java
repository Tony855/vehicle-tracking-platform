package com.tony855.vehicletracking.geofence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters=false)
@WebMvcTest(GeofenceController.class)
class GeofenceControllerTest {
    @Autowired MockMvc mvc;
    @MockBean JdbcTemplate jdbc;

    @Test
    void rejectsInvalidPolygonBeforeInsert() throws Exception {
        when(jdbc.queryForObject(anyString(),any(Class.class),any(Object.class))).thenReturn(false);
        mvc.perform(post("/api/v1/geofences")
                .with(jwt().jwt(j->j.claim("tenant_id","8c1901f0-9c61-4f1d-9bcb-7c76b2e91a01").claim("role","ADMIN")))
                .contentType("application/json")
                .content("{\"name\":\"Depot\",\"polygonWkt\":\"POLYGON EMPTY\",\"speedLimitKmh\":30}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_polygon_wkt"));
    }
}
