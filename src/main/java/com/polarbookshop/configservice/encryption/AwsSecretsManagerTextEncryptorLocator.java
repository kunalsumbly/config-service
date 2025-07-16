package com.polarbookshop.configservice.encryption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.config.server.encryption.TextEncryptorLocator;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Component
@Slf4j
public class AwsSecretsManagerTextEncryptorLocator implements TextEncryptorLocator {

    @Value("${aws.region:ap-southeast-2}")
    private String awsRegion;

    @Value("${config.server.sm.base.path:/config-secrets}")
    private String baseSecretPath;

    private SecretsManagerClient secretsManager;

    @PostConstruct
    public void initSecretsManager() {
        this.secretsManager = SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .build();
        log.info("SecretsManagerClient initialized with region: {}", awsRegion);
    }

    @Override
    public TextEncryptor locate(Map<String, String> properties) {
        String name = properties.get("name");
        String profiles = properties.get("profiles");
        
        log.info("Locating TextEncryptor for app: {} and profiles: {}", name, profiles);
        
        // Use the first profile if multiple are provided
        String profile = profiles;
        if (profiles != null && profiles.contains(",")) {
            profile = profiles.split(",")[0];
        }
        
        String keyPath = buildSecretPath(name, profile, "encrypt.key");
        String saltPath = buildSecretPath(name, profile, "encrypt.salt");

        log.info("Looking up encryption key at path: {}", keyPath);
        log.info("Looking up encryption salt at path: {}", saltPath);

        String key = getSecretValue(keyPath);
        String salt = getSecretValue(saltPath);

        if (key == null || key.isEmpty()) {
            log.error("Encryption key not found for app: {} and profile: {}", name, profile);
            throw new IllegalStateException("Encryption key not found for app: " + name + " and profile: " + profile);
        }

        if (salt == null || salt.isEmpty()) {
            log.error("Encryption salt not found for app: {} and profile: {}", name, profile);
            throw new IllegalStateException("Encryption salt not found for app: " + name + " and profile: " + profile);
        }

        // Validate that the salt is a valid hex string with an even number of characters
        if (salt.length() % 2 != 0) {
            log.error("Encryption salt must have an even number of characters: {}", salt);
            throw new IllegalArgumentException("Encryption salt must have an even number of characters");
        }

        // Check if the salt contains only hex characters (0-9, a-f, A-F)
        if (!salt.matches("[0-9a-fA-F]+")) {
            log.error("Encryption salt must contain only hex characters (0-9, a-f, A-F): {}", salt);
            throw new IllegalArgumentException("Encryption salt must contain only hex characters (0-9, a-f, A-F)");
        }

        log.info("TextEncryptor created successfully for app: {} and profile: {}", name, profile);
        return Encryptors.text(key, salt);
    }

    private String buildSecretPath(String appName, String profile, String secretName) {
        // Remove trailing slash if present to avoid double slashes
        String path = baseSecretPath;
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return String.format("%s/%s/%s/%s", path, appName, profile, secretName);
    }

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
}