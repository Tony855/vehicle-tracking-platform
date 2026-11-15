package com.tony855.vehicletracking.telemetry;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Normalized v1 location payload shared by all supported tracker families.
 * Tenant identity is deliberately absent: it must come from device authentication.
 */
public record LocationTelemetryRequest(
    @NotNull @Min(1) @Max(1) Integer schemaVersion,
    @NotNull UUID eventId,
    @NotBlank @Size(max = 96) String deviceUid,
    @NotNull Instant recordedAt,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
    @DecimalMin("0.0") BigDecimal speedKmh,
    BigDecimal headingDegrees,
    BigDecimal altitudeM,
    @Min(0) @Max(255) Integer satellites,
    @DecimalMin("0.0") BigDecimal accuracyM,
    Boolean ignitionOn
) {
    @AssertTrue(message = "headingDegrees must be in [0, 360)")
    public boolean isHeadingInRange() {
        return headingDegrees == null
            || (headingDegrees.compareTo(BigDecimal.ZERO) >= 0
                && headingDegrees.compareTo(new BigDecimal("360")) < 0);
    }
}
