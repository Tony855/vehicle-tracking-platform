package com.tony855.vehicletracking.telemetry;

import java.util.UUID;

public record LocationIngestionResponse(String result, UUID eventId) {}