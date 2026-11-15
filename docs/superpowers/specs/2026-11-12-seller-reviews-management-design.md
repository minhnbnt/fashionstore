# Thiết kế chức năng 2.7.1 — Xem & Phản hồi đánh giá (Kênh Người Bán)

- **Ngày:** 2026-11-12
- **Phạm vi repo:** `fashionstore` (Backend Spring Boot 4 + Data JDBC + PostgreSQL/Flyway) & `fashionstore-ui` (Frontend React 19 + TypeScript + Tailwind v4 + Orval)
- **Trạng thái:** Bản thiết kế chi tiết sau brainstorming — chờ rà soát & duyệt trước khi lập kế hoạch triển khai (`writing-plans`).

---

## 1. Mục tiêu & Ngữ cảnh

Sau khi hoàn thiện luồng đánh giá & tương tác **1.5**, hệ thống đã có nền tảng cho người bán gồm:
- `GET /api/seller/reviews?shopId=...&unreplied=...` và `GET /api/seller/reviews/summary?shopId=...` tại `SellerReviewController`.
- `PATCH /api/seller/reviews/{id}/reply` (phản hồi công khai 1 lần dưới đánh giá, ghi `ShopAuditService` và gửi thông báo cho người mua).
- `SellerReviewsTab.tsx` hiển thị điểm trung bình, phân bố 5 → 1 sao và danh sách đánh giá kèm ảnh/video/tag/phân loại hàng.

Tuy nhiên, để đáp ứng đầy đủ đặc tả **2.7.1. Xem & Phản hồi đánh giá** (trong phạm vi loại trừ phần mở rộng báo cáo vi phạm theo xác nhận của người dùng), hệ thống vẫn còn các khoảng trống sau:
- **Bộ lọc danh sách đánh giá chưa đủ:** `GET /api/seller/reviews` mới hỗ trợ `unreplied`, chưa lọc được theo **từng mức sao (1–5)** và **theo sản phẩm** ở server; `SellerReviewsTab.tsx` mới lọc client-side 4 tab (`Tất cả`, `Chưa phản hồi`, `5 sao`, `1 - 3 sao`).
- **Chưa có Mẫu phản hồi nhanh:** Shop chưa lưu được các mẫu câu trả lời thường dùng (văn bản thuần) để chèn nhanh khi phản hồi khách hàng.
- **Chưa có thống kê Trend theo thời gian:** Chưa có API tổng hợp số lượng đánh giá và điểm trung bình theo ngày/tuần/tháng cũng như biểu đồ xu hướng trên Seller Center.
- **Lỗi UX nhỏ khi phản hồi:** `SellerReviewsTab.tsx` hiện dùng `alert()` khi ô trả lời trống, bật toast thành công trước khi mutation hoàn tất và chưa hiển thị lỗi API nếu gửi thất bại.

### Goals (Mục tiêu)
1. **Danh sách & bộ lọc đánh giá đầy đủ (Server + UI):**
   - Mở rộng `GET /api/seller/reviews` hỗ trợ lọc kết hợp `unreplied` (boolean), `stars` (1–5), và `productId` (UUID sản phẩm thuộc shop).
   - Cập nhật `SellerReviewsTab.tsx` cho phép lọc theo từng mức sao (`Tất cả`, `5★`, `4★`, `3★`, `2★`, `1★`), theo sản phẩm của shop, và trạng thái `Chưa phản hồi`.
2. **Phản hồi đánh giá ổn định & hiển thị công khai:**
   - Giữ quy tắc phản hồi công khai 1 lần (`PATCH /api/seller/reviews/{id}/reply`), hiển thị dưới đánh giá trên trang sản phẩm (`ProductTabs.tsx`).
   - Bổ sung kiểm tra độ dài tối đa 2000 ký tự và trim khoảng trắng cho `ReplyReviewRequest`.
   - Khắc phục các lỗi UX trong form trả lời của `SellerReviewsTab.tsx` (báo lỗi inline thay `alert()`, khóa nút khi đang gửi, chỉ hiện toast thành công trong `onSuccess`).
