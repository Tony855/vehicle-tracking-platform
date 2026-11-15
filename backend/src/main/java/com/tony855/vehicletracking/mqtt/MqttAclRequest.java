package com.tony855.vehicletracking.mqtt;

import jakarta.validation.constraints.NotBlank;

public record MqttAclRequest(
    @NotBlank String username,
    @NotBlank String topic,
    @NotBlank String action
) {}
