# Data — model, DB, search, storage

## Spring Data JDBC aggregate

- 1 aggregate root = 1 repository: `Shop`, `Product`, `Category`, `Order`. Thành viên (`Variant`, `Image`, `Item`, `History`, `Member`) đi qua root, trừ 2 ngoại lệ có repo riêng vì query độc lập: `ShopMember` ("shop của tôi"), `OrderStatusHistory` (audit).
- Reference xuyên aggregate là **UUID trần**, không object. Không join lén qua object graph.
- Entity implement `Persistable<UUID>`, id sinh ở Java trong factory `create(...)` (`UUID.randomUUID()`), không trông chờ DB default trả về.
- Trạng thái dùng Java enum, tên trùng chữ hoa với CHECK constraint trong SQL. Đổi enum = sửa migration.
- Tiền VND: `BIGINT` (Java `Long`). Thời gian: `Instant`. `createdAt/updatedAt` set ở Java, trigger DB chỉ là backstop.
- Hành vi kiểm tra được bằng unit test thuần thì để trong entity (`order.confirm()`, `shop.approve()`). Cạnh tranh đồng thời (trừ kho) thì bằng SQL nguyên tử (`UPDATE ... WHERE stock >= qty`), không check in-memory.
- Multi-shop: mọi bảng product/order đều có `shop_id`, query luôn filter theo shop.

## Schema đã chốt

- `V1__marketplace.sql`: user, shop (duyệt `PENDING` → `APPROVED`), catalog full variant (SKU unique theo shop), order COD 1-checkout-1-shop, CHECK toán học (`total = subtotal + shipping`), partial unique (1 OWNER/shop, 1 default variant).
- `V2__product_search.sql`: index BM25 (`idx_product_bm25`, xem Search).
- `V13__product_moderation.sql`: product thêm `BANNED` + `violation_type`/`status_reason` (lần gỡ gần nhất), bảng `product_moderation_history` (audit gỡ/khôi phục). Ban đầu merge về với version V10, đã đánh số lại thành V13 do trùng với `V10__wishlist.sql`.
- `V14__product_reports.sql`: `product_report` (USER/SYSTEM, OPEN → ACTION_TAKEN/DISMISSED; partial unique 1 báo cáo mở/người/sản phẩm, 1 cờ hệ thống mở/sản phẩm), `banned_keyword` (unique `lower(keyword)`).
- `V12__site_content.sql`: `site_banner`, `site_popup` (lịch chạy `starts_at`/`ends_at`, CHECK kết thúc sau bắt đầu), `site_page` (slug unique, `DRAFT`/`PUBLISHED`), `faq_item`; seed 5 trang chính sách + vài FAQ.
- `V15__admin_order_monitoring.sql`: ghi nhận bên hủy đơn (`BUYER`/`SELLER`/`ADMIN`) và index phục vụ màn giám sát toàn sàn.
- `V16__public_holiday.sql`: lịch nghỉ/ngày làm bù cho SLA đơn hàng + trạng thái đồng bộ Calendarific; seed lịch 2026–2027.
- `V28__shop_invitations.sql`: Bảng `shop_invitations` (lưu lời mời nhân sự với `token` 64 hex, `expires_at` 7 ngày, partial unique index `(shop_id, lower(email))` khi `accepted_at IS NULL`). Bảng `shop_member` bổ sung cột `status VARCHAR(20) DEFAULT 'ACTIVE'` với constraint CHECK `status IN ('ACTIVE', 'DEACTIVATED')`.
- `V37__upload_centralization.sql` + `V38`–`V44`: tập trung file vào bảng `upload` — backfill dedupe key từ 16 cột/mảng của 12 bảng, thay cột key bằng `*_upload_id` FK (avatar, KYC, product media/video/garment, banner/popup, chat, review, return evidence); queue pgmq `upload_gc` + trigger producer (chi tiết mục S3 / file).
- Repository Spring Data JDBC: derived query sắp nhiều cột (`OrderByAAscBAsc`) làm AOT repository sinh code không compile được → viết `@Query` SQL tay.

## Search BM25 (pg_textsearch)

- Index partial `WHERE status = 'ACTIVE'`, `text_config = 'simple'` (stock PG không có stemmer tiếng Việt).
- Query bắt buộc `to_bm25query('...', 'idx_product_bm25')` với tên index tường minh + `WHERE status = 'ACTIVE'`, không thì planner bỏ qua index.
- Lọc boolean kết hợp xếp hạng thì `SET LOCAL default_text_search_config = 'simple'` trước.
- `simple` không unaccent: gõ "ao" không ra "áo". Search trả đủ kết quả xếp hạng, **không ngưỡng cắt** — frontend hiện theo đúng thứ tự rank.
- Liệt kê sản phẩm trang chủ: chưa có endpoint list, UI dùng `search` với từ khóa mặc định.

