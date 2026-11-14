# Thiết kế chức năng 1.5 — Đánh giá sản phẩm & Tương tác

- **Ngày:** 2026-11-12
- **Phạm vi repo:** `fashionstore` (Backend Spring Boot 4 + Data JDBC + PostgreSQL/Flyway + S3) & `fashionstore-ui` (Frontend React 19 + TypeScript + Tailwind v4 + Orval)
- **Trạng thái:** Bản thiết kế chi tiết sau brainstorming — chờ duyệt trước khi lập kế hoạch triển khai (`writing-plans`).

---

## 1. Mục tiêu & Ngữ cảnh

Hệ thống hiện đã có nền tảng đánh giá cơ bản (Flow 10 & Flow 14: bảng `review` từ `V7__reviews.sql`, `V11`, `V34` cho sửa tối đa 1 lần, API trả lời/báo cáo của Seller, và S3 presign + `VideoMetadataService`), nhưng còn thiếu nhiều yêu cầu cốt lõi của đặc tả **1.5.1 (Đánh giá sản phẩm)** và **1.5.2 (Xem đánh giá sản phẩm)**, đồng thời tồn tại một số lỗi lệch pha giữa Backend và Frontend:
- **Điều kiện & phạm vi đánh giá:** Backend hiện khóa cứng 1 tài khoản / 1 bài / sản phẩm (`uq_review_one_per_buyer`), trong khi yêu cầu thực tế là **1 đánh giá cho mỗi cặp `(đơn hàng đã hoàn thành, sản phẩm)`** (nhiều sản phẩm khác nhau trong đơn thì đánh giá từng món; 1 sản phẩm dù mua nhiều số lượng hay nhiều biến thể trong cùng đơn chỉ đánh giá 1 lần). Phía UI (`Orders.tsx`, `OrderDetail.tsx`) đang mở nút đánh giá cho mọi đơn `DELIVERED` kể cả khi chưa có `receivedAt`, gây lỗi `403` khi bấm.
- **Nội dung đánh giá 1.5.1:** Chưa bắt buộc nhận xét tối thiểu 10 ký tự, chưa có upload ảnh (tối đa 5) / video (tối đa 1, ≤ 30 giây), chưa có 5 tag đánh giá nhanh, chưa có tùy chọn ẩn danh tên, và chưa lưu/hiển thị thông tin size/phân loại hàng của người mua.
- **Xem & tương tác 1.5.2:** `ReviewResponse` chưa trả `sellerReply` & `repliedAt` ra cho người mua dù DB đã lưu; thiếu cơ chế thích **"Hữu ích"**, thiếu sắp xếp đưa đánh giá hữu ích nhất lên đầu kèm highlight, thiếu lọc theo sao / có media, và `SellerReviewsTab.tsx` phía Seller Center vẫn đang hardcode số liệu thống kê giả (`4.9`, `1,248 lượt đánh giá`, `92%`) cùng `images: []`.

### Goals (Mục tiêu)
1. **Đánh giá theo đơn hoàn thành `(orderId, productId)` (1.5.1):**
   - Chỉ mở đánh giá khi đơn thuộc về buyer, có `status = DELIVERED` và `receivedAt != null` (tức đã xác nhận đã nhận hàng hoặc tự động hoàn thành).
   - Mỗi sản phẩm phân biệt (`productId`) trong một đơn hoàn thành được đánh giá **tối đa 1 lần** (mua nhiều số lượng hoặc nhiều size/màu của cùng sản phẩm trong 1 đơn vẫn gom thành 1 đánh giá; mua lại ở đơn hoàn thành khác được đánh giá lại 1 lần cho đơn mới).
   - Giữ quy tắc chỉnh sửa tối đa 1 lần (`edited_at`, từ `V34`) và không cho xóa (`DELETE` trả `405`).
