package com.tony855.vehicletracking.device;

import java.time.Instant;
import java.util.UUID;

public record DeviceRegistrationResponse(
    UUID deviceId,
    String deviceUid,
    String credential,
    String credentialType,
    Instant createdAt
) {}
