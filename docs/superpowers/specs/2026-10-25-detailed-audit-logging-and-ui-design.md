# Tài Liệu Thiết Kế: Cải Tiến Chi Tiết Nhật Ký Hoạt Động (Detailed Audit Logging) & Giao Diện Hoạt Động (Activity Timeline)

- **Ngày tạo:** 2026-10-25
- **Tác giả:** Antigravity (Pair Programming với User)
- **Mục tiêu:** Nâng cấp toàn diện hệ thống Nhật ký hoạt động (Audit Trail) của Kênh người bán:
  1. Ghi chi tiết chính xác: **Ai đã làm gì, sửa cái gì, sửa dữ liệu nào (trước -> sau)** cho cả 4 nhóm nghiệp vụ: Sản phẩm & Kho, Đơn hàng & Đổi trả, Phân quyền & Nhân sự, CSKH & Tài chính.
  2. Nâng cấp giao diện hiển thị tại Frontend (`SellerStaffTab.tsx`) theo phong cách **Activity Timeline cao cấp**, phân nhóm màu sắc/icon theo loại hành động, Việt hóa đối tượng, hiển thị hộp chi tiết thay đổi nổi bật và bổ sung tìm kiếm nhanh từ khóa.

---

## 1. Yêu cầu & Phân Tích Hiện Trạng

### 1.1. Hiện trạng Backend
- Bảng `shop_member_audit` đã có các trường: `id`, `shop_id`, `member_id`, `action`, `target_type`, `target_id`, `detail`, `created_at`.
- Tuy nhiên, nhiều nghiệp vụ trước đây chỉ ghi dữ liệu tối thiểu vào `detail`:
  - `PRODUCT_UPDATE`: Chỉ ghi mỗi tên sản phẩm (không biết sửa giá, sửa tên, sửa danh mục, hay sửa phân loại nào).
  - `PRODUCT_STATUS`: Chỉ ghi tên sản phẩm (không biết chuyển từ trạng thái gì sang gì).
  - `UPDATE_ROLE`: Chỉ ghi `"Cập nhật vai trò: X (N quyền)"` (không biết thêm quyền gì, bỏ quyền gì).
  - `ORDER_CANCEL`: Chỉ ghi mã đơn (không biết lý do hủy đơn).
  - `ORDER_SHIP`: Chỉ ghi mã đơn (không ghi đơn vị vận chuyển / mã vận đơn).
  - `CHAT_REPLY`: Ghi `null` detail.
  - `INVENTORY_ADJUST`: Một số nhánh điều chỉnh kho chưa ghi rõ số lượng cũ -> mới.

### 1.2. Hiện trạng Frontend
- Danh sách Audit Log chỉ là text đơn giản xếp chồng.
- Mọi hành động dùng chung 1 badge màu tím (`bg-indigo-50 border-indigo-200 text-indigo-700`).
- `targetType` in tiếng Anh thô: `SHOP_MEMBER`, `RETURN_REQUEST`, `SHOP_ROLE`...
- Chưa có ô tìm kiếm từ khóa trong nhật ký.
- Thiếu nhãn cho `REVIEW_REPORT` trong `PERMISSION_LABELS`.

---

## 2. Thiết Kế Chi Tiết Dữ Liệu Nhật Ký (Backend)

Cấu trúc nội dung trường `detail` được chuẩn hóa theo mẫu trực quan:
`[Hành động chính]: [Đối tượng tác động] | [Chi tiết các trường thay đổi cụ thể]`

### 2.1. Nhóm Sản Phẩm & Kho (`CatalogService.java`)
1. **Tạo sản phẩm (`PRODUCT_CREATE`):**
   - Format: `Tạo sản phẩm: "[Tên]" | Danh mục: [Tên danh mục] | Giá: [minPrice]₫ | Tồn kho ban đầu: [totalStock] ([N] phân loại)`
2. **Cập nhật sản phẩm (`PRODUCT_UPDATE`):**
   - So sánh trạng thái cũ (`existing`) và dữ liệu mới (`request`):
     - Tên thay đổi: `Tên: "[Cũ]" -> "[Mới]"`
     - Giá thay đổi: `Giá: [Cũ]₫ -> [Mới]₫`
     - Danh mục thay đổi: `Danh mục: [Cũ] -> [Mới]`
     - Mô tả thay đổi: `Cập nhật mô tả sản phẩm`
     - Phân loại (variants): `Cập nhật [N] phân loại`
   - Format: `Cập nhật sản phẩm: "[Tên]" | Thay đổi: [Danh sách trường thay đổi]` (Nếu không đổi thông tin chung: `Cập nhật thông tin chi tiết sản phẩm`)