3. **Mẫu phản hồi nhanh (Quick Reply Templates — văn bản thuần theo Shop):**
   - Lưu danh sách mẫu trả lời thường dùng ở cấp `shop_id` trong DB (`review_reply_template`), dùng chung cho toàn bộ nhân sự có quyền `REVIEW_READ` (xem) / `REVIEW_REPLY` (tạo, sửa, xóa, áp dụng).
   - Trong form trả lời đánh giá, nhân sự có thể chọn 1 mẫu để chèn văn bản vào ô nhập và chỉnh sửa lại trước khi gửi.
   - Có hộp thoại quản lý mẫu (thêm, sửa, xóa) ngay trong tab Đánh giá.
4. **Thống kê đánh giá: Trung bình sao, phân bố & Trend theo thời gian:**
   - Giữ khối thống kê tổng quan hiện tại (điểm trung bình 1 chữ số thập phân, tổng lượt đánh giá, phân bố 5 → 1 sao).
   - Bổ sung API trend theo thời gian (`GET /api/seller/reviews/trend`) hỗ trợ chu kỳ `day` / `week` / `month` và khoảng `from` / `to` theo múi giờ `Asia/Ho_Chi_Minh`.
   - Hiển thị biểu đồ xu hướng trên `SellerReviewsTab.tsx` bằng `LineChart.tsx` dùng chung, cho phép chuyển đổi giữa **Số lượng đánh giá** và **Điểm trung bình sao**.

### Non-Goals & Anti-Goals
- **Không** mở rộng, sửa đổi hay xây mới chức năng **báo cáo đánh giá vi phạm** phía Seller hoặc màn hình kiểm duyệt phía Admin (giữ nguyên endpoint `POST /api/seller/reviews/{id}/report` và UI hiện tại).
- **Không** hỗ trợ cú pháp biến động (`{buyerName}`, `{productName}`) trong mẫu phản hồi nhanh — chỉ lưu và chèn văn bản thuần.
- **Không** cho phép sửa hoặc xóa phản hồi của shop sau khi đã gửi (giữ nguyên quy tắc phản hồi 1 lần).
- **Không** đặt API trend vào `SellerStatsController` vì controller này yêu cầu quyền `REVENUE_READ`, sẽ chặn nhân viên CSKH (`REVIEW_READ`) khi xem tab Đánh giá.
- **Không** vi phạm `docs/code-guide.md` (cấm ternary `? :` trong Java & TSX/TS, NullAway non-null mặc định, springdoc đầy đủ, message lỗi backend tiếng Anh) và `fashionstore-ui/docs/design-system.md`.

---

## 2. Phương án đã chọn & Quyết định kiến trúc

### Phương án 1: Mở rộng trực tiếp module `features/review` + bảng `review_reply_template` riêng *(Đã chọn)*
- Toàn bộ endpoint dành cho shop đặt trong `SellerReviewController` (`/api/seller/reviews/**`) và sử dụng đúng cặp quyền `ShopPermission.REVIEW_READ` / `ShopPermission.REVIEW_REPLY`.
- Tạo migration `V37__review_reply_templates.sql` cho bảng `review_reply_template` gắn `shop_id` (thay vì lưu JSON trên `shop`), tránh ghi đè đồng thời giữa nhiều nhân sự CSKH và hỗ trợ kiểm tra trùng tên mẫu ở DB.
- Truy vấn lọc động (`stars`, `productId`, `unreplied`) và thống kê trend (`GROUP BY period_label`) viết qua một repository `JdbcClient` chuyên biệt (`SellerReviewQueryRepository`) tương tự pattern của `ShopStatsRepository` và `ReviewHelpfulRepository`.

### Các quyết định kỹ thuật & nghiệp vụ đã chốt
1. **Phân quyền & Tải danh sách sản phẩm cho CSKH *(Quyết định đề xuất kỹ thuật)*:**
   - `GET /api/seller/reviews`, `GET /api/seller/reviews/products`, `GET /api/seller/reviews/summary`, `GET /api/seller/reviews/trend`, `GET /api/seller/reviews/templates`: yêu cầu `ShopPermission.REVIEW_READ`.
   - `PATCH /api/seller/reviews/{id}/reply`, `POST /api/seller/reviews/templates`, `PUT /api/seller/reviews/templates/{templateId}`, `DELETE /api/seller/reviews/templates/{templateId}`: yêu cầu `ShopPermission.REVIEW_REPLY`.
   - **Giải quyết xung đột quyền `PRODUCT_READ` cho nhân viên CSKH:** `SellerReviewsTab.tsx` trước đây gọi `GET /api/seller/shops/{shopId}/products` (`useListProducts`), vốn đòi quyền `PRODUCT_READ` (vai trò CSKH mặc định không có) và giới hạn 50 sản phẩm. Để CSKH dùng được đầy đủ bộ lọc theo sản phẩm và xem tên sản phẩm mà không phải cấp dư quyền kho/catalog (`PRODUCT_READ`), thiết kế bổ sung:
     - Trường `productName` trực tiếp trong `SellerReviewResponse` (lấy từ `JOIN product` khi truy vấn).
     - Endpoint gọn nhẹ `GET /api/seller/reviews/products?shopId={shopId}` (yêu cầu `REVIEW_READ`) trả về danh sách `{ id, name, reviewCount }` cho tất cả sản phẩm chưa xóa của shop cộng với sản phẩm đã xóa nhưng còn đánh giá, sắp xếp theo `reviewCount DESC, name ASC`.
