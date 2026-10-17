package com.minhnbnt.fashionstore.config;

import java.net.URI;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Slf4j
@Configuration
public class S3Config {

    @Bean
    public S3Presigner s3Presigner(
        @Value("${s3.endpoint}") URI endpoint,
        @Value("${s3.region}") String region,
        @Value("${s3.access-key}") String accessKey,
        @Value("${s3.secret-key}") String secretKey
    ) {

        return S3Presigner.builder()
            .endpointOverride(endpoint)
            .region(Region.of(region))
            .credentialsProvider(credentials(accessKey, secretKey))
            .serviceConfiguration(serviceConfiguration())
            .build();
    }

    @Bean
    public S3Client s3Client(
        @Value("${s3.endpoint}") URI endpoint,
        @Value("${s3.region}") String region,
        @Value("${s3.access-key}") String accessKey,
        @Value("${s3.secret-key}") String secretKey,
        @Value("${s3.bucket}") String bucket
    ) {

        var client = S3Client.builder()
            .endpointOverride(endpoint)
            .region(Region.of(region))
            .credentialsProvider(credentials(accessKey, secretKey))
            .serviceConfiguration(serviceConfiguration())
            // Some S3-compatible gateways reject the flexible checksums the SDK sends by default.
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .build();

        ensurePublicBucket(client, bucket);

        return client;
    }

    /**
     * Prepares the upload bucket for anonymous browser reads: creates it when
     * missing and attaches a public-read policy. Runs once at startup; S3
     * problems are logged and skipped so the app still boots without storage.
     */
    static void ensurePublicBucket(S3Client s3, String bucket) {
        try {
            ensureBucketExists(s3, bucket);
            putPublicReadPolicy(s3, bucket);
        } catch (RuntimeException e) {
            log.warn("Could not prepare bucket {}: {}", bucket, e.getMessage());
        }
    }

    private static void ensureBucketExists(S3Client s3, String bucket) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("Bucket {} already exists", bucket);
        } catch (NoSuchBucketException e) {
            createBucket(s3, bucket);
        } catch (S3Exception e) {
            // HEAD responses carry no error body, so a missing bucket can also
            // surface as a plain 404/301 instead of NoSuchBucketException.
            if (e.statusCode() == 404 || e.statusCode() == 301) {
                createBucket(s3, bucket);
            } else {
                throw e;
            }
        }
    }

    private static void createBucket(S3Client s3, String bucket) {

        var request = CreateBucketRequest.builder()
            .bucket(bucket)
            .build();

        s3.createBucket(request);

        log.info("Created bucket {}", bucket);
    }

    private static void putPublicReadPolicy(S3Client s3, String bucket) {

        var policy = """
            {
                "Version": "2012-10-17",
                "Statement": [{
                    "Sid": "PublicReadGetObject",
                    "Effect": "Allow",
                    "Principal": "*",
                    "Action": "s3:GetObject",
                    "Resource": "arn:aws:s3:::%s/*"
                }]
            }
        """.formatted(bucket).trim();

        var request = PutBucketPolicyRequest.builder()
            .bucket(bucket)
            .policy(policy)
            .build();

        s3.putBucketPolicy(request);

        log.info("Granted anonymous read on bucket {}", bucket);
    }

    private StaticCredentialsProvider credentials(String accessKey, String secretKey) {
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
