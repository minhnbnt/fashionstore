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
| [x] | 23b | PATCH | `/api/seller/orders/{id}/cancel` | Seller hủy đơn kèm lý do, tự động hoàn trả tồn kho nguyên tử |

## Flow 5 — Quản trị tài khoản

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 24 | GET | `/api/admin/stats` | Dashboard: tài khoản theo trạng thái/role, shop + đơn theo trạng thái, GMV, COD đã thu, xu hướng 14 ngày (giờ VN) |
| [x] | 25 | GET | `/api/admin/users?status=&role=&q=&page=&size=` | Tìm/lọc tài khoản phân trang |
| [x] | 26 | GET | `/api/admin/users/{id}` | Chi tiết + shop tham gia + số đơn + audit trail |
| [x] | 27 | PATCH | `/api/admin/users/{id}/lock` | Khóa kèm lý do; tạm khóa shop mà user làm OWNER |
| [x] | 28 | PATCH | `/api/admin/users/{id}/unlock` | Mở khóa tài khoản; không tự mở lại shop |

### Kiểm duyệt sản phẩm (gỡ sản phẩm vi phạm)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 28b | GET | `/api/admin/products?status=&shopId=&q=&page=&size=` | Tìm/lọc sản phẩm toàn sàn; `q` khớp tên/slug, tên shop, SKU |
| [x] | 28c | GET | `/api/admin/products/{id}` | Chi tiết + shop + biến thể + ảnh + lịch sử kiểm duyệt |
| [x] | 28d | PATCH | `/api/admin/products/{id}/ban` | Gỡ kèm `{violationType, reason}`: `ACTIVE`/`HIDDEN` → `BANNED` |
| [x] | 28e | PATCH | `/api/admin/products/{id}/restore` | Khôi phục `BANNED` → `HIDDEN`, shop tự đăng bán lại |

Sản phẩm `BANNED` biến mất khỏi search/PDP, checkout trả `409`; shop không `activate` được nhưng vẫn sửa/xóa mềm được, đơn đang xử lý vẫn tiếp tục. Chi tiết xem `docs/superpowers/specs/2026-10-17-admin-product-moderation-design.md`.

### Nội dung sàn (banner, popup, trang tĩnh, FAQ)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 28f | GET/POST | `/api/admin/banners?placement=` | Danh sách (kèm `scheduleState`) / tạo banner theo vị trí |
| [x] | 28g | PUT/DELETE | `/api/admin/banners/{id}` | Sửa toàn bộ / xóa banner |
| [x] | 28h | GET/POST | `/api/admin/popups` | Danh sách theo ưu tiên / tạo popup |
| [x] | 28i | PUT/DELETE | `/api/admin/popups/{id}` | Sửa / xóa popup |
| [x] | 28j | GET/POST | `/api/admin/pages` | Danh sách trang (không kèm nội dung) / tạo nháp, slug trùng `409` |
| [x] | 28k | GET/PUT/DELETE | `/api/admin/pages/{id}` | Chi tiết / sửa / xóa (chỉ `DRAFT`, còn lại `409`) |
| [x] | 28l | PATCH | `/api/admin/pages/{id}/publish`, `/unpublish` | `DRAFT` ↔ `PUBLISHED` |
| [x] | 28m | GET/POST | `/api/admin/faqs` | Danh sách theo nhóm / thêm câu hỏi |
| [x] | 28n | PUT/DELETE | `/api/admin/faqs/{id}` | Sửa / xóa câu hỏi |
| [x] | 28o | GET | `/api/public/banners?placement=` | Banner đang chạy (bật + trong khung giờ) theo thứ tự |
| [x] | 28p | GET | `/api/public/popups` | Popup đang chạy theo ưu tiên; trình duyệt lọc đối tượng + tần suất, hiện tối đa 1 |
| [x] | 28q | GET | `/api/public/pages`, `/api/public/pages/{slug}` | Link footer / nội dung trang đã xuất bản (nháp `404`) |
| [x] | 28r | GET | `/api/public/faqs` | FAQ đang hiện theo nhóm rồi thứ tự |

