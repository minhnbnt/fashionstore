package com.minhnbnt.fashionstore.infrastructure.seed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Tải ảnh demo từ picsum.photos. Sau {@value #MAX_CONSECUTIVE_FAILURES} lần lỗi liên tiếp thì bỏ luôn
 * các ảnh còn lại, để máy không có mạng không phải chờ hết timeout từng ảnh.
 */
@Slf4j
@Component
public class PicsumImageFetcher implements DemoImageFetcher {

    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final HttpClient http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private int consecutiveFailures;

    @Override
    public Optional<byte[]> fetch(String url) {

        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            return Optional.empty();
        }

        var request = HttpRequest.newBuilder(URI.create(url))
            .GET()
            .timeout(Duration.ofSeconds(15))
            .build();

        try {
            var body = http.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
            consecutiveFailures = 0;
            return Optional.of(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed(url, e.toString());
        } catch (IOException | RuntimeException e) {
            return failed(url, e.toString());
        }
    }

    private Optional<byte[]> failed(String url, String message) {
        consecutiveFailures++;
        log.warn("Skipping demo image {} ({}): {}", url, consecutiveFailures, message);
        return Optional.empty();
    }
}
