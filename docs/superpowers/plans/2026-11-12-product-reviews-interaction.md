# Implementation Plan: Product Reviews & Interaction (1.5)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement feature **1.5. Đánh giá & Tương tác** (per-order per-product completed-order reviews with 1-5 stars, >=10 char comment, <=5 images & <=1 video <=30s, 5 quick tags, optional anonymity, buyer variant snapshot, 1-decimal summary + 5-1 star distribution, "Hữu ích" toggle + highlight sorting/filtering, shop reply display, and real Seller Center review summary/media).

**Architecture:** Extend the existing `review` aggregate directly with `order_id`, `variant_label`, `image_keys TEXT[]`, `video_key`, `tags TEXT[]`, `is_anonymous`, `helpful_count` plus a `review_helpful (review_id, user_id)` join table in Flyway `V36__review_enhancements.sql`. Enforce `ReviewService -> OrderService` dependency direction, regenerate OpenAPI & Orval client hooks (`OpenApiExportTest` -> `pnpm codegen`), and update buyer Order/PDP and Seller Center React 19 UI components following `docs/code-guide.md` (no ternary `? :` in Java or TSX/TS, NullAway non-null, `Modal.tsx` shell, light/dark tokens).

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Data JDBC, PostgreSQL/Flyway, AWS S3 + `VideoMetadataService`, JUnit 5/MockMvc; React 19, TypeScript, Tailwind CSS v4, TanStack Query + Orval, Vitest + Testing Library.