2. **Đầy đủ trường dữ liệu đánh giá (1.5.1):**
   - Chọn `1–5` sao (bắt buộc).
   - Nhận xét chi tiết bắt buộc **tối thiểu 10 ký tự** (sau `trim()`, tối đa 2000 ký tự).
   - Đính kèm **tối đa 5 ảnh** và **tối đa 1 video ≤ 30 giây** (lưu S3 key, kiểm tra thời lượng ở cả client và backend qua `VideoMetadataService`).
   - Chọn nhanh từ **5 tag cố định**: `"Chất lượng tốt"`, `"Đúng mô tả"`, `"Giao hàng nhanh"`, `"Đóng gói cẩn thận"`, `"Size đúng chuẩn"`.
   - Tùy chọn **ẩn danh tên** (`anonymous: boolean`): che tên người đánh giá khi hiển thị công khai và ở Seller Center.
   - Tự động snapshot **thông tin phân loại/size** người mua đã đặt trong đơn đó (`variantLabel`, ví dụ `"M · Đen"` hoặc `"M · Đen, L · Trắng"` nếu mua nhiều biến thể cùng sản phẩm trong đơn).
3. **Xem đánh giá, Highlight & Tương tác "Hữu ích" (1.5.2):**
   - Tổng hợp điểm trung bình (làm tròn 1 chữ số thập phân khi hiển thị), tổng số đánh giá, phân bố từng mức sao `5 / 4 / 3 / 2 / 1` (số lượt + phần trăm) tách thành summary độc lập để không bị sai lệch khi lọc danh sách.
   - Hỗ trợ nút **"Hữu ích"** (toggle thích / bỏ thích cho user đăng nhập, chặn tự thích đánh giá của chính mình).
   - Mặc định sắp xếp đánh giá có nhiều lượt **"Hữu ích"** nhất lên đầu (`helpfulCount DESC, createdAt DESC`), gắn nhãn nổi bật **"Hữu ích nhất"** cho các đánh giá dẫn đầu có `helpfulCount > 0`; hỗ trợ chuyển sang **"Mới nhất"** và lọc theo số sao / có ảnh-video.
   - Hiển thị đầy đủ trên từng thẻ đánh giá: size/phân loại người mua, tag nhanh, ảnh & video (bấm xem phóng to), trạng thái đã chỉnh sửa, và **phản hồi từ shop** (`sellerReply`, `repliedAt`).
4. **Đồng bộ Seller Center & sửa lỗi liên đới:**
   - Bổ sung dữ liệu thật (thống kê tổng hợp của shop + ảnh/video/tag/size/ẩn danh) cho `SellerReviewsTab.tsx` và `SellerReviewResponse`.
   - Sửa lỗi nút đánh giá trên `Orders.tsx` & `OrderDetail.tsx` (chỉ bật khi `receivedAt != null`), sửa lỗi thiếu active state trên tab của `ProductTabs.tsx`, và bổ sung map lỗi `'review'` trong `apiAdapters.ts`.

### Non-Goals & Anti-Goals
- **Không** xây dựng màn hình Admin xử lý `is_reported` cho đánh giá bị shop báo cáo (giữ nguyên cờ `isReported` của Seller Center).
- **Không** mở rộng `ProductSummaryResponse` trên trang tìm kiếm / thẻ sản phẩm trang chủ.
- **Không** sửa luồng `Shop.rating` / `ShopStatsRepository` ngoài phạm vi đánh giá sản phẩm 1.5.
- **Không** cho viết đánh giá trực tiếp trên `ProductDetail` (luôn đi từ đơn hàng đã hoàn thành tại `/account/orders` hoặc `/account/orders/:id`).
- **Không** vi phạm `docs/code-guide.md` (cấm ternary `? :` ở Java và JSX/TS, NullAway non-null mặc định, springdoc đầy đủ, message lỗi backend tiếng Anh) và không tạo dependency vòng giữa các service.

---

## 2. Phương án đã chọn & Quyết định kiến trúc

### Phương án được chọn: Mở rộng trực tiếp aggregate `review` + bảng `review_helpful` (Phương án 1)
- Giữ một bảng `review` duy nhất, mở rộng thêm cột `order_id`, `variant_label`, `image_keys TEXT[]`, `video_key`, `tags TEXT[]`, `is_anonymous`, `helpful_count` (cùng pattern `evidence_keys TEXT[]` của `return_request` trong `V8__seller_center.sql`), và tạo bảng con `review_helpful (review_id, user_id)` để chống vote trùng và cập nhật nguyên tử `helpful_count`.
- **Lý do chọn:** Bám sát mô hình Spring Data JDBC phẳng của dự án (`docs/data.md`), sắp xếp `helpful_count DESC` bằng index trực tiếp không cần `GROUP BY` toàn bảng lúc đọc, và giữ sự liên kết tự nhiên với luồng phản hồi/báo cáo hiện có của Seller Center.

