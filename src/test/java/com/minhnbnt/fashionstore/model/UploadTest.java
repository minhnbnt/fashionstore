package com.minhnbnt.fashionstore.model;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UploadTest {

    @Test
    void createGeneratesIdAndMarksNew() {
        var key = "kyc/" + UUID.randomUUID() + ".png";
        var upload = Upload.create(key, "image/png");

        assertThat(upload.getId()).isNotNull();
        assertThat(upload.getS3Key()).isEqualTo(key);
        assertThat(upload.getContentType()).isEqualTo("image/png");
        assertThat(upload.getCreatedAt()).isNotNull();
        assertThat(upload.isNew()).isTrue();
        assertThat(upload).isInstanceOf(Persistable.class);
    }

    @Test
    void createGeneratesDistinctIds() {
        var first = Upload.create("a/" + UUID.randomUUID() + ".png", "image/png");
        var second = Upload.create("b/" + UUID.randomUUID() + ".png", "image/png");

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }
}
