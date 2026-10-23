package com.minhnbnt.fashionstore.infrastructure.seed;

import com.minhnbnt.fashionstore.dto.order.ApproveReturnRequest;
import com.minhnbnt.fashionstore.dto.order.CreateReturnRequest;
import com.minhnbnt.fashionstore.dto.chat.SendMessageRequest;
import com.minhnbnt.fashionstore.dto.review.CreateReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReplyReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReportReviewRequest;
import com.minhnbnt.fashionstore.features.chat.ChatService;
import com.minhnbnt.fashionstore.features.order.ReturnRequestService;
import com.minhnbnt.fashionstore.features.review.ReviewService;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Seeds customer-facing demo activity: product reviews (with one seller reply
 * and one reported review), return requests, and chat conversations.
 * Package-private collaborator of {@link DemoSeeder}, not a Spring bean.
 * Dev-only: never enabled outside {@code app.demo-seed=true}.
 */
class DemoEngagementSeeder {

    private final ReviewService reviews;

    private final ReturnRequestService returns;

    private final ChatService chat;

    DemoEngagementSeeder(ReviewService reviews, ReturnRequestService returns, ChatService chat) {
        this.reviews = reviews;
        this.returns = returns;
        this.chat = chat;
    }

    void seed(
        UUID ownerId,
        UUID shopId,
        Map<String, UUID> productIdBySlug,
        DemoOrderSeeder.DemoBuyers buyers,
        DemoOrderSeeder.DemoOrders orders
    ) {
        seedReviews(ownerId, productIdBySlug, buyers);
        seedReturns(ownerId, buyers, orders);
        seedChat(ownerId, shopId, buyers);
    }

    private void seedReviews(UUID ownerId, Map<String, UUID> productIdBySlug, DemoOrderSeeder.DemoBuyers buyers) {
        var aoThun = product(productIdBySlug, "ao-thun-oversize");
        var quanJeans = product(productIdBySlug, "quan-jeans-ong-suong");
        var chanVay = product(productIdBySlug, "chan-vay-chu-a");

        reviews.createReview(buyers.linh(), aoThun, new CreateReviewRequest(5, "Vải dày dặn, form đẹp."));
        reviews.createReview(buyers.tram(), aoThun, new CreateReviewRequest(5, "Mặc 3 tháng chưa xù."));
        reviews.createReview(buyers.khang(), aoThun, new CreateReviewRequest(4, "Form rộng thoải mái, giao nhanh."));
        reviews.createReview(buyers.linh(), chanVay, new CreateReviewRequest(5, "Màu lên chuẩn ảnh shop."));

        var oneStar = reviews.createReview(
            buyers.an(),
            quanJeans,
            new CreateReviewRequest(1, "Màu khác ảnh, chất vải cứng hơn mình nghĩ.")
        );
        reviews.reply(ownerId, oneStar.id(), new ReplyReviewRequest(
            "Cảm ơn bạn đã góp ý ạ. Shop sẽ chụp lại ảnh sát màu thật hơn để bạn tiện so sánh."
        ));
        reviews.report(ownerId, oneStar.id(), new ReportReviewRequest(
            "Đánh giá có nội dung sai sự thật về màu sắc và chất liệu sản phẩm."
        ));
    }

    private UUID product(Map<String, UUID> productIdBySlug, String slug) {
        return Objects.requireNonNull(productIdBySlug.get(slug), "Missing demo product: " + slug);
    }

    private void seedReturns(UUID ownerId, DemoOrderSeeder.DemoBuyers buyers, DemoOrderSeeder.DemoOrders orders) {
        // REQUESTED: waiting for the seller's decision.
        returns.create(buyers.linh(), orders.chanVayLinh(), new CreateReturnRequest(
            "DAMAGED",
            "Đuôi váy bị rách đường may khi nhận hàng.",
            null
        ));

        // APPROVED then COMPLETED: full return lifecycle already finished.
        var resolved = returns.create(buyers.an(), orders.quanJeansAn(), new CreateReturnRequest(
            "NOT_AS_DESCRIBED",
            "Sản phẩm không giống mô tả về màu sắc.",
            null
        ));
        returns.approve(ownerId, resolved.id(), new ApproveReturnRequest(
            "REFUND",
            784000L,
            "Đồng ý hoàn tiền sau khi nhận lại được hàng."
        ));
        returns.complete(ownerId, resolved.id(), "Đã hoàn tiền cho khách.");
    }

    private void seedChat(UUID ownerId, UUID shopId, DemoOrderSeeder.DemoBuyers buyers) {
        // Conversation with an unread buyer message (auto reply answers it).
        var unread = chat.startConversation(buyers.linh(), shopId);
        chat.sendMessage(buyers.linh(), unread.id(), new SendMessageRequest(
            "Cho mình hỏi áo này form rộng không shop ơi?",
            null
        ));

        // Conversation already handled: buyer asks, seller replies, seller reads.
        var handled = chat.startConversation(buyers.tram(), shopId);
        chat.sendMessage(buyers.tram(), handled.id(), new SendMessageRequest(
            "Mình vừa đặt đơn túi tote, shop xác nhận giúp mình nhé.",
            null
        ));
        chat.sendMessage(ownerId, handled.id(), new SendMessageRequest(
            "Dạ shop đã nhận được đơn, hôm nay shop gói hàng gửi bạn luôn ạ.",
            null
        ));
        chat.markRead(ownerId, handled.id());
    }
}
