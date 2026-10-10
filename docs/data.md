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

## S3 / file

- DB chỉ lưu **key** (`avatar_key`, `s3_key`), không lưu URL. Presign upload qua `S3Storage`, key sinh từ UUID + extension từ content-type.
- Bucket public-read qua website endpoint (xem `S3BucketWebsite` tự gọi `PutBucketWebsite` lúc app start). Frontend tự ghép `{public-base-url}/{key}`, base URL xem `config/springboot.yml` (`s3.public-base-url`).
- Browser muốn mở ảnh dev thì thêm `127.0.0.1 fashionstore.web.garage.localhost` vào hosts.

## Test DB

- Testcontainers dùng image Postgres của project (`ghcr.io/minhnbnt/postgresql:latest`, có sẵn `pg_textsearch`), khai `asCompatibleSubstituteFor("postgres")` — xem `TestcontainersConfiguration.java`. Không đổi về `postgres:*-alpine`, migration V2 sẽ nổ.
