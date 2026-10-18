# Module Quản trị — Giám sát đơn hàng toàn sàn

## Mục tiêu

Vận hành sàn (admin) theo dõi mọi đơn hàng của mọi shop như màn "Order Management" của Shopee/Lazada Ops:

1. Tìm/lọc đơn toàn sàn: trạng thái, shop, người mua, khoảng ngày đặt, từ khóa (mã đơn, tên/SĐT người nhận, tên shop, email người mua).
2. **Cảnh báo SLA**: tự đánh dấu đơn bị shop xử lý chậm và đơn đang có yêu cầu trả hàng chưa xong, để admin nhắc shop hoặc can thiệp.
3. Bảng đếm theo trạng thái + theo cảnh báo (tab có số, thẻ cảnh báo bấm vào là lọc).
4. Xem chi tiết đơn: người mua, shop, địa chỉ nhận, sản phẩm, tiền, dòng thời gian (ai làm gì, lúc nào), yêu cầu trả hàng.
5. **Hủy can thiệp**: admin hủy đơn chưa bàn giao vận chuyển kèm lý do (nghi gian lận, shop bỏ mặc đơn, người mua nhờ CSKH...), tự hoàn kho.

Ngoài phạm vi: tự động hủy đơn quá hạn bằng job định kỳ, điểm phạt shop, gửi thông báo/email, xử lý tranh chấp trả hàng thay shop, sửa thông tin đơn.

## Tham khảo nghiệp vụ

- Shopee: đơn thường phải bàn giao vận chuyển trong **2 ngày** kể từ lúc đặt, **không tính Chủ nhật và ngày lễ**; quá hạn tính vào tỷ lệ giao trễ, quá hạn lâu hơn thì sàn tự hủy.
- Lazada: đơn phải đóng gói, chuyển Ready-to-Ship trong **2 ngày làm việc**; đơn bị sàn hủy vì quá hạn tính là lỗi người bán. Hàng phải tới kho phân loại trong **7 ngày**.
- Walmart Marketplace / VTEX: sàn được hủy đơn thay shop (gian lận, theo yêu cầu người mua), ghi rõ ai hủy + lý do để hai bên cùng thấy.
- Sau khi đã bàn giao vận chuyển thì không hủy nữa, đi luồng giao thất bại / trả hàng.

## Cảnh báo SLA (`OrderAlert`)

Mỗi đơn có tối đa 1 cảnh báo, vì mỗi cảnh báo gắn với 1 trạng thái:

| Mã | Điều kiện | Ý nghĩa |
|---|---|---|
| `CONFIRM_LATE` | `PENDING`, đặt quá **1 ngày làm việc** | Shop chưa xác nhận đơn |
| `HANDOVER_LATE` | `CONFIRMED`, đặt quá **2 ngày làm việc** | Đã xác nhận nhưng chưa giao cho vận chuyển (mốc của Shopee/Lazada) |
| `DELIVERY_LATE` | `SHIPPING`, gửi đi quá **7 ngày làm việc** | Giao hàng quá lâu |
| `RETURN_OPEN` | `DELIVERED`, có yêu cầu trả hàng `REQUESTED`/`APPROVED` | Trả hàng đang chờ shop xử lý |

### Ngày làm việc

- Không tính **Chủ nhật** và **ngày lễ** (giờ Việt Nam). Thứ Bảy vẫn tính, giống Shopee.
- 1 ngày làm việc = 24 giờ trôi qua trong các ngày làm việc; ngày nghỉ "đóng băng" đồng hồ. Ví dụ đơn đặt 10h thứ Bảy thì trễ xác nhận lúc 10h thứ Hai, cả Chủ nhật không trễ thêm.
- `OrderSlaPolicy` lùi từ "bây giờ" (bean `Clock`) ra mốc cutoff theo ngày làm việc; SQL chỉ so `created_at`/`shipped_at <= cutoff`, nên vẫn lọc/đếm/sắp xếp được trong DB.
- Ngày lễ lưu ở DB (`public_holiday`), đồng bộ từ Calendarific và admin sửa tay, xem `2026-10-18-holiday-calendar-design.md`.

## Hủy can thiệp

