package com.minhnbnt.fashionstore.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutBucketWebsiteRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3BucketWebsiteTest {

    @Test
    void publishesWebsiteConfigOnStartup() {
        var s3 = mock(S3Client.class);
        when(s3.putBucketWebsite(any(PutBucketWebsiteRequest.class)))
            .thenReturn(null);
        var publisher = new S3BucketWebsite(s3, "fashionstore");

        publisher.run(mock(ApplicationArguments.class));

        verify(s3).putBucketWebsite(any(PutBucketWebsiteRequest.class));
    }
}
