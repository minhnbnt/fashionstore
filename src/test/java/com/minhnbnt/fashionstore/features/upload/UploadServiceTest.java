package com.minhnbnt.fashionstore.features.upload;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.upload.PresignRequest;
import com.minhnbnt.fashionstore.dto.upload.UploadPurpose;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UploadServiceTest {

    @Autowired
    private UploadService uploadService;

    @Autowired
    private UploadRepository uploads;

    @Value("${s3.bucket}")
    private String bucket;

    @Test
    void presignCreatesRowAndReturnsUploadId() {
        var response = uploadService.presign(new PresignRequest("image/png", null));

        var row = uploads.findById(response.uploadId()).orElseThrow();
        assertThat(row.getContentType()).isEqualTo("image/png");
        assertThat(row.getS3Key()).doesNotContain("/").endsWith(".png");

        assertThat(response.uploadUrl())
            .contains("/" + bucket + "/" + row.getS3Key() + "?")
            .contains("X-Amz-Signature=");
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    void kycPurposePutsKeyUnderKycPrefix() {
        var response = uploadService.presign(new PresignRequest("image/jpeg", UploadPurpose.KYC));

        var row = uploads.findById(response.uploadId()).orElseThrow();
        assertThat(row.getS3Key()).startsWith("kyc/");
        assertThat(response.uploadUrl()).contains("/" + bucket + "/" + row.getS3Key() + "?");
    }

    @Test
    void mediaPurposeKeepsRootPrefix() {
        var response = uploadService.presign(new PresignRequest("image/webp", UploadPurpose.MEDIA));

        var row = uploads.findById(response.uploadId()).orElseThrow();
        assertThat(row.getS3Key()).doesNotContain("/");
    }
}
