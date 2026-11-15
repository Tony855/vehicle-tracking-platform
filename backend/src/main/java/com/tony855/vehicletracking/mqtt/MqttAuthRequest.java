package com.tony855.vehicletracking.mqtt;

import jakarta.validation.constraints.NotBlank;

public record MqttAuthRequest(@NotBlank String username, @NotBlank String password) {}
