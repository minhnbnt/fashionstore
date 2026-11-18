# Product Q&A (1.5.3 Customer & 2.7.3 Shop) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement end-to-end Product Q&A for Customer PDP (`1.5.3`) and Seller Center (`2.7.3`), including verified-buyer answers, search/filtering, shop Q&A stats, and deduplicated real-time notifications with deep-linking (excluding violation reporting per user decision).

**Architecture:** Add a dedicated `features/qna/` domain in `fashionstore` (`V46__product_qna.sql`, `ProductQuestion`/`ProductAnswer` JDBC entities, `JdbcClient` query repository, `ProductQnaService`, `ProductQnaController`, `SellerQnaController`) reusing `OrderService.hasDeliveredAndReceivedProduct`, `ProductStockService`, `ShopAccessChecker` (`REVIEW_READ`/`REVIEW_REPLY`), `ShopAuditService`, and a new idempotent `NotificationService.createIdempotent`. Export OpenAPI -> regenerate Orval hooks (`pnpm codegen`), wire notification link safety + `QNA` Seller tab plumbing, then build focused Customer PDP (`ProductQnaSection`) and Seller Center (`SellerQnaTab`) UI components with full Vitest coverage.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Data JDBC + `JdbcClient`, PostgreSQL/Flyway, JUnit 5 / Testcontainers / MockMvc; React 19, TypeScript, Tailwind CSS v4, TanStack Query + Orval, Vitest + Testing Library.

