# Module Quản trị — tài khoản khách hàng, shop, duyệt shop mới

## Mục tiêu

Admin vận hành sàn như các marketplace lớn (Shopee/Lazada/Tiki Seller Center):

1. **Duyệt shop mới** theo hàng đợi: cũ nhất trước, duyệt hoặc từ chối **kèm lý do**; shop bị từ chối sửa hồ sơ rồi **gửi duyệt lại**.
2. **Quản lý shop** đang hoạt động: tìm kiếm, lọc trạng thái, **tạm khóa** (kèm lý do) và **mở lại**.
3. **Quản lý tài khoản khách hàng**: tìm kiếm, lọc, xem chi tiết, **khóa/mở khóa** (kèm lý do).
4. Mọi thao tác kiểm duyệt có **lịch sử (audit trail)**: ai làm, lúc nào, từ trạng thái nào sang trạng thái nào, lý do.

Ngoài phạm vi: KYC giấy tờ (CCCD, giấy phép kinh doanh), phân quyền nhiều cấp admin, gửi email thông báo, tự hủy đơn khi shop bị khóa.

## Máy trạng thái

Shop (enum có sẵn từ V1):

```
PENDING ──approve──▶ APPROVED ──suspend(reason)──▶ SUSPENDED
   │                    ▲                              │
   └─reject(reason)─▶ REJECTED        └────reinstate───┘
                        │
                        └─resubmit (OWNER)──▶ PENDING
```

- Chỉ `APPROVED` mới bán: trang shop public, search, PDP, checkout, tạo/sửa sản phẩm đều đã chặn shop khác `APPROVED`.
- Shop `SUSPENDED` vẫn xử lý được đơn đang có (confirm/ship/deliver chỉ check thành viên), để người mua không bị treo đơn.
- `status_reason` lưu lý do gần nhất (REJECTED/SUSPENDED), xóa khi về `PENDING`/`APPROVED`. Owner thấy lý do qua `ShopResponse.statusReason`.
- `submitted_at` = lần nộp gần nhất, hàng đợi duyệt sắp `submitted_at ASC` (FIFO).

Tài khoản (`user_profile`):

```
ACTIVE ──lock(reason)──▶ LOCKED ──unlock──▶ ACTIVE
```

- Không khóa được chính mình, không khóa được tài khoản `ADMIN`.
- Khóa owner thì tự **tạm khóa** mọi shop `APPROVED` họ làm OWNER (lý do ghi "Owner account locked: ..."). Mở khóa tài khoản **không** tự mở shop, admin xem xét mở riêng.

## Thực thi khóa tài khoản (2 lớp)

1. **Backend, tức thì**: `AccountFilter` chạy sau khi xác thực JWT. Tài khoản `LOCKED` bị trả `403` (`ProblemDetail`, `code=ACCOUNT_LOCKED`) cho mọi API, trừ `GET /api/users/me` (để UI hiện lý do) và các GET public.
2. **GoTrue**: gọi Admin API `PUT /admin/users/{id}` với `ban_duration` để chặn đăng nhập và refresh token. Cần `gotrue.service-role-key` (JWT `role=service_role` ký bằng key GoTrue; dev đã cấu hình sẵn trong `config/springboot.yml`). Key trống thì bỏ qua lớp này và log cảnh báo, lớp 1 vẫn chặn.

Gọi GoTrue lỗi thì rollback transaction và trả `502`, để DB và GoTrue không lệch nhau.

## Role

- `user_profile.role` (`CUSTOMER` | `SHOP` | `ADMIN`) mirror từ claim `app_metadata.role`, **chỉ nâng, không hạ** khi đồng bộ từ token.
- Duyệt shop: owner đang `CUSTOMER` được nâng lên `SHOP` trong DB và GoTrue (`app_metadata.role = "shop"`), FE refresh session để nhận role mới.

## Tài khoản admin đầu tiên (bootstrap)

`features/admin/AdminAccountBootstrap` chạy lúc khởi động khi có `app.bootstrap-admin.email` (giống `KEYCLOAK_ADMIN`, `GF_SECURITY_ADMIN_USER`):

- Email chưa có trong GoTrue: tạo qua `POST /admin/users` (`email_confirm=true`, `app_metadata.role=admin`), đăng nhập được ngay.
- Email đã có: chỉ gán lại `app_metadata.role=admin`, **không đổi mật khẩu**.
- Cả hai trường hợp đều mirror `user_profile` với role `ADMIN`.
- Thiếu `gotrue.service-role-key`, thiếu mật khẩu khi cần tạo, hay GoTrue chưa lên: log rồi bỏ qua, app vẫn khởi động.