**Design Spec:** [2026-11-12-product-reviews-interaction-design.md](file:///d:/fashionstore/fashionstore/docs/superpowers/specs/2026-11-12-product-reviews-interaction-design.md)

---

### Task 1: Database Migration & Domain Models/Repositories (`fashionstore`)

**Files:**
- Create: `fashionstore/src/main/resources/db/migration/V36__review_enhancements.sql`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/ReviewTag.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/ReviewSort.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/ReviewHelpful.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/Review.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/repository/ReviewHelpfulRepository.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/repository/ReviewRepository.java`

- [ ] **Step 1: Write `V36__review_enhancements.sql`**
  Add columns (`order_id`, `variant_label`, `image_keys`, `video_key`, `tags`, `is_anonymous`, `helpful_count`), backfill pre-V36 `order_id` & `variant_label` from latest completed matching order (`DELIVERED` + `received_at IS NOT NULL`), drop `uq_review_one_per_buyer`, create `uq_review_order_product` partial unique index and helpful/created sort indexes, and create `review_helpful` table + `idx_review_helpful_user`.

- [ ] **Step 2: Create `ReviewTag.java`, `ReviewSort.java`, and `ReviewHelpful.java`**
  - `ReviewTag`: public constants / `Set<String>` (`"Chất lượng tốt"`, `"Đúng mô tả"`, `"Giao hàng nhanh"`, `"Đóng gói cẩn thận"`, `"Size đúng chuẩn"`) and static `String[] normalizeAndValidate(@Nullable List<String> rawTags)` (deduplicates preserving order, rejects unknown/blank tags with `400 Bad Request`).
  - `ReviewSort`: enum `{ HELPFUL, NEWEST }`.
  - `ReviewHelpful`: `@Table("review_helpful")` value/entity class (`reviewId`, `userId`, `createdAt`) or JDBC operations via `ReviewHelpfulRepository` (check Spring Data JDBC composite key note: since Spring Data JDBC entities need a single `@Id`, use custom `@Query` SQL inserts/deletes/selects on `ReviewHelpfulRepository` or `JdbcClient` / `@Modifying @Query` returning `int`).
  Wait — important Spring Data JDBC detail: `review_helpful` has a composite PK `(review_id, user_id)` and no single surrogate UUID `@Id`! Using `@Modifying @Query` SQL methods in a repository interface (extending `Repository<Review, UUID>` or a small `@Repository` using `JdbcTemplate`/`NamedParameterJdbcTemplate`) avoids composite-PK issues in Spring Data JDBC completely! Let's use a `@Repository` class `ReviewHelpfulRepository` backed by `NamedParameterJdbcTemplate` (atomic `INSERT INTO review_helpful (review_id, user_id) VALUES (:reviewId, :userId) ON CONFLICT DO NOTHING`, `DELETE FROM review_helpful WHERE review_id = :reviewId AND user_id = :userId`, `SELECT review_id FROM review_helpful WHERE user_id = :userId AND review_id IN (:reviewIds)`). Clean, zero-allocation, race-safe!

- [ ] **Step 3: Update `Review.java`**
  Add `@Nullable UUID orderId`, `@Nullable String variantLabel`, `String[] imageKeys`, `@Nullable String videoKey`, `String[] tags`, `boolean isAnonymous`, `int helpfulCount`. Update `Review.create(...)` and `Review.update(...)` preserving `editedAt != null` -> `409 Conflict`. Follow `docs/code-guide.md` (no ternary, `@PersistenceCreator`, NullAway annotations).

- [ ] **Step 4: Update `ReviewRepository.java`**
  Add:
  - `Optional<Review> findByOrderIdAndProductId(UUID orderId, UUID productId);`
  - `Optional<Review> findByIdAndBuyerId(UUID id, UUID buyerId);`
  - Queries for filtered product reviews (`ORDER BY helpful_count DESC, created_at DESC` vs `ORDER BY created_at DESC`, optional `stars`, optional `hasMedia` checking `cardinality(image_keys) > 0 OR video_key IS NOT NULL`).
  - Atomic counter queries:
    - `@Modifying @Query("UPDATE review SET helpful_count = helpful_count + 1 WHERE id = :reviewId") void incrementHelpfulCount(UUID reviewId);`
    - `@Modifying @Query("UPDATE review SET helpful_count = GREATEST(helpful_count - 1, 0) WHERE id = :reviewId") void decrementHelpfulCount(UUID reviewId);`
  - Aggregate SQL projection record `ReviewSummaryRow(long totalCount, @Nullable Double averageRating, long fiveStarCount, long fourStarCount, long threeStarCount, long twoStarCount, long oneStarCount, long withMediaCount)` for product (`WHERE product_id = :productId`) and shop (`JOIN product p ON p.id = r.product_id WHERE p.shop_id = :shopId`).

- [ ] **Step 5: Compile check backend models/repositories**
  Run `./gradlew compileJava` in `d:\fashionstore\fashionstore` (or proceed directly into Task 2 and compile together).

---

### Task 2: `OrderService` Verification Helper, Review DTOs, `ReviewService`, & `DemoEngagementSeeder` (`fashionstore`)

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/review/ReviewSummaryResponse.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/review/ReviewHelpfulResponse.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/review/CreateReviewRequest.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/review/UpdateReviewRequest.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/review/ReviewResponse.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/review/SellerReviewResponse.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/ReviewService.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/infrastructure/seed/DemoEngagementSeeder.java`

- [ ] **Step 1: Add `resolveCompletedOrderProductVariant` to `OrderService.java`**
  - Lookup `orders.findByIdAndBuyerId(orderId, buyerId)` -> if empty throw `404 Not Found` (`"Order not found"`).
  - If `order.getStatus() != OrderStatus.DELIVERED || order.getReceivedAt() == null` throw `403 Forbidden` (`"Only completed orders can be reviewed"`).
  - Iterate `items.findByOrderId(orderId)`: verify at least one item has `productId.equals(item.getProductId())`; collect distinct non-blank `item.getVariantLabel()` into a `LinkedHashSet<String>`.
  - If no matching item found throw `400 Bad Request` (`"Product is not part of this order"`).
  - Return `variantLabels.isEmpty() ? null : String.join(", ", variantLabels)` (written without ternary using `if`!).

- [ ] **Step 2: Create/Update Review DTOs with Springdoc `@Schema` & Jakarta Validation**
  - `CreateReviewRequest`: `@NotNull UUID orderId`, `@Min(1) @Max(5) int stars`, `@NotBlank @Size(max = 2000) String comment`, `@Nullable @Size(max = 5) List<@NotBlank String> imageKeys`, `@Nullable String videoKey`, `@Nullable @Size(max = 5) List<@NotBlank String> tags`, `boolean anonymous`.
  - `UpdateReviewRequest`: `@Min(1) @Max(5) int stars`, `@NotBlank @Size(max = 2000) String comment`, `@Nullable @Size(max = 5) List<@NotBlank String> imageKeys`, `@Nullable String videoKey`, `@Nullable @Size(max = 5) List<@NotBlank String> tags`, `boolean anonymous`.
  - `ReviewResponse`: `id`, `productId`, `@Nullable UUID orderId`, `buyerDisplayName`, `@Nullable String variantLabel`, `stars`, `@Nullable String comment`, `List<String> imageKeys`, `@Nullable String videoKey`, `List<String> tags`, `boolean anonymous`, `boolean mine`, `int helpfulCount`, `boolean helpfulByMe`, `@Nullable String sellerReply`, `@Nullable Instant repliedAt`, `boolean edited`, `@Nullable Instant editedAt`, `createdAt`, `updatedAt`.
  - `SellerReviewResponse`: add `@Nullable UUID orderId`, `@Nullable String variantLabel`, `List<String> imageKeys`, `@Nullable String videoKey`, `List<String> tags`, `boolean anonymous`, `int helpfulCount`.
  - `ReviewSummaryResponse`: `long reviewCount`, `@Nullable Double averageRating`, `long fiveStarCount`, `long fourStarCount`, `long threeStarCount`, `long twoStarCount`, `long oneStarCount`, `long withMediaCount`.
  - `ReviewHelpfulResponse`: `UUID reviewId`, `int helpfulCount`, `boolean helpfulByMe`.

- [ ] **Step 3: Implement `ReviewService.java` enhancements**
  - Inject `ReviewHelpfulRepository` and `VideoMetadataService`.
  - Add pure helper `maskDisplayName(String displayName)`: trims name; preserves first character and appends `"***"` + last character (if trimmed length > 2), or first char + `"***"` if length <= 2.
  - Add private validation helpers:
    - `normalizeComment(String comment)`: trim, check `length >= 10 && length <= 2000`, else `400 Bad Request`.
    - `normalizeImageKeys(@Nullable List<String> imageKeys)`: trim each, reject blank or size > 5 with `400 Bad Request`, return `String[]`.
    - `normalizeVideoKey(@Nullable String videoKey)`: trim empty -> `null`, otherwise call `videoMetadataService.validateVideoDuration(key, 30.0)`.
    - `ReviewTag.normalizeAndValidate(tags)`.
  - Update `createReview`: verify product exists (active via `findProductById` / `requireVisible`), verify `reviews.findByOrderIdAndProductId(request.orderId(), productId).isEmpty()` else `409 Conflict`, call `orderService.resolveCompletedOrderProductVariant(...)`, validate comment/images/video/tags, save review.
  - Add `updateReviewById(UUID buyerId, UUID reviewId, UpdateReviewRequest request)` (keep/verify lookup `findByIdAndBuyerId` -> `404`), validate fields, skip S3 video validation only if `videoKey` is unchanged from `review.getVideoKey()`, call `review.update(...)` (`409` if already edited once), save and return with `mine = true` and `helpfulByMe` looked up.
  - Update `getReviews(UUID productId, @Nullable UUID currentUserId, ReviewSort sort, @Nullable Integer stars, boolean hasMedia)`: validate `stars == null || (stars >= 1 && stars <= 5)` else `400`, query sorted/filtered list, batch fetch `helpfulByMe` set when `currentUserId != null`, map `buyerDisplayName` masked if `isAnonymous`, compute `mine = currentUserId != null && review.getBuyerId().equals(currentUserId)`.
  - Add `reviewSummary(UUID productId)` and `shopReviewSummary(UUID userId, UUID shopId)`.
  - Add `toggleHelpful(UUID userId, UUID reviewId)`: find review (`404`), check active product (`404` via `requireVisible`), reject own review (`409 Conflict`), insert/delete in `review_helpful` and increment/decrement `helpful_count`, re-read updated `helpful_count` safely (`Math.max(0, updatedCount)`) and return `ReviewHelpfulResponse`.
  - Update `listMine(UUID buyerId)`: return raw `buyerDisplayName` even when `anonymous = true`, `mine = true`, lookup `helpfulByMe`.
  - Update `listForShop`: mask `buyerDisplayName` when `review.isAnonymous()` is true.

- [ ] **Step 4: Update `DemoEngagementSeeder.java`**
  Pass `orders` into `seedReviews(...)`, supply `orders.aoThunLinh()`, `orders.aoThunTram()`, `orders.aoThunKhang()`, `orders.chanVayLinh()`, `orders.quanJeansAn()`, include sample tags & 1 anonymous review while keeping existing review comments >= 10 chars.

---

### Task 3: Controllers, SecurityConfig, Backend Tests & OpenAPI Export (`fashionstore`)

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/controller/ReviewController.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/controller/MyReviewController.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/controller/SellerReviewController.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/config/SecurityConfig.java`
- Modify: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/review/ReviewServiceTest.java`
- Modify: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/review/ReviewControllerTest.java`

- [ ] **Step 1: Update `ReviewController.java`, `MyReviewController.java`, `SellerReviewController.java`, `SecurityConfig.java`**
  - `ReviewController`:
    - `GET /api/products/{productId}/reviews`: accept `@AuthenticationPrincipal @Nullable Jwt jwt`, `@RequestParam(defaultValue = "HELPFUL") ReviewSort sort`, `@RequestParam(required = false) Integer stars`, `@RequestParam(defaultValue = "false") boolean hasMedia`.
    - `GET /api/products/{productId}/reviews/summary`: returns `ReviewSummaryResponse`.
    - `POST /api/products/{productId}/reviews`: accepts updated `CreateReviewRequest`.
    - Remove old `PUT /api/products/{productId}/reviews` (moved cleanly to `PUT /api/reviews/{reviewId}`).
  - `MyReviewController`:
    - Add `PUT /api/reviews/{reviewId}` -> `reviews.updateReviewById(buyerId, reviewId, request)`.
    - Add `POST /api/reviews/{reviewId}/helpful` -> `reviews.toggleHelpful(userId, reviewId)`.
  - `SellerReviewController`:
    - Add `GET /api/seller/reviews/summary` (`@RequestParam UUID shopId`) -> `reviews.shopReviewSummary(userId, shopId)`.
  - `SecurityConfig`:
    - Add `"/api/products/*/reviews/**"` alongside `"/api/products/*/reviews"` in `PUBLIC_GET_PATHS`.

- [ ] **Step 2: Write/Update Unit & WebMvc Tests (`ReviewServiceTest.java` & `ReviewControllerTest.java`)**
  Cover:
  - Per-`(orderId, productId)` uniqueness (two orders for same product succeed; duplicate `(orderId, productId)` throws `409`; same order different products succeed; same order multi-quantity/multi-variant joins distinct `variantLabel` cleanly).
  - Order eligibility (`404` missing/wrong buyer, `403` `DELIVERED` with `receivedAt == null`, `400` product not in order).
  - Validation: comment `< 10` trimmed -> `400`, unknown tag -> `400`, `> 5` images -> `400`, video > 30s (`VideoMetadataService`) -> `400`.
  - Anonymous masking on public/seller vs raw name on `listMine`; `mine` flag computation.
  - Edit once by `reviewId` (`PUT /api/reviews/{reviewId}`) preserving `orderId` & `variantLabel`, `409` on second edit.
  - Helpful toggle (`POST /api/reviews/{reviewId}/helpful`): increment/decrement, `409` on self-like, `404` on inactive product/missing review.
  - Public filtering (`stars`, `hasMedia`, `sort = HELPFUL | NEWEST`) and summary totals + 5..1 breakdown; seller shop summary.

- [ ] **Step 3: Run Backend Tests & Export OpenAPI JSON**
  Run in `d:\fashionstore\fashionstore`:
  `./gradlew test --tests "com.minhnbnt.fashionstore.features.review.*" --tests "com.minhnbnt.fashionstore.OpenApiExportTest"`
  (Wait / run non-codegen prep in parallel if needed, then verify exit code 0.)

---

### Task 4: OpenAPI Codegen & Frontend Shared Helpers (`fashionstore-ui`)

**Files:**
- Modify: `fashionstore-ui/src/lib/api/uploadImage.ts`
- Modify: `fashionstore-ui/src/lib/api/apiAdapters.ts`
- Create: `fashionstore-ui/src/lib/review/reviewConstants.ts`
- Modify: `fashionstore-ui/src/lib/shop/ratingSummary.ts`
- Modify: `fashionstore-ui/src/types/seller/reviews.ts`
- Modify: `fashionstore-ui/src/lib/seller/sellerAdapters.ts`

- [ ] **Step 1: Run `pnpm codegen` in `d:\fashionstore\fashionstore-ui`**
  Verify new Orval endpoints/types (`ReviewSummaryResponse`, `ReviewHelpfulResponse`, `ReviewResponse` with `orderId`/`variantLabel`/`imageKeys`/`videoKey`/`tags`/`anonymous`/`mine`/`helpfulCount`/`helpfulByMe`/`sellerReply`/`repliedAt`, `updateReview`, `toggleHelpful`, seller summary hook).

- [ ] **Step 2: Update `uploadImage.ts` and `apiAdapters.ts`**
  - `uploadVideo(file: File, maxSeconds = 60): Promise<string>`: pass `maxSeconds` into `checkVideoDuration(file, maxSeconds)`.
  - Add `ERROR_MESSAGES.review` (400, 403, 404, 409) and `ERROR_MESSAGES.reviewHelpful` (404, 409) in `src/lib/api/apiAdapters.ts`.

- [ ] **Step 3: Create `src/lib/review/reviewConstants.ts`**
  Export:
  - `REVIEW_QUICK_TAGS` (`['Chất lượng tốt', 'Đúng mô tả', 'Giao hàng nhanh', 'Đóng gói cẩn thận', 'Size đúng chuẩn'] as const`)
  - `MIN_REVIEW_COMMENT_LENGTH = 10`
  - `MAX_REVIEW_IMAGES = 5`
  - `MAX_REVIEW_VIDEO_SECONDS = 30`
  - `maskDisplayNamePreview(name: string): string` (mirrors backend `maskDisplayName` for live preview in modal)
  - `reviewKey(orderId: string | undefined, productId: string | undefined): string`

- [ ] **Step 4: Update `src/types/seller/reviews.ts`, `src/lib/seller/sellerAdapters.ts`, & `src/lib/shop/ratingSummary.ts`**
  - Extend `ReviewItem` with `variantLabel?: string`, `tags: string[]`, `videoUrl?: string`, `helpfulCount: number`, `anonymous: boolean`, `repliedAt?: string`.
  - Update `reviewItem(...)` in `sellerAdapters.ts` to populate `images` via `imageFromKey` (for each key in `review.imageKeys`), `videoUrl` via `imageUrl(review.videoKey)` when present, `variantLabel`, `tags`, `helpfulCount`, `anonymous`, `repliedAt`.

---

### Task 5: Buyer Order Review Flow (`fashionstore-ui`)

**Files:**
- Modify: `fashionstore-ui/src/components/order/ReviewModal.tsx`
- Modify: `fashionstore-ui/src/components/order/ReviewProductPickerModal.tsx`
- Modify: `fashionstore-ui/src/pages/checkout/Orders.tsx`
- Modify: `fashionstore-ui/src/pages/checkout/OrderDetail.tsx`
- Modify: `fashionstore-ui/src/components/order/OrderModals.test.tsx`
- Modify: `fashionstore-ui/src/pages/checkout/Orders.test.tsx`

- [ ] **Step 1: Upgrade `ReviewModal.tsx`**
  - Accept `orderId: string`, `variantLabel?: string | null`, and optional `existingReview` (`ReviewResponse` containing `id`, `stars`, `comment`, `imageKeys`, `videoKey`, `tags`, `anonymous`, `edited`, `variantLabel`).
  - Render purchased `variantLabel` pill when present.
  - Render interactive 1–5 star selector with descriptive labels (`Rất tệ` .. `Tuyệt vời`).
  - Render 5 quick tag chips (toggle multi-select).
  - Require trimmed `comment.length >= 10` (disable/show clear inline helper + live counter `N/10 ký tự tối thiểu`).
  - Add real receipt media uploader: up to 5 images (`uploadImage`) with remove buttons + up to 1 video <= 30s (`uploadVideo(file, 30)`) with `<video controls>` preview + remove button + error banner on invalid duration/size.
  - Add checkbox `"Ẩn danh tên khi đánh giá"` with live masked preview (`maskDisplayNamePreview(displayName)`).
  - Call `POST /api/products/{productId}/reviews` (`orderId` included) when creating, or `PUT /api/reviews/{reviewId}` when editing (`!existingReview.edited`).

- [ ] **Step 2: Update `ReviewProductPickerModal.tsx`, `Orders.tsx`, `OrderDetail.tsx`**
  - Gate review eligibility strictly on `order.status === 'DELIVERED' && Boolean(order.receivedAt)`.
  - Deduplicate `OrderItem`s by `productId` within an order, joining distinct non-blank `variantLabel` values with `", "`.
  - Index `myReviews` primarily by `${review.orderId}:${review.productId}`, and build a legacy fallback `Map<productId, ReviewResponse>` strictly for reviews where `review.orderId` is missing/null.
  - Pass `orderId` and combined `variantLabel` into `ReviewProductPickerModal` and `ReviewModal`; invalidate `/api/reviews/mine`, `/api/products/{id}/reviews`, `/api/products/{id}/reviews/summary`, and product detail queries on success.

- [ ] **Step 3: Update & run `OrderModals.test.tsx` and `Orders.test.tsx`**
  Verify comment >= 10 chars validation, tag toggles, anonymity preview, media limits, `DELIVERED && receivedAt != null` gating, multi-variant deduplication per `(orderId, productId)`, and re-reviewing the same product on a second completed order. Run `pnpm test -- src/components/order/OrderModals.test.tsx src/pages/checkout/Orders.test.tsx`.

---

### Task 6: PDP Review Summary, Highlighting, Filtering & Helpful Interaction (`fashionstore-ui`)

**Files:**
- Modify: `fashionstore-ui/src/components/pdp/ProductInfo.tsx`
- Modify: `fashionstore-ui/src/components/pdp/ProductTabs.tsx`
- Modify: `fashionstore-ui/src/components/pdp/ProductTabs.test.tsx`
- Modify: `fashionstore-ui/src/components/pdp/ProductTabs.edited.test.tsx`

- [ ] **Step 1: Format `ProductInfo.tsx` rating to 1 decimal place**
  Render `ratingAverage.toFixed(1)` (while retaining null/empty state handling when count === 0).

- [ ] **Step 2: Upgrade `ProductTabs.tsx`**
  - Style active tab buttons clearly (`mo-ta`, `thong-so`, `danh-gia`) using light/dark tokens.
  - Call `useSummary(productId)` for full un-filtered summary stats (1-decimal `averageRating`, total review count, 5→1 star bars & counts/percentages, media count).
  - Manage state `sort` (`'HELPFUL'` default | `'NEWEST'`), `starsFilter` (`undefined` | `5..1`), `hasMediaOnly` (`boolean`), and `lightboxMedia` (`{ type: 'image' | 'video'; url: string } | null`).
  - Call `useList(productId, { sort, stars: starsFilter, hasMedia: hasMediaOnly })`.
  - Render sort buttons (`Hữu ích nhất` / `Mới nhất`) + filter chips (`Tất cả`, `5★`..`1★`, `Có hình ảnh / video`).
  - Compute `maxHelpfulCount` from current list when `sort === 'HELPFUL'`; render `"Hữu ích nhất"` badge on top helpful review(s) where `(review.helpfulCount ?? 0) > 0 && review.helpfulCount === maxHelpfulCount`.
  - On each review card: render author name + avatar, stars, date, `"Đã chỉnh sửa"` badge, `"Phân loại hàng: {variantLabel}"`, quick tag pills, comment, image thumbnails & `<video>` preview (click opens `Modal.tsx` lightbox), `"Phản hồi của Shop"` block (`sellerReply` + formatted `repliedAt`), and `"Hữu ích (N)"` button (`review.mine` disables button with subtle `"Đánh giá của bạn"` label; logged-out user navigates to `/login`; logged-in other user calls `useHelpful` mutation and invalidates review list query).

- [ ] **Step 3: Update & run PDP review tests**
  Run `pnpm test -- src/components/pdp/ProductTabs.test.tsx src/components/pdp/ProductTabs.edited.test.tsx`.

---

### Task 7: Seller Center Reviews Tab (`SellerReviewsTab.tsx`) & Full Cross-Repo Verification

**Files:**
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/stats/SellerReviewsTab.tsx`
- Optional New Test: `fashionstore-ui/src/components/seller-dashboard-tab/stats/SellerReviewsTab.test.tsx`

- [ ] **Step 1: Upgrade `SellerReviewsTab.tsx`**
  - Replace hardcoded mock stats (`4.9`, `1,248 lượt đánh giá`, `92%`, `6%`, `2%`) with live `useSummary1({ shopId })` query (`1`-decimal `averageRating`, actual `reviewCount`, full 5→1 star breakdown bars).
  - On each review item row: display `variantLabel`, quick `tags`, attached customer `images` + `videoUrl` (clickable `Modal.tsx` preview), `helpfulCount`, and masked/anonymous badge indicator when `anonymous === true`.
  - Invalidate summary + list queries after reply/report mutations if appropriate.

- [ ] **Step 2: Write unit tests for `SellerReviewsTab.tsx` & run full verification suite**
  - Run backend full relevant suite (`./gradlew test` or full Gradle check in `d:\fashionstore\fashionstore`).
  - Run frontend full test suite + production typecheck/build (`pnpm test` and `pnpm build` in `d:\fashionstore\fashionstore-ui`).

---

## Plan Self-Review
- **Spec Coverage Check:** Every section in [2026-11-12-product-reviews-interaction-design.md](file:///d:/fashionstore/fashionstore/docs/superpowers/specs/2026-11-12-product-reviews-interaction-design.md) (`V36` + backfill, `ReviewTag`, `ReviewSort`, `OrderService.resolveCompletedOrderProductVariant`, all 8 backend DTO/controller endpoints including `mine` & `/api/seller/reviews/summary`, `DemoEngagementSeeder`, `uploadVideo(file, 30)`, `ERROR_MESSAGES`, `ReviewModal`, `Orders`/`OrderDetail`, `ProductTabs`/`ProductInfo`, `SellerReviewsTab`) is mapped to concrete files and test steps.
- **Code Guide Compliance Check:** Explicitly avoids composite `@Id` on `review_helpful` (uses `NamedParameterJdbcTemplate`), preserves `ReviewService -> OrderService` dependency order, keeps `/api/seller/reviews` base path backward-compatible, avoids ternary `? :`, and triggers OpenAPI -> Orval codegen before UI changes.
- **No Placeholders / Clean Type Names:** `ReviewSummaryResponse`, `ReviewHelpfulResponse`, `ReviewResponse.mine`, `SellerReviewResponse`, `maskDisplayName`, `ReviewTag.normalizeAndValidate`, `OrderService.resolveCompletedOrderProductVariant`, `ReviewHelpfulRepository` used consistently across all tasks.

