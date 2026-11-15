package com.minhnbnt.fashionstore.features.upload;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class S3UrlServiceTest {

    private static final Duration DEFAULT_KYC_TTL = Duration.ofMinutes(15);

    private static S3Presigner presigner() {
        return S3Presigner.builder()
            .endpointOverride(URI.create("http://localhost:9000"))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("fashionstore", "passwordpassword")
            ))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }

    @Test
    void publicUrlMatchesPathStyleConvention() {
        var service = new S3UrlService("http://localhost:9000", "fashionstore", DEFAULT_KYC_TTL, presigner());

        assertThat(service.publicUrl("product/abc-123.webp"))
            .isEqualTo("http://localhost:9000/fashionstore/product/abc-123.webp");
        assertThat(service.publicUrl("kyc/id-front.png"))
            .isEqualTo("http://localhost:9000/fashionstore/kyc/id-front.png");
    }

    @Test
    void kycPresignExpiresAfterDefaultTtl() {
        var service = new S3UrlService("http://localhost:9000", "fashionstore", DEFAULT_KYC_TTL, presigner());
        var key = "kyc/" + UUID.randomUUID() + ".pdf";
        var before = Instant.now();

        var signed = service.presignKycRead(key);

        assertThat(signed.url()).startsWith("http://localhost:9000/fashionstore/kyc/");
        assertThat(queryParam(signed.url(), "X-Amz-Expires")).isEqualTo("900");
        assertThat(signed.expiresAt())
            .isCloseTo(before.plus(DEFAULT_KYC_TTL), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void kycPresignHonorsConfiguredTtl() {
        var service = new S3UrlService(
            "http://localhost:9000", "fashionstore", Duration.ofMinutes(2), presigner()
        );

        var signed = service.presignKycRead("kyc/" + UUID.randomUUID() + ".png");

        assertThat(queryParam(signed.url(), "X-Amz-Expires")).isEqualTo("120");
    }

    private static String queryParam(String url, String name) {
        var query = url.substring(url.indexOf('?') + 1);

        for (var pair : query.split("&")) {
            var eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            if (pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }

        throw new AssertionError("Missing query param " + name + " in " + url);
    }
}
