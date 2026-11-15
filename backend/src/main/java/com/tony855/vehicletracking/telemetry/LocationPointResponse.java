package com.tony855.vehicletracking.telemetry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LocationPointResponse(
    UUID eventId,
    Instant recordedAt,
    BigDecimal latitude,
    BigDecimal longitude,
    BigDecimal speedKmh,
    BigDecimal headingDegrees,
    BigDecimal altitudeM,
    Integer satellites,
    BigDecimal accuracyM,
    Boolean ignitionOn
) {}
