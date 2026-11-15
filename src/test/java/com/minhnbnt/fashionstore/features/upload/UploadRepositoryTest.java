package com.minhnbnt.fashionstore.features.upload;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.model.Upload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UploadRepositoryTest {

    @Autowired
    private UploadRepository uploads;

    @Test
    void roundtripsUploadAndReloadsAsNotNew() {
        var key = "roundtrip/" + UUID.randomUUID() + ".png";
        var saved = uploads.save(Upload.create(key, "image/png"));

        assertThat(saved.getId()).isNotNull();

        var loaded = uploads.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getS3Key()).isEqualTo(key);
        assertThat(loaded.getContentType()).isEqualTo("image/png");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.isNew()).isFalse();
    }

    @Test
    void findsByS3Key() {
        var key = "lookup/" + UUID.randomUUID() + ".webp";
        var saved = uploads.save(Upload.create(key, "image/webp"));

        var found = uploads.findByS3Key(key).orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getContentType()).isEqualTo("image/webp");
        assertThat(uploads.findByS3Key("lookup/missing-" + UUID.randomUUID() + ".webp")).isEmpty();
    }

    @Test
    void rejectsDuplicateS3Key() {
        var key = "unique/" + UUID.randomUUID() + ".jpg";
        uploads.save(Upload.create(key, "image/jpeg"));

        assertThatThrownBy(() -> uploads.save(Upload.create(key, "image/jpeg")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
