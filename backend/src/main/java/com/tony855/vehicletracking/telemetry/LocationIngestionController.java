package com.tony855.vehicletracking.telemetry;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/telemetry")
public class LocationIngestionController {
    private final LocationIngestionService ingestionService;

    public LocationIngestionController(LocationIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/locations")
    public ResponseEntity<LocationIngestionResponse> ingest(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody LocationTelemetryRequest request) {
        String credential = extractBearer(authorization);
        if (credential == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new LocationIngestionResponse("unauthorized", request.eventId()));
        }
        return ResponseEntity.ok(ingestionService.ingest(credential, request));
    }

    private String extractBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String value = authorization.substring(7).trim();
        return value.isEmpty() ? null : value;
    }

    @ExceptionHandler(LocationIngestionService.UnauthenticatedDeviceException.class)
    public ResponseEntity<Map<String, String>> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "invalid_device_credentials"));
    }
}