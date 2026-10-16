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
| [x] | 6 | GET | `/api/admin/shops?status=&q=&page=&size=` | Tìm/lọc shop; PENDING xếp FIFO theo lần nộp |
| [x] | 6b | GET | `/api/admin/shops/{id}` | Chi tiết shop + owner + số sản phẩm/đơn + audit trail |
| [x] | 7 | PATCH | `/api/admin/shops/{id}/approve` | Admin duyệt → `APPROVED` |
| [x] | 8 | PATCH | `/api/admin/shops/{id}/reject` | Admin từ chối kèm lý do → `REJECTED` |
| [x] | 9 | PUT | `/api/shops/{id}` | Sửa thông tin shop (OWNER/STAFF) |
| [x] | 9b | POST | `/api/shops/{id}/resubmit` | OWNER gửi duyệt lại `REJECTED` → `PENDING` |
| [x] | 9c | PATCH | `/api/admin/shops/{id}/suspend` | Admin tạm khóa kèm lý do `APPROVED` → `SUSPENDED` |
| [x] | 9d | PATCH | `/api/admin/shops/{id}/reinstate` | Admin mở lại `SUSPENDED` → `APPROVED` |

Admin được xác định từ GoTrue `app_metadata.role=admin`; `user_profile.role` mirror role để truy vấn vận hành. Migration V6 bổ sung trạng thái, lý do và audit trail cho tài khoản/shop.

## Flow 3 — Catalog (hàng hóa + search)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 10 | GET | `/api/categories` | Public danh mục (DemoSeeder seed sẵn, chưa cần CRUD) |
| [x] | 11 | POST | `/api/shops/{shopId}/products` | Tạo product kèm variants (OWNER/STAFF, shop đã `APPROVED`) |
| [x] | 12 | PUT | `/api/shops/{shopId}/products/{id}` | Sửa product / variant / tồn kho |
| [x] | 13 | POST | `/api/uploads/presign` | Xin URL upload ảnh (`S3Storage` có sẵn) |
| [x] | 14 | POST | `/api/shops/{shopId}/products/{id}/images` | Gắn `s3_key` vào product |
| [x] | 15 | GET | `/api/products/{id}` | Public chi tiết sản phẩm |
| [x] | 16 | GET | `/api/search/products?q=&category=&size=&color=&minPrice=&maxPrice=&sort=&page=` | Search BM25 + lọc danh mục/biến thể/giá + sắp xếp + phân trang (`idx_product_bm25`, bắt buộc `to_bm25query` + `status='ACTIVE'`) |
| [x] | 16b | GET | `/api/search/facets?q=&category=` | Size/màu/khoảng giá có sẵn để dựng bộ lọc |

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

## Flow 5 — Quản trị tài khoản

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 24 | GET | `/api/admin/stats` | Tổng quan tài khoản và shop theo trạng thái |
| [x] | 25 | GET | `/api/admin/users?status=&role=&q=&page=&size=` | Tìm/lọc tài khoản phân trang |
| [x] | 26 | GET | `/api/admin/users/{id}` | Chi tiết + shop tham gia + số đơn + audit trail |
| [x] | 27 | PATCH | `/api/admin/users/{id}/lock` | Khóa kèm lý do; tạm khóa shop mà user làm OWNER |
| [x] | 28 | PATCH | `/api/admin/users/{id}/unlock` | Mở khóa tài khoản; không tự mở lại shop |

Khóa tài khoản được thực thi hai lớp: `AccountFilter` chặn access token hiện tại ngay ở backend; `GoTrueAdminClient` ban/unban đăng nhập khi có cấu hình `gotrue.service-role-key`. Mọi chuyển trạng thái khóa dòng bằng `SELECT ... FOR UPDATE` và ghi lịch sử trong cùng transaction.
