package com.minhnbnt.fashionstore.infrastructure.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoMetadataServiceTest {

    @Mock
    private S3Client s3Client;

    private VideoMetadataService videoMetadataService;

    @BeforeEach
    void setUp() {
        videoMetadataService = new VideoMetadataService(s3Client);
        ReflectionTestUtils.setField(videoMetadataService, "bucket", "test-bucket");
    }

    private byte[] createSampleMp4(long timescale, long duration) throws IOException {
        var baos = new ByteArrayOutputStream();
        var dos = new DataOutputStream(baos);

        // ftyp box
        dos.writeInt(16);
        dos.writeBytes("ftyp");
        dos.writeBytes("isom");
        dos.writeInt(512);

        // mvhd payload
        var mvhdBaos = new ByteArrayOutputStream();
        var mvhdDos = new DataOutputStream(mvhdBaos);
        mvhdDos.writeByte(0); // version 0
        mvhdDos.writeByte(0);
        mvhdDos.writeByte(0);
        mvhdDos.writeByte(0);
        mvhdDos.writeInt(0);
        mvhdDos.writeInt(0);
        mvhdDos.writeInt((int) timescale);
        mvhdDos.writeInt((int) duration);
        mvhdDos.write(new byte[80]);

        byte[] mvhdPayload = mvhdBaos.toByteArray();
        int mvhdBoxSize = 8 + mvhdPayload.length;
        int moovBoxSize = 8 + mvhdBoxSize;

        dos.writeInt(moovBoxSize);
        dos.writeBytes("moov");
        dos.writeInt(mvhdBoxSize);
        dos.writeBytes("mvhd");
        dos.write(mvhdPayload);

        return baos.toByteArray();
    }

    @Test
    void validateVideoDuration_nullOrBlank_skipsCheck() {
        assertThatCode(() -> videoMetadataService.validateVideoDuration(null, 60.0))
            .doesNotThrowAnyException();
        assertThatCode(() -> videoMetadataService.validateVideoDuration("   ", 60.0))
            .doesNotThrowAnyException();
        verifyNoInteractions(s3Client);
    }

    @Test
    void validateVideoDuration_videoNotFound_throwsBadRequest() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().message("The specified key does not exist.").build());

        assertThatThrownBy(() -> videoMetadataService.validateVideoDuration("videos/not-exist.mp4", 60.0))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var ex = (ResponseStatusException) e;
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getReason()).contains("Video file not found");
            });
    }

    @Test
    void validateVideoDuration_s3Exception404_throwsBadRequest() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        assertThatThrownBy(() -> videoMetadataService.validateVideoDuration("videos/404.mp4", 60.0))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var ex = (ResponseStatusException) e;
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getReason()).contains("Video file not found");
            });
    }

    @Test
    void validateVideoDuration_exceeds60Seconds_throwsBadRequest() throws Exception {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenReturn(HeadObjectResponse.builder().contentLength(1024L).build());

        // 75 seconds at timescale 1000 = 75000
        byte[] mp4Bytes = createSampleMp4(1000, 75000);
        var inputStream = new ResponseInputStream<>(
            GetObjectResponse.builder().build(),
            AbortableInputStream.create(new ByteArrayInputStream(mp4Bytes))
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(inputStream);

        assertThatThrownBy(() -> videoMetadataService.validateVideoDuration("videos/long.mp4", 60.0))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var ex = (ResponseStatusException) e;
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getReason()).contains("exceeding maximum allowed");
            });
    }

    @Test
    void validateVideoDuration_validDuration_succeeds() throws Exception {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenReturn(HeadObjectResponse.builder().contentLength(1024L).build());

        // 45 seconds at timescale 1000 = 45000
        byte[] mp4Bytes = createSampleMp4(1000, 45000);
        var inputStream = new ResponseInputStream<>(
            GetObjectResponse.builder().build(),
            AbortableInputStream.create(new ByteArrayInputStream(mp4Bytes))
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(inputStream);

        assertThatCode(() -> videoMetadataService.validateVideoDuration("videos/good.mp4", 60.0))
            .doesNotThrowAnyException();
    }
}