### Các quyết định nghiệp vụ & biên kỹ thuật đã chốt
1. **Khóa duy nhất & tương thích dữ liệu cũ:**
   - Gỡ index `uq_review_one_per_buyer (product_id, buyer_id)`.
   - Thêm cột `order_id UUID REFERENCES orders (id) ON DELETE SET NULL` (cho phép `NULL` với dữ liệu cũ trước V36 nếu có; mọi review tạo mới qua API **bắt buộc** truyền `orderId`).
   - Tạo partial unique index `uq_review_order_product ON review (order_id, product_id) WHERE order_id IS NOT NULL`.
   - Cập nhật `DemoEngagementSeeder.java` truyền `orderId` tương ứng từ `DemoOrderSeeder` để dữ liệu demo tuân thủ đầy đủ schema mới.
2. **Thứ tự dependency giữa `ReviewService` và `OrderService`:**
   - Hiện tại `ReviewService` → `OrderService` (và `OrderService` không inject `ReviewService`). Giữ đúng chiều này: `OrderService` cung cấp method `resolveCompletedOrderProductVariant(UUID buyerId, UUID orderId, UUID productId)` trả về `String` (chuỗi `variantLabel` đã gộp), ném `404` nếu đơn không tồn tại/không thuộc buyer, `403` nếu đơn chưa `DELIVERED` hoặc chưa có `receivedAt` (hoặc sản phẩm không nằm trong đơn → `404`/`403` rõ ràng).
   - Cụ thể: nếu đơn không tồn tại hoặc `buyerId` không khớp → `404 Not Found`; nếu đơn chưa hoàn thành (`status != DELIVERED || receivedAt == null`) → `403 Forbidden`; nếu trong đơn không có `order_item` nào khớp `productId` → `400 Bad Request` (`"Product is not part of this order"`).
3. **Ẩn danh tên người đánh giá (`is_anonymous`):**
   - Trong DB vẫn snapshot `buyer_display_name` thật lúc tạo bài (đồng nhất pattern snapshot).
   - Khi `isAnonymous = true`:
     - API công khai (`GET /api/products/{productId}/reviews`) và API Seller (`GET /api/seller/reviews`) trả về `buyerDisplayName` đã che ký tự theo kiểu Shopee (hàm `maskDisplayName(String)` thuần Java, ví dụ `"Minh Nguyễn"` → `"M***n"`, `"An"` → `"A***"`, chuỗi 1 ký tự `"U"` → `"U***"`).
     - API cá nhân (`GET /api/reviews/mine`) trả về `buyerDisplayName` gốc của người dùng kèm `anonymous = true` để người mua nhận diện được thiết lập ẩn danh của mình (và sửa lại nếu chưa hết lượt).
   - **Quyết định kỹ thuật (trả lời builder check):**
     - `SellerReviewController` giữ nguyên base path hiện có `/api/seller/reviews` (`GET /api/seller/reviews?shopId=...&unreplied=...`, `PATCH /api/seller/reviews/{id}/reply`, `POST /api/seller/reviews/{id}/report`) và bổ sung `GET /api/seller/reviews/summary?shopId={shopId}` để tương thích ngược hoàn toàn với OpenAPI / Orval hooks hiện tại (`useList3`, `useReply`, `useReport`).
     - `ReviewResponse` bổ sung `boolean mine` (tính từ optional JWT tại server, luôn `true` trong `GET /api/reviews/mine`) giúp UI vô hiệu hóa nút `"Hữu ích"` cho bài đánh giá của chính mình ngay khi render, đồng thời vẫn giữ kiểm tra `409 Conflict` ở `POST /api/reviews/{reviewId}/helpful`.
     - Migration `V36__review_enhancements.sql` thực hiện SQL backfill `order_id` và `variant_label` cho các review cũ (nếu có) theo đơn hoàn thành gần nhất của `(buyer_id, product_id)`; ở Frontend, map đánh giá dùng khóa chính `${orderId}:${productId}` và chỉ fallback theo `productId` khi review cũ có `orderId == null`.
4. **Quy tắc sửa bài đánh giá (1 lần duy nhất):**
   - Vì một buyer có thể có nhiều review cho cùng `productId` (ở các đơn hàng khác nhau), endpoint sửa bài phải định danh theo `reviewId`: **`PUT /api/reviews/{reviewId}`** (thay cho `PUT /api/products/{productId}/reviews`).
   - Cho phép sửa `stars`, `comment`, `imageKeys`, `videoKey`, `tags`, `anonymous` tối đa 1 lần (`editedAt != null` → `409 Conflict`). Không sửa `orderId`, `productId`, `variantLabel`, `helpfulCount`.
