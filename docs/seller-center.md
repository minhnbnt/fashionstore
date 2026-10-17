# Tài Liệu Kỹ Thuật Kênh Người Bán (Seller Center)

Hệ thống Kênh Người Bán (Seller Center) của **FashionStore Marketplace** được xây dựng theo mô hình thương mại điện tử chuyên nghiệp (tham chiếu kiến trúc Shopee / Lazada / TikTok Shop Seller Center), phục vụ toàn bộ chu trình hoạt động của các gian hàng.

---

## 1. Tổng quan Kiến trúc & Miền Nghiệp vụ (7 Modules)

```
                            [ SELLER CENTER ]
                                   │
      ┌───────────┬───────────┬────┴──────┬───────────┬───────────┐
      ▼           ▼           ▼           ▼           ▼           ▼
1. Hồ sơ Shop 2. Sản phẩm  3. Đơn hàng 4. Doanh thu 5. CSKH   6. Trang trí
   & KYC         & Tồn kho    & Đổi trả   & Ví Shop   (Review/Chat) & Nhân sự
```

1. **Hồ sơ Shop & KYC (`features/shop`)**: Quản lý thông tin định danh, địa chỉ kho nhận hàng trả, chính sách đổi trả, thời gian chuẩn bị hàng (processing days), tài khoản ngân hàng, tin nhắn tự động CSKH, theo dõi gian hàng (Followers), đình chỉ/mở lại gian hàng bởi Admin.
2. **Sản phẩm & Tồn kho (`features/catalog`)**: Quản lý vòng đời sản phẩm (`ACTIVE`, `INACTIVE`, `DELETED`), quản lý biến thể đa kích cỡ/màu sắc, cảnh báo tồn kho thấp (`low_stock_threshold`), điều chỉnh kho tức thời kèm sổ nhật ký biến động (`stock_movement`), xuất/nhập danh mục sản phẩm hàng loạt (CSV bulk import/export).
3. **Đơn hàng & Đổi/Trả hàng (`features/order`)**: Quy trình xác nhận và giao hàng (`PENDING` -> `CONFIRMED` -> `SHIPPING` -> `DELIVERED`), hủy đơn an toàn kèm hoàn kho nguyên tử, quản lý yêu cầu trả hàng / hoàn tiền (`return_request`) với các bước xem xét chứng từ, phê duyệt phương án hoặc từ chối.
4. **Quản lý Doanh thu & Ví Người bán (`features/order`)**: Quyết toán tự động sau khi giao hàng thành công (`seller_settlement`) với thời gian tạm giữ 3 ngày đối soát khiếu nại, biểu đồ doanh thu theo ngày, thống kê sản phẩm bán chạy nhất, quản lý số dư và lệnh rút tiền (`seller_withdrawal`).
5. **Đánh giá & CSKH (`features/review`, `features/chat`)**: Đánh giá sản phẩm sau khi nhận hàng (1 bài/product/buyer, sửa/xóa được), cơ chế phản hồi và khiếu nại đánh giá xấu, hệ thống nhắn tin trực tuyến Buyer - Shop kèm cơ chế tự động trả lời (Auto-reply).
6. **Trang trí Gian hàng (`features/shop`)**: Trình thiết kế gian hàng linh hoạt dựa trên khối JSON layout (Banner Carousel, Bộ sưu tập nổi bật, Flash Sale, Video), hỗ trợ cơ chế lưu nháp (Draft Preview) và xuất bản chính thức (Publish).
7. **Phân quyền Nhân sự Shop (`features/shop`)**: Mô hình RBAC tùy biến theo nhu cầu từng gian hàng (Quản lý, CSKH, Kho vận, Marketing), mời nhân viên qua email bảo mật bằng token, phân quyền chi tiết từng chức năng, lưu vết hành động nhân viên (Audit Log).

---

## 2. Quy trình Nghiệp vụ Chi tiết

### 2.1. Đăng ký & Hoàn thiện Hồ sơ Shop
- Người bán đăng ký shop -> Trạng thái ban đầu `PENDING`.
- Admin xét duyệt hồ sơ -> Chuyển thành `APPROVED`.
- Người bán cập nhật hồ sơ:
  - **Địa chỉ kho & SĐT**: Cung cấp cho đơn vị vận chuyển lấy hàng và nhận hàng đổi trả.
  - **Thời gian chuẩn bị hàng**: Mặc định 2 ngày.
  - **Tài khoản ngân hàng**: Phục vụ việc rút tiền từ ví doanh thu.
  - **Tin nhắn tự động**: Tự động phản hồi tin nhắn đầu tiên khi khách mở hội thoại chat.

