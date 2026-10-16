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
| [x] | 24 | PATCH | `/api/seller/orders/{id}/cancel` | Seller hủy đơn kèm lý do, tự động hoàn trả tồn kho nguyên tử |

## Flow 5 — Seller Center: Quản lý hồ sơ Shop & Tương tác

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 25 | PATCH | `/api/shops/{id}/profile` | Cập nhật hồ sơ mở rộng (SĐT, địa chỉ kho, ngân hàng, tin nhắn tự động, giờ làm việc) |
| [x] | 26 | POST | `/api/shops/{id}/follow` | Buyer theo dõi shop (tăng followerCount) |
| [x] | 27 | DELETE | `/api/shops/{id}/follow` | Buyer bỏ theo dõi shop (giảm followerCount) |
| [x] | 28 | PATCH | `/api/admin/shops/{id}/suspend` | Admin tạm ngưng shop vi phạm |
| [x] | 29 | PATCH | `/api/admin/shops/{id}/unsuspend` | Admin mở lại hoạt động cho shop |

## Flow 6 — Seller Center: Quản lý Sản phẩm, Biến thể & Tồn kho

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 30 | DELETE | `/api/shops/{shopId}/products/{id}` | Xóa mềm sản phẩm (chuyển sang `DELETED`, kiểm tra không có đơn PENDING/SHIPPING) |
| [x] | 31 | DELETE | `/api/shops/{shopId}/products/{productId}/images/{imageId}` | Xóa ảnh sản phẩm |
| [x] | 32 | GET | `/api/seller/shops/{shopId}/products` | Danh sách sản phẩm dạng bảng cho seller (lọc status, từ khóa, phân trang, thống kê tồn/bán) |
| [x] | 33 | GET | `/api/seller/shops/{shopId}/inventory` | Quản lý tồn kho đa biến thể, cảnh báo hết hàng / tồn kho thấp (`lowStockThreshold`) |
| [x] | 34 | PATCH | `/api/seller/shops/{shopId}/inventory/{variantId}` | Điều chỉnh tồn kho tức thời (RESTOCK, DAMAGE, RETURN, AUDIT, MANUAL) kèm ghi log |
| [x] | 35 | GET | `/api/seller/shops/{shopId}/inventory/movements` | Lịch sử biến động tồn kho (Stock Movement Audit Trail) |
| [x] | 36 | GET | `/api/seller/shops/{shopId}/products/export` | Xuất danh sách sản phẩm & tồn kho ra định dạng CSV |
| [x] | 37 | POST | `/api/seller/shops/{shopId}/products/bulk-import` | Nhập hàng loạt sản phẩm từ file CSV (multipart/form-data) |

## Flow 7 — Seller Center: Xử lý Đổi / Trả hàng (Return & Refund)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 38 | POST | `/api/orders/{orderId}/return` | Buyer tạo yêu cầu trả hàng / hoàn tiền cho đơn đã `DELIVERED` |
| [x] | 39 | GET | `/api/orders/returns/mine` | Buyer xem danh sách yêu cầu trả hàng của mình |
| [x] | 40 | GET | `/api/seller/returns` | Seller xem các yêu cầu đổi trả cần xử lý (lọc theo trạng thái) |
| [x] | 41 | PATCH | `/api/seller/returns/{id}/approve` | Seller phê duyệt phương án đổi/trả & hoàn tiền |
| [x] | 42 | PATCH | `/api/seller/returns/{id}/reject` | Seller từ chối yêu cầu kèm lý do |
| [x] | 43 | PATCH | `/api/seller/returns/{id}/complete` | Xác nhận đã nhận lại hàng, tự động hoàn trả tồn kho |

## Flow 8 — Seller Center: Quản lý Doanh thu & Ví Shop (Revenue)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 44 | GET | `/api/seller/shops/{shopId}/revenue/summary` | Tổng quan doanh thu, số đơn, giá trị trung bình đơn, số dư khả dụng & tạm giữ |
| [x] | 45 | GET | `/api/seller/shops/{shopId}/revenue/daily` | Biểu đồ doanh thu theo ngày |
| [x] | 46 | GET | `/api/seller/shops/{shopId}/revenue/top-products` | Top sản phẩm có doanh thu và sản lượng bán cao nhất |
| [x] | 47 | GET | `/api/seller/shops/{shopId}/revenue/settlements` | Danh sách phiên quyết toán đơn hàng (Holding sau 3 ngày hoàn tất) |
| [x] | 48 | POST | `/api/seller/shops/{shopId}/revenue/withdrawals` | Tạo lệnh rút tiền về tài khoản ngân hàng của shop |
| [x] | 49 | GET | `/api/seller/shops/{shopId}/revenue/withdrawals` | Lịch sử rút tiền và tiến độ xử lý |