5. **Quy tắc thích "Hữu ích" (`review_helpful`):**
   - Endpoint `POST /api/reviews/{reviewId}/helpful` (yêu cầu JWT) thực hiện toggle:
     - Review không tồn tại hoặc sản phẩm của review không ở trạng thái `ACTIVE` → `404 Not Found`.
     - Người bấm là chính tác giả của review (`review.buyerId.equals(userId)`) → `409 Conflict` (`"You cannot mark your own review as helpful"`).
     - Giao dịch nguyên tử: kiểm tra/insert vào `review_helpful`; nếu insert thành công → `UPDATE review SET helpful_count = helpful_count + 1 WHERE id = :reviewId`; nếu đã tồn tại → xóa dòng trong `review_helpful` và `UPDATE review SET helpful_count = GREATEST(helpful_count - 1, 0) WHERE id = :reviewId`. Trả về `helpfulCount` mới và `helpfulByMe`.
   - `GET /api/products/{productId}/reviews` giữ public (`permitAll` trong `SecurityConfig`), nhận `@AuthenticationPrincipal @Nullable Jwt jwt` để tra `review_helpful` cho `currentUser` (1 query `IN` cho toàn bộ list review đang trả về, nếu `jwt == null` thì `helpfulByMe = false`).
6. **Quy tắc chọn "Hữu ích nhất" trên giao diện & API:**
   - API `GET /api/products/{productId}/reviews` mặc định `sort=HELPFUL` (`ORDER BY helpful_count DESC, created_at DESC`), hoặc `sort=NEWEST` (`ORDER BY created_at DESC`).
   - Trên UI, các bài đánh giá đứng đầu khi có `helpfulCount === maxHelpfulCount && maxHelpfulCount > 0` (hoặc top đầu khi `sort=HELPFUL` và `helpfulCount > 0`) được gắn badge highlight `"Hữu ích nhất"`.
7. **Quy tắc kiểm tra Media (≤ 5 ảnh, ≤ 1 video ≤ 30s):**
   - Frontend: dùng `uploadImage` (≤ 5MB/ảnh, tối đa 5 ảnh) và `uploadVideo(file, 30)` (≤ 50MB, ≤ 30 giây, tối đa 1 video) thông qua `POST /api/uploads/presign`.
   - Backend: kiểm tra `imageKeys.size() <= 5`, mỗi key không blank; nếu `videoKey != null && !videoKey.isBlank()`, khi tạo mới hoặc khi `videoKey` thay đổi lúc cập nhật, gọi `videoMetadataService.validateVideoDuration(videoKey, 30.0)` để xác thực file tồn tại trên S3 và thời lượng ≤ 30 giây.

---

## 3. Thiết kế chi tiết Database, API & UI

### 3.1. Database Migration (`V36__review_enhancements.sql`)

```sql
ALTER TABLE review
    ADD COLUMN IF NOT EXISTS order_id UUID REFERENCES orders (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS variant_label TEXT,
    ADD COLUMN IF NOT EXISTS image_keys TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS video_key TEXT,
    ADD COLUMN IF NOT EXISTS tags TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS helpful_count INT NOT NULL DEFAULT 0 CHECK (helpful_count >= 0);

DROP INDEX IF EXISTS uq_review_one_per_buyer;

-- Backfill order_id & variant_label cho các review cũ (nếu có) dựa trên đơn hoàn thành gần nhất khớp (buyer_id, product_id)
UPDATE review r
SET
    order_id = matched.order_id,
    variant_label = matched.variant_label
FROM (
    SELECT DISTINCT ON (r2.id)
        r2.id AS review_id,
        o.id AS order_id,
        NULLIF(
            string_agg(DISTINCT oi.variant_label, ', ')
                FILTER (WHERE oi.variant_label IS NOT NULL AND oi.variant_label <> ''),
            ''
        ) AS variant_label
    FROM review r2
    JOIN orders o ON o.buyer_id = r2.buyer_id
        AND o.status = 'DELIVERED'
        AND o.received_at IS NOT NULL
    JOIN order_item oi ON oi.order_id = o.id
        AND oi.product_id = r2.product_id
    WHERE r2.order_id IS NULL
    GROUP BY r2.id, o.id, o.received_at, o.created_at
    ORDER BY r2.id, o.received_at DESC, o.created_at DESC
) matched
WHERE r.id = matched.review_id
  AND r.order_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_review_order_product
    ON review (order_id, product_id)
    WHERE order_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_review_product_helpful
    ON review (product_id, helpful_count DESC, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_product_created
    ON review (product_id, created_at DESC);

CREATE TABLE IF NOT EXISTS review_helpful (
    review_id  UUID NOT NULL REFERENCES review (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (review_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_review_helpful_user
    ON review_helpful (user_id);
```