## S3 / file

- **File tập trung vào entity `upload`** (từ `V37__upload_centralization.sql` + FK V38–V44): bảng nghiệp vụ KHÔNG còn cột key — mọi cột media là `*_upload_id` UUID FK → `upload(id, s3_key UNIQUE, content_type, created_at)`. `upload.s3_key` là chỗ duy nhất chứa key; DB không lưu URL.
- Presign upload: `POST /api/uploads/presign` tạo row `upload` ngay, trả `{uploadId, uploadUrl, publicUrl, expiresAt}` — **không trả key**. `purpose` (`media` mặc định | `kyc`) quyết định prefix: `kyc` đặt key dưới `kyc/` riêng tư; `media` ở gốc bucket. `S3Storage.presign` dùng `S3Presigner` (PUT).
- Bucket policy: đọc ẩn danh chỉ áp gốc bucket; **Deny ẩn danh trên `kyc/*`** (`S3Config` put idempotent lúc boot, kiểm chứng bởi `S3KycPolicyIntegrationTest` trên versitygw thật). `headBucket` → thiếu thì tạo bucket.
- Response nghiệp vụ trả **URL hoàn chỉnh** do `S3UrlService` build — media public path-style `http://localhost:9000/fashionstore/{key}`, giấy tờ KYC trả presigned GET TTL 15 phút (`s3.kyc-url-ttl`, mặc định `PT15M`). Frontend không ghép URL tay, không nhìn thấy key.
- Cập nhật field media = "always state intent": echo `uploadId` cũ = giữ nguyên, id mới = thay, **null = xoá** (JSON không phân biệt absent/explicit null). Xoá garment khi try-on đang bật bị chặn 400.
- SDK gửi checksum mặc định bị một số S3-compatible gateway từ chối (`Invalid payload signature`): `S3Client` cấu hình `requestChecksumCalculation(WHEN_REQUIRED)` — xem `S3Config.java`.
- GC rác upload — **producer** (`V37__upload_centralization.sql`): trigger `trg_upload_gc` sau mỗi INSERT vào `upload` đẩy message `{upload_id, s3_key}` vào pgmq queue `upload_gc` với delay 24h (`pgmq.send(..., 86400)` — row presign mà client không PUT, hoặc media bị null = xoá là rác có chủ đích). Backfill V37 cũng bắn message cho cả row cũ — vô hại, message nằm chờ. Message mang cả `s3_key` để consumer xoá được object kể cả khi row `upload` đã bị xoá trước đó.
- TODO (consumer GC — **change riêng**, chưa làm): job định kỳ đọc `upload_gc`, với message đã visible: gọi hàm `upload_is_referenced(upload_id)` (check FK từ mọi bảng nghiệp vụ — cột FK đã đủ từ V38–V44) → không được tham chiếu thì xoá object S3 theo `s3_key` + row `upload`, rồi `pgmq.delete('upload_gc', msg_id)` ack. FK thật là "vệ sĩ": DELETE row đang bị tham chiếu nổ constraint, không xoá nhầm file đang dùng.

## Demo seed (dev-only)

- `infrastructure/DemoSeeder` chạy lúc boot khi `app.demo-seed=true` (bật trong `config/springboot.yml` local, mặc định tắt nên production không dính).
- Idempotent: skip khi shop slug `net-lab` đã tồn tại. Reset: `DELETE FROM shop WHERE slug = 'net-lab';` (cascade kéo product/variant/image).
- Nội dung: 1 shop APPROVED, 5 category, 9 product ACTIVE (giá/tồn thật, 1 món hết hàng demo), ảnh picsum tự tải + up S3 (lỗi thì warn + bỏ qua, UI rớt placeholder).
- Search test tay gõ **có dấu** ("áo" là keyword mặc định của Home).

## Test DB

- Testcontainers dùng image Postgres của project (`ghcr.io/minhnbnt/postgresql:latest`, có sẵn `pg_textsearch`), khai `asCompatibleSubstituteFor("postgres")` — xem `TestcontainersConfiguration.java`. Không đổi về `postgres:*-alpine`, migration V2 sẽ nổ.