2. **Quy tắc lọc đánh giá của Shop (`GET /api/seller/reviews`):**
   - Tham số: `shopId` (bắt buộc), `unreplied` (mặc định `false`), `stars` (tùy chọn `1..5`, ngoài khoảng này ném `400 Bad Request`), `productId` (tùy chọn UUID).
   - Khi truyền `productId`: lọc theo `p.shop_id = :shopId AND r.product_id = :productId` (nếu sản phẩm không thuộc shop hoặc chưa có đánh giá thì trả `[]`, không làm lộ sản phẩm của shop khác).
   - Sắp xếp mới nhất trước: `ORDER BY r.created_at DESC, r.id DESC`.
3. **Quy tắc Mẫu phản hồi nhanh:**
   - `title`: bắt buộc, sau `trim()` có độ dài `1..100` ký tự; không trùng (không phân biệt hoa/thường) với mẫu khác trong cùng shop (`409 Conflict`).
   - `content`: bắt buộc, sau `trim()` có độ dài `1..2000` ký tự (khớp giới hạn phản hồi shop).
   - Giới hạn tối đa **50 mẫu / shop** (`409 Conflict` nếu vượt quá).
   - Khi chèn mẫu vào ô trả lời ở `SellerReviewsTab.tsx`: nếu ô đang trống thì điền thẳng `content`; nếu ô đã có chữ nhân viên đang gõ thì nối thêm `\n` rồi chèn `content` để tránh mất nội dung đang soạn.
   - Ghi nhận audit log vào `ShopAuditService` với các action `REVIEW_TEMPLATE_CREATE`, `REVIEW_TEMPLATE_UPDATE`, `REVIEW_TEMPLATE_DELETE` (`targetType = "REVIEW_TEMPLATE"`), và cập nhật nhãn hiển thị trong `sellerPermissions.ts`.
4. **Quy tắc Thống kê Trend theo thời gian (`GET /api/seller/reviews/trend`):**
   - Chu kỳ `period`: chấp nhận `day` (mặc định), `week`, `month` (không phân biệt hoa/thường; giá trị khác ném `400 Bad Request`).
   - Khoảng thời gian mặc định theo múi giờ `Asia/Ho_Chi_Minh` khi `from`/`to` bỏ trống:
     - `day`: 30 ngày gần nhất (`today.minusDays(29)` → `today`).
     - `week`: 12 tuần gần nhất (`today.minusWeeks(11)` → `today`).
     - `month`: 12 tháng gần nhất (`today.minusMonths(11).withDayOfMonth(1)` → `today`).
   - Nếu `from` > `to` → `400 Bad Request`.
   - Biểu thức nhóm theo mốc thời gian khớp 100% với `ShopStatsRepository.revenueByPeriod` (để `LineChart.tsx` format nhãn trục X tự động):
     - `day`: `to_char(r.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD')`
     - `week`: `to_char(date_trunc('week', r.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh'), 'YYYY-"W"IW')`
     - `month`: `to_char(r.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM')`
   - Trả về danh sách các mốc có phát sinh đánh giá (`COUNT(*) > 0`) sắp xếp tăng dần theo `period_label`, mỗi điểm gồm `label`, `reviewCount`, `averageRating` (làm tròn 2 chữ số thập phân ở Java hoặc giá trị `double` từ SQL `AVG(r.stars)::double precision`). Không tạo mốc giả với `reviewCount = 0` vì `averageRating` tại mốc không có đánh giá không xác định.

---

