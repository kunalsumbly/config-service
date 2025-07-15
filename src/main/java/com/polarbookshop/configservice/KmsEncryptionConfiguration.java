//package com.polarbookshop.configservice;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Primary;
//import org.springframework.security.crypto.encrypt.TextEncryptor;
//import org.zalando.awsspring.cloud.bootstrap.encrypt.KmsTextEncryptor;
//import software.amazon.awssdk.regions.Region;
//import software.amazon.awssdk.services.kms.KmsClient;
//
//@Configuration
//@ConditionalOnProperty(name = "encrypt.key", matchIfMissing = false)
//public class KmsEncryptionConfiguration {
//
//    @Value("${encrypt.key}")
//    private String encryptKey;
//
//    @Value("${aws.region}")
//    private String awsRegion;
//
//    @Bean
//    @ConditionalOnMissingBean
//    public KmsClient kmsClient() {
//        return KmsClient.builder()
//            .region(Region.of(awsRegion))
//            .build();
//    }
//
//    @Bean
//    @Primary
//    public TextEncryptor textEncryptor(KmsClient kmsClient) {
//        return new KmsTextEncryptor(kmsClient, encryptKey, null);
//    }
//}