Nội dung trang + câu trả lời FAQ lưu Markdown, frontend render không cho HTML thô. Link banner/popup chỉ nhận đường dẫn nội bộ hoặc `http(s)`. Chi tiết xem `docs/superpowers/specs/2026-10-18-admin-site-content-design.md`.

Khóa tài khoản được thực thi hai lớp: `AccountFilter` chặn access token hiện tại ngay ở backend; `GoTrueAdminClient` ban/unban đăng nhập khi có cấu hình `gotrue.service-role-key`. Mọi chuyển trạng thái khóa dòng bằng `SELECT ... FOR UPDATE` và ghi lịch sử trong cùng transaction.

## Flow 6 — Seller Center: Quản lý hồ sơ Shop & Tương tác

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 29 | PATCH | `/api/shops/{id}/profile` | Cập nhật hồ sơ mở rộng (SĐT, địa chỉ kho, ngân hàng, tin nhắn tự động, giờ làm việc) |
| [x] | 30 | POST | `/api/shops/{id}/follow` | Buyer theo dõi shop (tăng followerCount) |
| [x] | 31 | DELETE | `/api/shops/{id}/follow` | Buyer bỏ theo dõi shop (giảm followerCount) |
| [x] | 32 | PATCH | `/api/admin/shops/{id}/suspend` | Admin tạm ngưng shop vi phạm |
| [x] | 33 | PATCH | `/api/admin/shops/{id}/reinstate` | Admin mở lại hoạt động cho shop |

## Flow 7 — Seller Center: Quản lý Sản phẩm, Biến thể & Tồn kho

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 34 | DELETE | `/api/shops/{shopId}/products/{id}` | Xóa mềm sản phẩm (chuyển sang `DELETED`, kiểm tra không có đơn PENDING/SHIPPING) |
| [x] | 35 | DELETE | `/api/shops/{shopId}/products/{productId}/images/{imageId}` | Xóa ảnh sản phẩm |
| [x] | 36 | GET | `/api/seller/shops/{shopId}/products` | Danh sách sản phẩm dạng bảng cho seller (lọc status, từ khóa, phân trang, thống kê tồn/bán) |
| [x] | 37 | GET | `/api/seller/shops/{shopId}/inventory` | Quản lý tồn kho đa biến thể, cảnh báo hết hàng / tồn kho thấp (`lowStockThreshold`) |
| [x] | 38 | PATCH | `/api/seller/shops/{shopId}/inventory/{variantId}` | Điều chỉnh tồn kho tức thời (RESTOCK, DAMAGE, RETURN, AUDIT, MANUAL) kèm ghi log |
| [x] | 39 | GET | `/api/seller/shops/{shopId}/inventory/movements` | Lịch sử biến động tồn kho (Stock Movement Audit Trail) |
| [x] | 40 | GET | `/api/seller/shops/{shopId}/products/export` | Xuất danh sách sản phẩm & tồn kho ra định dạng CSV |
| [x] | 41 | POST | `/api/seller/shops/{shopId}/products/bulk-import` | Nhập hàng loạt sản phẩm từ file CSV (multipart/form-data) |

## Flow 8 — Seller Center: Xử lý Đổi / Trả hàng (Return & Refund)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 42 | POST | `/api/orders/{orderId}/return` | Buyer tạo yêu cầu trả hàng / hoàn tiền cho đơn đã `DELIVERED` |
| [x] | 43 | GET | `/api/orders/returns/mine` | Buyer xem danh sách yêu cầu trả hàng của mình |
| [x] | 44 | GET | `/api/seller/returns` | Seller xem các yêu cầu đổi trả cần xử lý (lọc theo trạng thái) |
| [x] | 45 | PATCH | `/api/seller/returns/{id}/approve` | Seller phê duyệt phương án đổi/trả & hoàn tiền |
| [x] | 46 | PATCH | `/api/seller/returns/{id}/reject` | Seller từ chối yêu cầu kèm lý do |
| [x] | 47 | PATCH | `/api/seller/returns/{id}/complete` | Xác nhận đã nhận lại hàng, tự động hoàn trả tồn kho |