## Flow 9 — Seller Center: Đánh giá & Phản hồi Khách hàng

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 50 | POST | `/api/orders/{orderId}/reviews` | Buyer đánh giá sản phẩm (1-5 sao, bình luận, ảnh thực tế) |
| [x] | 51 | GET | `/api/products/{productId}/reviews` | Public danh sách đánh giá của sản phẩm |
| [x] | 52 | GET | `/api/seller/shops/{shopId}/reviews` | Seller xem toàn bộ đánh giá của shop (lọc đánh giá chưa phản hồi) |
| [x] | 53 | POST | `/api/seller/reviews/{id}/reply` | Seller phản hồi đánh giá của khách hàng |
| [x] | 54 | POST | `/api/seller/reviews/{id}/report` | Seller báo cáo đánh giá vi phạm tiêu chuẩn cộng đồng |

## Flow 10 — Seller Center: Tin nhắn & Chat trực tuyến (CSKH)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 55 | POST | `/api/chat/conversations` | Khởi tạo hội thoại chat giữa khách hàng và shop |
| [x] | 56 | GET | `/api/chat/conversations/buyer` | Danh sách hội thoại của khách hàng |
| [x] | 57 | GET | `/api/chat/conversations/seller` | Danh sách hội thoại tại Seller Center (kèm lọc tin chưa đọc) |
| [x] | 58 | GET | `/api/chat/conversations/{id}/messages` | Lấy lịch sử tin nhắn trong cuộc hội thoại |
| [x] | 59 | POST | `/api/chat/conversations/{id}/messages` | Gửi tin nhắn mới (tự kích hoạt Auto Reply nếu có) |
| [x] | 60 | PATCH | `/api/chat/conversations/{id}/read` | Đánh dấu đã đọc cuộc hội thoại |

## Flow 11 — Trang trí Gian hàng (Shop Decoration) & Quản lý Phân quyền Nhân viên

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 61 | GET | `/api/shops/{shopId}/decoration` | Public giao diện trang trí gian hàng hiển thị cho khách hàng |
| [x] | 62 | GET | `/api/seller/shops/{shopId}/decoration` | Seller xem giao diện gian hàng và bản nháp layout kéo-thả hiện tại |
| [x] | 63 | PUT | `/api/seller/shops/{shopId}/decoration/draft` | Lưu nháp các khối giao diện (Banner, Bộ sưu tập, Flash sale...) |
| [x] | 64 | POST | `/api/seller/shops/{shopId}/decoration/publish` | Xuất bản bố cục nháp thành giao diện chính thức |
| [x] | 65 | DELETE | `/api/seller/shops/{shopId}/decoration/draft` | Hủy bỏ các thay đổi nháp chưa xuất bản |
| [x] | 66 | GET | `/api/seller/shops/{shopId}/roles` | Danh sách các nhóm vai trò mặc định và tùy chỉnh của shop |
| [x] | 67 | POST | `/api/seller/shops/{shopId}/roles` | Chủ shop tạo vai trò mới và phân quyền chi tiết |
| [x] | 68 | PUT | `/api/seller/shops/{shopId}/roles/{roleId}` | Chỉnh sửa tên và quyền của vai trò tùy chỉnh |
| [x] | 69 | DELETE | `/api/seller/shops/{shopId}/roles/{roleId}` | Xóa vai trò tùy chỉnh |
| [x] | 70 | GET | `/api/seller/shops/{shopId}/members` | Danh sách nhân viên và quyền hạn hiện tại |
| [x] | 71 | POST | `/api/seller/shops/{shopId}/invitations` | Mời nhân viên qua email kèm gán vai trò |
| [x] | 72 | POST | `/api/invitations/{token}/accept` | Nhân viên chấp nhận lời mời tham gia shop |
| [x] | 73 | PATCH | `/api/seller/shops/{shopId}/members/{memberId}/role` | Chủ shop thay đổi vai trò nhân viên |
| [x] | 74 | DELETE | `/api/seller/shops/{shopId}/members/{memberId}` | Chủ shop thu hồi quyền và xóa nhân viên |
| [x] | 75 | GET | `/api/seller/shops/{shopId}/audit` | Nhật ký thao tác nhân sự (Audit Log) |