### 2.2. Quản lý Sản phẩm, Tồn kho & Biến động Kho
- **Xóa mềm (Soft Delete)**: Khi xóa sản phẩm, hệ thống kiểm tra sản phẩm không còn đơn hàng nào đang trong quá trình xử lý (`PENDING`, `CONFIRMED`, `SHIPPING`), sau đó đổi trạng thái sang `DELETED`. Sản phẩm biến mất khỏi trang mua hàng nhưng vẫn giữ tính toàn vẹn dữ liệu cho lịch sử đơn hàng.
- **Sổ nhật ký biến động kho (`stock_movement`)**: Mọi thao tác tăng giảm số lượng tồn kho (bán hàng, hủy đơn, hoàn hàng, nhập kho, kiểm kê, hủy hỏng) đều bắt buộc ghi nhận:
  - `variant_id`, `change_qty`, `previous_stock`, `new_stock`, `reason`, `reference_id`, `created_by`.
- **Cảnh báo tồn kho**: Cột `low_stock_threshold` cho phép thiết lập ngưỡng tối thiểu, hệ thống lọc nhanh các biến thể sắp hết hàng để người bán kịp nhập hàng.
- **Bulk CSV Import/Export**: Định dạng chuẩn CSV gồm 8 cột (`name,slug,description,categoryId,sku,size,color,stock,price`). Hỗ trợ xuất danh mục hiện tại và nhập dữ liệu sản phẩm hàng loạt.

### 2.3. Quy trình Xử lý Đơn hàng & Đổi / Trả
1. **Xác nhận đơn**: Trừ kho nguyên tử với điều kiện kiểm tra tồn kho (`stock >= requested_qty`).
2. **Giao hàng (`SHIPPING`)**: Bàn giao hàng cho đơn vị vận chuyển.
3. **Hoàn tất (`DELIVERED`)**: Thu tiền COD, cập nhật `payment_status = 'COLLECTED'`, tự động tạo phiên quyết toán doanh thu (`seller_settlement`) ở trạng thái tạm giữ `HOLDING` trong 3 ngày.
4. **Hủy đơn bởi người bán**: Người bán chỉ có thể hủy khi đơn chưa giao thành công, ghi chú lý do hủy, tự động hoàn trả tồn kho nguyên tử (`stock = stock + qty`) và ghi log `stock_movement` lý do `SELLER_CANCEL`.
5. **Yêu cầu Đổi/Trả (`return_request`)**:
   - Buyer chỉ được tạo yêu cầu khi đơn hàng đã ở trạng thái `DELIVERED`.
   - Buyer tải lên lý do, mô tả, ảnh/video bằng chứng (`evidence_keys`).
   - Seller tiếp nhận:
     - **Chấp thuận (`APPROVE`)**: Chọn phương án (hoàn tiền toàn bộ/một phần, đổi hàng) và cung cấp địa chỉ nhận lại hàng.
     - **Từ chối (`REJECT`)**: Nêu rõ lý do từ chối.
     - **Hoàn tất (`COMPLETE`)**: Sau khi nhận lại kiện hàng đổi trả, seller xác nhận hoàn tất để hệ thống hoàn kho.

### 2.4. Quyết toán Doanh thu & Rút tiền (Wallet & Settlement)
- **Chu kỳ giữ tiền (Escrow Holding)**: Tiền hàng sau khi giao thành công được giữ trong 3 ngày để bảo đảm quyền lợi đổi/trả của khách hàng.
- **Ví doanh thu (`wallet_balance`)**: Tổng tiền của các phiên quyết toán đã sang trạng thái `RELEASED` trừ đi các khoản tiền đã rút.
- **Lệnh rút tiền (`seller_withdrawal`)**: Người bán gửi lệnh rút tiền (tối thiểu 50.000 VNĐ). Hệ thống kiểm tra số dư khả dụng, ghi nhận lệnh và chuyển sang trạng thái xử lý ngân hàng.

### 2.5. Đánh giá Sản phẩm & Chat CSKH
- **Đánh giá**: Mỗi buyer chỉ được đánh giá 1 lần duy nhất cho mỗi sản phẩm, chỉ khi đơn đã `DELIVERED` và buyer đã xác nhận nhận hàng (`received`). Buyer có thể sửa/xóa bài của mình. Dùng bảng `review` duy nhất (`V7` + `V11`, `product_review` đã drop).
- **Seller Reply & Report**: Người bán có thể phản hồi giải thích công khai dưới đánh giá (1 lần) hoặc báo cáo nếu có hành vi bôi nhọ, cạnh tranh không lành mạnh.
- **Tin nhắn trực tuyến**: Buyer và Seller trao đổi trực tiếp, hệ thống theo dõi số tin chưa đọc (`unread_count`). Khi buyer gửi tin đầu tiên, hệ thống tự kích hoạt tin nhắn chào mừng / giải đáp tự động nếu shop có cài đặt `auto_reply_text`.

### 2.6. Trang trí Gian hàng (Shop Decoration)
- Bố cục giao diện được biểu diễn dạng chuỗi JSON gồm danh sách các block:
  - Banner quảng cáo (`BANNER`)
  - Bộ sưu tập sản phẩm nổi bật (`COLLECTION`)
  - Đồng hồ đếm ngược Flash Sale (`FLASH_SALE`)
  - Video giới thiệu (`VIDEO`)
