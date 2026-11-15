package com.tony855.vehicletracking.telemetry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices/{deviceUid}")
public class DeviceLocationQueryController {
    private final DeviceLocationQueryService queryService;

    public DeviceLocationQueryController(DeviceLocationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/presence")
    public DeviceLocationQueryService.DevicePresence presence(
        @PathVariable String deviceUid,
        @RequestHeader(value = "Authorization", required = false) String authorization) {
        return queryService.presence(deviceUid, bearer(authorization));
    }

    @GetMapping("/locations/latest")
    public ResponseEntity<LocationPointResponse> latest(
        @PathVariable String deviceUid,
        @RequestHeader(value = "Authorization", required = false) String authorization) {
        LocationPointResponse result = queryService.latest(deviceUid, bearer(authorization));
        return result == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/locations")
    public List<LocationPointResponse> history(
        @PathVariable String deviceUid,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue = "500") @Min(1) @Max(1000) int limit) {
        if (from.isAfter(to)) throw new IllegalArgumentException("from must be <= to");
        return queryService.history(deviceUid, bearer(authorization), from, to, limit);
    }

    private String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new DeviceLocationQueryService.UnauthenticatedDeviceException();
        }
        String value = authorization.substring(7).trim();
        if (value.isEmpty()) throw new DeviceLocationQueryService.UnauthenticatedDeviceException();
        return value;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidRange() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_time_range"));
    }

    @ExceptionHandler(DeviceLocationQueryService.UnauthenticatedDeviceException.class)
    public ResponseEntity<Map<String, String>> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "invalid_device_credentials"));
    }
}
