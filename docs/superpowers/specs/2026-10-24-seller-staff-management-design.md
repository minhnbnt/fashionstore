# Thiết kế: Quản lý tài khoản nhân viên (2.8.1)

**Ngày:** 2026-10-24
**Phạm vi:** Seller Center — mời nhân viên, phân quyền RBAC, vô hiệu hóa/kích hoạt, nhật ký hoạt động.

## 1. Mục tiêu

Chủ shop có thể:

1. Mời nhân viên bằng email tài khoản đã đăng ký trên website.
2. Gán vai trò với 5 nhóm quyền: sản phẩm, đơn hàng, chat/đánh giá, xem thống kê, tài chính.
3. Vô hiệu hóa / kích hoạt lại tài khoản nhân viên (không xóa cứng).
4. Xem nhật ký "ai đã làm gì, lúc nào" trên toàn bộ hoạt động của shop.

Nhân viên nhận thông báo **trong website** (không gửi email) và chấp nhận lời mời bằng link trong thông báo.

## 2. Hiện trạng

Đã có sẵn, giữ nguyên và hoàn thiện:

- `V8__seller_center.sql`: `shop_role`, `shop_member` (`role_id`, `status`), `shop_invitation`, `shop_member_audit`.
- `ShopStaffService` + `SellerStaffController` + `StaffInvitationController`: CRUD vai trò, danh sách thành viên, mời, accept, đổi vai trò, xóa, audit.
- UI `SellerStaffTab`: danh sách thành viên, vai trò, audit, modal mời, modal tạo vai trò.

Khoảng trống:

| # | Vấn đề | Hệ quả |
|---|--------|--------|
| 1 | `ShopAccessChecker` chỉ check `OWNER/STAFF`, bỏ qua `shop_role.permissions` | STAFF làm được mọi thứ, kể cả rút tiền |
| 2 | Mời chỉ tạo token, không có thông báo | Nhân viên không biết được mời |
| 3 | Accept không kiểm tra email người nhận, không chống trùng | Ai có token cũng vào được shop |
| 4 | `DELETE` xóa cứng `shop_member` | Mất lịch sử, không khôi phục được |
| 5 | Audit chỉ ghi thao tác nhân sự | Không trả lời được "ai sửa sản phẩm / hủy đơn / rút tiền" |
| 6 | UI không gate theo quyền, member hiện `userId` thay vì email | STAFF thấy menu không được phép, khó dùng |

## 3. Quyết định thiết kế

### 3.1. Mời qua email + thông báo trong website

- Chủ shop nhập email nhân viên **đã đăng ký tài khoản** trên website.
- Backend tra `user_profile` theo email; nếu chưa có thì tra GoTrue Admin API (khi có `gotrue.service-role-key`) và tạo mirror `user_profile`.
- Không tìm thấy tài khoản → `404` với message `"No registered account with this email"`.
- Tạo `shop_invitation` + `user_notification` cho người được mời. Không gửi SMTP.
- Thông báo có link `/invitations/{token}`. Nhân viên đăng nhập đúng email, mở link, bấm chấp nhận.
- Accept kiểm tra: token tồn tại, chưa hết hạn, chưa accept, chưa thu hồi, `user_id` hiện tại khớp `invitee_id` (hoặc email nếu lời mời cũ chưa có `invitee_id`).
- Chủ shop có thể thu hồi lời mời đang chờ; thu hồi thì xóa thông báo tương ứng.

**Giả định:** chỉ mời được tài khoản đã tồn tại. Nếu muốn mời trước khi đăng ký, mở rộng sau bằng cách giữ lời mời `PENDING` và match khi user đăng ký.

### 3.2. RBAC giữ nguyên, thêm enforce ở backend

