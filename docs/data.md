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

## Search BM25 (pg_textsearch)

- Index partial `WHERE status = 'ACTIVE'`, `text_config = 'simple'` (stock PG không có stemmer tiếng Việt).
- Query bắt buộc `to_bm25query('...', 'idx_product_bm25')` với tên index tường minh + `WHERE status = 'ACTIVE'`, không thì planner bỏ qua index.
- Lọc boolean kết hợp xếp hạng thì `SET LOCAL default_text_search_config = 'simple'` trước.
- `simple` không unaccent: gõ "ao" không ra "áo". Search trả đủ kết quả xếp hạng, **không ngưỡng cắt** — frontend hiện theo đúng thứ tự rank.
- Liệt kê sản phẩm trang chủ: chưa có endpoint list, UI dùng `search` với từ khóa mặc định.

## S3 / file

- DB chỉ lưu **key** (`avatar_key`, `s3_key`), không lưu URL. Presign upload qua `S3Storage`, key sinh từ UUID + extension từ content-type.
- Bucket và policy đọc ẩn danh (`s3:GetObject`, `Principal: *`) do `S3Config` bảo đảm lúc tạo `S3Client` khi app khởi động: `headBucket` → thiếu thì tạo mới, policy luôn put lại (idempotent, lỗi S3 chỉ warn, không chặn boot). Frontend tự ghép `{public-base-url}/{key}`, base URL xem `config/springboot.yml` (`s3.public-base-url`).
- URL ảnh public là path-style trên chính port S3, bucket name làm prefix: `http://localhost:9000/fashionstore/{key}`.
- SDK gửi checksum mặc định bị một số S3-compatible gateway từ chối (`Invalid payload signature`): `S3Client` cấu hình `requestChecksumCalculation(WHEN_REQUIRED)` — xem `S3Config.java`. Không đụng `S3Presigner`.

## Demo seed (dev-only)

- `infrastructure/DemoSeeder` chạy lúc boot khi `app.demo-seed=true` (bật trong `config/springboot.yml` local, mặc định tắt nên production không dính).
- Idempotent: skip khi shop slug `net-lab` đã tồn tại. Reset: `DELETE FROM shop WHERE slug = 'net-lab';` (cascade kéo product/variant/image).
- Nội dung: 1 shop APPROVED, 5 category, 9 product ACTIVE (giá/tồn thật, 1 món hết hàng demo), ảnh picsum tự tải + up S3 (lỗi thì warn + bỏ qua, UI rớt placeholder).
- Search test tay gõ **có dấu** ("áo" là keyword mặc định của Home).

## Test DB

- Testcontainers dùng image Postgres của project (`ghcr.io/minhnbnt/postgresql:latest`, có sẵn `pg_textsearch`), khai `asCompatibleSubstituteFor("postgres")` — xem `TestcontainersConfiguration.java`. Không đổi về `postgres:*-alpine`, migration V2 sẽ nổ.
