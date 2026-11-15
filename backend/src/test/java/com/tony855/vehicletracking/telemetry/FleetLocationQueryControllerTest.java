package com.tony855.vehicletracking.telemetry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class FleetLocationQueryControllerTest {
    private final FleetLocationQueryService service=org.mockito.Mockito.mock(FleetLocationQueryService.class);
    private final FleetLocationQueryController controller=new FleetLocationQueryController(service);

    private Jwt jwt(String role) {
        return Jwt.withTokenValue("test-token").header("alg","none")
            .claim("tenant_id","8c1901f0-9c61-4f1d-9bcb-7c76b2e91a01")
            .claim("role",role).build();
    }

    @Test
    void returnsTenantFleetLatestLocations() {
        UUID tenant=UUID.fromString("8c1901f0-9c61-4f1d-9bcb-7c76b2e91a01");
        UUID vehicle=UUID.fromString("a4d219b0-6a55-4a12-b7b0-6d01f8c4f881");
        var point=new FleetLocationQueryService.FleetLocationPoint(vehicle,"PP-001","Truck 1",
            "headunit-001","ANDROID_HEADUNIT",UUID.randomUUID(),
            Instant.parse("2026-11-15T10:00:00Z"),new BigDecimal("11.5564"),
            new BigDecimal("104.9282"),new BigDecimal("45.0"),new BigDecimal("180.0"),
            null,18,new BigDecimal("4.0"),true);
        when(service.latestForTenant(tenant,500)).thenReturn(List.of(point));
        var result=controller.latest(jwt("VIEWER"),500);
        assertEquals(1,result.size());
        assertEquals(vehicle,result.get(0).vehicleId());
        assertEquals("ANDROID_HEADUNIT",result.get(0).hardwareType());
        assertEquals(new BigDecimal("11.5564"),result.get(0).latitude());
    }

    @Test
    void rejectsInvertedHistoryRange() {
        var result=controller.history(jwt("ADMIN"),UUID.randomUUID(),
            Instant.parse("2026-11-16T00:00:00Z"),Instant.parse("2026-11-15T00:00:00Z"),500,0);
        assertEquals(400,result.getStatusCode().value());
    }
}