- **Cơ chế Draft - Publish**: Người bán có thể lưu nháp (`draft`) tùy ý chỉnh sửa, xem trước giao diện và chỉ xuất bản (`publish`) khi hài lòng. Cho phép hủy bản nháp (`discard`) để quay về giao diện hiện tại.

### 2.7. Quản lý Nhân sự Shop & Phân quyền (RBAC)
- **4 Vai trò Hệ thống mặc định**:
  - `Quản lý (Manager)`: Toàn quyền sản phẩm, đơn hàng, kho và doanh thu.
  - `Chăm sóc khách hàng (CSKH)`: Đọc đơn hàng, xem và trả lời đánh giá, nhắn tin chat.
  - `Kho vận (Warehouse)`: Đọc/xử lý đơn hàng, điều chỉnh và kiểm kê tồn kho.
  - `Marketing`: Xem sản phẩm, trang trí gian hàng, xem doanh thu.
- **Vai trò tùy chỉnh**: Chủ shop có thể tạo thêm vai trò mới và chọn danh sách quyền hạn chi tiết.
- **Mời nhân viên**: Gửi thư mời qua email với token có hạn sử dụng 7 ngày. Khi nhân viên chấp nhận qua `/api/invitations/{token}/accept`, tài khoản được liên kết làm `STAFF` của shop.
- **Audit Log**: Toàn bộ thao tác thêm, sửa quyền, xóa nhân sự được lưu vào bảng `shop_member_audit`.

---

## 3. Lược đồ Cơ sở Dữ liệu (Migration V8__seller_center.sql)

```sql
-- Mở rộng shop
ALTER TABLE shop ADD COLUMN phone, address, return_policy, processing_days, ...;

-- Biến động kho
CREATE TABLE stock_movement (
    id UUID PRIMARY KEY,
    variant_id UUID NOT NULL,
    change_qty INT NOT NULL,
    previous_stock INT NOT NULL,
    new_stock INT NOT NULL,
    reason VARCHAR(50) NOT NULL,
    reference_id UUID,
    note VARCHAR(255),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- Người theo dõi shop
CREATE TABLE shop_follower (
    shop_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (shop_id, user_id)
);

-- Yêu cầu trả hàng / đổi hàng
CREATE TABLE return_request (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    shop_id UUID NOT NULL,
    buyer_id UUID NOT NULL,
    reason VARCHAR(100) NOT NULL,
    description TEXT,
    evidence_keys TEXT[],
    status VARCHAR(50) NOT NULL,
    resolution VARCHAR(50),
    refund_amount BIGINT,
    seller_note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Quyết toán người bán
CREATE TABLE seller_settlement (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL,
    order_id UUID NOT NULL UNIQUE,
    gross_amount BIGINT NOT NULL,
    platform_fee BIGINT NOT NULL DEFAULT 0,
    net_amount BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    hold_until TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

-- Rút tiền
CREATE TABLE seller_withdrawal (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    bank_account VARCHAR(100) NOT NULL,
    bank_holder VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ
);

-- Đánh giá sản phẩm (bảng duy nhất, V7 + V11 — seller reply/report trên cùng bảng)
CREATE TABLE review (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    buyer_id UUID NOT NULL,
    buyer_display_name TEXT NOT NULL,
    stars INT NOT NULL,
    comment TEXT,
    seller_reply TEXT,
    replied_at TIMESTAMPTZ,
    is_reported BOOLEAN NOT NULL DEFAULT FALSE,
    report_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_review_one_per_buyer UNIQUE (product_id, buyer_id)
);

-- Chat hội thoại & tin nhắn
CREATE TABLE chat_conversation (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL,
    buyer_id UUID NOT NULL,
    last_message TEXT,
    last_message_at TIMESTAMPTZ,
    buyer_unread_count INT NOT NULL DEFAULT 0,
    seller_unread_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_chat_conversation_shop_buyer UNIQUE (shop_id, buyer_id)
);

CREATE TABLE chat_message (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    sender_type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    image_key VARCHAR(255),
    sent_at TIMESTAMPTZ NOT NULL
);

-- Trang trí gian hàng
CREATE TABLE shop_decoration (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL UNIQUE,
    layout TEXT NOT NULL DEFAULT '[]',
    draft TEXT,
    published_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Vai trò, lời mời & nhật ký nhân viên
CREATE TABLE shop_role (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    permissions TEXT[] NOT NULL,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_shop_role_name UNIQUE (shop_id, name)
);

CREATE TABLE shop_invitation (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    role_id UUID NOT NULL,
    token VARCHAR(100) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE shop_member_audit (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL,
    member_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id UUID,
    detail TEXT,
    created_at TIMESTAMPTZ NOT NULL
);
```
