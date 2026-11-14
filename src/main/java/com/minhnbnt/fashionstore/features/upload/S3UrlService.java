package com.minhnbnt.fashionstore.features.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;

/**
 * Builds read URLs for S3 objects: anonymous reads use the path-style public
 * URL ({public-base-url}/{bucket}/{key}), KYC documents live under the denied
 * {@code kyc/} prefix and need a short-lived presigned GET instead.
 */
@Service
public class S3UrlService {

    public record PresignedRead(String url, Instant expiresAt) {
    }

    private final String publicBaseUrl;
    private final String bucket;
    private final Duration kycUrlTtl;
    private final S3Presigner presigner;

    public S3UrlService(
        @Value("${s3.public-base-url}") String publicBaseUrl,
        @Value("${s3.bucket}") String bucket,
        @Value("${s3.kyc-url-ttl:PT15M}") Duration kycUrlTtl,
        S3Presigner presigner
    ) {
        this.publicBaseUrl = publicBaseUrl;
        this.bucket = bucket;
        this.kycUrlTtl = kycUrlTtl;
        this.presigner = presigner;
    }

    public String publicUrl(String s3Key) {
        return publicBaseUrl + "/" + bucket + "/" + s3Key;
    }

    public PresignedRead presignKycRead(String s3Key) {

        var request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(s3Key)
            .build();

        var presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(kycUrlTtl)
            .getObjectRequest(request)
            .build();

        var signed = presigner.presignGetObject(presignRequest);

        return new PresignedRead(signed.url().toString(), signed.expiration());
    }
}
