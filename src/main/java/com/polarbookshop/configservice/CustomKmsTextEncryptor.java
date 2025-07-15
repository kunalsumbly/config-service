package com.polarbookshop.configservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;

import java.util.Base64;

@Component
@Primary
public class CustomKmsTextEncryptor implements TextEncryptor {

    private final KmsClient kmsClient;
    private final String kmsKeyId;
    private static final String CIPHER_PREFIX = "{cipher}";

    public CustomKmsTextEncryptor(
            @Value("${encrypt.key}") String kmsKeyId,
            @Value("${aws.region}") String region) {
        this.kmsKeyId = kmsKeyId;
        this.kmsClient = KmsClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            EncryptRequest request = EncryptRequest.builder()
                    .keyId(kmsKeyId)
                    .plaintext(SdkBytes.fromUtf8String(plaintext))
                    .build();

            EncryptResponse response = kmsClient.encrypt(request);

            return Base64.getEncoder().encodeToString(
                    response.ciphertextBlob().asByteArray()
            );

        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt with KMS", e);
        }
    }

    @Override
    public String decrypt(String encryptedText) {
        try {
            // Handle the {cipher} prefix if present
            String cleanEncryptedText = encryptedText;
            if (encryptedText.startsWith(CIPHER_PREFIX)) {
                cleanEncryptedText = encryptedText.substring(CIPHER_PREFIX.length());
            }

            // Decode Base64 to get the ciphertext blob
            byte[] ciphertextBlob = Base64.getDecoder().decode(cleanEncryptedText);

            DecryptRequest request = DecryptRequest.builder()
                    .ciphertextBlob(SdkBytes.fromByteArray(ciphertextBlob))
                    .build();

            DecryptResponse response = kmsClient.decrypt(request);

            return response.plaintext().asUtf8String();

        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt with KMS: " + e.getMessage(), e);
        }
    }
}
