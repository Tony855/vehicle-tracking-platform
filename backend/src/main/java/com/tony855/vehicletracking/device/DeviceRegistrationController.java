package com.tony855.vehicletracking.device;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceRegistrationController {
    private final DeviceRegistrationService registrationService;

    public DeviceRegistrationController(DeviceRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<DeviceRegistrationResponse> register(
        @Valid @RequestBody DeviceRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
    }

    @ExceptionHandler(DeviceRegistrationService.InvalidEnrollmentTokenException.class)
    public ResponseEntity<Map<String, String>> invalidToken() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "invalid_or_expired_enrollment_token"));
    }

    @ExceptionHandler(DeviceRegistrationService.DuplicateDeviceException.class)
    public ResponseEntity<Map<String, String>> duplicateDevice() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "device_uid_already_registered"));
    }
}