**Spec:** [2026-11-18-product-qna-design.md](file:///d:/fashionstore/fashionstore/docs/superpowers/specs/2026-11-18-product-qna-design.md)

## Global Constraints

- Follow `fashionstore/docs/code-guide.md`: **no ternary (`? :`)** in Java or TS/TSX; NullAway non-null by default (`@Nullable` only when truly optional); Springdoc `@Tag`/`@Operation`/`@ApiResponse`/`@Parameter`/`@Schema` on all endpoints & DTOs; backend exception messages in English; entity Lombok allowlist (`@Getter`, `@AllArgsConstructor(onConstructor_ = @PersistenceCreator)` — no `@Data`/`@Setter`/`@Builder`).
- Follow `fashionstore/docs/structure.md`: `features/qna/` uses 3-layer split (`controller/`, `repository/`, service at domain root) and never injects repositories of other domains (call `ProductStockService`, `OrderService`, `ShopService`, `UserService`, `NotificationService`, `ShopAuditService`).
- Follow `fashionstore-ui/AGENTS.md` & `docs/design-system.md`: `pnpm` only; no ternary (`? :`); `@phosphor-icons/react` icons; shared `Modal.tsx` and `ConfirmDialog.tsx`; Vietnamese UI copy; light + dark theme tokens.
- **No violation reporting:** Do NOT add report endpoints, DB report columns, or report buttons anywhere in Customer or Seller Q&A.
- Configurable content limit defaults to `1..1000` characters (`app.qna.max-length=1000`); trim before checking; reject blank/whitespace-only (`400 Bad Request`).
- Verified buyer condition strictly uses `OrderService.hasDeliveredAndReceivedProduct(userId, productId)`.

## Review Focus

1. **`notificationLinks.ts` `#...` regex corrupting Q&A deep-links:** `getTargetNotificationLink` appends `&orderId=<token>` when `/shop` notifications contain `#...` in title/body (e.g. a customer asking `"Có màu #black không?"`). Must skip `orderId` injection for `tab=qna` or `QNA_*` notifications. Pinned by `notificationLinks.test.ts` (Task 4).
2. **Duplicate Q&A notifications on retry/sync (`2.7.3 §2.5`):** Calling `NotificationService.createIdempotent` twice with the same `(userId, type, refId)` must not insert a second row or push a second WebSocket event. Pinned by `ProductQnaServiceTest` (Task 2).
3. **`ILIKE` wildcard injection & whitespace-only content (`QNA-C05`, `1.5.3 §2.4`):** Searching `%` or `_` must match literally (escaped with `\`), and submitting `"   \n\t  "` or `1001` chars must fail with `400` on Backend and show inline error on Frontend without firing a mutation. Pinned by `ProductQnaServiceTest` (Task 2), `ProductQnaControllerTest` (Task 3), and `ProductQnaSection.test.tsx` (Task 5).
4. **Deleting last Shop answer & cross-role/closed-state isolation (`QNA-S04`, `QNA-S05`):** Deleting the last `SHOP` answer on a non-closed `ANSWERED` question (even with verified-buyer answers present) must revert status to `UNANSWERED`; editing/deleting a `VERIFIED_BUYER` answer via Seller endpoints must return `403`; posting/editing/deleting on a `CLOSED` question must return `409`. Pinned by `ProductQnaServiceTest` (Task 2).
5. **CSKH Staff without `PRODUCT_READ` & deep-link outside page 0 (`2.7.3 §2.2`, `§2.5`):** CSKH staff (`REVIEW_READ`, `REVIEW_REPLY`, no `PRODUCT_READ`) must populate the product filter from `GET /api/seller/qna/products` (never calling `useListProducts`), and navigating to `/shop?shopId=...&tab=qna&questionId=<id>` must open that question via `GET /api/seller/qna/questions/{questionId}` even if it is not on page 0. Pinned by `ProductQnaServiceTest` (Task 2) and `SellerQnaTab.test.tsx` (Task 6).

---

### Task 1: Migration `V46`, Entities, Repositories, DTOs & Idempotent Notifications (`fashionstore`)

**Files:**
- Create: `fashionstore/src/main/resources/db/migration/V46__product_qna.sql`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/ProductQuestionStatus.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/ProductAnswerRole.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/ProductQuestionSort.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/ProductQuestion.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/ProductAnswer.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/qna/repository/ProductQuestionRepository.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/qna/repository/ProductAnswerRepository.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/qna/repository/ProductQnaQueryRepository.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/qna/CreateQuestionRequest.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/qna/UpdateQuestionRequest.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/qna/CreateAnswerRequest.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/qna/UpdateAnswerRequest.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/qna/ProductAnswerResponse.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/qna/ProductQuestionResponse.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/qna/ProductQuestionPageResponse.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/qna/SellerQuestionResponse.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/qna/SellerQnaProductOptionResponse.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/qna/SellerQnaStatsResponse.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationRepository.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationService.java`

**Interfaces:**
- Produces:
  - `ProductQuestion.create(String code, UUID productId, UUID shopId, UUID askerId, String askerDisplayName, String content) -> ProductQuestion`
  - `ProductQuestion.editContent(String content) -> void`
  - `ProductQuestion.markAnswered() -> void`, `markUnanswered() -> void`, `close(UUID closedBy) -> void`, `reopen(boolean hasShopAnswer) -> void`
  - `ProductAnswer.createShopAnswer(UUID questionId, UUID authorId, String shopName, String content) -> ProductAnswer`
  - `ProductAnswer.createVerifiedBuyerAnswer(UUID questionId, UUID authorId, String buyerDisplayName, String content) -> ProductAnswer`
  - `ProductAnswer.editContent(String content) -> void`
  - `ProductQuestionRepository`: `findByIdAndProductId(UUID id, UUID productId)`
  - `ProductAnswerRepository`: `findByQuestionIdOrderByCreatedAtAscIdAsc(UUID questionId)`, `findByQuestionIdInOrderByCreatedAtAscIdAsc(Collection<UUID> questionIds)`, `countByQuestionId(UUID questionId)`, `countByQuestionIdAndAuthorRole(UUID questionId, ProductAnswerRole role)`
  - `ProductQnaQueryRepository`:
    - `record SellerQuestionRow(ProductQuestion question, String productName, long answerCount, long shopAnswerCount)`
    - `record SellerQnaStatsRow(long totalQuestions, long unansweredCount, long answeredCount, long closedCount, long shopRepliedCount)`
    - `findProductQuestions(UUID productId, @Nullable String keyword, int page, int size) -> List<ProductQuestion>`
    - `countProductQuestions(UUID productId, @Nullable String keyword) -> long`
    - `findShopQuestions(UUID shopId, @Nullable ProductQuestionStatus status, @Nullable UUID productId, @Nullable Instant from, @Nullable Instant to, @Nullable String keyword, ProductQuestionSort sort, int page, int size) -> List<SellerQuestionRow>`
    - `countShopQuestions(UUID shopId, @Nullable ProductQuestionStatus status, @Nullable UUID productId, @Nullable Instant from, @Nullable Instant to, @Nullable String keyword) -> long`
    - `findShopQuestionRowById(UUID questionId) -> Optional<SellerQuestionRow>`
    - `findShopQnaProductOptions(UUID shopId) -> List<SellerQnaProductOptionResponse>`
    - `summarizeShopQna(UUID shopId) -> SellerQnaStatsRow`
  - `NotificationService.createIdempotent(Collection<UUID> recipients, NotificationCategory category, String type, String title, String body, @Nullable String link, UUID refId) -> void`

- [ ] **Step 1: Create `V46__product_qna.sql`**
  Write the exact DDL from Spec Section 3: `product_question` (with composite FK `(shop_id, product_id) REFERENCES product (shop_id, id) ON DELETE CASCADE` and length check `1..1000`), `product_answer` (with `chk_product_answer_verified_role` enforcing `QNA-C03`), all four lookup indexes, and `uq_notification_qna_dedup` partial unique index on `user_notification (user_id, type, ref_id)`.

- [ ] **Step 2: Create enums and JDBC entities in `model/`**
  - `ProductQuestionStatus`: `UNANSWERED`, `ANSWERED`, `CLOSED`.
  - `ProductAnswerRole`: `SHOP`, `VERIFIED_BUYER`.
  - `ProductQuestionSort`: `NEWEST`, `OLDEST`.
  - `ProductQuestion` and `ProductAnswer`: implement `Persistable<UUID>` with `@Getter`, `@AllArgsConstructor(onConstructor_ = @PersistenceCreator)`, `@Transient @Getter(AccessLevel.NONE) private boolean newRecord`, static factories, and explicit state-transition methods (`markAnswered`, `markUnanswered`, `close`, `reopen`, `editContent`). No ternary operators.

- [ ] **Step 3: Create `ProductQuestionRepository`, `ProductAnswerRepository`, and `ProductQnaQueryRepository`**
  - Implement `escapeLike(String raw)` replacing `\`, `%`, `_` with `\\`, `\%`, `\_`, and append `ESCAPE '\'` in all `ILIKE` clauses.
  - `findProductQuestions` / `countProductQuestions`: match `q.product_id = :productId` and when keyword is non-blank, `(q.content ILIKE :pattern ESCAPE '\' OR EXISTS (SELECT 1 FROM product_answer a WHERE a.question_id = q.id AND a.content ILIKE :pattern ESCAPE '\'))`. Order by `q.created_at DESC, q.id DESC`.
  - `findShopQuestions` / `countShopQuestions`: join `product p ON p.id = q.product_id`, filter by `q.shop_id = :shopId`, optional `q.status`, `q.product_id`, `q.created_at >= :from`, `q.created_at < :to`, and optional keyword matching `p.name`, `p.slug`, `p.id::text`, `q.code`, `q.asker_display_name`, `q.content`, or `EXISTS (SELECT 1 FROM product_variant v WHERE v.product_id = p.id AND v.sku ILIKE :pattern ESCAPE '\')`. Sort by `created_at DESC, id DESC` (`NEWEST`) or `created_at ASC, id ASC` (`OLDEST`).
  - `findShopQnaProductOptions`: `SELECT p.id, p.name, COUNT(q.id) AS question_count FROM product p LEFT JOIN product_question q ON q.product_id = p.id WHERE p.shop_id = :shopId AND (p.status <> 'DELETED' OR q.id IS NOT NULL) GROUP BY p.id, p.name ORDER BY question_count DESC, p.name ASC`.
  - `summarizeShopQna`: count total, `UNANSWERED`, `ANSWERED`, `CLOSED`, and `COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM product_answer a WHERE a.question_id = q.id AND a.author_role = 'SHOP')) AS shop_replied_count`.

- [ ] **Step 4: Add `insertIfAbsent` to `NotificationRepository` and `createIdempotent` to `NotificationService`**
  - `NotificationRepository.insertIfAbsent(...) -> Optional<NotificationRow>` using `INSERT INTO user_notification (...) VALUES (...) ON CONFLICT DO NOTHING RETURNING ...` via `.query(NotificationRepository::toRow).optional()`.
  - `NotificationService.createIdempotent(...)`: loops recipients, calls `insertIfAbsent`, and calls `publisher.publish(recipientId, toResponse(row))` ONLY when `Optional` is present.

- [ ] **Step 5: Create all request/response DTO records in `dto/qna/`**
  - Request DTOs (`CreateQuestionRequest`, `UpdateQuestionRequest`, `CreateAnswerRequest`, `UpdateAnswerRequest`): `@NotBlank @Size(max = 1000) String content` with `@Schema`.
  - `ProductAnswerResponse(UUID id, UUID questionId, String authorDisplayName, ProductAnswerRole authorRole, boolean verifiedBuyer, String content, boolean mine, boolean canEdit, boolean canDelete, @Nullable Instant editedAt, Instant createdAt, Instant updatedAt)`.
  - `ProductQuestionResponse(UUID id, String code, UUID productId, String askerDisplayName, String content, ProductQuestionStatus status, boolean mine, boolean canEdit, boolean canDelete, @Nullable Instant closedAt, @Nullable Instant editedAt, Instant createdAt, Instant updatedAt, List<ProductAnswerResponse> answers)`.
  - `ProductQuestionPageResponse(List<ProductQuestionResponse> items, long total, int page, int size, boolean canAnswerAsBuyer, int maxContentLength)`.
  - `SellerQuestionResponse(UUID id, String code, UUID productId, String productName, @Nullable String productImageUrl, String askerDisplayName, String content, ProductQuestionStatus status, long answerCount, long shopAnswerCount, @Nullable Instant closedAt, @Nullable Instant editedAt, Instant createdAt, Instant updatedAt, List<ProductAnswerResponse> answers)`.
  - `SellerQnaProductOptionResponse(UUID id, String name, long questionCount)`.
  - `SellerQnaStatsResponse(long totalQuestions, long unansweredCount, long answeredCount, long closedCount, double responseRate)`.

- [ ] **Step 6: Verify compilation**
  Run: `.\gradlew.bat compileJava` in `d:\fashionstore\fashionstore`
  Expected: `BUILD SUCCESSFUL` with zero ErrorProne/NullAway errors.

- [ ] **Step 7: Commit**
  ```bash
  git add src/main/resources/db/migration/V46__product_qna.sql src/main/java/com/minhnbnt/fashionstore/model/Product*.java src/main/java/com/minhnbnt/fashionstore/features/qna/ src/main/java/com/minhnbnt/fashionstore/dto/qna/ src/main/java/com/minhnbnt/fashionstore/features/notification/
  git commit -m "feat(qna): add schema, domain models, repositories, DTOs, and idempotent notifications"
  ```

---

### Task 2: `ProductQnaService` & Full Integration Test Suite (`fashionstore`)

**Files:**
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/qna/ProductQnaService.java`
- Create: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/qna/ProductQnaServiceTest.java`

**Interfaces:**
- Consumes: `ProductQuestionRepository`, `ProductAnswerRepository`, `ProductQnaQueryRepository`, `ProductStockService`, `OrderService`, `ShopService`, `UserService`, `ShopAccessChecker`, `ShopAuditService`, `NotificationService`.
- Produces on `ProductQnaService`:
  - `listForProduct(UUID productId, @Nullable UUID currentUserId, @Nullable String keyword, int page, int size) -> ProductQuestionPageResponse`
  - `createQuestion(UUID askerId, UUID productId, CreateQuestionRequest request) -> ProductQuestionResponse`
  - `updateQuestion(UUID askerId, UUID productId, UUID questionId, UpdateQuestionRequest request) -> ProductQuestionResponse`
  - `deleteQuestion(UUID askerId, UUID productId, UUID questionId) -> void`
  - `createBuyerAnswer(UUID buyerId, UUID productId, UUID questionId, CreateAnswerRequest request) -> ProductAnswerResponse`
  - `updateBuyerAnswer(UUID buyerId, UUID productId, UUID questionId, UUID answerId, UpdateAnswerRequest request) -> ProductAnswerResponse`
  - `deleteBuyerAnswer(UUID buyerId, UUID productId, UUID questionId, UUID answerId) -> void`
  - `listForShop(UUID userId, UUID shopId, @Nullable ProductQuestionStatus status, @Nullable UUID productId, @Nullable LocalDate from, @Nullable LocalDate to, @Nullable String keyword, ProductQuestionSort sort, int page, int size) -> PageResponse<SellerQuestionResponse>`
  - `getShopQuestion(UUID userId, UUID questionId) -> SellerQuestionResponse`
  - `listShopQnaProducts(UUID userId, UUID shopId) -> List<SellerQnaProductOptionResponse>`
  - `shopQnaStats(UUID userId, UUID shopId) -> SellerQnaStatsResponse`
  - `createShopAnswer(UUID userId, UUID questionId, CreateAnswerRequest request) -> SellerQuestionResponse`
  - `updateShopAnswer(UUID userId, UUID answerId, UpdateAnswerRequest request) -> ProductAnswerResponse`
  - `deleteShopAnswer(UUID userId, UUID answerId) -> void`
  - `closeQuestion(UUID userId, UUID questionId) -> SellerQuestionResponse`
  - `reopenQuestion(UUID userId, UUID questionId) -> SellerQuestionResponse`

- [ ] **Step 1: Write failing integration tests in `ProductQnaServiceTest.java`**
  Annotate with `@Import(TestcontainersConfiguration.class)` and `@SpringBootTest`. Cover all business rules and Review Focus items:
  - `customerAsksQuestionAndNotifiesShopIdempotently`: creates question (`UNANSWERED`, `QA-*` code), asserts `QNA_NEW_QUESTION` (`SELLING`) notification with title `"Sản phẩm có câu hỏi mới"` and link `"/shop?shopId=...&tab=qna&questionId=..."`; calling `notifications.createIdempotent(...)` again with same `(userId, "QNA_NEW_QUESTION", questionId)` leaves notification count at 1.
  - `rejectsBlankWhitespaceAndOverLengthContent`: `"   \t\n "` and 1001-char string throw `400 BAD_REQUEST`; inactive/hidden product throws `404 NOT_FOUND`.
  - `listsQuestionsNewestFirstSearchesQuestionAndAnswerAndEscapesWildcards`: searches matching question text, matching answer text, ignores other products, escapes `%` and `_` literally, paginates cleanly, returns `canAnswerAsBuyer=true` for verified buyer and `false` for non-buyer/guest.
  - `askerCanEditAndDeleteOnlyWhileUnansweredAndNonOwnerForbidden`: owner edits/deletes when 0 answers; non-owner gets `403`; after any answer (Shop or Buyer) is posted, edit/delete returns `409`.
  - `verifiedBuyerAnswerFlowAndAuthorization`: buyer with `DELIVERED + received_at` posts answer (`VERIFIED_BUYER`, `verifiedBuyer=true`), asker gets `QNA_NEW_ANSWER` (`ACTIVITY`) notification with link `"/product/{productId}?questionId={questionId}#product-qna"`, replying to own question sends no self-notification; non-buyer and `DELIVERED`-without-receipt buyer get `403`; buyer edits/deletes own answer; another user gets `403`; posting/editing/deleting on `CLOSED` question returns `409`.
  - `sellerFiltersSearchesSortsAndStatsWithCskhStaff`: CSKH staff (`REVIEW_READ`, `REVIEW_REPLY`, no `PRODUCT_READ`) filters by status/product/VN date range (`from > to` throws `400`), searches by product name, variant SKU, question code, asker name, and content, sorts `NEWEST` & `OLDEST`, loads `listShopQnaProducts`, fetches `getShopQuestion`, and verifies `shopQnaStats` counts & `responseRate` (including closed question that had a Shop answer).
  - `sellerRepliesFollowsUpEditsDeletesAndClosesReopens`: Shop reply transitions `UNANSWERED -> ANSWERED`, sends `QNA_NEW_ANSWER` to asker, allows second follow-up Shop reply, edits Shop answer, deletes Shop answers — deleting last Shop answer on non-closed question (even with a buyer answer present) reverts status to `UNANSWERED`; attempting to edit/delete a `VERIFIED_BUYER` answer throws `403`; cross-shop member gets `403`; `close` sets `CLOSED` and `reopen` restores `ANSWERED` or `UNANSWERED`; `shopAudits` records `QNA_ANSWER_CREATE`, `QNA_ANSWER_UPDATE`, `QNA_ANSWER_DELETE`, `QNA_QUESTION_CLOSE`, `QNA_QUESTION_REOPEN`.

- [ ] **Step 2: Run test to verify it fails**
  Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.qna.ProductQnaServiceTest"` in `d:\fashionstore\fashionstore`
  Expected: Compilation/test failure because `ProductQnaService` does not exist yet.

- [ ] **Step 3: Implement `ProductQnaService.java`**
  - Use an explicit constructor (instead of `@RequiredArgsConstructor`, so `@Value` is preserved cleanly) injecting `@Value("${app.qna.max-length:1000}") int maxContentLength` alongside all required service/repository dependencies.
  - Generate question code `"QA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT)`.
  - Trim & validate content (`1..maxContentLength`), returning `400 Bad Request` on blank or over-limit.
  - Enforce `productStock.requireActiveProduct(productId)` on all customer/public operations.
  - Batch load answers via `answers.findByQuestionIdInOrderByCreatedAtAscIdAsc(questionIds)` and product thumbnails via `productStock.findProductDisplayInfos(productIds)` to avoid N+1 queries.
  - Round `responseRate` to 1 decimal place (`BigDecimal.valueOf(shopReplied * 100.0 / total).setScale(1, RoundingMode.HALF_UP).doubleValue()`, or `0.0` when `total == 0`).
  - Use `NotificationService.createIdempotent` for `QNA_NEW_QUESTION` (`SELLING`, `refId = question.getId()`) and `QNA_NEW_ANSWER` (`ACTIVITY`, `refId = answer.getId()`). Delete notifications via `notifications.deleteByRefId(id)` when a question or answer is deleted.
  - Use VN timezone (`ZoneId.of("Asia/Ho_Chi_Minh")`) when converting `LocalDate from` (`startOfDay`) and `LocalDate to` (`plusDays(1).atStartOfDay()`).

- [ ] **Step 4: Run integration tests to verify they pass**
  Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.qna.ProductQnaServiceTest"` in `d:\fashionstore\fashionstore`
  Expected: All tests in `ProductQnaServiceTest` PASS.

- [ ] **Step 5: Commit**
  ```bash
  git add src/main/java/com/minhnbnt/fashionstore/features/qna/ src/test/java/com/minhnbnt/fashionstore/features/qna/ProductQnaServiceTest.java
  git commit -m "feat(qna): implement ProductQnaService with customer and seller integration tests"
  ```

---

### Task 3: REST Controllers, Slice Tests & OpenAPI Codegen (`fashionstore` + `fashionstore-ui`)

**Files:**
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/qna/controller/ProductQnaController.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/qna/controller/SellerQnaController.java`
- Create: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/qna/ProductQnaControllerTest.java`
- Create: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/qna/SellerQnaControllerTest.java`
- Modify: `fashionstore-ui/docs/openapi.json`

**Interfaces:**
- Produces HTTP endpoints (all annotated with Springdoc `@Tag`, `@Operation`, `@ApiResponse`, `@Parameter`, `@SecurityRequirement(name = "bearerAuth")`):
  - `ProductQnaController` (`@Tag(name = "Product Q&A")`, `@RequestMapping("/api/products/{productId}/questions")`):
    - `GET ""` (`listProductQuestions`) — public, optional JWT
    - `POST ""` (`createProductQuestion`) — `201`, JWT
    - `PUT "/{questionId}"` (`updateProductQuestion`) — `200`, JWT
    - `DELETE "/{questionId}"` (`deleteProductQuestion`) — `204`, JWT
    - `POST "/{questionId}/answers"` (`createProductAnswer`) — `201`, JWT
    - `PUT "/{questionId}/answers/{answerId}"` (`updateProductAnswer`) — `200`, JWT
    - `DELETE "/{questionId}/answers/{answerId}"` (`deleteProductAnswer`) — `204`, JWT
  - `SellerQnaController` (`@Tag(name = "Seller - Q&A")`, `@RequestMapping("/api/seller/qna")`):
    - `GET "/questions"` (`listSellerQuestions`) — `200`, JWT
    - `GET "/questions/{questionId}"` (`getSellerQuestion`) — `200`, JWT
    - `GET "/products"` (`listSellerQnaProducts`) — `200`, JWT
    - `GET "/stats"` (`getSellerQnaStats`) — `200`, JWT
    - `POST "/questions/{questionId}/answers"` (`createSellerAnswer`) — `201`, JWT
    - `PUT "/answers/{answerId}"` (`updateSellerAnswer`) — `200`, JWT
    - `DELETE "/answers/{answerId}"` (`deleteSellerAnswer`) — `204`, JWT
    - `PATCH "/questions/{questionId}/close"` (`closeSellerQuestion`) — `200`, JWT
    - `PATCH "/questions/{questionId}/reopen"` (`reopenSellerQuestion`) — `200`, JWT

- [ ] **Step 1: Write failing `@WebMvcTest` slice tests**
  - `ProductQnaControllerTest` (`@WebMvcTest(ProductQnaController.class)` + `@Import(SecurityConfig.class)`):
    - `GET /api/products/{id}/questions` succeeds `200` without JWT and passes `currentUserId` when JWT is present.
    - `POST /api/products/{id}/questions` without JWT returns `401`; with JWT + valid body returns `201`; with blank body or 1001-char body returns `400`.
    - `PUT`/`DELETE` question and `POST`/`PUT`/`DELETE` answer enforce JWT (`401` when missing) and propagate `200`/`201`/`204`/`403`/`409`.
  - `SellerQnaControllerTest` (`@WebMvcTest(SellerQnaController.class)` + `@Import(SecurityConfig.class)`):
    - Missing JWT returns `401` on all endpoints.
    - `GET /api/seller/qna/questions` forwards `shopId`, `status`, `productId`, `from`, `to`, `q`, `sort`, `page`, `size` and returns `200`.
    - `GET /questions/{id}`, `GET /products`, `GET /stats`, `POST /questions/{id}/answers` (`201`, `400` on blank), `PUT /answers/{id}` (`200`), `DELETE /answers/{id}` (`204`), `PATCH /questions/{id}/close` (`200`), `PATCH /questions/{id}/reopen` (`200`).

- [ ] **Step 2: Run slice tests to verify they fail**
  Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.qna.*ControllerTest"` in `d:\fashionstore\fashionstore`
  Expected: Failure (controllers not defined).

- [ ] **Step 3: Implement `ProductQnaController.java` and `SellerQnaController.java`**
  Follow `ReviewController` and `SellerReviewController` patterns verbatim (explicit `operationId` on every `@Operation` so Orval generates clean, predictable hook names such as `useListProductQuestions`, `useCreateProductQuestion`, `useListSellerQuestions`, `useGetSellerQnaStats`, `useCreateSellerAnswer`, etc.).

- [ ] **Step 4: Run full backend test suite**
  Run: `.\gradlew.bat test` in `d:\fashionstore\fashionstore`
  Expected: Entire backend suite PASSES (`BUILD SUCCESSFUL`).

- [ ] **Step 5: Refresh `fashionstore-ui/docs/openapi.json` and run `pnpm codegen`**
  Start backend locally (or use existing Springdoc OpenAPI export task/endpoint `GET http://localhost:8080/v3/api-docs`), save to `d:\fashionstore\fashionstore-ui\docs\openapi.json`, then run `pnpm codegen` in `d:\fashionstore\fashionstore-ui`.
  Expected: Orval generates `generated/endpoints/product-q-a/` and `generated/endpoints/seller-q-a/` (verify exact generated paths before Tasks 4–6).

- [ ] **Step 6: Commit**
  ```bash
  # In fashionstore
  git add src/main/java/com/minhnbnt/fashionstore/features/qna/controller/ src/test/java/com/minhnbnt/fashionstore/features/qna/*ControllerTest.java
  git commit -m "feat(qna): add ProductQnaController and SellerQnaController with slice tests"
  # In fashionstore-ui
  git add docs/openapi.json
  git commit -m "chore(api): update OpenAPI spec with Product Q&A endpoints"
  ```

---

### Task 4: Notification Deep-Link Safety & Seller Center Navigation/Permissions Plumbing (`fashionstore-ui`)

**Files:**
- Modify: `fashionstore-ui/src/lib/content/notificationLinks.ts`
- Create: `fashionstore-ui/src/lib/content/notificationLinks.test.ts`
- Modify: `fashionstore-ui/src/types/seller/navigation.ts`
- Modify: `fashionstore-ui/src/lib/seller/sellerNavigation.ts`
- Modify: `fashionstore-ui/src/lib/seller/sellerPermissions.ts`
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/SellerLayout.tsx`
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/index.ts`
- Create: `fashionstore-ui/src/components/seller-dashboard-tab/qna/SellerQnaTab.tsx` (minimal typed stub so Task 4 compiles independently; replaced in Task 6)
- Modify: `fashionstore-ui/src/pages/shop/ShopDashboard.tsx`
- Modify: `fashionstore-ui/src/pages/shop/ShopDashboardNavigation.test.tsx`

**Interfaces:**
- Consumes: Orval-generated `useGetSellerQnaStats` from Task 3 (for `unansweredQnaCount` sidebar badge).
- Produces:
  - `SellerTabId` includes `'QNA'`.
  - `canSeeTab('QNA', permissions, isOwner)` returns `true` for `isOwner || permissions.includes('REVIEW_READ') || permissions.includes('REVIEW_REPLY')`.
  - `SellerLayoutProps` accepts `unansweredQnaCount: number` and renders sidebar menu item `"Hỏi đáp sản phẩm"` under `CHĂM SÓC KHÁCH HÀNG` with `Question` icon and badge.
  - `ShopDashboard` renders `<SellerQnaTab key={shopId} shopId={shopId} initialQuestionId={searchParams.get('questionId') ?? undefined} />` when `activeTab === 'QNA'`.
  - `getTargetNotificationLink` preserves `/shop?shopId=...&tab=qna&questionId=...` untouched even when body contains `#...`.
  - `realtimeInvalidationKeys` invalidates `['/api/seller/qna']` and `['/api/products']` for `QNA_` notification types.

- [ ] **Step 1: Write failing tests in `notificationLinks.test.ts` and update `ShopDashboardNavigation.test.tsx`**
  - In `notificationLinks.test.ts`:
    - Assert `getTargetNotificationLink({ type: 'QNA_NEW_QUESTION', title: 'Sản phẩm có câu hỏi mới', body: 'Áo có màu #black không?', link: '/shop?shopId=s1&tab=qna&questionId=q1' })` returns `'/shop?shopId=s1&tab=qna&questionId=q1'` (NO `&orderId=black` appended).
    - Assert `getTargetNotificationLink({ type: 'ORDER_PLACED', title: 'Đơn mới #ORD-123', body: '...', link: '/shop?shopId=s1&tab=orders' })` still appends `&orderId=ORD-123`.
    - Assert `realtimeInvalidationKeys('QNA_NEW_QUESTION')` includes `['/api/seller/qna']` and `['/api/products']`.
  - In `ShopDashboardNavigation.test.tsx`:
    - Assert `parseSellerTab('qna') === 'QNA'`, `sellerTabParam('QNA') === 'qna'`, `canSeeTab('QNA', ['REVIEW_READ'], false) === true`, `canSeeTab('QNA', ['ORDER_READ'], false) === false`.

- [ ] **Step 2: Run tests to verify they fail**
  Run: `pnpm test src/lib/content/notificationLinks.test.ts src/pages/shop/ShopDashboardNavigation.test.tsx` in `d:\fashionstore\fashionstore-ui`
  Expected: FAIL.

- [ ] **Step 3: Implement plumbing changes across the listed files**
  - `notificationLinks.ts`: guard `orderId` injection so it skips when `link.includes('tab=qna')` or `item.type?.startsWith('QNA_')`; add `if (notificationType.startsWith('QNA_'))` invalidation keys.
  - `types/seller/navigation.ts`: add `'QNA'` to `SellerTabId`.
  - `sellerNavigation.ts`: add `'QNA'` to `SELLER_TAB_IDS` and `DEFAULT_TAB_PREFERENCE` (right after `'REVIEWS'`).
  - `sellerPermissions.ts`: add `QNA` branch in `canSeeTab`, plus Vietnamese labels & badge meta for `QNA_ANSWER_CREATE`, `QNA_ANSWER_UPDATE`, `QNA_ANSWER_DELETE`, `QNA_QUESTION_CLOSE`, `QNA_QUESTION_REOPEN`, and target types `PRODUCT_QUESTION`, `PRODUCT_ANSWER`.
  - `SellerLayout.tsx`: add `unansweredQnaCount: number` prop, `getPageMeta('QNA')`, `buildBreadcrumbs('QNA')`, and sidebar item `{ id: 'QNA', label: 'Hỏi đáp sản phẩm', icon: Question, badgeCount: unansweredQnaCount, badgeColor: 'bg-blue-700' }` under `CHĂM SÓC KHÁCH HÀNG`.
  - `components/seller-dashboard-tab/index.ts`: export lazy `SellerQnaTab`, backed by a minimal typed stub in `qna/SellerQnaTab.tsx` (`export default function SellerQnaTab(_props: { shopId: string; initialQuestionId?: string }) { return null }`) so Task 4 compiles and tests cleanly before Task 6.
  - `ShopDashboard.tsx`: add `canSeeQna` + `useGetSellerQnaStats` query for `unansweredQnaCount`, pass to `SellerLayout`, and mount `<SellerQnaTab key={shopId} shopId={shopId} initialQuestionId={searchParams.get('questionId') ?? undefined} />`.

- [ ] **Step 4: Run tests to verify they pass**
  Run: `pnpm test src/lib/content/notificationLinks.test.ts src/pages/shop/ShopDashboardNavigation.test.tsx` in `d:\fashionstore\fashionstore-ui`
  Expected: PASS.

- [ ] **Step 5: Commit**
  ```bash
  git add src/lib/content/notificationLinks.ts src/lib/content/notificationLinks.test.ts src/types/seller/navigation.ts src/lib/seller/sellerNavigation.ts src/lib/seller/sellerPermissions.ts src/components/seller-dashboard-tab/ src/pages/shop/ShopDashboard.tsx src/pages/shop/ShopDashboardNavigation.test.tsx
  git commit -m "feat(qna): wire Q&A notification deep-links and Seller Center navigation"
  ```

---

### Task 5: Customer PDP Q&A Section (`fashionstore-ui`)

**Files:**
- Create: `fashionstore-ui/src/components/pdp/qna/ProductQnaSection.tsx`
- Create: `fashionstore-ui/src/components/pdp/qna/ProductQnaItem.tsx`
- Create: `fashionstore-ui/src/components/pdp/qna/ProductQnaSection.test.tsx`
- Modify: `fashionstore-ui/src/pages/ProductDetail.tsx`

**Interfaces:**
- Consumes: Orval hooks from Task 3 (`useListProductQuestions`, `useCreateProductQuestion`, `useUpdateProductQuestion`, `useDeleteProductQuestion`, `useCreateProductAnswer`, `useUpdateProductAnswer`, `useDeleteProductAnswer`), `useSessionQuery`, `ConfirmDialog`, `UserAvatar`, `notify`.
- Produces:
  - `ProductQnaSection({ productId }: { productId: string })` rendered directly below `<ProductTabs product={product} />` in `ProductDetail.tsx` with `id="product-qna"`.

- [ ] **Step 1: Write failing tests in `ProductQnaSection.test.tsx`**
  Cover all 1.5.3 requirements and Review Focus items:
  - Renders below-reviews Q&A heading, total count, newest-first question list, `"Chưa có câu trả lời"` state on unanswered questions, and **no violation report button** anywhere.
  - Visually distinguishes `SHOP` answers (`"Shop"` badge) from `VERIFIED_BUYER` answers (`"Đã mua hàng"` badge).
  - Guest user sees login prompt CTA to `/login` instead of Ask Question form; logged-in user sees textarea with `0 / 1000` counter.
  - Submitting whitespace-only text shows inline validation error and does NOT fire mutation or open confirm dialog; submitting valid question opens `ConfirmDialog`, and confirming triggers `createProductQuestion`, clears input, and shows success toast in `onSuccess`.
  - Keyword search updates query param `q`, shows empty-search message when `items` is empty with active keyword, and Clear button resets keyword to `""`.
  - Expand/collapse toggle hides and reveals answers per question; `?questionId=<id>` in URL auto-expands and highlights that question.
  - Verified buyer (`canAnswerAsBuyer: true`) sees inline answer composer on non-closed questions (hidden on `CLOSED` questions and hidden when `canAnswerAsBuyer: false`) and can submit a reply.
  - Question owner sees Edit/Delete ONLY when `canEdit`/`canDelete` is true (0 answers); non-owner sees neither. Answer owner sees Edit/Delete ONLY on their own answers.
  - Pagination "Tải thêm câu hỏi" button appears when loaded count < `total` and fetches next page.

- [ ] **Step 2: Run test to verify it fails**
  Run: `pnpm test src/components/pdp/qna/ProductQnaSection.test.tsx` in `d:\fashionstore\fashionstore-ui`
  Expected: FAIL (`ProductQnaSection` not found).

- [ ] **Step 3: Implement `ProductQnaItem.tsx`, `ProductQnaSection.tsx`, and wire into `ProductDetail.tsx`**
  - No ternary (`? :`) anywhere; use helper functions and `&&`.
  - Debounce or submit-on-enter/button keyword search with clear (`X`) icon button.
  - Accumulate pages cleanly when clicking `"Tải thêm câu hỏi"` (resetting to page 0 when `productId` or search keyword changes).
  - Use `ConfirmDialog` for question submission confirmation (`1.5.3 §2.2`), question deletion, and answer deletion.
  - In `ProductDetail.tsx`, mount `<ProductQnaSection productId={productId} />` immediately after `<ProductTabs product={product} />` and before `<RelatedProducts ... />`.

- [ ] **Step 4: Run tests to verify they pass**
  Run: `pnpm test src/components/pdp/qna/ProductQnaSection.test.tsx` in `d:\fashionstore\fashionstore-ui`
  Expected: PASS.

- [ ] **Step 5: Commit**
  ```bash
  git add src/components/pdp/qna/ src/pages/ProductDetail.tsx
  git commit -m "feat(qna): add Customer PDP Q&A section below product reviews"
  ```

---

### Task 6: Seller Center Q&A Management Tab & Full Verification (`fashionstore-ui`)

**Files:**
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/qna/SellerQnaTab.tsx` (replace stub from Task 4 with full implementation)
- Create: `fashionstore-ui/src/components/seller-dashboard-tab/qna/SellerQnaDetailModal.tsx`
- Create: `fashionstore-ui/src/components/seller-dashboard-tab/qna/SellerQnaTab.test.tsx`

**Interfaces:**
- Consumes: Orval hooks from Task 3 (`useListSellerQuestions`, `useGetSellerQuestion`, `useListSellerQnaProducts`, `useGetSellerQnaStats`, `useCreateSellerAnswer`, `useUpdateSellerAnswer`, `useDeleteSellerAnswer`, `useCloseSellerQuestion`, `useReopenSellerQuestion`), `useSellerPermissions`, `Modal`, `ConfirmDialog`, `EmptyState`, `Pagination`, `notify`, `urlOrPlaceholder`, `fallbackImageOnError`.
- Produces:
  - `SellerQnaTab({ shopId, initialQuestionId }: { shopId: string; initialQuestionId?: string })`.

- [ ] **Step 1: Write failing tests in `SellerQnaTab.test.tsx`**
  Cover all 2.7.3 requirements and Review Focus items:
  - Renders 5 stats summary cards: Tổng số câu hỏi, Chưa trả lời, Đã trả lời, Đã đóng, Tỷ lệ phản hồi (`%`).
  - CSKH staff with `REVIEW_READ` + `REVIEW_REPLY` (no `PRODUCT_READ`) loads product filter options from `useListSellerQnaProducts` (assert `useListProducts` is never imported/called).
  - Filters by status (`UNANSWERED` / `ANSWERED` / `CLOSED`), product, date range (`from > to` shows inline validation error and blocks query), keyword search + clear, and sort (`NEWEST` / `OLDEST`).
  - Renders each question row with `code`, product thumbnail + name, asker name, question content, created time, answer count, and status badge (`Chưa trả lời` / `Đã trả lời` / `Đã đóng`).
  - Opens `SellerQnaDetailModal` automatically when `initialQuestionId` is passed (fetching via `useGetSellerQuestion` even if not in current page results).
  - Inside detail modal:
    - Shop user with `REVIEW_REPLY` can post an initial answer AND a follow-up answer (`onSuccess` shows toast and invalidates list/detail/stats; blank reply shows inline error without calling mutation).
    - Edit & Delete buttons render ONLY on `SHOP` answers — NEVER on customer question and NEVER on `VERIFIED_BUYER` answers.
    - Close button opens `ConfirmDialog` and calls `closeSellerQuestion`; when `CLOSED`, reply composer is hidden and Reopen button calls `reopenSellerQuestion`.
    - Read-only staff (`REVIEW_READ` without `REVIEW_REPLY`) sees detail thread cleanly with all reply/edit/delete/close controls hidden.

- [ ] **Step 2: Run test to verify it fails**
  Run: `pnpm test src/components/seller-dashboard-tab/qna/SellerQnaTab.test.tsx` in `d:\fashionstore\fashionstore-ui`
  Expected: FAIL.

- [ ] **Step 3: Implement `SellerQnaDetailModal.tsx` and `SellerQnaTab.tsx`**
  - No ternary (`? :`) anywhere; use helper functions and `&&`.
  - In `SellerQnaTab`: clean responsive layout matching `SellerReviewsTab.tsx` (stat cards grid -> filter/search card -> question list with product thumbnail + `code` + status badge + open detail button -> `Pagination`).
  - Clear `questionId` query param (or track dismissed state) cleanly when closing a deep-linked modal so reopening/switching filters works smoothly.
  - In `SellerQnaDetailModal`: uses shared `Modal.tsx` shell, displays product link (`/product/{productId}`), full chronological answer list visually distinguishing `SHOP` vs `VERIFIED_BUYER` (`"Đã mua hàng"`), inline edit/delete (`ConfirmDialog`) strictly for `SHOP` answers, reply textarea (`0 / 1000` counter + trim check), and Close (`ConfirmDialog`) / Reopen actions.

- [ ] **Step 4: Run full frontend & backend verification gates**
  In `d:\fashionstore\fashionstore-ui`:
  - `pnpm format:check` (run `pnpm format` if needed, then re-verify)
  - `pnpm lint` (must be 0 errors, 0 warnings)
  - `pnpm test` (all Vitest tests green)
  - `pnpm build` (`orval && tsc -b && vp build` green)
  In `d:\fashionstore\fashionstore`:
  - `.\gradlew.bat test` (all JUnit + Testcontainers tests green)

- [ ] **Step 5: Commit**
  ```bash
  git add src/components/seller-dashboard-tab/qna/
  git commit -m "feat(qna): add Seller Center Q&A management tab and detail modal"
  ```