## Flow 9 — Seller Center: Quản lý Doanh thu & Ví Shop (Revenue)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 48 | GET | `/api/seller/shops/{shopId}/revenue/summary` | Tổng quan doanh thu, số đơn, giá trị trung bình đơn, số dư khả dụng & tạm giữ |
| [x] | 49 | GET | `/api/seller/shops/{shopId}/revenue/daily` | Biểu đồ doanh thu theo ngày |
| [x] | 50 | GET | `/api/seller/shops/{shopId}/revenue/top-products` | Top sản phẩm có doanh thu và sản lượng bán cao nhất |
| [x] | 51 | GET | `/api/seller/shops/{shopId}/revenue/settlements` | Danh sách phiên quyết toán đơn hàng (Holding sau 3 ngày hoàn tất) |
| [x] | 52 | POST | `/api/seller/shops/{shopId}/revenue/withdrawals` | Tạo lệnh rút tiền về tài khoản ngân hàng của shop |
| [x] | 53 | GET | `/api/seller/shops/{shopId}/revenue/withdrawals` | Lịch sử rút tiền và tiến độ xử lý |

## Flow 10 — Seller Center: Đánh giá & Phản hồi Khách hàng

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 56 | GET | `/api/seller/shops/{shopId}/reviews` | Seller xem toàn bộ đánh giá của shop (lọc đánh giá chưa phản hồi) |
| [x] | 57 | PATCH | `/api/seller/reviews/{id}/reply` | Seller phản hồi đánh giá của khách hàng |
| [x] | 58 | POST | `/api/seller/reviews/{id}/report` | Seller báo cáo đánh giá vi phạm tiêu chuẩn cộng đồng |

Buyer tạo/sửa/xóa đánh giá đã chuyển hẳn về Flow 14 (`review`, bảng `review` — gộp 1 hệ thống, `product_review` đã drop ở `V11`).

## Flow 11 — Seller Center: Tin nhắn & Chat trực tuyến (CSKH)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 59 | POST | `/api/chat/conversations` | Khởi tạo hội thoại chat giữa khách hàng và shop |
| [x] | 60 | GET | `/api/chat/conversations/buyer` | Danh sách hội thoại của khách hàng |
| [x] | 61 | GET | `/api/chat/conversations/seller` | Danh sách hội thoại tại Seller Center (kèm lọc tin chưa đọc) |
| [x] | 62 | GET | `/api/chat/conversations/{id}/messages` | Lấy lịch sử tin nhắn trong cuộc hội thoại |
| [x] | 63 | POST | `/api/chat/conversations/{id}/messages` | Gửi tin nhắn mới (tự kích hoạt Auto Reply nếu có) |
| [x] | 64 | PATCH | `/api/chat/conversations/{id}/read` | Đánh dấu đã đọc cuộc hội thoại |