### 3.2. Backend Models, DTOs & Endpoints (`fashionstore`)

#### Danh sách Tag đánh giá nhanh chuẩn (`ReviewTag`)
Whitelist 5 giá trị hợp lệ (khớp 100% đặc tả 1.5.1):
- `"Chất lượng tốt"`
- `"Đúng mô tả"`
- `"Giao hàng nhanh"`
- `"Đóng gói cẩn thận"`
- `"Size đúng chuẩn"`

#### Entity `Review.java`
- Bổ sung field:
  - `private @Nullable UUID orderId;`
  - `private @Nullable String variantLabel;`
  - `private String[] imageKeys;`
  - `private @Nullable String videoKey;`
  - `private String[] tags;`
  - `private boolean isAnonymous;`
  - `private int helpfulCount;`
- `Review.create(...)` nhận thêm `orderId`, `variantLabel`, `imageKeys`, `videoKey`, `tags`, `isAnonymous` (khởi tạo `helpfulCount = 0`).
- `Review.update(...)` nhận `stars`, `comment`, `imageKeys`, `videoKey`, `tags`, `isAnonymous` (giữ kiểm tra `editedAt != null` → `409 Conflict`).

#### Các Endpoints & DTOs
1. **`POST /api/products/{productId}/reviews`** (Auth JWT)
   - Request `CreateReviewRequest`:
     - `@NotNull UUID orderId`
     - `@Min(1) @Max(5) int stars`
     - `@NotBlank String comment` (service trim và validate `10..2000` ký tự → `400 Bad Request`)
     - `@Nullable @Size(max = 5) List<@NotBlank String> imageKeys`
     - `@Nullable String videoKey`
     - `@Nullable @Size(max = 5) List<@NotBlank String> tags`
     - `boolean anonymous`
   - Response `201 Created`: `ReviewResponse`
   - Mã lỗi: `400` (comment < 10 / > 2000 ký tự, tag lạ, > 5 ảnh, video > 30s hoặc không tồn tại trên S3, sản phẩm không thuộc đơn), `401`, `403` (đơn chưa hoàn thành), `404` (sản phẩm hoặc đơn không tồn tại), `409` (đã đánh giá sản phẩm này trong đơn này rồi).

2. **`PUT /api/reviews/{reviewId}`** (Auth JWT — đặt trong `MyReviewController` `/api/reviews`)
   - Request `UpdateReviewRequest`:
     - `@Min(1) @Max(5) int stars`
     - `@NotBlank String comment` (trim `10..2000`)
     - `@Nullable @Size(max = 5) List<@NotBlank String> imageKeys`
     - `@Nullable String videoKey`
     - `@Nullable @Size(max = 5) List<@NotBlank String> tags`
     - `boolean anonymous`
   - Response `200 OK`: `ReviewResponse`
   - Mã lỗi: `400`, `401`, `404` (không tìm thấy hoặc không phải của mình), `409` (đã chỉnh sửa 1 lần rồi).

3. **`GET /api/products/{productId}/reviews`** (Public, nhận optional JWT)
   - Query params:
     - `sort` (`HELPFUL` mặc định | `NEWEST`)
     - `stars` (`@Nullable Integer`, `1..5`)
     - `hasMedia` (`boolean`, mặc định `false`)
   - Response `200 OK`: `List<ReviewResponse>`
   - Trường của `ReviewResponse`:
     - `UUID id`, `UUID productId`, `@Nullable UUID orderId`, `String buyerDisplayName` (đã che nếu `anonymous`), `@Nullable String variantLabel`, `int stars`, `@Nullable String comment`, `List<String> imageKeys`, `@Nullable String videoKey`, `List<String> tags`, `boolean anonymous`, `boolean mine`, `int helpfulCount`, `boolean helpfulByMe`, `@Nullable String sellerReply`, `@Nullable Instant repliedAt`, `boolean edited`, `@Nullable Instant editedAt`, `Instant createdAt`, `Instant updatedAt`.

