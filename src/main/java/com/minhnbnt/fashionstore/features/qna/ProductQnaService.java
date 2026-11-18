package com.minhnbnt.fashionstore.features.qna;

import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.qna.CreateAnswerRequest;
import com.minhnbnt.fashionstore.dto.qna.CreateQuestionRequest;
import com.minhnbnt.fashionstore.dto.qna.ProductAnswerResponse;
import com.minhnbnt.fashionstore.dto.qna.ProductQuestionPageResponse;
import com.minhnbnt.fashionstore.dto.qna.ProductQuestionResponse;
import com.minhnbnt.fashionstore.dto.qna.SellerQnaProductOptionResponse;
import com.minhnbnt.fashionstore.dto.qna.SellerQnaStatsResponse;
import com.minhnbnt.fashionstore.dto.qna.SellerQuestionResponse;
import com.minhnbnt.fashionstore.dto.qna.UpdateAnswerRequest;
import com.minhnbnt.fashionstore.dto.qna.UpdateQuestionRequest;
import com.minhnbnt.fashionstore.features.catalog.ProductStockService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.notification.NotificationCategory;
import com.minhnbnt.fashionstore.features.notification.NotificationService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.qna.repository.ProductAnswerRepository;
import com.minhnbnt.fashionstore.features.qna.repository.ProductQnaQueryRepository;
import com.minhnbnt.fashionstore.features.qna.repository.ProductQuestionRepository;
import com.minhnbnt.fashionstore.features.shop.ShopAuditService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.ProductAnswer;
import com.minhnbnt.fashionstore.model.ProductAnswerRole;
import com.minhnbnt.fashionstore.model.ProductQuestion;
import com.minhnbnt.fashionstore.model.ProductQuestionSort;
import com.minhnbnt.fashionstore.model.ProductQuestionStatus;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import com.minhnbnt.fashionstore.model.ShopPermission;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductQnaService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final String NOTIF_NEW_QUESTION = "QNA_NEW_QUESTION";
    public static final String NOTIF_NEW_ANSWER = "QNA_NEW_ANSWER";

    private final ProductQuestionRepository questions;
    private final ProductAnswerRepository answers;
    private final ProductQnaQueryRepository queryRepository;
    private final ProductStockService productStock;
    private final OrderService orderService;
    private final ShopService shops;
    private final UserService users;
    private final ShopAccessChecker access;
    private final ShopAuditService audit;
    private final NotificationService notifications;
    private final int maxContentLength;

    public ProductQnaService(
        ProductQuestionRepository questions,
        ProductAnswerRepository answers,
        ProductQnaQueryRepository queryRepository,
        ProductStockService productStock,
        OrderService orderService,
        ShopService shops,
        UserService users,
        ShopAccessChecker access,
        ShopAuditService audit,
        NotificationService notifications,
        @Value("${app.qna.max-length:1000}") int maxContentLength
    ) {
        this.questions = questions;
        this.answers = answers;
        this.queryRepository = queryRepository;
        this.productStock = productStock;
        this.orderService = orderService;
        this.shops = shops;
        this.users = users;
        this.access = access;
        this.audit = audit;
        this.notifications = notifications;
        this.maxContentLength = maxContentLength;
    }

    private String normalizeAndValidateContent(String raw) {
        var trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content must not be blank");
        }
        if (trimmed.length() > maxContentLength) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Content must not exceed " + maxContentLength + " characters"
            );
        }
        return trimmed;
    }

    private static String generateCode() {
        return "QA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String truncateSnippet(String raw) {
        var cleaned = raw.trim().replaceAll("\\R+", " ");
        if (cleaned.length() > 120) {
            return cleaned.substring(0, 117) + "...";
        }
        return cleaned;
    }

    @Transactional(readOnly = true)
    public ProductQuestionPageResponse listForProduct(
        @NonNull UUID productId,
        @Nullable UUID currentUserId,
        @Nullable String keyword,
        int page,
        int size
    ) {
        productStock.requireActiveProduct(productId);
        var targetPage = Math.max(0, page);
        var targetSize = Math.clamp(size, 1, 50);

        String normalizedKeyword = null;
        if (keyword != null && !keyword.isBlank()) {
            normalizedKeyword = keyword.trim();
        }

        var total = queryRepository.countProductQuestions(productId, normalizedKeyword);
        var rows = queryRepository.findProductQuestions(productId, normalizedKeyword, targetPage, targetSize);

        var questionIds = rows.stream().map(ProductQuestion::getId).toList();
        var allAnswers = answers.findByQuestionIdInOrderByCreatedAtAscIdAsc(questionIds);
        var answersByQuestionId = new LinkedHashMap<UUID, List<ProductAnswer>>();
        for (var a : allAnswers) {
            answersByQuestionId.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
        }

        boolean canAnswerAsBuyer = false;
        if (currentUserId != null && orderService.hasDeliveredAndReceivedProduct(currentUserId, productId)) {
            canAnswerAsBuyer = true;
        }

        var items = new ArrayList<ProductQuestionResponse>(rows.size());
        for (var q : rows) {
            var qAnswers = answersByQuestionId.getOrDefault(q.getId(), List.of());
            var answerResponses = new ArrayList<ProductAnswerResponse>(qAnswers.size());
            for (var a : qAnswers) {
                boolean aMine = false;
                if (currentUserId != null
                        && a.getAuthorRole() == ProductAnswerRole.VERIFIED_BUYER
                        && currentUserId.equals(a.getAuthorId())) {
                    aMine = true;
                }
                boolean aCanEdit = false;
                if (aMine && q.getStatus() != ProductQuestionStatus.CLOSED) {
                    aCanEdit = true;
                }
                boolean aCanDelete = aCanEdit;

                answerResponses.add(new ProductAnswerResponse(
                    a.getId(),
                    a.getQuestionId(),
                    a.getAuthorDisplayName(),
                    a.getAuthorRole(),
                    a.isVerifiedBuyer(),
                    a.getContent(),
                    aMine,
                    aCanEdit,
                    aCanDelete,
                    a.getEditedAt(),
                    a.getCreatedAt(),
                    a.getUpdatedAt()
                ));
            }

            boolean qMine = false;
            if (currentUserId != null && currentUserId.equals(q.getAskerId())) {
                qMine = true;
            }
            boolean qCanEdit = false;
            if (qMine && qAnswers.isEmpty() && q.getStatus() != ProductQuestionStatus.CLOSED) {
                qCanEdit = true;
            }
            boolean qCanDelete = qCanEdit;

            items.add(new ProductQuestionResponse(
                q.getId(),
                q.getCode(),
                q.getProductId(),
                q.getAskerDisplayName(),
                q.getContent(),
                q.getStatus(),
                qMine,
                qCanEdit,
                qCanDelete,
                q.getClosedAt(),
                q.getEditedAt(),
                q.getCreatedAt(),
                q.getUpdatedAt(),
                answerResponses
            ));
        }

        return new ProductQuestionPageResponse(
            items,
            total,
            targetPage,
            targetSize,
            canAnswerAsBuyer,
            maxContentLength
        );
    }

    @Transactional
    public ProductQuestionResponse createQuestion(
        @NonNull UUID askerId,
        @NonNull UUID productId,
        @NonNull CreateQuestionRequest request
    ) {
        var product = productStock.requireActiveProduct(productId);
        var content = normalizeAndValidateContent(request.content());
        var displayName = users.getById(askerId).displayName();
        var code = generateCode();

        var question = ProductQuestion.create(
            code,
            productId,
            product.getShopId(),
            askerId,
            displayName,
            content
        );
        var saved = questions.save(question);

        var recipients = shops.findActiveUserIdsByShop(product.getShopId());
        notifications.createIdempotent(
            recipients,
            NotificationCategory.SELLING,
            NOTIF_NEW_QUESTION,
            "Sản phẩm có câu hỏi mới",
            displayName + " hỏi về " + product.getName() + ": " + truncateSnippet(content),
            "/shop?shopId=" + product.getShopId() + "&tab=qna&questionId=" + saved.getId(),
            saved.getId()
        );

        return new ProductQuestionResponse(
            saved.getId(),
            saved.getCode(),
            saved.getProductId(),
            saved.getAskerDisplayName(),
            saved.getContent(),
            saved.getStatus(),
            true,
            true,
            true,
            saved.getClosedAt(),
            saved.getEditedAt(),
            saved.getCreatedAt(),
            saved.getUpdatedAt(),
            List.of()
        );
    }

    @Transactional
    public ProductQuestionResponse updateQuestion(
        @NonNull UUID askerId,
        @NonNull UUID productId,
        @NonNull UUID questionId,
        @NonNull UpdateQuestionRequest request
    ) {
        productStock.requireActiveProduct(productId);
        var question = questions.findByIdAndProductId(questionId, productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        if (!question.getAskerId().equals(askerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own question");
        }
        if (question.getStatus() == ProductQuestionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Question is closed");
        }
        if (answers.countByQuestionId(questionId) > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot edit a question that already has answers"
            );
        }

        var content = normalizeAndValidateContent(request.content());
        question.editContent(content);
        var saved = questions.save(question);

        return new ProductQuestionResponse(
            saved.getId(),
            saved.getCode(),
            saved.getProductId(),
            saved.getAskerDisplayName(),
            saved.getContent(),
            saved.getStatus(),
            true,
            true,
            true,
            saved.getClosedAt(),
            saved.getEditedAt(),
            saved.getCreatedAt(),
            saved.getUpdatedAt(),
            List.of()
        );
    }

    @Transactional
    public void deleteQuestion(
        @NonNull UUID askerId,
        @NonNull UUID productId,
        @NonNull UUID questionId
    ) {
        productStock.requireActiveProduct(productId);
        var question = questions.findByIdAndProductId(questionId, productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        if (!question.getAskerId().equals(askerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own question");
        }
        if (question.getStatus() == ProductQuestionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Question is closed");
        }
        if (answers.countByQuestionId(questionId) > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot delete a question that already has answers"
            );
        }

        questions.delete(question);
        notifications.deleteByRefId(questionId);
    }

    @Transactional
    public ProductAnswerResponse createBuyerAnswer(
        @NonNull UUID buyerId,
        @NonNull UUID productId,
        @NonNull UUID questionId,
        @NonNull CreateAnswerRequest request
    ) {
        var product = productStock.requireActiveProduct(productId);
        var question = questions.findByIdAndProductId(questionId, productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        if (question.getStatus() == ProductQuestionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Question is closed");
        }
        if (!orderService.hasDeliveredAndReceivedProduct(buyerId, productId)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only verified buyers can answer product questions"
            );
        }

        var content = normalizeAndValidateContent(request.content());
        var displayName = users.getById(buyerId).displayName();
        var answer = ProductAnswer.createVerifiedBuyerAnswer(questionId, buyerId, displayName, content);
        var saved = answers.save(answer);

        if (!question.getAskerId().equals(buyerId)) {
            notifications.createIdempotent(
                Set.of(question.getAskerId()),
                NotificationCategory.ACTIVITY,
                NOTIF_NEW_ANSWER,
                "Câu hỏi của bạn có phản hồi mới",
                displayName + " (Đã mua hàng) đã trả lời về "
                    + product.getName() + ": " + truncateSnippet(content),
                "/product/" + productId + "?questionId=" + questionId + "#product-qna",
                saved.getId()
            );
        }

        return new ProductAnswerResponse(
            saved.getId(),
            saved.getQuestionId(),
            saved.getAuthorDisplayName(),
            saved.getAuthorRole(),
            saved.isVerifiedBuyer(),
            saved.getContent(),
            true,
            true,
            true,
            saved.getEditedAt(),
            saved.getCreatedAt(),
            saved.getUpdatedAt()
        );
    }

    @Transactional
    public ProductAnswerResponse updateBuyerAnswer(
        @NonNull UUID buyerId,
        @NonNull UUID productId,
        @NonNull UUID questionId,
        @NonNull UUID answerId,
        @NonNull UpdateAnswerRequest request
    ) {
        productStock.requireActiveProduct(productId);
        var question = questions.findByIdAndProductId(questionId, productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        if (question.getStatus() == ProductQuestionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Question is closed");
        }

        var answer = answers.findById(answerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found"));
        if (!answer.getQuestionId().equals(questionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found");
        }
        if (answer.getAuthorRole() != ProductAnswerRole.VERIFIED_BUYER || !answer.getAuthorId().equals(buyerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own answer");
        }

        var content = normalizeAndValidateContent(request.content());
        answer.editContent(content);
        var saved = answers.save(answer);

        return new ProductAnswerResponse(
            saved.getId(),
            saved.getQuestionId(),
            saved.getAuthorDisplayName(),
            saved.getAuthorRole(),
            saved.isVerifiedBuyer(),
            saved.getContent(),
            true,
            true,
            true,
            saved.getEditedAt(),
            saved.getCreatedAt(),
            saved.getUpdatedAt()
        );
    }

    @Transactional
    public void deleteBuyerAnswer(
        @NonNull UUID buyerId,
        @NonNull UUID productId,
        @NonNull UUID questionId,
        @NonNull UUID answerId
    ) {
        productStock.requireActiveProduct(productId);
        var question = questions.findByIdAndProductId(questionId, productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        if (question.getStatus() == ProductQuestionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Question is closed");
        }

        var answer = answers.findById(answerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found"));
        if (!answer.getQuestionId().equals(questionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found");
        }
        if (answer.getAuthorRole() != ProductAnswerRole.VERIFIED_BUYER || !answer.getAuthorId().equals(buyerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own answer");
        }

        answers.delete(answer);
        notifications.deleteByRefId(answerId);
    }

    @Transactional(readOnly = true)
    public PageResponse<SellerQuestionResponse> listForShop(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @Nullable ProductQuestionStatus status,
        @Nullable UUID productId,
        @Nullable LocalDate from,
        @Nullable LocalDate to,
        @Nullable String keyword,
        @NonNull ProductQuestionSort sort,
        int page,
        int size
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVIEW_READ);

        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must not be after end date");
        }

        Instant fromInstant = null;
        if (from != null) {
            fromInstant = from.atStartOfDay(VN_ZONE).toInstant();
        }
        Instant toInstant = null;
        if (to != null) {
            toInstant = to.plusDays(1).atStartOfDay(VN_ZONE).toInstant();
        }

        var targetPage = Math.max(0, page);
        var targetSize = Math.clamp(size, 1, 50);

        String normalizedKeyword = null;
        if (keyword != null && !keyword.isBlank()) {
            normalizedKeyword = keyword.trim();
        }

        var total = queryRepository.countShopQuestions(
            shopId,
            status,
            productId,
            fromInstant,
            toInstant,
            normalizedKeyword
        );
        var rows = queryRepository.findShopQuestions(
            shopId,
            status,
            productId,
            fromInstant,
            toInstant,
            normalizedKeyword,
            sort,
            targetPage,
            targetSize
        );

        var questionIds = rows.stream().map(r -> r.question().getId()).toList();
        var allAnswers = answers.findByQuestionIdInOrderByCreatedAtAscIdAsc(questionIds);
        var answersByQuestionId = new LinkedHashMap<UUID, List<ProductAnswer>>();
        for (var a : allAnswers) {
            answersByQuestionId.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
        }

        var productIds = rows.stream().map(r -> r.question().getProductId()).toList();
        var displayInfos = productStock.findProductDisplayInfos(productIds);

        boolean canReply = access.permissionsOf(userId, shopId).contains(ShopPermission.REVIEW_REPLY);

        var items = new ArrayList<SellerQuestionResponse>(rows.size());
        for (var row : rows) {
            var q = row.question();
            var qAnswers = answersByQuestionId.getOrDefault(q.getId(), List.of());
            var answerResponses = new ArrayList<ProductAnswerResponse>(qAnswers.size());
            for (var a : qAnswers) {
                boolean mine = false;
                if (a.getAuthorRole() == ProductAnswerRole.SHOP) {
                    mine = true;
                }
                boolean canEdit = mine && canReply;
                boolean canDelete = canEdit;

                answerResponses.add(new ProductAnswerResponse(
                    a.getId(),
                    a.getQuestionId(),
                    a.getAuthorDisplayName(),
                    a.getAuthorRole(),
                    a.isVerifiedBuyer(),
                    a.getContent(),
                    mine,
                    canEdit,
                    canDelete,
                    a.getEditedAt(),
                    a.getCreatedAt(),
                    a.getUpdatedAt()
                ));
            }

            String productImageUrl = null;
            var display = displayInfos.get(q.getProductId());
            if (display != null) {
                productImageUrl = display.defaultImageUrl();
            }

            items.add(new SellerQuestionResponse(
                q.getId(),
                q.getCode(),
                q.getProductId(),
                row.productName(),
                productImageUrl,
                q.getAskerDisplayName(),
                q.getContent(),
                q.getStatus(),
                row.answerCount(),
                row.shopAnswerCount(),
                q.getClosedAt(),
                q.getEditedAt(),
                q.getCreatedAt(),
                q.getUpdatedAt(),
                answerResponses
            ));
        }

        return new PageResponse<>(items, total, targetPage, targetSize);
    }

    @Transactional(readOnly = true)
    public SellerQuestionResponse getShopQuestion(
        @NonNull UUID userId,
        @NonNull UUID questionId
    ) {
        var row = queryRepository.findShopQuestionRowById(questionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
        var question = row.question();
        access.requireShopPermission(userId, question.getShopId(), ShopPermission.REVIEW_READ);

        var qAnswers = answers.findByQuestionIdOrderByCreatedAtAscIdAsc(questionId);
        boolean canReply = access.permissionsOf(userId, question.getShopId()).contains(ShopPermission.REVIEW_REPLY);

        var answerResponses = new ArrayList<ProductAnswerResponse>(qAnswers.size());
        for (var a : qAnswers) {
            boolean mine = false;
            if (a.getAuthorRole() == ProductAnswerRole.SHOP) {
                mine = true;
            }
            boolean canEdit = mine && canReply;
            boolean canDelete = canEdit;

            answerResponses.add(new ProductAnswerResponse(
                a.getId(),
                a.getQuestionId(),
                a.getAuthorDisplayName(),
                a.getAuthorRole(),
                a.isVerifiedBuyer(),
                a.getContent(),
                mine,
                canEdit,
                canDelete,
                a.getEditedAt(),
                a.getCreatedAt(),
                a.getUpdatedAt()
            ));
        }

        String productImageUrl = null;
        var displayInfos = productStock.findProductDisplayInfos(List.of(question.getProductId()));
        var display = displayInfos.get(question.getProductId());
        if (display != null) {
            productImageUrl = display.defaultImageUrl();
        }

        return new SellerQuestionResponse(
            question.getId(),
            question.getCode(),
            question.getProductId(),
            row.productName(),
            productImageUrl,
            question.getAskerDisplayName(),
            question.getContent(),
            question.getStatus(),
            row.answerCount(),
            row.shopAnswerCount(),
            question.getClosedAt(),
            question.getEditedAt(),
            question.getCreatedAt(),
            question.getUpdatedAt(),
            answerResponses
        );
    }

    @Transactional(readOnly = true)
    public List<SellerQnaProductOptionResponse> listShopQnaProducts(
        @NonNull UUID userId,
        @NonNull UUID shopId
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVIEW_READ);
        return queryRepository.findShopQnaProductOptions(shopId);
    }

    @Transactional(readOnly = true)
    public SellerQnaStatsResponse shopQnaStats(
        @NonNull UUID userId,
        @NonNull UUID shopId
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVIEW_READ);
        var row = queryRepository.summarizeShopQna(shopId);

        double responseRate = 0.0;
        if (row.totalQuestions() > 0) {
            responseRate = BigDecimal.valueOf((row.shopRepliedCount() * 100.0) / row.totalQuestions())
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
        }

        return new SellerQnaStatsResponse(
            row.totalQuestions(),
            row.unansweredCount(),
            row.answeredCount(),
            row.closedCount(),
            responseRate
        );
    }

    @Transactional
    public SellerQuestionResponse createShopAnswer(
        @NonNull UUID userId,
        @NonNull UUID questionId,
        @NonNull CreateAnswerRequest request
    ) {
        var row = queryRepository.findShopQuestionRowById(questionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
        var question = row.question();
        access.requireShopPermission(userId, question.getShopId(), ShopPermission.REVIEW_REPLY);

        if (question.getStatus() == ProductQuestionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Question is closed");
        }

        var content = normalizeAndValidateContent(request.content());
        var shopName = shops.getShopName(question.getShopId());
        var answer = ProductAnswer.createShopAnswer(questionId, userId, shopName, content);
        var savedAnswer = answers.save(answer);

        if (question.getStatus() == ProductQuestionStatus.UNANSWERED) {
            question.markAnswered();
            questions.save(question);
        }

        audit.record(
            question.getShopId(),
            userId,
            "QNA_ANSWER_CREATE",
            "PRODUCT_QUESTION",
            questionId,
            "Trả lời câu hỏi " + question.getCode() + ": " + truncateSnippet(content)
        );

        if (!question.getAskerId().equals(userId)) {
            notifications.createIdempotent(
                Set.of(question.getAskerId()),
                NotificationCategory.ACTIVITY,
                NOTIF_NEW_ANSWER,
                "Câu hỏi của bạn có phản hồi mới",
                shopName + " đã trả lời câu hỏi về " + row.productName() + ": " + truncateSnippet(content),
                "/product/" + question.getProductId() + "?questionId=" + questionId + "#product-qna",
                savedAnswer.getId()
            );
        }

        return getShopQuestion(userId, questionId);
    }

    @Transactional
    public ProductAnswerResponse updateShopAnswer(
        @NonNull UUID userId,
        @NonNull UUID answerId,
        @NonNull UpdateAnswerRequest request
    ) {
        var answer = answers.findById(answerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found"));
        var question = questions.findById(answer.getQuestionId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        access.requireShopPermission(userId, question.getShopId(), ShopPermission.REVIEW_REPLY);

        if (answer.getAuthorRole() != ProductAnswerRole.SHOP) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Shop can only edit its own answers");
        }
        if (question.getStatus() == ProductQuestionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Question is closed");
        }

        var content = normalizeAndValidateContent(request.content());
        answer.editContent(content);
        var saved = answers.save(answer);

        audit.record(
            question.getShopId(),
            userId,
            "QNA_ANSWER_UPDATE",
            "PRODUCT_ANSWER",
            answerId,
            "Cập nhật câu trả lời: " + truncateSnippet(content)
        );

        return new ProductAnswerResponse(
            saved.getId(),
            saved.getQuestionId(),
            saved.getAuthorDisplayName(),
            saved.getAuthorRole(),
            saved.isVerifiedBuyer(),
            saved.getContent(),
            true,
            true,
            true,
            saved.getEditedAt(),
            saved.getCreatedAt(),
            saved.getUpdatedAt()
        );
    }

    @Transactional
    public void deleteShopAnswer(
        @NonNull UUID userId,
        @NonNull UUID answerId
    ) {
        var answer = answers.findById(answerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found"));
        var question = questions.findById(answer.getQuestionId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        access.requireShopPermission(userId, question.getShopId(), ShopPermission.REVIEW_REPLY);

        if (answer.getAuthorRole() != ProductAnswerRole.SHOP) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Shop can only delete its own answers");
        }
        if (question.getStatus() == ProductQuestionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Question is closed");
        }

        answers.delete(answer);
        notifications.deleteByRefId(answerId);

        if (answers.countByQuestionIdAndAuthorRole(question.getId(), ProductAnswerRole.SHOP) == 0) {
            question.markUnanswered();
            questions.save(question);
        }

        audit.record(
            question.getShopId(),
            userId,
            "QNA_ANSWER_DELETE",
            "PRODUCT_ANSWER",
            answerId,
            "Xóa câu trả lời"
        );
    }

    @Transactional
    public SellerQuestionResponse closeQuestion(
        @NonNull UUID userId,
        @NonNull UUID questionId
    ) {
        var row = queryRepository.findShopQuestionRowById(questionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
        var question = row.question();
        access.requireShopPermission(userId, question.getShopId(), ShopPermission.REVIEW_REPLY);

        if (question.getStatus() != ProductQuestionStatus.CLOSED) {
            question.close(userId);
            questions.save(question);
            audit.record(
                question.getShopId(),
                userId,
                "QNA_QUESTION_CLOSE",
                "PRODUCT_QUESTION",
                questionId,
                "Đóng câu hỏi " + question.getCode()
            );
        }

        return getShopQuestion(userId, questionId);
    }

    @Transactional
    public SellerQuestionResponse reopenQuestion(
        @NonNull UUID userId,
        @NonNull UUID questionId
    ) {
        var row = queryRepository.findShopQuestionRowById(questionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
        var question = row.question();
        access.requireShopPermission(userId, question.getShopId(), ShopPermission.REVIEW_REPLY);

        if (question.getStatus() == ProductQuestionStatus.CLOSED) {
            boolean hasShopAnswer = answers.countByQuestionIdAndAuthorRole(questionId, ProductAnswerRole.SHOP) > 0;
            question.reopen(hasShopAnswer);
            questions.save(question);
            audit.record(
                question.getShopId(),
                userId,
                "QNA_QUESTION_REOPEN",
                "PRODUCT_QUESTION",
                questionId,
                "Mở lại câu hỏi " + question.getCode()
            );
        }

        return getShopQuestion(userId, questionId);
    }
}