## Flow 12 — Trang trí Gian hàng (Shop Decoration) & Quản lý Phân quyền Nhân viên

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 65 | GET | `/api/shops/{shopId}/decoration` | Public giao diện trang trí gian hàng hiển thị cho khách hàng |
| [x] | 66 | GET | `/api/seller/shops/{shopId}/decoration` | Seller xem giao diện gian hàng và bản nháp layout kéo-thả hiện tại |
| [x] | 67 | PUT | `/api/seller/shops/{shopId}/decoration/draft` | Lưu nháp các khối giao diện (Banner, Bộ sưu tập, Flash sale...) |
| [x] | 68 | POST | `/api/seller/shops/{shopId}/decoration/publish` | Xuất bản bố cục nháp thành giao diện chính thức |
| [x] | 69 | DELETE | `/api/seller/shops/{shopId}/decoration/draft` | Hủy bỏ các thay đổi nháp chưa xuất bản |
| [x] | 70 | GET | `/api/seller/shops/{shopId}/roles` | Danh sách các nhóm vai trò mặc định và tùy chỉnh của shop |
| [x] | 71 | POST | `/api/seller/shops/{shopId}/roles` | Chủ shop tạo vai trò mới và phân quyền chi tiết |
| [x] | 72 | PUT | `/api/seller/shops/{shopId}/roles/{roleId}` | Chỉnh sửa tên và quyền của vai trò tùy chỉnh |
| [x] | 73 | DELETE | `/api/seller/shops/{shopId}/roles/{roleId}` | Xóa vai trò tùy chỉnh |
| [x] | 74 | GET | `/api/seller/shops/{shopId}/members` | Danh sách nhân viên và quyền hạn hiện tại |
| [x] | 75 | POST | `/api/seller/shops/{shopId}/invitations` | Mời nhân viên qua email kèm gán vai trò |
| [x] | 76 | POST | `/api/invitations/{token}/accept` | Nhân viên chấp nhận lời mời tham gia shop |
| [x] | 77 | PATCH | `/api/seller/shops/{shopId}/members/{memberId}/role` | Chủ shop thay đổi vai trò nhân viên |
| [x] | 78 | DELETE | `/api/seller/shops/{shopId}/members/{memberId}` | Chủ shop thu hồi quyền và xóa nhân viên |
| [x] | 79 | GET | `/api/seller/shops/{shopId}/audit` | Nhật ký thao tác nhân sự (Audit Log) |

## Flow 13 — Cart (giỏ + checkout nhiều shop)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 80 | GET | `/api/cart` | Xem giỏ (giá/tồn live, chưa có thì giỏ rỗng) |
| [x] | 81 | POST | `/api/cart/items` | Thêm variant (trùng thì cộng dồn) |
| [x] | 82 | PUT | `/api/cart/items/{variantId}` | Đổi số lượng (tối thiểu 1) |
| [x] | 83 | DELETE | `/api/cart/items/{variantId}` | Xóa item |
| [x] | 84 | DELETE | `/api/cart` | Xóa sạch giỏ |
| [x] | 85 | POST | `/api/cart/checkout` | Kiểm tra hết rồi đặt, mỗi shop 1 đơn, hỏng thì 409 kèm từng item lỗi |

Chi tiết luồng này xem `docs/cart.md`. Địa chỉ giao hàng vẫn là text thuần (cart chưa dùng tới sổ địa chỉ `V5__address_book`) — xem mục "Nâng cấp sau" trong đó.

## Flow 14 — Review (đánh giá sau khi nhận hàng)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 86 | PATCH | `/api/orders/{id}/received` | Buyer xác nhận đã nhận (chỉ đơn `DELIVERED`) |
| [x] | 87 | POST | `/api/products/{productId}/reviews` | Viết đánh giá (stars 1–5, đã nhận hàng mới viết được) |
| [x] | 88 | GET | `/api/products/{productId}/reviews` | Public liệt kê, mới nhất trước |
| [x] | 89 | PUT | `/api/products/{productId}/reviews` | Sửa bài của mình |
| [x] | 90 | DELETE | `/api/products/{productId}/reviews` | Xóa bài của mình |

Chi tiết luồng này xem `docs/review.md`.

## Flow 15 — Wishlist (sản phẩm yêu thích)

| Xong | # | Method | Path | Mô tả |
|------|---|--------|------|-------|
| [x] | 91 | GET | `/api/wishlist` | Liệt kê đã lưu, món tạm không mua được vẫn hiện kèm cờ |
| [x] | 92 | POST | `/api/wishlist/items` | Lưu/bỏ lưu (toggle), món ẩn → `404` |
| [x] | 93 | DELETE | `/api/wishlist/items/{productId}` | Xóa khỏi wishlist |

Chi tiết luồng này xem `docs/wishlist.md`.