4. **`GET /api/products/{productId}/reviews/summary`** (Public)
   - Kiểm tra product `ACTIVE` (`404` nếu ẩn/không tồn tại).
   - Response `200 OK` (`ReviewSummaryResponse`):
     - `long reviewCount`
     - `@Nullable Double averageRating`
     - `long fiveStarCount`, `long fourStarCount`, `long threeStarCount`, `long twoStarCount`, `long oneStarCount`
     - `long withMediaCount`

5. **`POST /api/reviews/{reviewId}/helpful`** (Auth JWT — đặt trong `MyReviewController` `/api/reviews`)
   - Toggle thích / bỏ thích bài đánh giá.
   - Response `200 OK` (`ReviewHelpfulResponse`): `UUID reviewId`, `int helpfulCount`, `boolean helpfulByMe`.
   - Mã lỗi: `401`, `404` (review không tồn tại hoặc product không `ACTIVE`), `409` (tự bấm hữu ích cho bài của chính mình).

6. **`GET /api/reviews/mine`** (Auth JWT)
   - Trả `List<ReviewResponse>` của chính buyer (kèm `orderId`, `variantLabel`, `imageKeys`, `videoKey`, `tags`, `anonymous`, `helpfulCount`, `sellerReply`, `repliedAt`).

7. **Seller Endpoints (`SellerReviewController` — giữ nguyên base path `/api/seller/reviews`):**
   - `GET /api/seller/reviews` giữ nguyên đường dẫn + param `shopId`, `unreplied`, cập nhật `SellerReviewResponse` trả đủ `orderId`, `variantLabel`, `imageKeys`, `videoKey`, `tags`, `anonymous`, `helpfulCount` (ẩn tên `buyerDisplayName` khi `anonymous = true`).
   - Thêm `GET /api/seller/reviews/summary` (param `shopId`, yêu cầu quyền `REVIEW_READ`) trả `ReviewSummaryResponse` tính trực tiếp trên toàn bộ review của shop để thay thế hoàn toàn mock stats trên UI Seller.
   - Giữ nguyên `PATCH /api/seller/reviews/{id}/reply` và `POST /api/seller/reviews/{id}/report`.

8. **SecurityConfig:**
   - Đảm bảo `PUBLIC_GET_PATHS` có `"/api/products/*/reviews"` và `"/api/products/*/reviews/**"` (vẫn parse JWT nếu client gửi Bearer token nhờ `BearerTokenAuthenticationFilter`).

### 3.3. Frontend Architecture (`fashionstore-ui`)

Sau khi backend cập nhật OpenAPI, tải lại `docs/openapi.json` và chạy `pnpm codegen` (Orval) để sinh hooks/types mới.

1. **`src/lib/api/uploadImage.ts` & `src/lib/api/apiAdapters.ts`**:
   - Cập nhật `uploadVideo(file: File, maxSeconds = 60): Promise<string>` để hỗ trợ giới hạn 30 giây cho review (`uploadVideo(file, 30)`).
   - Bổ sung `ERROR_MESSAGES.review` trong `apiAdapters.ts` cho các mã `400`, `403`, `404`, `409` và `reviewHelpful` (`401`, `404`, `409`).
2. **`src/pages/checkout/Orders.tsx`, `src/pages/checkout/OrderDetail.tsx`, `src/components/order/ReviewProductPickerModal.tsx`**:
   - Điều kiện mở nút đánh giá: `order.status === 'DELIVERED' && Boolean(order.receivedAt)`.
   - Khóa map nhận diện đánh giá: `${orderId}:${productId}` (thay cho `productId`); chỉ fallback theo `productId` đối với review cũ có `orderId == null` để đảm bảo tương thích ngược.
   - Khi một đơn hàng có nhiều dòng `OrderItem` cùng `productId` (ví dụ mua cùng lúc size M và size L), `uniqueProducts` gộp chuỗi `variantLabel` phân cách bằng `", "` (loại trùng) để hiển thị trong `ReviewProductPickerModal` và truyền sang `ReviewModal`.
