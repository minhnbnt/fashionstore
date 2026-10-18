package com.minhnbnt.fashionstore.model;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SitePageTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    private static SitePage page() {
        return SitePage.create(
            "chinh-sach-doi-tra", " Chính sách đổi trả ", "# Đổi trả", " ", PageFooterGroup.POLICY, 1, ADMIN_ID
        );
    }

    private static void assertConflict(Throwable e) {
        assertThat(e).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createStartsAsDraft() {
        var page = page();

        assertThat(page.getStatus()).isEqualTo(PageStatus.DRAFT);
        assertThat(page.getPublishedAt()).isNull();
        assertThat(page.getTitle()).isEqualTo("Chính sách đổi trả");
        assertThat(page.getMetaDescription()).isNull();
    }

    @Test
    void publishThenUnpublish() {
        var page = page();

        page.publish(ADMIN_ID);
        assertThat(page.getStatus()).isEqualTo(PageStatus.PUBLISHED);
        assertThat(page.getPublishedAt()).isNotNull();

        page.unpublish(ADMIN_ID);
        assertThat(page.getStatus()).isEqualTo(PageStatus.DRAFT);
        assertThat(page.getPublishedAt()).isNull();
    }

    @Test
    void publishTwiceConflicts() {
        var page = page();
        page.publish(ADMIN_ID);

        assertThatThrownBy(() -> page.publish(ADMIN_ID)).satisfies(SitePageTest::assertConflict);
    }

    @Test
    void unpublishDraftConflicts() {
        assertThatThrownBy(() -> page().unpublish(ADMIN_ID)).satisfies(SitePageTest::assertConflict);
    }

    @Test
    void publishedPageCannotBeDeleted() {
        var page = page();
        page.publish(ADMIN_ID);

        assertThatThrownBy(page::requireDeletable).satisfies(SitePageTest::assertConflict);
    }

    @Test
    void draftPageCanBeDeleted() {
        page().requireDeletable();
    }
}
