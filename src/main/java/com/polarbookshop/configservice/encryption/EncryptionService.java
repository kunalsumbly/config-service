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
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.polarbookshop.configservice.constants.ConfigConstants.*;

@Service
@Slf4j
public class EncryptionService {


    @Value("${aws.region:ap-southeast-2}")
    private String awsRegion;

    @Value("${config.server.aws.sm.secrets.path:/config-secrets}")
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
     * Result of the createEncryptionKeys operation
     */
    public enum KeyCreationResult {
        CREATED,      // Keys were newly created
        ALREADY_EXISTS, // Keys already existed
        ERROR         // An error occurred
    }

    /**
     * Creates encryption keys for a specific application and profile only if they don't already exist
     * @param appName The application name
     * @param profile The profile name
     * @return KeyCreationResult indicating whether keys were created, already existed, or an error occurred
     */
    public KeyCreationResult createEncryptionKeys(String appName, String profile) {
        try {
            log.info("Creating encryption keys for app: {} and profile: {}", appName, profile);

            // Build paths for encryption key, salt, and shell secret
            String keyPath = buildSecretPath(appName, profile, "encrypt.key");
            String saltPath = buildSecretPath(appName, profile, "encrypt.salt");
            String shellPath = buildShellSecretPath(appName, profile+SECRET_PATH_SEPARATOR);

            // Check if encryption key, salt, and shell secret already exist
            boolean keyExists = secretExists(keyPath);
            boolean saltExists = secretExists(saltPath);
            boolean shellExists = secretExists(shellPath);

            // If all secrets already exist, return success without creating new ones
            if (keyExists && saltExists && shellExists) {
                log.info("Encryption keys and shell secret already exist for app: {} and profile: {}", appName, profile);
                return KeyCreationResult.ALREADY_EXISTS;
            }

            // If any of the secrets don't exist, create all of them to ensure consistency
            log.info("Creating new encryption keys and shell secret for app: {} and profile: {}", appName, profile);

            // Generate random encryption key and salt
            String encryptionKey = generateRandomKey(32);
            String encryptionSalt = generateRandomHexSalt(16);

            // Store encryption key in AWS Secrets Manager
            createSecret(keyPath, encryptionKey);
            log.info("Created encryption key at path: {}", keyPath);

            // Store encryption salt in AWS Secrets Manager
            createSecret(saltPath, encryptionSalt);
            log.info("Created encryption salt at path: {}", saltPath);

            // Create empty shell secret for storing application secrets
            createEmptyShellSecret(shellPath);
            log.info("Created empty shell secret at path: {}", shellPath);

            return KeyCreationResult.CREATED;
        } catch (Exception e) {
            log.error("Failed to create encryption keys for app: {} and profile: {}", appName, profile, e);
            return KeyCreationResult.ERROR;
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
     * Checks if a secret exists in AWS Secrets Manager
     * @param secretId The secret ID
     * @return true if the secret exists, false otherwise
     */
    private boolean secretExists(String secretId) {
        try {
            GetSecretValueRequest getRequest = GetSecretValueRequest.builder()
                    .secretId(secretId)
                    .build();
            secretsManager.getSecretValue(getRequest);
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking if secret exists: {}", secretId, e);
            return false;
        }
    }

    /**
     * Creates a new secret in AWS Secrets Manager only if it doesn't already exist
     * @param secretId The secret ID
     * @param secretValue The secret value
     * @return true if the secret was created or already exists, false otherwise
     */
    private boolean createSecret(String secretId, String secretValue) {
        try {
            if (!secretExists(secretId)) {
                CreateSecretRequest createRequest = CreateSecretRequest.builder()
                        .name(secretId)
                        .secretString(secretValue)
                        .build();
                secretsManager.createSecret(createRequest);
                log.info("Created new secret: {}", secretId);
            } else {
                log.info("Secret already exists, not creating: {}", secretId);
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to create secret: {}", secretId, e);
            return false;
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
     * Creates a new empty shell secret in AWS Secrets Manager only if it doesn't already exist
     * @param secretId The secret ID
     * @return true if the shell secret was created or already exists, false otherwise
     */
    private boolean createEmptyShellSecret(String secretId) {
        try {
            if (!secretExists(secretId)) {
                // Secret doesn't exist, create it with an empty JSON object
                CreateSecretRequest createRequest = CreateSecretRequest.builder()
                        .name(secretId)
                        .secretString("{}")  // Empty JSON object as initial value
                        .build();
                secretsManager.createSecret(createRequest);
                log.info("Created new empty shell secret at: {}", secretId);
            } else {
                log.info("Shell secret already exists, not creating: {}", secretId);
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to create empty shell secret at: {}", secretId, e);
            return false;
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
     * Adds multiple secrets to the shell secret container
     * @param appName The application name
     * @param profile The profile name
     * @param secrets Map of key-value pairs to encrypt and store
     * @return true if all secrets were added successfully
     */
    public boolean addSecrets(String appName, String profile, Map<String, String> secrets) {
        try {
            log.info("Adding {} secrets for app: {} and profile: {}", secrets.size(), appName, profile);

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

            // Add or update each secret
            for (Map.Entry<String, String> entry : secrets.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                // Encrypt the value
                String encryptedValue = encrypt(appName, profile, value);

                // Add to the map
                secretsMap.put(key, "{cipher}"+encryptedValue);
                log.info("Encrypted secret with key '{}'", key);
            }

            // Convert back to JSON
            String updatedContent = objectMapper.writeValueAsString(secretsMap);

            // Update the shell secret
            UpdateSecretRequest updateRequest = UpdateSecretRequest.builder()
                    .secretId(shellPath)
                    .secretString(updatedContent)
                    .build();
            secretsManager.updateSecret(updateRequest);

            log.info("Successfully added {} secrets for app: {} and profile: {}", secrets.size(), appName, profile);
            return true;
        } catch (Exception e) {
            log.error("Failed to add secrets for app: {} and profile: {}", appName, profile, e);
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

    /**
     * Rotates encryption keys for a specific application and profile and re-encrypts specified secrets
     * @param appName The application name
     * @param profile The profile name
     * @param secretsToRotate List of secret keys to rotate
     * @return true if keys were rotated successfully
     */
    public boolean rotateEncryptionKeys(String appName, String profile, List<String> secretsToRotate) {
        log.info("Rotating encryption keys for app: {} and profile: {}", appName, profile);

        // Get the shell secret path
        String shellPath = buildShellSecretPath(appName, profile+SECRET_PATH_SEPARATOR);

        // Get the encryption key and salt paths
        String keyPath = buildSecretPath(appName, profile, ENCRYPT_KEY);
        String saltPath = buildSecretPath(appName, profile, ENCRYPT_SALT);

        // Variables to store original values for rollback
        String originalShellContent = null;
        String originalKey = null;
        String originalSalt = null;
        boolean shellUpdated = false;
        boolean keyUpdated = false;
        boolean saltUpdated = false;

        try {
            // Get the current shell secret content
            originalShellContent = getSecretValue(shellPath);
            Map<String, String> secretsMap;

            try {
                // Parse the JSON content
                secretsMap = objectMapper.readValue(originalShellContent, Map.class);
            } catch (Exception e) {
                log.error("Failed to parse shell secret content: {}", e.getMessage());
                return false;
            }

            // Get the old encryption keys
            originalKey = getSecretValue(keyPath);
            originalSalt = getSecretValue(saltPath);

            // Create old TextEncryptor
            TextEncryptor oldEncryptor = Encryptors.text(originalKey, originalSalt);

            // Generate new encryption key and salt
            String newKey = generateRandomKey(32);
            String newSalt = generateRandomHexSalt(16);

            // Create new TextEncryptor
            TextEncryptor newEncryptor = Encryptors.text(newKey, newSalt);

            // Re-encrypt specified secrets
            Map<String, String> updatedSecretsMap = new HashMap<>(secretsMap);

            for (String secretKey : secretsToRotate) {
                if (secretsMap.containsKey(secretKey)) {
                    String encryptedValue = secretsMap.get(secretKey);

                    // Check if the value is encrypted (starts with {cipher})
                    if (encryptedValue.startsWith("{cipher}")) {
                        // Extract the encrypted part
                        String cipherText = encryptedValue.substring("{cipher}".length());

                        // Decrypt with old keys
                        String plainText = oldEncryptor.decrypt(cipherText);

                        // Re-encrypt with new keys
                        String newEncryptedValue = newEncryptor.encrypt(plainText);

                        // Update the map with the new encrypted value
                        updatedSecretsMap.put(secretKey, "{cipher}" + newEncryptedValue);

                        log.info("Re-encrypted secret: {}", secretKey);
                    } else {
                        log.warn("Secret {} is not encrypted, skipping", secretKey);
                    }
                } else {
                    log.warn("Secret {} not found, skipping", secretKey);
                }
            }

            // Convert updated map back to JSON
            String updatedContent = objectMapper.writeValueAsString(updatedSecretsMap);

            try {
                // Update the shell secret with re-encrypted values
                UpdateSecretRequest updateShellRequest = UpdateSecretRequest.builder()
                        .secretId(shellPath)
                        .secretString(updatedContent)
                        .build();
                secretsManager.updateSecret(updateShellRequest);
                shellUpdated = true;
                log.info("Updated shell secret with re-encrypted values");

                // Update the encryption key
                try {
                    storeSecret(keyPath, newKey);
                    keyUpdated = true;
                    log.info("Updated encryption key");

                    // Update the encryption salt
                    storeSecret(saltPath, newSalt);
                    saltUpdated = true;
                    log.info("Updated encryption salt");
                } catch (Exception e) {
                    // If updating key or salt fails, rollback shell update
                    log.error("Failed to update encryption keys or salt, rolling back shell update", e);
                    throw e;
                }
            } catch (Exception e) {
                // Rollback any changes made
                rollbackChanges(shellPath, keyPath, saltPath, originalShellContent, originalKey, originalSalt, 
                                shellUpdated, keyUpdated, saltUpdated);
                log.error("Failed during key rotation, rolled back all changes", e);
                return false;
            }

            return true;
        } catch (Exception e) {
            // Rollback any changes made
            rollbackChanges(shellPath, keyPath, saltPath, originalShellContent, originalKey, originalSalt, 
                            shellUpdated, keyUpdated, saltUpdated);
            log.error("Failed to rotate encryption keys for app: {} and profile: {}", appName, profile, e);
            return false;
        }
    }

    /**
     * Rolls back changes made during key rotation if any operation fails
     */
    private void rollbackChanges(String shellPath, String keyPath, String saltPath, 
                                String originalShellContent, String originalKey, String originalSalt,
                                boolean shellUpdated, boolean keyUpdated, boolean saltUpdated) {
        try {
            if (shellUpdated && originalShellContent != null) {
                log.info("Rolling back shell secret update");
                UpdateSecretRequest updateShellRequest = UpdateSecretRequest.builder()
                        .secretId(shellPath)
                        .secretString(originalShellContent)
                        .build();
                secretsManager.updateSecret(updateShellRequest);
            }

            if (keyUpdated && originalKey != null) {
                log.info("Rolling back encryption key update");
                UpdateSecretRequest updateKeyRequest = UpdateSecretRequest.builder()
                        .secretId(keyPath)
                        .secretString(originalKey)
                        .build();
                secretsManager.updateSecret(updateKeyRequest);
            }

            if (saltUpdated && originalSalt != null) {
                log.info("Rolling back encryption salt update");
                UpdateSecretRequest updateSaltRequest = UpdateSecretRequest.builder()
                        .secretId(saltPath)
                        .secretString(originalSalt)
                        .build();
                secretsManager.updateSecret(updateSaltRequest);
            }

            log.info("Successfully rolled back all changes");
        } catch (Exception e) {
            log.error("Failed to rollback changes, manual intervention may be required", e);
        }
    }

    /**
     * Gets all keys from a shell secret for a specific application and profile
     * @param appName The application name
     * @param profile The profile name
     * @return A list of keys or null if an error occurs
     */
    public List<String> getAllKeys(String appName, String profile) {
        try {
            log.info("Getting all keys for app: {} and profile: {}", appName, profile);

            // Get the shell secret path
            String shellPath = buildShellSecretPath(appName, profile+SECRET_PATH_SEPARATOR);

            // Check if the shell secret exists
            if (!secretExists(shellPath)) {
                log.warn("Shell secret does not exist for app: {} and profile: {}", appName, profile);
                return List.of(); // Return empty list if shell secret doesn't exist
            }

            // Get the shell secret content
            String shellContent = getSecretValue(shellPath);
            Map<String, String> secretsMap;

            try {
                // Parse the JSON content
                secretsMap = objectMapper.readValue(shellContent, Map.class);
            } catch (Exception e) {
                log.error("Failed to parse shell secret content: {}", e.getMessage());
                return null;
            }

            // Extract and return the keys
            List<String> keys = secretsMap.keySet().stream().toList();
            log.info("Found {} keys for app: {} and profile: {}", keys.size(), appName, profile);

            return keys;
        } catch (Exception e) {
            log.error("Failed to get all keys for app: {} and profile: {}", appName, profile, e);
            return null;
        }
    }
}
