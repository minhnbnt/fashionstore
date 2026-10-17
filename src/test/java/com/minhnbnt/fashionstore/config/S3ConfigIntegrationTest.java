package com.minhnbnt.fashionstore.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test against a real versitygw container: ensures the bucket,
 * uploads an object and verifies anonymous reads work over plain HTTP.
 */
@Testcontainers
class S3ConfigIntegrationTest {

    private static final String ACCESS_KEY = "fashionstore";

    private static final String SECRET_KEY = "passwordpassword";

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

    @BeforeAll
    static void connectClient() {
        s3 = s3Client();
    }

    private static S3Client s3Client() {

        var endpoint = "http://" + VERSITY.getHost() + ":" + VERSITY.getMappedPort(9000);

        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .build();
    }

    private static String baseUrl() {
        return "http://" + VERSITY.getHost() + ":" + VERSITY.getMappedPort(9000);
    }

    private static HttpResponse<String> anonymousGet(String key)
            throws IOException, InterruptedException {

        var request = HttpRequest.newBuilder(URI.create(baseUrl() + "/fashionstore/" + key))
            .GET()
            .build();

        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void uploadsBecomeAnonymouslyReadableAfterEnsure() throws IOException, InterruptedException {

        S3Config.ensurePublicBucket(s3, "fashionstore");

        var request = PutObjectRequest.builder()
            .bucket("fashionstore")
            .key("demo/it-check.jpg")
            .contentType("image/jpeg")
            .build();
        s3.putObject(request, RequestBody.fromBytes("jpeg-bytes".getBytes(StandardCharsets.UTF_8)));

        var response = anonymousGet("demo/it-check.jpg");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("jpeg-bytes");
    }

    @Test
    void anonymousGetOnMissingKeyIs404Not403() throws IOException, InterruptedException {

        S3Config.ensurePublicBucket(s3, "fashionstore");

        var response = anonymousGet("demo/no-such-key.jpg");
        assertThat(response.statusCode()).isEqualTo(404);
    }
}
