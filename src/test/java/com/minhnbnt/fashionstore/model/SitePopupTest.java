package com.minhnbnt.fashionstore.model;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SitePopupTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Test
    void needsImageOrTitle() {
        assertThatThrownBy(() -> SitePopup.create(
            "Popup trống", " ", "Nội dung", null, null, null,
            PopupAudience.ALL, PopupFrequency.ONCE_PER_DAY, 0, 0, true, null, null, ADMIN_ID
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void imageOnlyPopupIsFine() {
        var popup = SitePopup.create(
            "Sale 10.10", null, null, "p.webp", " ", "/search",
            PopupAudience.GUEST, PopupFrequency.ONCE, 3, 10, true, null, null, ADMIN_ID
        );

        assertThat(popup.getTitle()).isNull();
        assertThat(popup.getCtaLabel()).isNull();
        assertThat(popup.getImageKey()).isEqualTo("p.webp");
        assertThat(popup.getAudience()).isEqualTo(PopupAudience.GUEST);
        assertThat(popup.getDelaySeconds()).isEqualTo(3);
        assertThat(popup.getPriority()).isEqualTo(10);
    }
}
