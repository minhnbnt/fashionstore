# Seller Reviews Management (2.7.1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement feature **2.7.1. Xem & Phản hồi đánh giá** (excluding violation report extensions): server-side filtering by stars (1–5), product, and unreplied status; CSKH-safe product options & `productName` projection; plain-text per-shop quick reply templates CRUD + quick insertion; time-series review trend stats (`day` / `week` / `month`); and reply form UX fixes.

**Architecture:** Extend `features/review` in `fashionstore` with Flyway `V37__review_reply_templates.sql`, `ReviewReplyTemplate` aggregate, `SellerReviewQueryRepository` (`JdbcClient`), and new endpoints under `/api/seller/reviews/**` guarded by `ShopPermission.REVIEW_READ` / `REVIEW_REPLY`. Export OpenAPI -> regenerate Orval hooks (`pnpm codegen`), add non-currency axis formatting (`formatAxisValue`, `maxY`) to `LineChart.tsx`, and upgrade `SellerReviewsTab.tsx` with focused sub-components and full Vitest coverage.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Data JDBC + `JdbcClient`, PostgreSQL/Flyway, JUnit 5 / Testcontainers / MockMvc; React 19, TypeScript, Tailwind CSS v4, TanStack Query + Orval, Vitest + Testing Library.

**Spec:** [2026-11-12-seller-reviews-management-design.md](file:///d:/fashionstore/fashionstore/docs/superpowers/specs/2026-11-12-seller-reviews-management-design.md)

## Global Constraints

- Follow `fashionstore/docs/code-guide.md`: **no ternary (`? :`)** in Java or TSX/TS; NullAway non-null by default (explicit `@Nullable` only when truly nullable); Springdoc `@Operation`/`@ApiResponse`/`@Parameter`/`@Schema` on all endpoints & DTOs; backend exception messages in English; no `@Setter`/`@Data`/`@Builder` on entities.
- Follow `fashionstore-ui/docs/design-system.md`: use shared `Modal.tsx` shell, semantic light/dark tokens (`dark:bg-[#11182a]`, `dark:border-white/10`), `@phosphor-icons/react` icons, Vietnamese UI copy.
- Keep `POST /api/seller/reviews/{id}/report` untouched and do not add admin review report moderation.
- Preserve `ReviewService.listForShop(UUID userId, UUID shopId, boolean onlyUnreplied)` 3-arg overload delegating to the 5-arg overload so `SellerPermissionEnforcementTest` and existing callers stay intact.
- Quick reply templates are plain text only (no `{buyerName}` or `{productName}` variable interpolation).

## Review Focus

1. **CSKH Staff without `PRODUCT_READ`:** CSKH role only has `ORDER_READ`, `REVIEW_READ`, `REVIEW_REPLY`, `CHAT_ACCESS`. Calling `useListProducts` causes `403`. Pinned by `ReviewServiceTest` (CSKH staff calls `listForShop`, `listShopReviewProducts`, `shopReviewTrend`, `listReplyTemplates`, `createReplyTemplate` without `PRODUCT_READ`) and `SellerReviewsTab.test.tsx` (asserts `useListProducts` is not required and product dropdown populates from `GET /api/seller/reviews/products`).
2. **Whitespace / Case-insensitive Duplicate Template Titles & 50-Template Cap:** `"  Cảm ơn  "` vs `"cảm ơn"` in the same shop must return `409 Conflict` cleanly without exposing raw DB constraint errors; updating a template while keeping its own title must succeed; shop #51 creation must return `409 Conflict`. Pinned by `ReviewServiceTest`.
3. **Invalid Filter & Trend Range Inputs:** `stars=0`, `stars=6`, invalid `period`, or `from > to` must return `400 Bad Request` both in `ReviewService` and `SellerReviewController`, and UI date range validation must guard invalid `from`/`to` before firing the query. Pinned by `ReviewServiceTest`, `SellerReviewControllerTest`, and `SellerReviewsTab.test.tsx`.
4. **Non-Currency Y-Axis Labels on `LineChart`:** Without `formatAxisValue`, `LineChart` renders `"5 ₫"` / `"0 ₫"` for review counts and star ratings. Pinned by `LineChart.test.tsx` and `SellerReviewsTab.test.tsx`.
5. **Reply Mutation Toast Timing & Non-Empty Template Insertion:** Reply toast must only appear in `onSuccess` (never before server confirmation or on error), blank reply must show inline error without calling `alert()`, and selecting a template when `replyContent` already has draft text must append on a new line rather than overwriting. Pinned by `SellerReviewsTab.test.tsx`.

---

### Task 1: Migration `V37`, Entity `ReviewReplyTemplate`, Repositories & Review DTOs (`fashionstore`)

**Files:**
- Create: `fashionstore/src/main/resources/db/migration/V37__review_reply_templates.sql`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/ReviewReplyTemplate.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/repository/ReviewReplyTemplateRepository.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/repository/SellerReviewQueryRepository.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/review/SellerReviewProductOptionResponse.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/review/ReviewReplyTemplateResponse.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/review/SaveReviewReplyTemplateRequest.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/review/SellerReviewTrendResponse.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/review/SellerReviewResponse.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/review/ReplyReviewRequest.java`

**Interfaces:**
- Produces:
  - `ReviewReplyTemplate.create(UUID shopId, UUID createdBy, String title, String content) -> ReviewReplyTemplate`
  - `ReviewReplyTemplate.update(String title, String content) -> void`
  - `ReviewReplyTemplateRepository`: `findByShopIdOrderByUpdatedAtDescCreatedAtDesc(UUID shopId)`, `countByShopId(UUID shopId)`, `existsDuplicateTitle(UUID shopId, String title)`, `existsDuplicateTitleExcludingId(UUID shopId, String title, UUID excludeId)`
  - `SellerReviewQueryRepository`:
    - `record SellerReviewRow(Review review, String productName)`
    - `record ReviewTrendRow(String label, long reviewCount, double averageRating)`
    - `findShopReviews(UUID shopId, boolean onlyUnreplied, @Nullable Integer stars, @Nullable UUID productId) -> List<SellerReviewRow>`
    - `findShopReviewProductOptions(UUID shopId) -> List<SellerReviewProductOptionResponse>`
    - `findShopReviewTrend(UUID shopId, String period, Instant from, Instant to) -> List<ReviewTrendRow>`
  - Updated `SellerReviewResponse(..., String productName, ...)` (insert `productName` right after `productId` or at documented position; document exact constructor order in `SellerReviewResponse.java`)

- [ ] **Step 1: Create `V37__review_reply_templates.sql`**
  Write the SQL from Spec Section 3.1: `review_reply_template` table with `chk_review_reply_template_title_len` (`1..100`), `chk_review_reply_template_content_len` (`1..2000`), unique index `uq_review_reply_template_shop_title` on `(shop_id, lower(btrim(title)))`, `idx_review_reply_template_shop_updated`, and `idx_review_created_at` on `review (created_at DESC)`.

- [ ] **Step 2: Create `ReviewReplyTemplate.java` and `ReviewReplyTemplateRepository.java`**
  - `ReviewReplyTemplate` implements `Persistable<UUID>` with `@AllArgsConstructor(onConstructor_ = @PersistenceCreator)`, `@Getter`, `@Transient @Getter(AccessLevel.NONE) private boolean newRecord`, `create(...)`, and `update(String title, String content)`.
  - `ReviewReplyTemplateRepository` extends `CrudRepository<ReviewReplyTemplate, UUID>` with `@Query` methods matching `lower(btrim(title)) = lower(:title)` for create and update duplicate checks.

- [ ] **Step 3: Create `SellerReviewQueryRepository.java`**
  - Use `JdbcClient`.
  - `findShopReviews`: `SELECT r.*, p.name AS product_name FROM review r JOIN product p ON p.id = r.product_id WHERE p.shop_id = :shopId` plus optional `AND r.seller_reply IS NULL` (when `onlyUnreplied`), `AND r.stars = :stars` (when `stars != null`), `AND r.product_id = :productId` (when `productId != null`), `ORDER BY r.created_at DESC, r.id DESC`. Map `TEXT[]` (`image_keys`, `tags`) cleanly via `rs.getArray(...)` helper returning `String[]` (empty array if null), and instantiate `Review` via its all-args constructor with `newRecord = false`.
  - `findShopReviewProductOptions`: `SELECT p.id, p.name, COUNT(r.id) AS review_count FROM product p LEFT JOIN review r ON r.product_id = p.id WHERE p.shop_id = :shopId AND (p.status <> 'DELETED' OR r.id IS NOT NULL) GROUP BY p.id, p.name ORDER BY review_count DESC, p.name ASC`.
  - `findShopReviewTrend`: build `formatExpr` (`day`, `week`, `month` in `'Asia/Ho_Chi_Minh'`, matching `ShopStatsRepository.revenueByPeriod`), `JOIN product p ON p.id = r.product_id WHERE p.shop_id = :shopId AND r.created_at >= :from AND r.created_at < :to GROUP BY period_label ORDER BY period_label ASC`, returning `COUNT(*) AS review_count` and `AVG(r.stars)::double precision AS average_rating`.

- [ ] **Step 4: Create/Update DTOs in `dto/review/`**
  - Update `SellerReviewResponse`: add `@Schema(description = "Tên sản phẩm") String productName` after `productId`.
  - Update `ReplyReviewRequest`: add `@Size(max = 2000)`.
  - Create `SellerReviewProductOptionResponse(UUID id, String name, long reviewCount)`, `ReviewReplyTemplateResponse(UUID id, UUID shopId, String title, String content, Instant createdAt, Instant updatedAt)`, `SaveReviewReplyTemplateRequest(@NotBlank @Size(max = 100) String title, @NotBlank @Size(max = 2000) String content)`, and `SellerReviewTrendResponse(String period, List<Point> points)` with nested `Point(String label, long reviewCount, double averageRating)`.

- [ ] **Step 5: Run `./gradlew compileJava` in `d:\fashionstore\fashionstore`**
  Expected: `BUILD SUCCESSFUL` with zero NullAway/ErrorProne errors on new classes (note: `ReviewService.toSellerResponse` update happens in Task 2, so run compile check after Task 2 Step 2 or update `toSellerResponse` signature right away if compiling here).

---

### Task 2: `ReviewService` Enhancements, `DemoEngagementSeeder` & `ReviewServiceTest` (`fashionstore`)

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/ReviewService.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/infrastructure/seed/DemoEngagementSeeder.java`
- Modify: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/review/ReviewServiceTest.java`

**Interfaces:**
- Consumes: `SellerReviewQueryRepository`, `ReviewReplyTemplateRepository`, `ReviewReplyTemplate`, DTOs from Task 1.
- Produces on `ReviewService`:
  - `listForShop(UUID userId, UUID shopId, boolean onlyUnreplied) -> List<SellerReviewResponse>` (delegates to 5-arg)
  - `listForShop(UUID userId, UUID shopId, boolean onlyUnreplied, @Nullable Integer stars, @Nullable UUID productId) -> List<SellerReviewResponse>`
  - `listShopReviewProducts(UUID userId, UUID shopId) -> List<SellerReviewProductOptionResponse>`
  - `shopReviewTrend(UUID userId, UUID shopId, String period, @Nullable LocalDate from, @Nullable LocalDate to) -> SellerReviewTrendResponse`
  - `listReplyTemplates(UUID userId, UUID shopId) -> List<ReviewReplyTemplateResponse>`
  - `createReplyTemplate(UUID userId, UUID shopId, SaveReviewReplyTemplateRequest request) -> ReviewReplyTemplateResponse`
  - `updateReplyTemplate(UUID userId, UUID templateId, SaveReviewReplyTemplateRequest request) -> ReviewReplyTemplateResponse`
  - `deleteReplyTemplate(UUID userId, UUID templateId) -> void`

- [ ] **Step 1: Write failing integration tests in `ReviewServiceTest.java`**
  Add tests covering all Review Focus cases:
  - `sellerReviewsFilterByStarsProductAndUnrepliedAndRejectsInvalidStars`: create reviews across 2 products (5★, 3★, 1★; reply one), verify combinations of `stars`, `productId`, `unreplied`, `productName` populated, and `stars = 0` / `stars = 6` -> `400 Bad Request`.
  - `sellerReviewProductOptionsIncludesDeletedProductWithReviewsAndWorksForCskhStaff`: create CSKH staff with `REVIEW_READ`, `REVIEW_REPLY` (no `PRODUCT_READ`), delete one reviewed product, verify `listShopReviewProducts` returns both active and deleted-with-reviews products ordered by `reviewCount DESC`, and CSKH can call `listForShop`, `listShopReviewProducts`, `shopReviewTrend`, and template endpoints.
  - `sellerReviewTrendAggregatesByDayWeekMonthAndValidatesDates`: verify `day`/`week`/`month` points, default bounds, `averageRating` accuracy, invalid period -> `400`, `from > to` -> `400`.
  - `replyTemplatesCrudEnforcesPermissionsDuplicatesLimitAndAudit`: verify create -> list -> update (same title succeeds, colliding title -> `409`) -> delete -> `404` on missing template, 50-template cap (`409` on 51st), blank/oversized reply -> `400`, non-member/missing `REVIEW_REPLY` -> `403`, and `shop_member_audit` entries (`REVIEW_TEMPLATE_CREATE`, `REVIEW_TEMPLATE_UPDATE`, `REVIEW_TEMPLATE_DELETE`).

- [ ] **Step 2: Run failing tests to confirm red**
  Run: `./gradlew test --tests "com.minhnbnt.fashionstore.features.review.ReviewServiceTest"` in `d:\fashionstore\fashionstore`
  Expected: FAIL (missing methods/constructors on `ReviewService`).

- [ ] **Step 3: Implement `ReviewService` methods & update `DemoEngagementSeeder`**
  - Add constants `MAX_REPLY_TEMPLATES_PER_SHOP = 50`, `MAX_TEMPLATE_TITLE_LENGTH = 100`, `VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh")`.
  - Implement 5-arg `listForShop` (validate `stars` `1..5`, call `sellerQuery.findShopReviews`), keep 3-arg overload delegating with `null, null`.
  - Update `reply` (trim & validate length `1..2000`, resolve `productName` via `found.productName()`) and `report` (resolve `productName` via `productStock.findProductById`).
  - Implement `listShopReviewProducts`, `shopReviewTrend` (normalize `period.toLowerCase(Locale.ROOT)`, validate `"day"|"week"|"month"`, compute default `from`/`to` by `period`, validate `!from.isAfter(to)`, convert to VN zone `[from.atStartOfDay(VN_ZONE).toInstant(), to.plusDays(1).atStartOfDay(VN_ZONE).toInstant())`, round `averageRating` to 2 decimals via `Math.round(avg * 100.0) / 100.0`), and template CRUD with `ShopAuditService.record(...)`.
  - Update `DemoEngagementSeeder.seed(...)` to call `seedReplyTemplates(ownerId, shopId)` when `reviews.listReplyTemplates(ownerId, shopId).isEmpty()`.

- [ ] **Step 4: Run `ReviewServiceTest` to verify green**
  Run: `./gradlew test --tests "com.minhnbnt.fashionstore.features.review.ReviewServiceTest"`
  Expected: PASS (all existing + new tests pass).

- [ ] **Step 5: Commit backend domain & service work**
  ```bash
  git -C d:\fashionstore\fashionstore add src/main/resources/db/migration/V37__review_reply_templates.sql src/main/java/com/minhnbnt/fashionstore/model/ReviewReplyTemplate.java src/main/java/com/minhnbnt/fashionstore/features/review/ src/main/java/com/minhnbnt/fashionstore/dto/review/ src/main/java/com/minhnbnt/fashionstore/infrastructure/seed/DemoEngagementSeeder.java src/test/java/com/minhnbnt/fashionstore/features/review/ReviewServiceTest.java
  git -C d:\fashionstore\fashionstore commit -m "feat(review): add seller filters, product options, trend stats, and reply templates"
  ```

---

### Task 3: `SellerReviewController`, WebMvc Slice Tests & OpenAPI Export (`fashionstore`)

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/controller/SellerReviewController.java`
- Create: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/review/SellerReviewControllerTest.java`
- Generated/Updated: `fashionstore/docs/openapi.json` & `fashionstore-ui/docs/openapi.json` (via `OpenApiExportTest`)

**Interfaces:**
- Consumes: `ReviewService` methods from Task 2.
- Produces HTTP endpoints:
  - `GET /api/seller/reviews?shopId&unreplied&stars&productId`
  - `GET /api/seller/reviews/products?shopId`
  - `GET /api/seller/reviews/trend?shopId&period&from&to`
  - `GET /api/seller/reviews/templates?shopId`
  - `POST /api/seller/reviews/templates?shopId` (201)
  - `PUT /api/seller/reviews/templates/{templateId}` (200)
  - `DELETE /api/seller/reviews/templates/{templateId}` (204)

- [ ] **Step 1: Write failing `SellerReviewControllerTest.java` (`@WebMvcTest(SellerReviewController.class)` + `@Import(SecurityConfig.class)`)**
  Test:
  - Unauthenticated requests to `/api/seller/reviews`, `/products`, `/trend`, `/templates` -> `401`.
  - `GET /api/seller/reviews` passes `shopId`, `unreplied`, `stars`, `productId` to `reviews.listForShop(...)`.
  - `GET /api/seller/reviews/products` returns options array.
  - `GET /api/seller/reviews/trend` rejects invalid `period=year` (`400`) and passes valid `period`, `from`, `to`.
  - `POST/PUT/DELETE /api/seller/reviews/templates` return `201`, `200`, `204`, validate `@NotBlank`/`@Size` (`400`), and propagate `403`/`409`.

- [ ] **Step 2: Run `SellerReviewControllerTest` to verify failure**
  Run: `./gradlew test --tests "com.minhnbnt.fashionstore.features.review.SellerReviewControllerTest"`
  Expected: FAIL (missing endpoints/params).

- [ ] **Step 3: Update `SellerReviewController.java`**
  Add Springdoc annotations (`@Operation`, `@ApiResponse`, `@Parameter`, `@SecurityRequirement`) on all modified/new endpoints; keep parameter validation (`@Pattern(regexp = "(?i)day|week|month")` on `period`, `@Valid` on request bodies).

- [ ] **Step 4: Run controller tests, `SellerPermissionEnforcementTest`, and `OpenApiExportTest`**
  Run: `./gradlew test --tests "com.minhnbnt.fashionstore.features.review.*" --tests "com.minhnbnt.fashionstore.features.shop.SellerPermissionEnforcementTest" --tests "com.minhnbnt.fashionstore.OpenApiExportTest"`
  Expected: PASS and updated `openapi.json` written to both repos.

- [ ] **Step 5: Commit controller, tests & OpenAPI spec (plus run full `./gradlew test` in background while starting Task 4!)**
  ```bash
  git -C d:\fashionstore\fashionstore add src/main/java/com/minhnbnt/fashionstore/features/review/controller/SellerReviewController.java src/test/java/com/minhnbnt/fashionstore/features/review/SellerReviewControllerTest.java docs/openapi.json
  git -C d:\fashionstore\fashionstore commit -m "feat(review): expose seller review filters, trend, and template endpoints"
  ```

---

### Task 4: OpenAPI Codegen, `LineChart` Axis Props, Adapters, Types & Audit Labels (`fashionstore-ui`)

**Files:**
- Modify: `fashionstore-ui/src/components/ui/charts/LineChart.tsx`
- Modify: `fashionstore-ui/src/components/ui/charts/LineChart.test.tsx`
- Modify: `fashionstore-ui/src/lib/api/apiAdapters.ts`
- Modify: `fashionstore-ui/src/types/seller/reviews.ts`
- Modify: `fashionstore-ui/src/lib/seller/sellerAdapters.ts`
- Modify: `fashionstore-ui/src/lib/seller/sellerPermissions.ts`

**Interfaces:**
- Consumes: `fashionstore-ui/docs/openapi.json` generated in Task 3.
- Produces:
  - Orval hooks in `generated/endpoints/seller-reviews/seller-reviews.ts`
  - `LineChartProps`: optional `formatAxisValue?: (val: number) => string` and `maxY?: number`
  - `ReviewItem` (uses `productName` from `SellerReviewResponse`), `ReviewReplyTemplateItem`, `ReviewTrendPoint`, `SellerReviewProductOption`
  - `reviewReplyTemplateItem(...)`, `reviewTrendPoints(...)`, `ERROR_MESSAGES.reviewReply`, `ERROR_MESSAGES.reviewTemplate`, audit labels for `REVIEW_TEMPLATE_CREATE`, `REVIEW_TEMPLATE_UPDATE`, `REVIEW_TEMPLATE_DELETE`, `REVIEW_TEMPLATE`.

- [ ] **Step 1: Run `pnpm codegen` in `d:\fashionstore\fashionstore-ui`**
  Verify generated hooks for `GET /api/seller/reviews` (with `stars`, `productId`, `unreplied`), `/products`, `/trend`, and `/templates` (`GET`/`POST`/`PUT`/`DELETE`).

- [ ] **Step 2: Write failing tests in `LineChart.test.tsx`**
  Test that passing `formatAxisValue: (v) => String(v)` and `maxY: 5` renders `5` and `0` on the Y-axis without `'₫'` currency suffix, while omitting `formatAxisValue` preserves existing `'0 ₫'` behavior.

- [ ] **Step 3: Run `LineChart.test.tsx` to verify failure**
  Run: `pnpm test -- src/components/ui/charts/LineChart.test.tsx` in `d:\fashionstore\fashionstore-ui`
  Expected: FAIL (`5 ₫` / `0 ₫` rendered instead of `5` / `0`).

- [ ] **Step 4: Update `LineChart.tsx`, `apiAdapters.ts`, `types/seller/reviews.ts`, `sellerAdapters.ts`, `sellerPermissions.ts`**
  - `LineChart.tsx`: add optional `formatAxisValue` and `maxY` on `LineChartProps`; when `maxY !== undefined && maxY > 0`, use `maxY` as `ceiling`, and format Y-axis ticks with `formatAxisValue(val)` if provided (else `formatCompactAxis(val)`), avoiding any ternary `? :`!
  - `apiAdapters.ts`: add `reviewReply` (`400`, `403`, `404`, `409`) and `reviewTemplate` (`400`, `403`, `404`, `409`).
  - `types/seller/reviews.ts` & `sellerAdapters.ts`: add `ReviewReplyTemplateItem`, `ReviewTrendPoint`, `SellerReviewProductOption`, and update `reviewItem(review, fallbackProductName?)` to prefer `review.productName` before fallback.
  - `sellerPermissions.ts`: add labels for `REVIEW_TEMPLATE_CREATE`, `REVIEW_TEMPLATE_UPDATE`, `REVIEW_TEMPLATE_DELETE`, and `TARGET_TYPE_LABELS.REVIEW_TEMPLATE`.

- [ ] **Step 5: Run `LineChart.test.tsx` and `sellerAdapters.test.ts` to verify pass, then commit**
  Run: `pnpm test -- src/components/ui/charts/LineChart.test.tsx src/lib/seller/sellerAdapters.test.ts`
  ```bash
  git -C d:\fashionstore\fashionstore-ui add docs/openapi.json src/components/ui/charts/LineChart.tsx src/components/ui/charts/LineChart.test.tsx src/lib/api/apiAdapters.ts src/types/seller/reviews.ts src/lib/seller/sellerAdapters.ts src/lib/seller/sellerPermissions.ts
  git -C d:\fashionstore\fashionstore-ui commit -m "feat(review): regenerate openapi client, support non-currency LineChart axis, and add review adapters"
  ```

---

### Task 5: `SellerReviewsTab.tsx`, Sub-Components & Full Cross-Repo Verification (`fashionstore-ui`)

**Files:**
- Create: `fashionstore-ui/src/components/seller-dashboard-tab/stats/ReviewTrendCard.tsx`
- Create: `fashionstore-ui/src/components/seller-dashboard-tab/stats/ReviewReplyTemplateModal.tsx`
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/stats/SellerReviewsTab.tsx`
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/stats/SellerReviewsTab.test.tsx`

**Interfaces:**
- Consumes: Orval hooks from Task 4, `LineChart` (`formatAxisValue`, `maxY`), `Modal.tsx`, `sellerAdapters.ts`, `apiErrorText`/`hookErrorMessage`.
- Produces:
  - `ReviewTrendCard({ shopId }: { shopId: string })`
  - `ReviewReplyTemplateModal({ shopId, canEdit, templates, onClose, onSaved }: ...)`
  - Upgraded `SellerReviewsTab({ shopId }: { shopId: string })`

- [ ] **Step 1: Write comprehensive failing tests in `SellerReviewsTab.test.tsx`**
  Cover all Review Focus cases:
  - CSKH permission mode (`can('REVIEW_READ') = true`, `can('REVIEW_REPLY') = true`, `can('PRODUCT_READ') = false`): renders `productName` from `SellerReviewResponse`, renders product filter options from `GET /api/seller/reviews/products`, does NOT show create/edit/delete template buttons when `can('REVIEW_REPLY') = false`.
  - Server filter wiring: clicking `Chưa phản hồi`, selecting `4 sao` (and `1..5 sao`), and selecting a product option passes `{ shopId, unreplied, stars, productId }` into the review list query hook.
  - Reply UX: submitting blank reply shows inline error without `window.alert`; `onSuccess` triggers success toast; `onError` does not show success toast and renders error banner.
  - Quick reply template insertion: clicking a template inserts content when textarea is empty, and appends with `\n` when textarea already has text; opening management modal allows creating/editing/deleting templates and shows `409` duplicate/limit error cleanly.
  - Trend chart card: switches between `Ngày` / `Tuần` / `Tháng`, validates `toDate < fromDate` inline without firing query, and switches metric between `Số đánh giá` (`lượt`) and `Điểm trung bình` (`sao`, `maxY={5}`).

- [ ] **Step 2: Run `SellerReviewsTab.test.tsx` to verify failure**
  Run: `pnpm test -- src/components/seller-dashboard-tab/stats/SellerReviewsTab.test.tsx`
  Expected: FAIL (missing new UI controls and sub-components).

- [ ] **Step 3: Implement `ReviewTrendCard.tsx`, `ReviewReplyTemplateModal.tsx`, and update `SellerReviewsTab.tsx`**
  - Strictly avoid ternary `? :` in all JSX/TS files.
  - Use `Modal.tsx` for `ReviewReplyTemplateModal`.
  - Keep existing report modal/badge untouched (`POST /api/seller/reviews/{id}/report`).

- [ ] **Step 4: Run `SellerReviewsTab.test.tsx` and full frontend test + build**
  Run in `d:\fashionstore\fashionstore-ui`:
  `pnpm test` and `pnpm build`
  Expected: all tests pass and production build succeeds with 0 TypeScript/Vite errors.

- [ ] **Step 5: Run full backend verification & commit frontend**
  Run `./gradlew test` in `d:\fashionstore\fashionstore` (if not already run cleanly after final backend edit) and commit `fashionstore-ui` changes:
  ```bash
  git -C d:\fashionstore\fashionstore-ui add src/components/seller-dashboard-tab/stats/
  git -C d:\fashionstore\fashionstore-ui commit -m "feat(seller-reviews): add star/product filters, quick reply templates, and trend chart"
  ```

---

## Plan Self-Review

- **Spec Coverage Check:** Every goal in [2026-11-12-seller-reviews-management-design.md](file:///d:/fashionstore/fashionstore/docs/superpowers/specs/2026-11-12-seller-reviews-management-design.md) (`GET /api/seller/reviews` with `stars` & `productId`, `GET /api/seller/reviews/products` + `productName`, `GET /api/seller/reviews/trend`, `review_reply_template` migration + CRUD + audit + demo seed, `LineChart` axis customization, `SellerReviewsTab` filters/templates/trend/UX fixes) maps 1:1 to Tasks 1–5.
- **No Unresolved Ambiguities / Consistent Interfaces:** Exact method signatures, DTO field lists, permission constants (`REVIEW_READ` / `REVIEW_REPLY`), and `LineChart` props match across backend and frontend tasks.
- **Review Focus Pinned:** All 5 high-risk failure modes (CSKH without `PRODUCT_READ`, duplicate/limit template conflicts, invalid filter/date bounds, `LineChart` currency suffix bug, reply toast timing & template append behavior) have explicit tests in Tasks 2, 3, 4, and 5.