- Giữ 4 vai trò hệ thống + vai trò tùy chỉnh.
- Thêm enum `ShopPermission`; `shop_role.permissions` lưu tên enum.
- `OWNER` bypass toàn bộ. `STAFF` có `role_id = null` → **không có quyền nào** (fail closed).
- Quyền lạ trong DB bị bỏ qua, không làm crash.
- Enforce tại service bằng `ShopAccessChecker.requireShopPermission(...)`; controller không tự check.

Bảng quyền:

| Nhóm yêu cầu | Permission |
|---|---|
| Quản lý sản phẩm | `PRODUCT_READ`, `PRODUCT_WRITE` |
| Quản lý tồn kho | `INVENTORY_READ`, `INVENTORY_WRITE` |
| Quản lý đơn hàng | `ORDER_READ`, `ORDER_PROCESS`, `ORDER_CANCEL`, `RETURN_PROCESS` |
| Trả lời chat/đánh giá | `CHAT_ACCESS`, `REVIEW_READ`, `REVIEW_REPLY` |
| Xem thống kê (chỉ xem) | `REVENUE_READ` |
| Quản lý tài chính | `REVENUE_READ` + `REVENUE_WITHDRAW` |
| Trang trí gian hàng | `DECORATION_WRITE` |

Vai trò mặc định sau migration:

- **Quản lý:** tất cả quyền.
- **CSKH:** `ORDER_READ`, `REVIEW_READ`, `REVIEW_REPLY`, `CHAT_ACCESS`.
- **Kho vận:** `ORDER_READ`, `ORDER_PROCESS`, `INVENTORY_READ`, `INVENTORY_WRITE`.
- **Marketing:** `PRODUCT_READ`, `DECORATION_WRITE`, `REVENUE_READ`.

### 3.3. Vô hiệu hóa thay vì xóa cứng

- `DELETE /members/{memberId}` chuyển thành vô hiệu hóa (`status = 'DEACTIVATED'`).
- Thêm `PATCH /members/{memberId}/status` với `{status: ACTIVE|DEACTIVATED}` để kích hoạt lại.
- Member `DEACTIVATED` bị `ShopAccessChecker` từ chối ngay ở request kế tiếp.
- Không xóa cứng trong phạm vi này. OWNER không thể bị vô hiệu hóa/đổi vai trò.
- Xóa vai trò tùy chỉnh: `shop_member.role_id` về `NULL` (FK `ON DELETE SET NULL`) → nhân viên mất quyền, an toàn.

### 3.4. Nhật ký hoạt động dùng chung

- Tái sử dụng `shop_member_audit` làm **shop activity log**; cột `member_id` là **actor**.
- Thêm `ShopAuditService.record(shopId, actorId, action, targetType, targetId, detail)`.
- Ghi cho: sản phẩm, tồn kho, đơn hàng, trả hàng, chat, đánh giá, rút tiền, trang trí, nhân sự.
- API `GET /audit` trả `PageResponse<ShopActivityResponse>` có `actorEmail`, `actorName`, filter `action`, phân trang.
- Các log chuyên biệt hiện có (`product_audit_log`, `order_status_history`, `stock_movement`) vẫn giữ nguyên; activity log là read model thống nhất cho chủ shop.

### 3.5. Thông báo trong website

Bảng `user_notification` tối giản:

- `type`: `SHOP_INVITATION` (mở rộng sau).
- `title`, `body`, `link`, `ref_id`, `read_at`, `created_at`.
- API: list phân trang, unread count, mark read, mark all read.
- UI: `NotificationBell` dùng chung ở Header storefront và SellerLayout; badge số chưa đọc, dropdown, bấm vào đánh dấu đã đọc và điều hướng.

## 4. Thay đổi dữ liệu (V28)

