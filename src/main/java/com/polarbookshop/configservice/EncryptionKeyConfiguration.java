//package com.polarbookshop.configservice;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Primary;
//import org.springframework.security.crypto.encrypt.Encryptors;
//import org.springframework.security.crypto.encrypt.TextEncryptor;
//import software.amazon.awssdk.regions.Region;
//import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
//import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
//import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
//
//@Configuration
//@Slf4j
//public class EncryptionKeyConfiguration {
//
//    private static final Region AWS_REGION = Region.AP_SOUTHEAST_2;
//    private static final String SECRET_PATH_TEMPLATE = "%s%s";
//    @Value("${encrypt.key}")
//    private String encryptionKey;
//
//    @Value("${encrypt.salt}")
//    private String encryptionSalt;
//
//    @Bean
//    @Primary
//    public TextEncryptor textEncryptor() {
//        log.info("Using hardcoded encryption key: {} and salt: {}", encryptionKey, encryptionSalt);
//        return Encryptors.text(encryptionKey, encryptionSalt);
//    }
////
////    @Value("${spring.profiles.active:default}")
////    private String activeProfile;
////
////    @Value("${config.server.aws.sm.encryption.secretPath:config-server/encryption-key/}")
////    private String configServerEncryptionSecretPath;
////
////    @Value("${config.server.aws.sm.encryption.encryption.saltPath:config-server/encryption-key-salt/}")
////    private String configServerEncryptionSaltPath;
////
////    private final SecretsManagerClient secretsManager = SecretsManagerClient.builder()
////            .region(AWS_REGION)
////            .build();
//
////    @Bean
////    @Primary
////    public TextEncryptor textEncryptor() {
////        try {
////            log.info("Initializing TextEncryptor with AWS Secrets Manager...");
////            String encryptionKeyPath = buildSecretPath(configServerEncryptionSecretPath);
////            String encryptionSaltPath = buildSecretPath(configServerEncryptionSaltPath);
////
////            String encryptionKey = getSecretValue(encryptionKeyPath);
////            log.info("Encryption key fetched from awssecretmanager at path: {}", encryptionKeyPath);
////
////            String encryptionSalt = getSecretValue(encryptionSaltPath);
////            log.info("Encryption salt fetched from awssecretmanager at path: {}", encryptionSaltPath);
////
////            log.info("TextEncryptor initialized successfully");
////            return Encryptors.text(encryptionKey, encryptionSalt);
////        } catch (Exception e) {
////            log.error("Failed to retrieve encryption key/salt from AWS Secrets Manager", e);
////            throw new IllegalStateException("Cannot initialize encryption ", e);
////        }
////    }
////
////    private String buildSecretPath(String basePath) {
////        return String.format(SECRET_PATH_TEMPLATE, basePath, activeProfile);
////    }
////
////    private String getSecretValue(String secretId) {
////        GetSecretValueRequest request = GetSecretValueRequest.builder()
////                .secretId(secretId)
////                .build();
////        GetSecretValueResponse response = secretsManager.getSecretValue(request);
////        return response.secretString();
////    }
//}