package com.tony855.vehicletracking.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ApiInfoController {
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of("service", "vehicle-tracking-api", "apiVersion", "v1", "status", "running", "timestamp", Instant.now().toString());
    }
}