3. **Đổi trạng thái sản phẩm (`PRODUCT_STATUS` & `PRODUCT_STATUS_BULK`):**
   - Format: `Đổi trạng thái sản phẩm "[Tên]": [[Trạng thái cũ]] -> [[Trạng thái mới]]`
4. **Xóa sản phẩm (`PRODUCT_DELETE`):**
   - Format: `Xóa sản phẩm: "[Tên]" (Mã ID: [ID])`
5. **Xóa ảnh sản phẩm (`PRODUCT_IMAGE_DELETE`):**
   - Format: `Xóa ảnh sản phẩm: "[Tên]" (Vị trí thứ [position + 1])`
6. **Điều chỉnh kho (`INVENTORY_ADJUST` / `MANUAL` / `RESTOCK`):**
   - Format: `Điều chỉnh tồn kho sản phẩm "[Tên]" (Phân loại: [Size/Màu]): [Tồn cũ] -> [Tồn mới] ([+/-Delta]). Lý do: [Lý do]`

### 2.2. Nhóm Đơn Hàng & Đổi Trả (`OrderService.java` & `ReturnRequestService.java`)
1. **Xác nhận đơn hàng (`ORDER_CONFIRM`):**
   - Format: `Xác nhận đơn hàng #[Mã đơn] | Tổng thanh toán: [Tiền]₫ ([N] sản phẩm)`
2. **Giao đơn hàng (`ORDER_SHIP`):**
   - Format: `Giao đơn hàng #[Mã đơn] cho đơn vị vận chuyển` (kèm đơn vị VC / mã vận đơn nếu có)
3. **Giao hàng thành công (`ORDER_DELIVER`):**
   - Format: `Đánh dấu đơn hàng #[Mã đơn] giao thành công`
4. **Hủy đơn hàng (`ORDER_CANCEL`):**
   - Format: `Hủy đơn hàng #[Mã đơn]. Lý do: [Lý do hủy] | Khôi phục tồn kho cho [N] mặt hàng`
5. **Duyệt đổi trả (`RETURN_APPROVE`):**
   - Format: `Duyệt yêu cầu đổi trả đơn #[Mã đơn] | Phương án: [Hoàn tiền / Đổi hàng] | Số tiền hoàn: [Tiền]₫`
6. **Từ chối đổi trả (`RETURN_REJECT`):**
   - Format: `Từ chối yêu cầu đổi trả đơn #[Mã đơn]. Lý do từ chối: [Lý do]`
7. **Hoàn tất đổi trả (`RETURN_COMPLETE`):**
   - Format: `Hoàn tất nhận hàng đổi trả đơn #[Mã đơn] và nhập lại kho`

### 2.3. Nhóm Phân Quyền & Nhân Sự (`ShopStaffService.java`)
1. **Tạo vai trò (`CREATE_ROLE`):**
   - Format: `Tạo vai trò mới: "[Tên vai trò]" gồm [N] quyền: [Danh sách tên quyền tiếng Việt]`
2. **Cập nhật vai trò (`UPDATE_ROLE`):**
   - So sánh quyền cũ và quyền mới:
     - Quyền thêm mới: `+Quyền A, +Quyền B`
     - Quyền bị gỡ: `-Quyền C`
     - Đổi tên: `Đổi tên: "[Tên cũ]" -> "[Tên mới]"`
   - Format: `Cập nhật vai trò "[Tên vai trò]": [Danh sách thay đổi]`
3. **Xóa vai trò (`DELETE_ROLE`):**
   - Format: `Xóa vai trò: "[Tên vai trò]"`
4. **Mời nhân viên (`INVITE_STAFF`):**
   - Format: `Gửi lời mời tham gia tới: [Email] với vai trò "[Tên vai trò]"`
5. **Thu hồi lời mời (`REVOKE_INVITATION`):**
   - Format: `Thu hồi lời mời gửi tới tài khoản: [Email]`
6. **Đổi vai trò thành viên (`CHANGE_MEMBER_ROLE`):**
   - Format: `Đổi vai trò thành viên [Email] từ "[Vai trò cũ]" sang "[Vai trò mới]"`
7. **Kích hoạt / Vô hiệu hóa (`ACTIVATE_MEMBER` / `DEACTIVATE_MEMBER`):**
   - Format: `Kích hoạt lại tài khoản thành viên: [Email]` / `Vô hiệu hóa tài khoản thành viên: [Email]`

