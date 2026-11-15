package com.tony855.vehicletracking.telemetry;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LocationTelemetryRequestTest {
    private static Validator validator;
    private static jakarta.validation.ValidatorFactory factory;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void acceptsValidWgs84Location() {
        var request = new LocationTelemetryRequest(
            1, UUID.randomUUID(), "GPS-001", Instant.parse("2026-01-15T10:20:30Z"),
            new BigDecimal("11.5564"), new BigDecimal("104.9282"),
            new BigDecimal("42.5"), new BigDecimal("180.0"),
            null, 17, new BigDecimal("3.2"), true
        );
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsOutOfRangeCoordinatesAndHeading() {
        var request = new LocationTelemetryRequest(
            1, UUID.randomUUID(), "GPS-001", Instant.parse("2026-01-15T10:20:30Z"),
            new BigDecimal("91"), new BigDecimal("181"),
            new BigDecimal("-1"), new BigDecimal("360"),
            null, 8, new BigDecimal("-0.1"), false
        );
        assertFalse(validator.validate(request).isEmpty());
    }
}