## 3. Thiết kế chi tiết Database, API & UI

### 3.1. Database Migration (`V37__review_reply_templates.sql`)

```sql
CREATE TABLE review_reply_template (
    id         UUID PRIMARY KEY,
    shop_id    UUID NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    title      VARCHAR(100) NOT NULL,
    content    TEXT NOT NULL,
    created_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_review_reply_template_title_len CHECK (char_length(btrim(title)) BETWEEN 1 AND 100),
    CONSTRAINT chk_review_reply_template_content_len CHECK (char_length(btrim(content)) BETWEEN 1 AND 2000)
);

CREATE UNIQUE INDEX uq_review_reply_template_shop_title
    ON review_reply_template (shop_id, lower(btrim(title)));

CREATE INDEX idx_review_reply_template_shop_updated
    ON review_reply_template (shop_id, updated_at DESC, created_at DESC);

CREATE INDEX idx_review_created_at
    ON review (created_at DESC);
```

### 3.2. Backend Entities, Repositories, DTOs & Endpoints (`fashionstore`)

#### Entity & Repositories
- **`ReviewReplyTemplate.java`** (`@Table("review_reply_template")`):
  - Fields: `id`, `shopId`, `title`, `content`, `@Nullable createdBy`, `createdAt`, `updatedAt`, `@Transient boolean newRecord`.
  - Factory `ReviewReplyTemplate.create(UUID shopId, UUID createdBy, String title, String content)` và method behavior `update(String title, String content)` (tuân thủ `docs/code-guide.md`, không dùng `@Setter`/`@Data`/`@Builder`).
- **`ReviewReplyTemplateRepository.java`** (`CrudRepository<ReviewReplyTemplate, UUID>`):
  - `List<ReviewReplyTemplate> findByShopIdOrderByUpdatedAtDescCreatedAtDesc(UUID shopId);`
  - `long countByShopId(UUID shopId);`
  - `boolean existsByShopIdAndTitleIgnoreCase(UUID shopId, String title);` (kèm query check trùng loại trừ `id` hiện tại khi cập nhật: `existsByShopIdAndTitleIgnoreCaseAndIdNot(UUID shopId, String title, UUID id)` — viết bằng `@Query` với `lower(btrim(title)) = lower(:title)` để khớp index DB).
- **`SellerReviewQueryRepository.java`** (`@Repository` dùng `JdbcClient` + `RowMapper`):
  - `List<SellerReviewRow> findShopReviews(UUID shopId, boolean onlyUnreplied, @Nullable Integer stars, @Nullable UUID productId)`: `JOIN product p ON p.id = r.product_id`, trả đầy đủ entity `Review` kèm `p.name AS product_name` (chặn `N+1` và không cần gọi API catalog).
  - `List<SellerReviewProductOptionResponse> findShopReviewProductOptions(UUID shopId)`: trả các sản phẩm thuộc `shop_id = :shopId` có `p.status <> 'DELETED'` hoặc có ít nhất 1 đánh giá (`GROUP BY p.id, p.name ORDER BY COUNT(r.id) DESC, p.name ASC`).
  - `List<ReviewTrendRow> findShopReviewTrend(UUID shopId, String period, Instant from, Instant to)`: trả `record ReviewTrendRow(String label, long reviewCount, double averageRating)`.

#### DTOs (`dto/review/`)
- Cập nhật `SellerReviewResponse`: bổ sung `@Schema(description = "Tên sản phẩm") String productName` (lấy từ `JOIN product` khi list, hoặc từ `productStock.findProductById` khi reply/report).
- Cập nhật `ReplyReviewRequest`: `@NotBlank @Size(max = 2000) String reply` (service trim và validate không rỗng / ≤ 2000).
- `SellerReviewProductOptionResponse(UUID id, String name, long reviewCount)`
- `ReviewReplyTemplateResponse(UUID id, UUID shopId, String title, String content, Instant createdAt, Instant updatedAt)`
- `SaveReviewReplyTemplateRequest(@NotBlank @Size(max = 100) String title, @NotBlank @Size(max = 2000) String content)`
- `SellerReviewTrendResponse(String period, List<Point> points)` với `record Point(String label, long reviewCount, double averageRating)`

