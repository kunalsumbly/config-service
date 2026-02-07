package com.polarbookshop.configservice.controller;

import com.polarbookshop.configservice.dto.DecryptSecretRequest;
import com.polarbookshop.configservice.dto.RotateKeysRequest;
import com.polarbookshop.configservice.encryption.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "${spring.cloud.config.server.prefix:}")
@RequiredArgsConstructor
@Slf4j
public class ConfigServerEncryptionController {

    private final EncryptionService encryptionService;

    /**
     * Creates encryption keys for a specific application and profile only if they don't already exist
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
            EncryptionService.KeyCreationResult result = encryptionService.createEncryptionKeys(appName, profile);

            switch (result) {
                case CREATED:
                    log.info("Successfully created new encryption keys for app: {} and profile: {}", appName, profile);
                    return ResponseEntity.ok(Map.of(
                            "status", "success",
                            "message", "New encryption keys created successfully for " + appName + "/" + profile
                    ));
                case ALREADY_EXISTS:
                    log.info("Encryption keys already exist for app: {} and profile: {}", appName, profile);
                    return ResponseEntity.ok(Map.of(
                            "status", "success",
                            "message", "Encryption keys already exist for " + appName + "/" + profile + ", no changes made"
                    ));
                case ERROR:
                    log.error("Failed to create encryption keys for app: {} and profile: {}", appName, profile);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                            "status", "error",
                            "message", "Failed to create encryption keys"
                    ));
                default:
                    log.error("Unexpected result when creating encryption keys for app: {} and profile: {}", appName, profile);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                            "status", "error",
                            "message", "Unexpected result when creating encryption keys"
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
     * Adds secrets to the shell secret container
     * @param appName The application name
     * @param profile The profile name
     * @param requestBody The request body containing key-value pairs to store
     * @return Success or error message
     */
    @PostMapping("/add-secret/{appName}/{profile}")
    public ResponseEntity<Map<String, String>> addSecret(
            @PathVariable String appName,
            @PathVariable String profile,
            @RequestBody Map<String, String> requestBody) {

        log.info("Received request to add secrets for app: {} and profile: {}", appName, profile);

        // Check if the request body contains the legacy format with 'key' and 'value' fields

            // Handle new format with direct key-value pairs
            if (requestBody.isEmpty()) {
                log.error("Request body is empty");
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "Request body cannot be empty"
                ));
            }

            try {
                boolean success = encryptionService.addSecrets(appName, profile, requestBody);

                if (success) {
                    log.info("Successfully added {} secrets for app: {} and profile: {}", requestBody.size(), appName, profile);
                    return ResponseEntity.ok(Map.of(
                            "status", "success",
                            "message", "Secrets added successfully for " + appName + "/" + profile
                    ));
                } else {
                    log.error("Failed to add secrets for app: {} and profile: {}", appName, profile);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                            "status", "error",
                            "message", "Failed to add secrets"
                    ));
                }
            } catch (Exception e) {
                log.error("Error adding secrets for app: {} and profile: {}", appName, profile, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "status", "error",
                        "message", "Error: " + e.getMessage()
                ));
            }

    }

    /**
     * Rotates encryption keys and re-encrypts specified secrets
     * @param appName The application name
     * @param profile The profile name
     * @param request The request body containing the list of secrets to rotate
     * @return Success or error message
     */
    @PostMapping("/rotate-keys/{appName}/{profile}")
    public ResponseEntity<Map<String, String>> rotateKeys(
            @PathVariable String appName,
            @PathVariable String profile,
            @RequestBody RotateKeysRequest request) {

        log.info("Received request to rotate encryption keys for app: {} and profile: {}", appName, profile);

        if (request.getRotate() == null || request.getRotate().isEmpty()) {
            log.error("Request body missing or empty 'rotate' field");
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Request body must contain a non-empty 'rotate' field with the list of secrets to rotate"
            ));
        }

        List<String> secretsToRotate = request.getRotate();
        log.info("Secrets to rotate: {}", secretsToRotate);

        try {
            boolean success = encryptionService.rotateEncryptionKeys(appName, profile, secretsToRotate);

            if (success) {
                log.info("Successfully rotated encryption keys for app: {} and profile: {}", appName, profile);
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "Encryption keys rotated successfully for " + appName + "/" + profile
                ));
            } else {
                log.error("Failed to rotate encryption keys for app: {} and profile: {}", appName, profile);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "status", "error",
                        "message", "Failed to rotate encryption keys"
                ));
            }
        } catch (Exception e) {
            log.error("Error rotating encryption keys for app: {} and profile: {}", appName, profile, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Gets all keys stored under a specific application and profile
     * @param appName The application name
     * @param profile The profile name
     * @return List of keys or error message
     */
    @GetMapping("/get-all-keys/{appName}/{profile}")
    public ResponseEntity<?> getAllKeys(
            @PathVariable String appName,
            @PathVariable String profile) {

        log.info("Received request to get all keys for app: {} and profile: {}", appName, profile);

        try {
            List<String> keys = encryptionService.getAllKeys(appName, profile);

            if (keys != null) {
                log.info("Successfully retrieved {} keys for app: {} and profile: {}", keys.size(), appName, profile);
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "keys", keys
                ));
            } else {
                log.error("Failed to retrieve keys for app: {} and profile: {}", appName, profile);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "status", "error",
                        "message", "Failed to retrieve keys"
                ));
            }
        } catch (Exception e) {
            log.error("Error retrieving keys for app: {} and profile: {}", appName, profile, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Decrypts a secret using the encryption keys for a specific application and profile
     * @param appName The application name
     * @param profile The profile name
     * @param request The request body containing the encrypted secret
     * @return The decrypted plain text or error message
     */
    @PostMapping("/decrypt-secret/{appName}/{profile}")
    public ResponseEntity<Map<String, String>> decryptSecret(
            @PathVariable String appName,
            @PathVariable String profile,
            @RequestBody DecryptSecretRequest request) {

        log.info("Received request to decrypt secret for app: {} and profile: {}", appName, profile);

        if (request.getSecret() == null || request.getSecret().trim().isEmpty()) {
            log.error("Request body missing or empty 'secret' field");
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Request body must contain a non-empty 'secret' field"
            ));
        }

        String encryptedValue = request.getSecret().trim();
        
        // Check if the secret has the expected {cipher} prefix
        if (!encryptedValue.startsWith("{cipher}")) {
            log.error("Secret does not have the expected {{cipher}} prefix");
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Secret must start with {cipher} prefix"
            ));
        }

        // Remove the {cipher} prefix to get the actual encrypted value
        String actualEncryptedValue = encryptedValue.substring(8); // Remove "{cipher}"

        if (actualEncryptedValue.isEmpty()) {
            log.error("No encrypted value found after {{cipher}} prefix");
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "No encrypted value found after {cipher} prefix"
            ));
        }

        try {
            String decryptedValue = encryptionService.decrypt(appName, profile, actualEncryptedValue);
            
            log.info("Successfully decrypted secret for app: {} and profile: {}", appName, profile);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "plaintext", decryptedValue
            ));
        } catch (Exception e) {
            log.error("Error decrypting secret for app: {} and profile: {}", appName, profile, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Error: " + e.getMessage()
            ));
        }
    }
}