### 2.4. Nhóm CSKH & Tài Chính (`ReviewService.java`, `ChatService.java`, `RevenueService.java`)
1. **Phản hồi đánh giá (`REVIEW_REPLY`):**
   - Format: `Phản hồi đánh giá [N] sao đơn #[Mã đơn]: "[Nội dung phản hồi]"`
2. **Báo cáo đánh giá (`REVIEW_REPORT`):**
   - Format: `Báo cáo đánh giá vi phạm (Đơn #[Mã đơn]). Lý do: [Lý do]`
3. **Phản hồi chat (`CHAT_REPLY`):**
   - Format: `Gửi tin nhắn phản hồi tới khách hàng trong hội thoại #[Mã hội thoại]`
4. **Rút tiền (`WITHDRAWAL_CREATE`):**
   - Format: `Yêu cầu rút tiền: [Số tiền]₫ về tài khoản [Ngân hàng] - STK [STK]`

---

## 3. Thiết Kế Giao Diện Activity Timeline (Frontend)

### 3.1. Phân loại màu sắc & Icon (`sellerPermissions.ts`)
Tạo hàm helper `getAuditActionMeta(action: string)` trả về:
- **Badge Type:**
  - `danger` (Đỏ/Hồng - `rose`): `PRODUCT_DELETE`, `PRODUCT_IMAGE_DELETE`, `ORDER_CANCEL`, `RETURN_REJECT`, `REVOKE_INVITATION`, `DELETE_ROLE`, `DEACTIVATE_MEMBER`, `REVIEW_REPORT`. Icon: `Trash`, `XCircle`, `Prohibit`.
  - `success` (Xanh ngọc - `emerald`): `ORDER_DELIVER`, `RETURN_APPROVE`, `RETURN_COMPLETE`, `ACCEPT_INVITATION`, `ACTIVATE_MEMBER`, `DECORATION_PUBLISH`. Icon: `CheckCircle`, `Check`.
  - `info` (Chàm/Xanh dương - `indigo`/`blue`): `CREATE_ROLE`, `UPDATE_ROLE`, `CHANGE_MEMBER_ROLE`, `INVITE_STAFF`, `ORDER_CONFIRM`, `ORDER_SHIP`. Icon: `ShieldCheck`, `UserPlus`, `Key`, `Truck`.
  - `warning` (Vàng/Cam - `amber`): `INVENTORY_ADJUST`, `WITHDRAWAL_CREATE`, `PRODUCT_STATUS`, `PRODUCT_UPDATE`. Icon: `Package`, `Wallet`, `ArrowsClockwise`.
  - `neutral` (Xám/Tím - `violet`): `CHAT_REPLY`, `REVIEW_REPLY`, `DECORATION_SAVE`, `PRODUCT_CREATE`. Icon: `ChatDots`, `Star`.

### 3.2. Việt hóa Đối tượng (`formatTargetType`)
- `SHOP_MEMBER` -> `Thành viên`
- `STAFF_INVITATION` -> `Lời mời`
- `SHOP_ROLE` -> `Vai trò`
- `PRODUCT` -> `Sản phẩm`
- `ORDER` -> `Đơn hàng`
- `RETURN` / `RETURN_REQUEST` -> `Đổi trả`
- `REVIEW` -> `Đánh giá`
- `WITHDRAWAL` -> `Rút tiền`
- `CONVERSATION` -> `Hội thoại Chat`
- `DECORATION` -> `Trang trí shop`

### 3.3. Cải tiến Card Timeline trong `SellerStaffTab.tsx`
- Mỗi item trong danh sách là một thẻ hoạt động sang trọng (`border border-zinc-200/80 bg-white/70 dark:bg-white/[0.02]`):
  - Bên trái: Avatar người thực hiện (nếu là hệ thống: icon `Robot`, nếu là người dùng: Avatar chữ cái đầu với màu sắc theo tên).
  - Giữa:
    - Hàng 1: Badge hành động phân loại màu + Tên người thực hiện + Email + Tag đối tượng tiếng Việt.
    - Hàng 2: Khối `detail` chứa chi tiết thay đổi cụ thể (trước -> sau), định dạng font chữ rõ nét, màu tương phản tốt.
  - Bên phải: Thời gian thao tác có icon `Clock`, hiển thị ngày giờ rõ ràng.
- **Thanh công cụ Nhật ký:**
  - Dropdown lọc theo loại hành động.
  - Ô tìm kiếm từ khóa (`auditSearchQuery`) lọc tức thì theo email người thực hiện hoặc nội dung thay đổi trong trang.
