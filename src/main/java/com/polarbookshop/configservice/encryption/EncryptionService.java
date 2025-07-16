package com.polarbookshop.configservice.encryption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.UpdateSecretRequest;

import jakarta.annotation.PostConstruct;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.polarbookshop.configservice.constants.ConfigConstants.SECRET_PATH_SEPARATOR;

@Service
@Slf4j
public class EncryptionService {

    @Value("${aws.region:ap-southeast-2}")
    private String awsRegion;

    @Value("${config.server.aws.sm.encryption.key.path:/config-secrets}")
    private String baseSecretPath;

    private SecretsManagerClient secretsManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initSecretsManager() {
        this.secretsManager = SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .build();
        log.info("SecretsManagerClient initialized with region: {}", awsRegion);
    }

    /**
     * Creates or updates encryption keys for a specific application and profile
     * @param appName The application name
     * @param profile The profile name
     * @return true if keys were created/updated successfully
     */
    public boolean createEncryptionKeys(String appName, String profile) {
        try {
            log.info("Creating encryption keys for app: {} and profile: {}", appName, profile);

            // Generate random encryption key and salt
            String encryptionKey = generateRandomKey(32);
            String encryptionSalt = generateRandomHexSalt(16);

            // Store encryption key in AWS Secrets Manager
            String keyPath = buildSecretPath(appName, profile, "encrypt.key");
            storeSecret(keyPath, encryptionKey);
            log.info("Stored encryption key at path: {}", keyPath);

            // Store encryption salt in AWS Secrets Manager
            String saltPath = buildSecretPath(appName, profile, "encrypt.salt");
            storeSecret(saltPath, encryptionSalt);
            log.info("Stored encryption salt at path: {}", saltPath);

            // Create empty shell secret for storing application secrets
            String shellPath = buildShellSecretPath(appName, profile+SECRET_PATH_SEPARATOR);
            storeEmptyShellSecret(shellPath);
            log.info("Created empty shell secret at path: {}", shellPath);

            return true;
        } catch (Exception e) {
            log.error("Failed to create encryption keys for app: {} and profile: {}", appName, profile, e);
            return false;
        }
    }

    /**
     * Creates a TextEncryptor for a specific application and profile
     * @param appName The application name
     * @param profile The profile name
     * @return A TextEncryptor configured with app-specific and profile-specific keys
     */
    private TextEncryptor getAppSpecificTextEncryptor(String appName, String profile) {
        String keyPath = buildSecretPath(appName, profile, "encrypt.key");
        String saltPath = buildSecretPath(appName, profile, "encrypt.salt");

        String encryptionKey = getSecretValue(keyPath);
        String encryptionSalt = getSecretValue(saltPath);

        if (encryptionKey == null || encryptionKey.isEmpty()) {
            throw new IllegalStateException("Encryption key not found for app: " + appName + " and profile: " + profile);
        }

        if (encryptionSalt == null || encryptionSalt.isEmpty()) {
            throw new IllegalStateException("Encryption salt not found for app: " + appName + " and profile: " + profile);
        }

        // Validate that the salt is a valid hex string with an even number of characters
        if (encryptionSalt.length() % 2 != 0) {
            log.error("Encryption salt must have an even number of characters: {}", encryptionSalt);
            throw new IllegalArgumentException("Encryption salt must have an even number of characters");
        }

        // Check if the salt contains only hex characters (0-9, a-f, A-F)
        if (!encryptionSalt.matches("[0-9a-fA-F]+")) {
            log.error("Encryption salt must contain only hex characters (0-9, a-f, A-F): {}", encryptionSalt);
            throw new IllegalArgumentException("Encryption salt must contain only hex characters (0-9, a-f, A-F)");
        }

        return Encryptors.text(encryptionKey, encryptionSalt);
    }


    /**
     * Builds the full path for a secret in AWS Secrets Manager
     * @param appName The application name
     * @param profile The profile name
     * @param secretName The secret name (encrypt-key or encrypt-salt)
     * @return The full path for the secret
     */
    private String buildSecretPath(String appName, String profile, String secretName) {
        // Remove trailing slash if present to avoid double slashes
        String path = baseSecretPath;
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return String.format("%s/%s/%s/%s", path, appName, profile, secretName);
    }

    /**
     * Builds the path for the shell secret in AWS Secrets Manager
     * @param appName The application name
     * @param profile The profile name
     * @return The path for the shell secret
     */
    private String buildShellSecretPath(String appName, String profile) {
        // Remove trailing slash if present to avoid double slashes
        String path = baseSecretPath;
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return String.format("%s/%s/%s", path, appName, profile);
    }


    /**
     * Stores a secret value in AWS Secrets Manager
     * @param secretId The secret ID
     * @param secretValue The secret value
     */
    private void storeSecret(String secretId, String secretValue) {
        try {
            // Check if the secret already exists
            try {
                GetSecretValueRequest getRequest = GetSecretValueRequest.builder()
                        .secretId(secretId)
                        .build();
                secretsManager.getSecretValue(getRequest);

                // Secret exists, update it
                UpdateSecretRequest updateRequest = UpdateSecretRequest.builder()
                        .secretId(secretId)
                        .secretString(secretValue)
                        .build();
                secretsManager.updateSecret(updateRequest);
                log.info("Updated existing secret: {}", secretId);
            } catch (ResourceNotFoundException e) {
                // Secret doesn't exist, create it
                CreateSecretRequest createRequest = CreateSecretRequest.builder()
                        .name(secretId)
                        .secretString(secretValue)
                        .build();
                secretsManager.createSecret(createRequest);
                log.info("Created new secret: {}", secretId);
            }
        } catch (Exception e) {
            log.error("Failed to store secret value for secretId: {}", secretId, e);
            throw e;
        }
    }

