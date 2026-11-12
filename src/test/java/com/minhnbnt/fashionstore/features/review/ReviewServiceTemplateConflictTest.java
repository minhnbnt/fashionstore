package com.minhnbnt.fashionstore.features.review;

import com.minhnbnt.fashionstore.dto.review.SaveReviewReplyTemplateRequest;
import com.minhnbnt.fashionstore.features.catalog.ProductStockService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.notification.NotificationService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.review.repository.ReviewHelpfulRepository;
import com.minhnbnt.fashionstore.features.review.repository.ReviewReplyTemplateRepository;
import com.minhnbnt.fashionstore.features.review.repository.ReviewRepository;
import com.minhnbnt.fashionstore.features.review.repository.SellerReviewQueryRepository;
import com.minhnbnt.fashionstore.features.shop.ShopAuditService;
import com.minhnbnt.fashionstore.infrastructure.media.VideoMetadataService;
import com.minhnbnt.fashionstore.model.ReviewReplyTemplate;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTemplateConflictTest {

    @Mock
    private ReviewReplyTemplateRepository templates;

    @Mock
    private ShopAccessChecker access;

    @Mock
    private ShopAuditService audit;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
            mock(ReviewRepository.class),
            mock(ReviewHelpfulRepository.class),
            templates,
            mock(SellerReviewQueryRepository.class),
            mock(ProductStockService.class),
            mock(OrderService.class),
            mock(UserService.class),
            access,
            audit,
            mock(NotificationService.class),
            mock(VideoMetadataService.class)
        );
    }

    @Test
    void translatesConcurrentUniqueConstraintViolationToConflict() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var request = new SaveReviewReplyTemplateRequest("Cảm ơn", "Cảm ơn bạn!");

        when(templates.countByShopId(shopId)).thenReturn(0L);
        when(templates.existsDuplicateTitle(shopId, "Cảm ơn")).thenReturn(false);
        when(templates.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> reviewService.createReplyTemplate(userId, shopId, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        var existing = ReviewReplyTemplate.create(shopId, userId, "Cũ", "Nội dung cũ");
        when(templates.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(templates.existsDuplicateTitleExcludingId(shopId, "Cảm ơn", existing.getId())).thenReturn(false);

        assertThatThrownBy(() -> reviewService.updateReplyTemplate(userId, existing.getId(), request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
