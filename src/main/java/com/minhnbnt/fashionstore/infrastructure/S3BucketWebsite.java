package com.minhnbnt.fashionstore.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.IndexDocument;
import software.amazon.awssdk.services.s3.model.PutBucketWebsiteRequest;
import software.amazon.awssdk.services.s3.model.WebsiteConfiguration;

@Component
@Slf4j
public class S3BucketWebsite implements ApplicationRunner {

    private final S3Client s3;

    private final String bucket;

    public S3BucketWebsite(S3Client s3, @Value("${s3.bucket}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void run(ApplicationArguments args) {
        var request = PutBucketWebsiteRequest.builder()
            .bucket(bucket)
            .websiteConfiguration(WebsiteConfiguration.builder()
                .indexDocument(IndexDocument.builder().suffix("index.html").build())
                .build())
            .build();
        try {
            s3.putBucketWebsite(request);
            log.info("Exposed bucket {} as website", bucket);
        } catch (RuntimeException e) {
            log.warn("Could not expose bucket {} as website: {}", bucket, e.getMessage());
        }
    }
}
