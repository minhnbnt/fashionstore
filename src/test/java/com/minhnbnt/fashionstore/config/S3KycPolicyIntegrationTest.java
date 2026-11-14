package com.minhnbnt.fashionstore.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test against a real versitygw container: verifies the bucket policy
 * denies anonymous reads on the {@code kyc/} prefix while public keys stay readable
 * and root-credential presigned requests keep working (versitygw evaluates policies
 * for anonymous callers only, so an explicit Deny never blocks a presigned request).
 * The last read case strips the query string from a presigned URL — the signature
 * only guards the query, so the bare path must still be denied for KYC objects.
 */
@Testcontainers
class S3KycPolicyIntegrationTest {

    private static final String ACCESS_KEY = "fashionstore";

    private static final String SECRET_KEY = "passwordpassword";

    private static final String BUCKET = "fashionstore";

    @Container
    private static final GenericContainer<?> VERSITY = new GenericContainer<>(
        DockerImageName.parse("ghcr.io/minhnbnt/versitygw:latest"))
        .withEnv("ROOT_ACCESS_KEY", ACCESS_KEY)
        .withEnv("ROOT_SECRET_KEY", SECRET_KEY)
        .withEnv("VGW_PORT", ":9000")
        .withEnv("VGW_BACKEND", "posix")
        .withEnv("VGW_BACKEND_ARGS", "/data/s3")
        .withExposedPorts(9000)
        .waitingFor(Wait.forListeningPort());

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static S3Client s3;

    private static S3Presigner presigner;

    @BeforeAll
    static void connectClients() {

        var endpoint = "http://" + VERSITY.getHost() + ":" + VERSITY.getMappedPort(9000);
        var credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY));

        s3 = s3Client(endpoint, credentials);
        presigner = presigner(endpoint, credentials);

        S3Config.ensurePublicBucket(s3, BUCKET);
    }

    private static S3Client s3Client(String endpoint, StaticCredentialsProvider credentials) {

        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(credentials)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .build();
    }

    private static S3Presigner presigner(String endpoint, StaticCredentialsProvider credentials) {

        return S3Presigner.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(credentials)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }

    private static void putObject(String key, String body) {

        var request = PutObjectRequest.builder()
            .bucket(BUCKET)
            .key(key)
            .contentType("image/jpeg")
            .build();

        s3.putObject(request, RequestBody.fromBytes(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static HttpResponse<String> get(String url)
            throws IOException, InterruptedException {

        var request = HttpRequest.newBuilder(URI.create(url)).GET().build();

        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> put(String url, String body)
            throws IOException, InterruptedException {

        // Presigned PUT ký cả header Content-Type, nên request bắt buộc gửi header trùng khớp.
        var request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "image/jpeg")
            .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String presignedGetUrl(String key) {

        var request = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(5))
            .getObjectRequest(GetObjectRequest.builder().bucket(BUCKET).key(key).build())
            .build();

        return presigner.presignGetObject(request).url().toString();
    }

    private static String presignedPutUrl(String key) {

        var request = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(10))
            .putObjectRequest(PutObjectRequest.builder()
                .bucket(BUCKET)
                .key(key)
                .contentType("image/jpeg")
                .build())
            .build();

        return presigner.presignPutObject(request).url().toString();
    }

    @Test
    void publicKeysRemainAnonymouslyReadable() throws IOException, InterruptedException {

        putObject("demo/pub-check.jpg", "public-bytes");

        var response = get(baseUrl() + "/fashionstore/demo/pub-check.jpg");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("public-bytes");
    }

    @Test
    void anonymousGetOnKycPrefixIsDenied() throws IOException, InterruptedException {

        putObject("kyc/id-front-check.jpg", "kyc-bytes");

        var response = get(baseUrl() + "/fashionstore/kyc/id-front-check.jpg");

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void presignedGetOnKycPrefixStillWorks() throws IOException, InterruptedException {

        putObject("kyc/id-front-check.jpg", "kyc-bytes");

        var response = get(presignedGetUrl("kyc/id-front-check.jpg"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("kyc-bytes");
    }

    @Test
    void strippingSignatureFromPresignedUrlDoesNotBypassKycDeny()
            throws IOException, InterruptedException {

        putObject("kyc/id-front-check.jpg", "kyc-bytes");

        var presigned = presignedGetUrl("kyc/id-front-check.jpg");
        var queryStart = presigned.indexOf('?');
        assertThat(queryStart).isGreaterThan(0);

        var bareUrl = presigned.substring(0, queryStart);
        var response = get(bareUrl);

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void presignedPutIntoKycPrefixStillWorks() throws IOException, InterruptedException {

        var response = put(presignedPutUrl("kyc/id-front-upload.jpg"), "uploaded-kyc-bytes");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(get(baseUrl() + "/fashionstore/kyc/id-front-upload.jpg").statusCode())
            .isEqualTo(403);
    }

    private static String baseUrl() {
        return "http://" + VERSITY.getHost() + ":" + VERSITY.getMappedPort(9000);
    }
}
