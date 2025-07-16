package com.polarbookshop.configservice.encryption;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(path = "${spring.cloud.config.server.prefix:}")
@RequiredArgsConstructor
@Slf4j
public class ConfigServerEncryptionController {

    private final EncryptionService encryptionService;

    /**
     * Creates or updates encryption keys for a specific application and profile
     * @param appName The application name
     * @param profile The profile name
     * @return Success or error message
     */
    @PostMapping("/create-encryption-keys/{appName}/{profile}")
    public ResponseEntity<Map<String, String>> createEncryptionKeys(
            @PathVariable String appName,
            @PathVariable String profile) {

        log.info("Received request to create encryption keys for app: {} and profile: {}", appName, profile);

        try {
            boolean success = encryptionService.createEncryptionKeys(appName, profile);

            if (success) {
                log.info("Successfully created encryption keys for app: {} and profile: {}", appName, profile);
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "Encryption keys created successfully for " + appName + "/" + profile
                ));
            } else {
                log.error("Failed to create encryption keys for app: {} and profile: {}", appName, profile);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "status", "error",
                        "message", "Failed to create encryption keys"
                ));
            }
        } catch (Exception e) {
            log.error("Error creating encryption keys for app: {} and profile: {}", appName, profile, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Adds a secret to the shell secret container
     * @param appName The application name
     * @param profile The profile name
     * @param requestBody The request body containing the key and value
     * @return Success or error message
     */
    @PostMapping("/add-secret/{appName}/{profile}")
    public ResponseEntity<Map<String, String>> addSecret(
            @PathVariable String appName,
            @PathVariable String profile,
            @RequestBody Map<String, String> requestBody) {

        log.info("Received request to add secret for app: {} and profile: {}", appName, profile);

        if (!requestBody.containsKey("key") || !requestBody.containsKey("value")) {
            log.error("Request body missing 'key' or 'value' field");
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Request body must contain 'key' and 'value' fields"
            ));
        }

        String key = requestBody.get("key");
        String value = requestBody.get("value");

        try {
            boolean success = encryptionService.addSecret(appName, profile, key, value);

            if (success) {
                log.info("Successfully added secret with key '{}' for app: {} and profile: {}", key, appName, profile);
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "Secret added successfully for " + appName + "/" + profile
                ));
            } else {
                log.error("Failed to add secret with key '{}' for app: {} and profile: {}", key, appName, profile);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "status", "error",
                        "message", "Failed to add secret"
                ));
            }
        } catch (Exception e) {
            log.error("Error adding secret with key '{}' for app: {} and profile: {}", key, appName, profile, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Error: " + e.getMessage()
            ));
        }
    }
}
