package com.minhnbnt.fashionstore.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Value("${s3.endpoint}")
    private URI endpoint;

    @Value("${s3.region}")
    private String region;

    @Value("${s3.access-key}")
    private String accessKey;

    @Value("${s3.secret-key}")
    private String secretKey;

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
            .endpointOverride(endpoint)
            .region(Region.of(region))
            .credentialsProvider(credentials())
            .serviceConfiguration(serviceConfiguration())
            .build();
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .endpointOverride(endpoint)
            .region(Region.of(region))
            .credentialsProvider(credentials())
            .serviceConfiguration(serviceConfiguration())
            // Garage (S3-compatible) rejects the flexible checksums the SDK sends by default.
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)
        );
    }

    private static S3Configuration serviceConfiguration() {
        return S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .build();
    }
}
