package com.tony855.vehicletracking.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeviceRegistrationRequest(
    @NotBlank @Size(min = 24, max = 256) String enrollmentToken,
    @NotBlank @Size(min = 3, max = 96)
    @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "deviceUid contains unsupported characters")
    String deviceUid,
    @NotNull DeviceHardwareType hardwareType
) {}