- Chỉ hủy được `PENDING`/`CONFIRMED` (chưa bàn giao vận chuyển). `SHIPPING`/`DELIVERED`/`CANCELLED` → `409`.
- Đơn `CONFIRMED` đã trừ kho → hủy thì hoàn kho + ghi `stock_movement` `ORDER_CANCEL`, giống shop tự hủy.
- Lý do bắt buộc: loại (`AdminCancelReason`) + ghi chú 5–500 ký tự. Lưu `cancel_reason` = mã loại, `cancel_note` = ghi chú, `cancelled_by = 'ADMIN'`, dòng lịch sử trạng thái ghi admin + ghi chú.
- Đồng thời: khóa dòng `SELECT ... FOR UPDATE`. Các thao tác shop (xác nhận/gửi/giao/hủy) cũng khóa dòng để admin hủy và shop xác nhận cùng lúc không ghi đè nhau; người sau nhận `409`.

| Mã `AdminCancelReason` | Ý nghĩa |
|---|---|
| `FRAUD_SUSPECTED` | Nghi gian lận, đơn ảo, buff đơn |
| `SELLER_UNRESPONSIVE` | Shop không xử lý đơn quá hạn |
| `BUYER_REQUEST` | Người mua yêu cầu hủy qua CSKH |
| `PROHIBITED_ITEM` | Đơn chứa hàng vi phạm chính sách |
| `OTHER` | Khác (ghi rõ trong ghi chú) |

## Ai hủy đơn (`cancelled_by`)

Cột mới `orders.cancelled_by` ∈ `BUYER` | `SELLER` | `ADMIN`, giống Shopee hiển thị "Đã hủy bởi Người mua/Người bán/Hệ thống". Đơn cũ backfill: có `cancel_reason` → `SELLER` (chỉ shop hủy mới nhập lý do), còn lại → `BUYER`.

Dòng thời gian chi tiết đơn suy ra vai trò người thao tác: trùng người mua → `BUYER`, là thành viên shop → `SELLER`, khác → `ADMIN`.

## API

| Method | Path | Mô tả |
|---|---|---|
| GET | `/api/admin/orders?status=&alert=&shopId=&buyerId=&q=&from=&to=&page=&size=` | Danh sách phân trang. Mặc định mới đặt trước; lọc theo cảnh báo thì cũ nhất trước (quá hạn lâu nhất lên đầu) |
| GET | `/api/admin/orders/summary?shopId=&buyerId=&q=&from=&to=` | Đếm theo trạng thái + theo cảnh báo, cùng bộ lọc với danh sách (trừ `status`/`alert`) |
| GET | `/api/admin/orders/{id}` | Chi tiết: đơn + sản phẩm + dòng thời gian + yêu cầu trả hàng |
| PATCH | `/api/admin/orders/{id}/cancel` | body `{reason, note}`, `PENDING`/`CONFIRMED` → `CANCELLED` |

`from`/`to` là ngày (`yyyy-MM-dd`) theo giờ Việt Nam, `to` tính trọn ngày. `from > to` → `400`.

## Lịch nghỉ lễ

Ngày lễ lưu ở bảng `public_holiday` (migration V13), đồng bộ từ Calendarific + admin sửa tay. Xem `2026-10-18-holiday-calendar-design.md`.

## Code

- Migration `V11__admin_order_monitoring.sql`: cột `cancelled_by` + backfill, index `(created_at DESC)` cho danh sách toàn sàn.
- `model/Order`: `cancelledBy`, `cancelByAdmin(reason, note)`; `cancel()`/`cancelBySeller()` ghi người hủy. `model/OrderAlert`, `model/AdminCancelReason`, `model/OrderCanceller`.
- `features/order/`: read model admin ở `OrderAdminQueryRepository`, luật hủy can thiệp trong `OrderService`.
- `features/admin/`: `AdminOrderController` + `AdminOrderService` điều phối, giống `AdminProductService`.

## UI (`fashionstore-ui`)

- `/admin/orders`: 4 thẻ cảnh báo có số (bấm để lọc), tab trạng thái có số, ô tìm kiếm + khoảng ngày, danh sách phân trang.
- Bảng trượt chi tiết: người mua, shop, địa chỉ nhận, sản phẩm, tiền, dòng thời gian, yêu cầu trả hàng, nút hủy can thiệp.
- Hộp thoại hủy: chọn loại lý do + ghi chú (có câu mẫu), cảnh báo hoàn kho.
- Thanh điều hướng admin thêm "Giám sát đơn hàng".