| Property | Env | Dev mặc định |
|---|---|---|
| `app.bootstrap-admin.email` | `ADMIN_EMAIL` | `admin@fashionstore.local` |
| `app.bootstrap-admin.password` | `ADMIN_PASSWORD` | `Admin@123456` |
| `gotrue.service-role-key` | `GOTRUE_SERVICE_ROLE_KEY` | JWT `role=service_role` ký bằng khóa dev công khai |

Production: không dùng giá trị dev; ký service-role key bằng khóa GoTrue thật và truyền mật khẩu qua secret.

## JIT provisioning

UI chưa gọi `POST /api/users`, nên `AccountFilter` tự tạo `user_profile` từ claim JWT (`email`, `user_metadata.full_name`/`name`, fallback phần trước `@` của email) ở request xác thực đầu tiên (`INSERT ... ON CONFLICT DO NOTHING`).

## API

| Method | Path | Mô tả |
|---|---|---|
| GET | `/api/admin/stats` | Đếm user theo trạng thái/role, shop + đơn theo trạng thái, GMV + COD đã thu, `dailyTrend` 14 ngày (user mới, shop mới, số đơn, giá trị đơn chưa hủy; mốc ngày theo `Asia/Ho_Chi_Minh`) |
| GET | `/api/admin/shops?status=&q=&page=&size=` | Danh sách phân trang, `q` tìm tên/slug/email owner |
| GET | `/api/admin/shops/{id}` | Chi tiết + owner + lịch sử |
| PATCH | `/api/admin/shops/{id}/approve` | PENDING → APPROVED |
| PATCH | `/api/admin/shops/{id}/reject` | body `{reason}`, PENDING → REJECTED |
| PATCH | `/api/admin/shops/{id}/suspend` | body `{reason}`, APPROVED → SUSPENDED |
| PATCH | `/api/admin/shops/{id}/reinstate` | SUSPENDED → APPROVED |
| POST | `/api/shops/{id}/resubmit` | OWNER: REJECTED → PENDING |
| GET | `/api/admin/users?status=&role=&q=&page=&size=` | Danh sách phân trang, `q` tìm email/tên |
| GET | `/api/admin/users/{id}` | Chi tiết + shop tham gia + số đơn + lịch sử |
| PATCH | `/api/admin/users/{id}/lock` | body `{reason}` |
| PATCH | `/api/admin/users/{id}/unlock` | |

Lý do: bắt buộc, 5–500 ký tự. Trạng thái sai thì `409`, khóa admin hoặc tự khóa mình thì `409`.

Đồng thời: transaction kiểm duyệt khóa dòng bằng `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`), hai admin bấm cùng lúc thì người sau nhận `409`.

## Code

- `features/admin/` (phẳng): controller + service điều phối (`AdminShopService`, `AdminUserService`), gọi `ShopService`, `UserService`, `OrderService`, `GoTrueAdminClient`. Domain service không gọi chéo nhau, tránh vòng phụ thuộc.
- `features/shop/`: luật trạng thái + `shop_status_history` + query admin.
- `features/identity/`: luật khóa + `user_status_history` + query admin + provisioning.
- `infrastructure/GoTrueAdminClient`: `RestClient`, test bằng `MockRestServiceServer`.
- Migration `V6__admin_moderation.sql` (V4 bỏ trống, V5 đã chạy).

## UI (`fashionstore-ui`)

- `/admin`: tổng quan (thẻ số liệu người dùng/shop/đơn/doanh thu, biểu đồ cột 14 ngày chọn chỉ số, phân bổ theo trạng thái, 5 shop chờ duyệt cũ nhất).
- `/admin/shops`: tab trạng thái (Chờ duyệt / Đang hoạt động / Tạm khóa / Từ chối / Tất cả), ô tìm kiếm, bảng phân trang. `/admin/shops/:id`: hồ sơ, owner, lịch sử, các nút hành động.
- `/admin/users`, `/admin/users/:id`: tương tự cho khách hàng.
- Hộp thoại lý do: có lý do mẫu để chọn nhanh + ô ghi chú, bắt buộc với từ chối/khóa.
- `/account`: mục "Shop của tôi" hiện trạng thái + lý do; shop bị từ chối có nút "Gửi duyệt lại". Tài khoản bị khóa thì hiện banner lý do.