```sql
ALTER TABLE shop_invitation ADD COLUMN invitee_id UUID REFERENCES user_profile(id) ON DELETE CASCADE;
ALTER TABLE shop_invitation ADD COLUMN revoked_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_shop_invitation_pending
    ON shop_invitation (shop_id, lower(email))
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

CREATE TABLE user_notification (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    link TEXT,
    ref_id UUID,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_user ON user_notification (user_id, read_at, created_at DESC);

CREATE INDEX idx_shop_audit_shop_created ON shop_member_audit (shop_id, created_at DESC);

-- Cập nhật quyền cho 4 vai trò hệ thống đã tồn tại.
UPDATE shop_role SET permissions = ARRAY[...] WHERE is_system = TRUE AND name = 'Quản lý (Manager)';
-- ... CSKH, Kho vận, Marketing
```

## 5. API

### Mới

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/seller/shops/{shopId}/my-permissions` | member active | `{isOwner, permissions}` của user hiện tại |
| GET | `/api/seller/shops/{shopId}/invitations` | OWNER | Lời mời đang chờ |
| DELETE | `/api/seller/shops/{shopId}/invitations/{id}` | OWNER | Thu hồi lời mời |
| PATCH | `/api/seller/shops/{shopId}/members/{memberId}/status` | OWNER | `{status: ACTIVE\|DEACTIVATED}` |
| GET | `/api/invitations/{token}` | authenticated | Chi tiết lời mời cho trang accept |
| GET | `/api/notifications?page=&size=` | authenticated | Thông báo của chính mình |
| GET | `/api/notifications/unread-count` | authenticated | `{unread}` |
| PATCH | `/api/notifications/{id}/read` | authenticated | Đánh dấu đã đọc |
| PATCH | `/api/notifications/read-all` | authenticated | Đánh dấu tất cả |

### Sửa

| Method | Path | Thay đổi |
|---|---|---|
| POST | `/api/seller/shops/{shopId}/invitations` | Bắt buộc email có tài khoản; tạo notification; trả `StaffInvitationResponse` |
| POST | `/api/invitations/{token}/accept` | Kiểm tra email/invitee, chống trùng, 410 khi hết hạn/thu hồi |
| DELETE | `/api/seller/shops/{shopId}/members/{memberId}` | Vô hiệu hóa thay vì xóa |
| GET | `/api/seller/shops/{shopId}/audit` | `PageResponse<ShopActivityResponse>` + filter `action`, có actor email/name |

## 6. UI

- `NotificationBell` dùng chung; thêm vào `Header` và `SellerLayout`.
- Trang `/invitations/:token` (RequireAuth): hiện shop, vai trò, trạng thái; nút chấp nhận; báo lỗi nếu sai email.
- `SellerStaffTab`:
  - Member hiện email thật, badge `ACTIVE/DEACTIVATED`.
  - Nút vô hiệu hóa / kích hoạt lại.
  - Danh sách lời mời đang chờ + thu hồi.
  - Sửa/xóa vai trò tùy chỉnh.
  - Audit hiện actor email + thời gian + filter.
- `SellerLayout` + `ShopDashboard`: ẩn menu/tab và nút hành động theo `my-permissions`; chặn URL tab không có quyền.
- Header: hiện link "Kênh Người Bán" khi user là thành viên shop (không chỉ khi GoTrue role = `shop`).

## 7. Không nằm trong phạm vi

- Gửi email SMTP/template.
- Mời email chưa đăng ký (chờ đăng ký rồi tự match).
- Chuyển quyền OWNER, nhiều OWNER/shop.
- Phân quyền theo từng sản phẩm/đơn cụ thể.
- Push notification realtime (WebSocket); UI poll mỗi 30 giây.

## 8. Rủi ro / điểm cần kiểm

1. STAFF có `role_id = null` phải bị chặn, không được fallback "cho qua".
2. Vô hiệu hóa phải có hiệu lực ngay, không cần đăng nhập lại.
3. Token mời phải gắn đúng người; sai email → `403`, không tạo member.
4. Sửa/xóa role phải có hiệu lực ngay với member đang dùng role đó.
5. OWNER không thể tự vô hiệu hóa/đổi vai trò.
6. Quyền lạ trong DB không làm crash; log cảnh báo và bỏ qua.
