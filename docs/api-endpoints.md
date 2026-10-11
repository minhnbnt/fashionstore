# API Endpoints — Fashionstore Marketplace

Thứ tự triển khai theo luồng, luồng sau dựa luồng trước.
Base: `com.minhnbnt.fashionstore` — shop | catalog | order | identity (mỗi domain có `controller/` + `service/` + `repository/`), `model/` + `dto/` chung ở ngoài.

## Hợp đồng lỗi

- Mọi lỗi render theo RFC 9457 `ProblemDetail`, không DTO riêng (`ResponseStatusException` đã implement `ErrorResponse`).
- `OpenApiProblemCustomizer` tự gắn body `application/problem+json` cho 400/401/403/404/409 của mọi endpoint trong spec.
- 401/403 từ security filter body rỗng — UI chỉ dựa status code.

## Flow 1 — Identity

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 1 | POST | `/api/users` | Mirror GoTrue user vào `user_profile` (authenticated, id từ JWT sub) |
| [x] | 2 | GET | `/api/users/me` | Profile của user đang login |

## Flow 2 — Shop (đăng ký + duyệt)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 3 | POST | `/api/shops` | Đăng ký shop → `PENDING` |
| [x] | 4 | GET | `/api/shops/mine` | Shop của tôi (dựa `shop_member`) |
| [x] | 5 | GET | `/api/shops/{slug}` | Public xem shop |
| [x] | 6 | GET | `/api/admin/shops?status=` | Liệt kê shop theo trạng thái (mặc định PENDING) |
| [x] | 7 | PATCH | `/api/admin/shops/{id}/approve` | Admin duyệt → `APPROVED` |
| [x] | 8 | PATCH | `/api/admin/shops/{id}/reject` | Admin từ chối → `REJECTED` |
| [x] | 9 | PUT | `/api/shops/{id}` | Sửa thông tin shop (OWNER/STAFF) |

> Quyết định còn mở: admin xác định bằng cách nào — đọc role từ GoTrue `app_metadata`, hay thêm cột `is_admin` vào `user_profile` (migration V4 — V3 đã dùng để bỏ product DRAFT).

## Flow 3 — Catalog (hàng hóa + search)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 10 | GET | `/api/categories` | Public danh mục (DemoSeeder seed sẵn, chưa cần CRUD) |
| [x] | 11 | POST | `/api/shops/{shopId}/products` | Tạo product kèm variants (OWNER/STAFF, shop đã `APPROVED`) |
| [x] | 12 | PUT | `/api/shops/{shopId}/products/{id}` | Sửa product / variant / tồn kho |
| [x] | 13 | POST | `/api/uploads/presign` | Xin URL upload ảnh (`S3Storage` có sẵn) |
| [x] | 14 | POST | `/api/shops/{shopId}/products/{id}/images` | Gắn `s3_key` vào product |
| [x] | 15 | GET | `/api/products/{id}` | Public chi tiết sản phẩm |
| [x] | 16 | GET | `/api/search/products?q=...` | Search BM25 (`idx_product_bm25`, bắt buộc `to_bm25query` + `status='ACTIVE'`) |

Chi tiết luồng này xem `docs/catalog.md`.

## Flow 4 — Order COD (1 checkout 1 shop)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 17 | POST | `/api/orders` | Checkout, 1 đơn đúng 1 shop |
| [x] | 18 | GET | `/api/orders/mine` | Buyer xem đơn của mình |
| [x] | 19 | GET | `/api/orders/{id}` | Buyer xem chi tiết (chỉ khi `PENDING` mới cancel được) |
| [x] | 19b | PATCH | `/api/orders/{id}/cancel` | Buyer hủy đơn (chỉ khi `PENDING`) |
| [x] | 20 | GET | `/api/seller/orders` | Seller xem đơn mọi shop mình tham gia |
| [x] | 21 | PATCH | `/api/seller/orders/{id}/confirm` | Confirm + trừ kho nguyên tử (`UPDATE ... WHERE stock >= qty`) |
| [x] | 22 | PATCH | `/api/seller/orders/{id}/ship` | Chuyển sang `SHIPPING` |
| [x] | 23 | PATCH | `/api/seller/orders/{id}/deliver` | `DELIVERED` = thu COD (`payment_status='COLLECTED'`) |
