package com.minhnbnt.fashionstore.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3ConfigTest {

    private final S3Client s3 = mock(S3Client.class);

    @Test
    void createsMissingBucketAndGrantsAnonymousRead() {
        when(s3.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(NoSuchBucketException.builder().message("no bucket").build());

        S3Config.ensurePublicBucket(s3, "fashionstore");

        verify(s3).createBucket(any(CreateBucketRequest.class));

        var captor = ArgumentCaptor.forClass(PutBucketPolicyRequest.class);
        verify(s3).putBucketPolicy(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("fashionstore");
        assertThat(captor.getValue().policy())
            // versitygw rejects a policy whose first byte is not '{'.
            .startsWith("{")
            .contains("\"Principal\": \"*\"")
            .contains("s3:GetObject")
            .contains("arn:aws:s3:::fashionstore/*");
    }

    @Test
    void createsBucketWhenHeadBucketReturns404() {
        when(s3.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(S3Exception.builder().statusCode(404).message("404").build());

        S3Config.ensurePublicBucket(s3, "fashionstore");

        verify(s3).createBucket(any(CreateBucketRequest.class));
        verify(s3).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }

    @Test
    void keepsExistingBucketAndRefreshesPolicy() {
        S3Config.ensurePublicBucket(s3, "fashionstore");

        verify(s3, never()).createBucket(any(CreateBucketRequest.class));
        verify(s3).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }

    @Test
    void warnsInsteadOfFailingWhenS3Unreachable() {
        when(s3.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(SdkClientException.create("connection refused"));

        assertThatCode(() -> S3Config.ensurePublicBucket(s3, "fashionstore"))
            .doesNotThrowAnyException();
        verify(s3, never()).createBucket(any(CreateBucketRequest.class));
        verify(s3, never()).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }
}