3. **`src/components/order/ReviewModal.tsx`**:
   - Nhận thêm `variantLabel?: string | null`.
   - Hiển thị thông tin sản phẩm & phân loại/size đã mua.
   - Bộ chọn 1–5 sao kèm mô tả cảm nhận.
   - 5 chip tag đánh giá nhanh chọn nhiều (toggle).
   - Textarea có kiểm tra tối thiểu 10 ký tự (trimmed), bộ đếm ký tự và thông báo inline rõ ràng khi dưới 10 ký tự.
   - Trình tải media thực tế: tối đa 5 ảnh + 1 video ≤ 30s (dùng `uploadImage` và `uploadVideo(file, 30)`), preview thumbnail/video và nút gỡ từng tệp.
   - Checkbox `"Ẩn danh tên khi đánh giá"` kèm preview tên sau khi che.
4. **`src/components/pdp/ProductTabs.tsx`, `src/components/pdp/ProductInfo.tsx`, `src/lib/shop/ratingSummary.ts`**:
   - `ProductInfo.tsx`: làm tròn `averageRating.toFixed(1)`.
   - `ProductTabs.tsx`:
     - Highlight rõ tab đang chọn (`mo-ta`, `thong-so`, `danh-gia`).
     - Tab `Đánh giá`: gọi cả `useList` (truyền `sort`, `stars`, `hasMedia`) và summary endpoint (`useSummary` — luôn giữ tổng hợp điểm TB 1 chữ số thập phân, tổng lượt đánh giá và phân bố 5/4/3/2/1 sao chính xác khi đang bật bộ lọc).
     - Thanh điều khiển: chuyển sắp xếp (`Hữu ích nhất` mặc định / `Mới nhất`) + filter chips (`Tất cả`, `5★`..`1★`, `Có hình ảnh / video`).
     - Thẻ đánh giá: hiển thị badge `"Hữu ích nhất"` cho các đánh giá hữu ích đứng đầu (`sort === 'HELPFUL'` và `helpfulCount > 0`), tên (hoặc tên ẩn danh), sao, ngày, badge `"Đã chỉnh sửa"`, badge `"Phân loại hàng: {variantLabel}"`, tag nhanh, nhận xét, gallery ảnh/video (bấm vào mở `Modal` lightbox xem ảnh lớn hoặc phát video), khối `"Phản hồi của Shop"` (nếu `sellerReply`), và nút `"Hữu ích (N)"` (dùng `review.mine` để vô hiệu hóa và ghi nhãn rõ nếu là bài của chính mình; bấm gọi mutation toggle nếu đã đăng nhập, hoặc điều hướng `/login` nếu chưa đăng nhập).
5. **`src/components/seller-dashboard-tab/stats/SellerReviewsTab.tsx` & `src/lib/seller/sellerAdapters.ts` & `src/types/seller/reviews.ts`**:
   - Gọi endpoint summary thật của shop thay cho số hardcode (`4.9`, `1,248`, `92%`, `6%`, `2%`), hiển thị đủ phân bố 5→1 sao.
   - Cập nhật `reviewItem` adapter map đủ `variantLabel`, `tags`, `images` (từ `imageKeys` qua `imageUrl`), `videoKey`, `helpfulCount`, `isAnonymous`.
   - Hiển thị size, tag, ảnh/video khách đăng và lượt hữu ích trong danh sách đánh giá của Seller.

---

## 4. Rủi ro & Điểm cần lưu ý khi kiểm thử
1. **Dữ liệu seed & Test cũ:** Các unit/integration test cũ trong `ReviewServiceTest`, `ReviewControllerTest`, `OrderModals.test.tsx`, `Orders.test.tsx`, `ProductTabs.test.tsx` có chỗ truyền `comment = null` hoặc dưới 10 ký tự hoặc không truyền `orderId` → cần cập nhật đồng bộ theo quy tắc mới.
2. **Orval Codegen:** Bất kỳ thay đổi DTO/Controller nào ở backend đều phải xuất lại `docs/openapi.json` và chạy `pnpm codegen` (hoặc `pnpm build`) ở `fashionstore-ui` trước khi chỉnh UI.
3. **Đồng bộ giao diện Light/Dark & Quy chuẩn code:** Tuân thủ tuyệt đối `docs/design-system.md` (dùng `Modal.tsx` dùng chung, không dùng `dark:bg-zinc-800`, bo góc `rounded-3xl`/`rounded-full`, không dùng ternary `? :` trong cả Java lẫn TSX/TS).