#### Endpoints (`SellerReviewController` — `/api/seller/reviews`)
1. `GET /api/seller/reviews` (sửa bổ sung optional params):
   - Params: `shopId`, `unreplied` (default `false`), `stars` (`@Nullable Integer`), `productId` (`@Nullable UUID`).
   - Trả `200 OK`: `List<SellerReviewResponse>` (có `productName`).
   - Mã lỗi: `400` (`stars` ngoài `1..5`), `401`, `403` (thiếu `REVIEW_READ`).
2. `GET /api/seller/reviews/products`:
   - Params: `shopId`.
   - Trả `200 OK`: `List<SellerReviewProductOptionResponse>`.
   - Mã lỗi: `401`, `403` (thiếu `REVIEW_READ`).
3. `GET /api/seller/reviews/trend`:
   - Params: `shopId`, `period` (default `"day"`, `@Pattern(regexp = "(?i)day|week|month")`), `from` (`@Nullable LocalDate`), `to` (`@Nullable LocalDate`).
   - Trả `200 OK`: `SellerReviewTrendResponse`.
   - Mã lỗi: `400` (`period` không hợp lệ hoặc `from > to`), `401`, `403` (thiếu `REVIEW_READ`).
4. `GET /api/seller/reviews/templates`:
   - Params: `shopId`.
   - Trả `200 OK`: `List<ReviewReplyTemplateResponse>`.
   - Mã lỗi: `401`, `403` (thiếu `REVIEW_READ`).
5. `POST /api/seller/reviews/templates`:
   - Params: `shopId`, body `SaveReviewReplyTemplateRequest`.
   - Trả `201 Created`: `ReviewReplyTemplateResponse`.
   - Mã lỗi: `400`, `401`, `403` (thiếu `REVIEW_REPLY`), `409` (trùng tên mẫu hoặc vượt quá 50 mẫu).
6. `PUT /api/seller/reviews/templates/{templateId}`:
   - Path: `templateId`, body `SaveReviewReplyTemplateRequest`.
   - Trả `200 OK`: `ReviewReplyTemplateResponse`.
   - Mã lỗi: `400`, `401`, `403` (thiếu `REVIEW_REPLY`), `404` (không tìm thấy mẫu), `409` (trùng tên mẫu).
7. `DELETE /api/seller/reviews/templates/{templateId}`:
   - Path: `templateId`.
   - Trả `204 No Content`.
   - Mã lỗi: `401`, `403` (thiếu `REVIEW_REPLY`), `404` (không tìm thấy mẫu).

#### Demo Seed (`DemoEngagementSeeder.java`)
- Seed thêm 3 mẫu phản hồi nhanh mẫu cho demo shop (ví dụ: `"Cảm ơn đánh giá 5 sao"`, `"Hỗ trợ đổi size"`, `"Xin lỗi về trải nghiệm"`) thông qua service tạo mẫu (khi shop chưa có mẫu nào để đảm bảo idempotent nếu chạy lại).

### 3.3. Frontend Architecture (`fashionstore-ui`)

Sau khi backend cập nhật `docs/openapi.json`, chạy `pnpm codegen` để cập nhật Orval hooks/models.

1. **Adapters & Permissions:**
   - `src/lib/api/apiAdapters.ts`: bổ sung `ERROR_MESSAGES.reviewReply` (`400`, `403`, `404`, `409`) và `ERROR_MESSAGES.reviewTemplate` (`400`, `403`, `404`, `409`).
   - `src/lib/seller/sellerAdapters.ts` & `src/types/seller/reviews.ts`: thêm adapter `reviewReplyTemplateItem(...)`, `reviewTrendPoints(...)` và các type tương ứng (`ReviewReplyTemplateItem`, `ReviewTrendPoint`).
   - `src/lib/seller/sellerPermissions.ts`: bổ sung nhãn audit tiếng Việt cho `REVIEW_TEMPLATE_CREATE`, `REVIEW_TEMPLATE_UPDATE`, `REVIEW_TEMPLATE_DELETE`, và `targetType` `"REVIEW_TEMPLATE"`.
