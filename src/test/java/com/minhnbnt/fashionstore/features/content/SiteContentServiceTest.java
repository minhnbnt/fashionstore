package com.minhnbnt.fashionstore.features.content;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.content.BannerRequest;
import com.minhnbnt.fashionstore.dto.content.FaqRequest;
import com.minhnbnt.fashionstore.dto.content.PopupRequest;
import com.minhnbnt.fashionstore.dto.content.PublicBannerResponse;
import com.minhnbnt.fashionstore.dto.content.PublicFaqResponse;
import com.minhnbnt.fashionstore.dto.content.PublicPageLinkResponse;
import com.minhnbnt.fashionstore.dto.content.PublicPopupResponse;
import com.minhnbnt.fashionstore.dto.content.SitePageRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.model.BannerPlacement;
import com.minhnbnt.fashionstore.model.FaqCategory;
import com.minhnbnt.fashionstore.model.PageFooterGroup;
import com.minhnbnt.fashionstore.model.PageStatus;
import com.minhnbnt.fashionstore.model.PopupAudience;
import com.minhnbnt.fashionstore.model.PopupFrequency;
import com.minhnbnt.fashionstore.model.ScheduleState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB dùng chung giữa các class test cùng context, nên chỉ assert theo id của chính test, không assert cả danh sách.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SiteContentServiceTest {

    private static final Duration DAY = Duration.ofDays(1);

    @Autowired
    private BannerService banners;

    @Autowired
    private PopupService popups;

    @Autowired
    private SitePageService pages;

    @Autowired
    private FaqService faqs;

    @Autowired
    private UserService users;

    private UUID admin() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("admin-" + id + "@t.local", "Admin", null));
        return id;
    }

    private static BannerRequest banner(
        String title,
        int sortOrder,
        boolean active,
        @Nullable Instant startsAt,
        @Nullable Instant endsAt
    ) {
        return new BannerRequest(
            BannerPlacement.HOME_STRIP, title, title + ".webp", null, "/search?q=ao",
            sortOrder, active, startsAt, endsAt
        );
    }

    private static PopupRequest popup(String name, int priority, boolean active) {
        return new PopupRequest(
            name, "Giảm 50%", null, null, "Mua ngay", "/search", PopupAudience.ALL,
            PopupFrequency.ONCE_PER_DAY, 2, priority, active, null, null
        );
    }

    private static SitePageRequest page(String slug) {
        return new SitePageRequest(slug, "Trang " + slug, "# Nội dung", null, PageFooterGroup.SUPPORT, 9);
    }

    private static void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status);
    }

    @Test
    void publicBannersOnlyShowRunningOnesInOrder() {
        var adminId = admin();
        var now = Instant.now();
        var second = banners.create(adminId, banner("strip-second", 2, true, now.minus(DAY), now.plus(DAY)));
        var first = banners.create(adminId, banner("strip-first", 1, true, null, null));
        var off = banners.create(adminId, banner("strip-off", 0, false, null, null));
        var later = banners.create(adminId, banner("strip-later", 0, true, now.plus(DAY), null));
        var overStart = now.minus(DAY.multipliedBy(2));
        var over = banners.create(adminId, banner("strip-over", 0, true, overStart, now.minus(DAY)));

        var ids = banners.running(BannerPlacement.HOME_STRIP).stream().map(PublicBannerResponse::id).toList();

        assertThat(ids).containsSubsequence(first.id(), second.id());
        assertThat(ids).doesNotContain(off.id(), later.id(), over.id());
        assertThat(off.scheduleState()).isEqualTo(ScheduleState.INACTIVE);
        assertThat(later.scheduleState()).isEqualTo(ScheduleState.SCHEDULED);
        assertThat(over.scheduleState()).isEqualTo(ScheduleState.EXPIRED);
        assertThat(first.scheduleState()).isEqualTo(ScheduleState.RUNNING);
    }

    @Test
    void bannerUpdateAndDelete() {
        var adminId = admin();
        var created = banners.create(adminId, banner("strip-edit", 0, true, null, null));

        var updated = banners.update(adminId, created.id(), banner("strip-edited", 3, false, null, null));
        assertThat(updated.title()).isEqualTo("strip-edited");
        assertThat(updated.active()).isFalse();

        banners.delete(created.id());
        assertThatThrownBy(() -> banners.delete(created.id()))
            .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void runningPopupsSortedByPriority() {
        var adminId = admin();
        var low = popups.create(adminId, popup("popup-low", 1, true));
        var high = popups.create(adminId, popup("popup-high", 900, true));
        var off = popups.create(adminId, popup("popup-off", 999, false));

        var ids = popups.running().stream().map(PublicPopupResponse::id).toList();

        assertThat(ids).containsSubsequence(high.id(), low.id());
        assertThat(ids).doesNotContain(off.id());
    }

    @Test
    void pageLifecycleDraftPublishUnpublishDelete() {
        var adminId = admin();
        var created = pages.create(adminId, page("huong-dan-chon-size"));
        assertThat(created.status()).isEqualTo(PageStatus.DRAFT);
        assertThatThrownBy(() -> pages.getPublished("huong-dan-chon-size"))
            .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));

        pages.publish(adminId, created.id());
        assertThat(pages.getPublished("huong-dan-chon-size").title()).isEqualTo("Trang huong-dan-chon-size");
        assertThat(pages.publishedLinks()).extracting(PublicPageLinkResponse::slug).contains("huong-dan-chon-size");
        assertThatThrownBy(() -> pages.delete(created.id()))
            .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));

        pages.unpublish(adminId, created.id());
        pages.delete(created.id());
        assertThatThrownBy(() -> pages.getForAdmin(created.id()))
            .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void duplicateSlugConflicts() {
        var adminId = admin();
        var first = pages.create(adminId, page("slug-trung"));
        var second = pages.create(adminId, page("slug-khac"));

        assertThatThrownBy(() -> pages.create(adminId, page("slug-trung")))
            .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        assertThatThrownBy(() -> pages.update(adminId, second.id(), page("slug-trung")))
            .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));

        var same = pages.update(adminId, first.id(), page("slug-trung"));
        assertThat(same.slug()).isEqualTo("slug-trung");
    }

    @Test
    void hiddenFooterPagesStayOutOfLinks() {
        var adminId = admin();
        var request = new SitePageRequest("landing-an", "Landing", "# x", null, PageFooterGroup.NONE, 0);
        var created = pages.create(adminId, request);
        pages.publish(adminId, created.id());

        assertThat(pages.publishedLinks()).extracting(PublicPageLinkResponse::slug).doesNotContain("landing-an");
        assertThat(pages.getPublished("landing-an").title()).isEqualTo("Landing");
    }

    @Test
    void seededPoliciesArePublished() {
        assertThat(pages.publishedLinks())
            .extracting(PublicPageLinkResponse::slug)
            .contains("gioi-thieu", "chinh-sach-doi-tra", "chinh-sach-bao-mat");
        assertThat(pages.getPublished("chinh-sach-doi-tra").content()).contains("30 ngày");
    }

    @Test
    void publicFaqsHideUnpublishedAndGroupInEnumOrder() {
        var adminId = admin();
        var hidden = faqs.create(adminId, new FaqRequest(FaqCategory.ORDER, "Câu ẩn?", "Ẩn", 0, false));
        var other = faqs.create(adminId, new FaqRequest(FaqCategory.OTHER, "Câu khác?", "Khác", 0, true));
        var order = faqs.create(adminId, new FaqRequest(FaqCategory.ORDER, "Câu đặt hàng?", "Đặt", 99, true));

        var ids = faqs.published().stream().map(PublicFaqResponse::id).toList();

        assertThat(ids).containsSubsequence(order.id(), other.id());
        assertThat(ids).doesNotContain(hidden.id());
        assertThat(faqs.listForAdmin()).extracting(f -> f.id()).contains(hidden.id());
    }
}