    /**
     * Creates or updates an empty shell secret in AWS Secrets Manager
     * @param secretId The secret ID
     */
    private void storeEmptyShellSecret(String secretId) {
        try {
            // Check if the secret already exists
            try {
                GetSecretValueRequest getRequest = GetSecretValueRequest.builder()
                        .secretId(secretId)
                        .build();
                secretsManager.getSecretValue(getRequest);
                log.info("Empty shell secret already exists at: {}", secretId);
            } catch (ResourceNotFoundException e) {
                // Secret doesn't exist, create it with an empty JSON object
                CreateSecretRequest createRequest = CreateSecretRequest.builder()
                        .name(secretId)
                        .secretString("{}")  // Empty JSON object as initial value
                        .build();
                secretsManager.createSecret(createRequest);
                log.info("Created new empty shell secret at: {}", secretId);
            }
        } catch (Exception e) {
            log.error("Failed to create empty shell secret at: {}", secretId, e);
            throw e;
        }
    }

    /**
     * Encrypts a value using app-specific and profile-specific encryption keys
     * @param appName The application name
     * @param profile The profile name
     * @param value The value to encrypt
     * @return The encrypted value
     */
    public String encrypt(String appName, String profile, String value) {
        try {
            log.info("Encrypting value for app: {} and profile: {}", appName, profile);

            TextEncryptor encryptor = getAppSpecificTextEncryptor(appName, profile);
            String encryptedValue = encryptor.encrypt(value);
            log.info("Successfully encrypted value for app: {} and profile: {}", appName, profile);

            return encryptedValue;
        } catch (Exception e) {
            log.error("Failed to encrypt value for app: {} and profile: {}", appName, profile, e);
            throw e;
        }
    }

    /**
     * Decrypts a value using app-specific and profile-specific encryption keys
     * @param appName The application name
     * @param profile The profile name
     * @param encryptedValue The encrypted value
     * @return The decrypted value
     */
    public String decrypt(String appName, String profile, String encryptedValue) {
        try {
            log.info("Decrypting value for app: {} and profile: {}", appName, profile);

            TextEncryptor encryptor = getAppSpecificTextEncryptor(appName, profile);
            String decryptedValue = encryptor.decrypt(encryptedValue);
            log.info("Successfully decrypted value for app: {} and profile: {}", appName, profile);

            return decryptedValue;
        } catch (Exception e) {
            log.error("Failed to decrypt value for app: {} and profile: {}", appName, profile, e);
            throw e;
        }
    }

    /**
     * Adds a secret to the shell secret container
     * @param appName The application name
     * @param profile The profile name
     * @param key The key for the secret
     * @param value The plaintext value to encrypt and store
     * @return true if the secret was added successfully
     */
    public boolean addSecret(String appName, String profile, String key, String value) {
        try {
            log.info("Adding secret with key '{}' for app: {} and profile: {}", key, appName, profile);

            // Encrypt the value
            String encryptedValue = encrypt(appName, profile, value);

            // Get the shell secret path
            String shellPath = buildShellSecretPath(appName, profile+SECRET_PATH_SEPARATOR);

            // Get the current shell secret content
            String shellContent = getSecretValue(shellPath);
            Map<String, String> secretsMap;

            try {
                // Parse the JSON content
                secretsMap = objectMapper.readValue(shellContent, Map.class);
            } catch (Exception e) {
                log.warn("Failed to parse shell secret content, initializing with empty map: {}", e.getMessage());
                secretsMap = new HashMap<>();
            }

            // Add or update the secret
            secretsMap.put(key, "{cipher}"+encryptedValue);

            // Convert back to JSON
            String updatedContent = objectMapper.writeValueAsString(secretsMap);

            // Update the shell secret
            UpdateSecretRequest updateRequest = UpdateSecretRequest.builder()
                    .secretId(shellPath)
                    .secretString(updatedContent)
                    .build();
            secretsManager.updateSecret(updateRequest);

            log.info("Successfully added secret with key '{}' for app: {} and profile: {}", key, appName, profile);
            return true;
        } catch (Exception e) {
            log.error("Failed to add secret with key '{}' for app: {} and profile: {}", key, appName, profile, e);
            return false;
        }
    }

    /**
     * Retrieves a secret value from AWS Secrets Manager
     * @param secretId The secret ID
     * @return The secret value
     */
    private String getSecretValue(String secretId) {
        try {
            log.debug("Retrieving secret value for secretId: {}", secretId);
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretId)
                    .build();
            GetSecretValueResponse response = secretsManager.getSecretValue(request);
            log.debug("Successfully retrieved secret value for secretId: {}", secretId);
            return response.secretString();
        } catch (Exception e) {
            log.error("Failed to retrieve secret value for secretId: {}", secretId, e);
            throw e;
        }
    }

    /**
     * Generates a random encryption key
     * @param length The length of the key in bytes
     * @return A Base64-encoded random key
     */
    private String generateRandomKey(int length) {
        byte[] key = new byte[length];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * Generates a random hex salt
     * @param length The length of the salt in bytes
     * @return A hex-encoded random salt
     */
    private String generateRandomHexSalt(int length) {
        byte[] salt = new byte[length];
        new SecureRandom().nextBytes(salt);
        StringBuilder hexSalt = new StringBuilder();
        for (byte b : salt) {
            hexSalt.append(String.format("%02x", b));
        }
        return hexSalt.toString();
    }
}
