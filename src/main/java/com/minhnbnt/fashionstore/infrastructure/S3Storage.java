package com.minhnbnt.fashionstore.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3Storage {

    public record PresignedUpload(String key, String url, Instant expiresAt) {
    }

    @Value("${s3.bucket}")
    private String bucket;

    @Value("${s3.url-ttl:PT10M}")
    private Duration urlTtl;

    private final S3Presigner presigner;

    public PresignedUpload presign(String contentType) {

        var key = UUID.randomUUID() + "." + extensionOf(contentType);

        var request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .build();

        var presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(urlTtl)
            .putObjectRequest(request)
            .build();

        var signed = presigner.presignPutObject(presignRequest);

        return new PresignedUpload(
            key,
            signed.url().toString(),
            Instant.now().plus(urlTtl)
        );
    }

    private static String extensionOf(String contentType) {

        var subtype = MimeTypeUtils.parseMimeType(contentType).getSubtype();

        var prefixIndex = subtype.indexOf("+");
        if (prefixIndex < 0) {
            return subtype;
        }

        return subtype.substring(0, prefixIndex);
    }
}
