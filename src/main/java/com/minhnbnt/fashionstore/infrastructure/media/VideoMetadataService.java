package com.minhnbnt.fashionstore.infrastructure.media;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.OptionalDouble;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoMetadataService {

    private final S3Client s3Client;

    @Value("${s3.bucket}")
    private String bucket;

    /**
     * Xác thực file video trên S3 và kiểm tra thời lượng không vượt quá maxSeconds.
     *
     * @param videoKey   S3 key của video
     * @param maxSeconds Thời lượng tối đa (giây), mặc định 60s
     */
    public void validateVideoDuration(@Nullable String videoKey, double maxSeconds) {
        if (videoKey == null || videoKey.isBlank()) {
            return;
        }

        try {
            s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(videoKey)
                .build());
        } catch (NoSuchKeyException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file not found: " + videoKey);
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            if (e.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file not found: " + videoKey);
            }
            log.warn("Cannot verify video key {}: {}", videoKey, e.getMessage());
            return;
        } catch (Exception e) {
            log.warn("Cannot verify video key {}: {}", videoKey, e.getMessage());
            return;
        }

        try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(GetObjectRequest.builder()
            .bucket(bucket)
            .key(videoKey)
            .build())) {

            OptionalDouble duration = Mp4DurationParser.parseDurationSeconds(stream);
            if (duration.isPresent()) {
                double durationSeconds = duration.getAsDouble();
                if (durationSeconds > maxSeconds + 0.5) {
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        String.format("Product video duration is %.1f seconds, exceeding maximum allowed of %.0f seconds", durationSeconds, maxSeconds)
                    );
                }
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Không thể phân tích metadata video {}: {}", videoKey, e.getMessage());
        }
    }
}
