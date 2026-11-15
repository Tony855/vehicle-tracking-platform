package com.tony855.vehicletracking.geofence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;

class GeofenceControllerTest {
    @Test
    void rejectsInvalidPolygonBeforeInsert() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),any(Class.class),any(Object.class))).thenReturn(false);
        GeofenceController controller=new GeofenceController(jdbc);
        Jwt jwt=Jwt.withTokenValue("test-token").header("alg","none")
            .claim("tenant_id","8c1901f0-9c61-4f1d-9bcb-7c76b2e91a01")
            .claim("role","ADMIN").build();
        ResponseEntity<?> result=controller.create(jwt,
            new GeofenceController.GeofenceRequest("Depot","POLYGON EMPTY",
                new java.math.BigDecimal("30")));
        assertEquals(400,result.getStatusCode().value());
        verify(jdbc,never()).queryForObject(contains("INSERT INTO geofences"),
            any(Class.class),any(Object.class),any(Object.class),any(Object.class));
    }
}
