package com.minhnbnt.fashionstore.model;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SiteBannerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Test
    void createTrimsAndDropsBlankOptionals() {
        var imageId = UUID.randomUUID();
        var banner = SiteBanner.create(
            BannerPlacement.HOME_CAROUSEL, "  Sale thu  ", imageId, null, " ", 1, true, null, null, ADMIN_ID
        );

        assertThat(banner.getTitle()).isEqualTo("Sale thu");
        assertThat(banner.getImageUploadId()).isEqualTo(imageId);
        assertThat(banner.getMobileImageUploadId()).isNull();
        assertThat(banner.getLinkUrl()).isNull();
        assertThat(banner.getUpdatedBy()).isEqualTo(ADMIN_ID);
        assertThat(banner.isNew()).isTrue();
    }

    @Test
    void endMustComeAfterStart() {
        var start = Instant.parse("2026-10-20T00:00:00Z");

        assertThatThrownBy(() -> SiteBanner.create(
            BannerPlacement.HOME_STRIP, "Sale", UUID.randomUUID(), null, "/search", 0, true, start, start, ADMIN_ID
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateReplacesEveryField() {
        var banner = SiteBanner.create(
            BannerPlacement.HOME_CAROUSEL, "Sale", UUID.randomUUID(), null, null, 0, true, null, null, ADMIN_ID
        );
        var start = Instant.parse("2026-10-20T00:00:00Z");
        var end = start.plus(Duration.ofDays(3));
        var editor = UUID.randomUUID();
        var newImageId = UUID.randomUUID();
        var newMobileImageId = UUID.randomUUID();

        banner.update(
            BannerPlacement.HOME_STRIP, "Tết", newImageId, newMobileImageId, "/search?q=ao", 5, false, start, end, editor
        );

        assertThat(banner.getPlacement()).isEqualTo(BannerPlacement.HOME_STRIP);
        assertThat(banner.getImageUploadId()).isEqualTo(newImageId);
        assertThat(banner.getMobileImageUploadId()).isEqualTo(newMobileImageId);
        assertThat(banner.getLinkUrl()).isEqualTo("/search?q=ao");
        assertThat(banner.getSortOrder()).isEqualTo(5);
        assertThat(banner.isActive()).isFalse();
        assertThat(banner.getStartsAt()).isEqualTo(start);
        assertThat(banner.getEndsAt()).isEqualTo(end);
        assertThat(banner.getUpdatedBy()).isEqualTo(editor);
    }
}