2. **Nâng cấp `SellerReviewsTab.tsx` (tách sub-component gọn sạch nếu cần):**
   - **Header:** Bổ sung nút **"Mẫu phản hồi nhanh"** (mở `Modal.tsx` quản lý mẫu).
   - **Khối thống kê & Trend:**
     - Giữ thẻ Điểm trung bình + Phân bố 5 → 1 sao từ `useSummary`.
     - Bổ sung prop tùy chọn `formatAxisValue?: (val: number) => string` và `maxY?: number` cho `LineChart.tsx` (giữ mặc định hiện có `formatCompactAxis` và `niceCeiling` để không ảnh hưởng các màn hình doanh thu/admin).
     - Thêm thẻ **"Xu hướng đánh giá theo thời gian"** gọi `GET /api/seller/reviews/trend`:
       - Chuyển chu kỳ `Ngày` / `Tuần` / `Tháng`.
       - Bộ lọc khoảng ngày `Từ ngày` / `Đến ngày` (validate `toDate < fromDate` inline giống `SellerStatsRevenueTab.tsx`).
       - Chuyển metric hiển thị trên `LineChart`: **Số đánh giá** (`formatAxisValue` số nguyên + đơn vị `lượt`) / **Điểm trung bình** (`maxY={5}`, `formatAxisValue` 1 chữ số thập phân + đơn vị `sao`).
   - **Thanh lọc danh sách đánh giá (gọi API server):**
     - Tab nhanh: `Tất cả`, `Chưa phản hồi`.
     - Bộ lọc sao: `Tất cả sao`, `5 sao`, `4 sao`, `3 sao`, `2 sao`, `1 sao`.
     - Dropdown chọn **Sản phẩm**: `Tất cả sản phẩm` + danh sách sản phẩm từ `GET /api/seller/reviews/products` (thay cho `useListProducts` để nhân viên CSKH không có `PRODUCT_READ` vẫn lọc được và không bị giới hạn 50 sản phẩm); tên sản phẩm trên từng thẻ lấy trực tiếp từ `review.productName`.
     - Truyền `{ shopId, unreplied, stars, productId }` vào `useList3` để lọc trực tiếp phía server.
   - **Form phản hồi & Chọn mẫu nhanh:**
     - Khi bấm `"Trả lời đánh giá này"`, hiển thị bộ chọn **"Chèn mẫu phản hồi nhanh"** (chip hoặc dropdown các mẫu từ `GET /api/seller/reviews/templates`) ngay phía trên ô `textarea`.
     - Bấm chọn mẫu sẽ chèn văn bản thuần vào `replyContent` để người bán chỉnh sửa trước khi gửi.
     - Kiểm tra `replyContent.trim() === ''` bằng lỗi inline dưới `textarea` (bỏ `alert()`), disable nút khi `replyMutation.isPending`, dùng `mutate(..., { onSuccess, onError })` để chỉ báo toast thành công khi API trả về `200`.
   - **Modal Quản lý mẫu phản hồi nhanh (`Modal.tsx`):**
     - Hiển thị danh sách mẫu hiện có (tiêu đề + nội dung + nút Sửa / Xóa nếu `can('REVIEW_REPLY')`).
     - Form tạo mới / chỉnh sửa mẫu (validate tiêu đề 1–100 ký tự, nội dung 1–2000 ký tự, hiển thị lỗi `409` khi trùng tên hoặc đạt giới hạn 50 mẫu).

---

## 4. Rủi ro & Điểm cần lưu ý khi kiểm thử

1. **Tương thích `ReviewControllerTest` & `ReviewServiceTest`:**
   - Khi mở rộng `GET /api/seller/reviews` thêm `stars` và `productId`, cần giữ giá trị mặc định `required = false` và kiểm tra đầy đủ unit/WebMvc test cho cả trường hợp có/không truyền filter, cũng như kiểm tra quyền `REVIEW_READ` vs `REVIEW_REPLY` cho tất cả endpoint mới.
2. **Múi giờ & Format nhãn biểu đồ Trend:**
   - Backend tính `AT TIME ZONE 'Asia/Ho_Chi_Minh'` và trả về đúng format `YYYY-MM-DD`, `YYYY-Www`, `YYYY-MM` để `formatAxisLabel` trong `LineChart.tsx` hiển thị chuẩn `dd/MM`, `Tww`, `Tmm`.
3. **Tuân thủ `code-guide.md` & `design-system.md`:**
   - Tuyệt đối không dùng toán tử 3 ngôi (`? :`) ở cả Java lẫn TypeScript/TSX; mọi `Modal` dùng `Modal.tsx`; dùng class semantic light/dark chuẩn (`dark:bg-[#11182a]`, `dark:border-white/10`); đảm bảo NullAway pass `compileJava` và `compileTestJava`.

