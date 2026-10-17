package com.minhnbnt.fashionstore.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class S3StorageTest {

    private final S3Presigner presigner = S3Presigner.builder()
            .endpointOverride(URI.create("http://localhost:9000"))
            .region(Region.of("us-east-1"))
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("access-key", "secret-key")))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();

    private S3Storage storage() {
        return storage(Duration.ofMinutes(10));
    }

    private S3Storage storage(Duration urlTtl) {
        var storage = new S3Storage(presigner);
        ReflectionTestUtils.setField(storage, "bucket", "fashionstore");
        ReflectionTestUtils.setField(storage, "urlTtl", urlTtl);
        return storage;
    }

    @Test
    void presignReturnsSignedPathStyleUrlForGeneratedKey() {
        var upload = storage().presign("image/png");

        assertThat(upload.key()).endsWith(".png");
        assertThat(upload.url())
                .startsWith("http://localhost:9000/fashionstore/")
                .contains("X-Amz-Signature=");
    }

    @Test
    void svgContentTypeGetsSvgExtension() {
        assertThat(storage().presign("image/svg+xml").key()).endsWith(".svg");
    }

    @Test
    void urlExpiresInTenMinutes() {
        var now = Instant.now();

        assertThat(storage().presign("image/png").expiresAt())
                .isBetween(now.plusSeconds(9 * 60), now.plusSeconds(11 * 60));
    }

    @Test
    void urlTtlIsConfigurable() {
        var now = Instant.now();

        assertThat(storage(Duration.ofSeconds(90)).presign("image/png").expiresAt())
                .isBetween(now.plusSeconds(80), now.plusSeconds(100));
    }
}
