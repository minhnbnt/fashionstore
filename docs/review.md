# Review API — đánh giá sau khi nhận hàng

Luồng 7 của sàn (`features/review/` + receipt trong `features/order/`). Đọc cùng `api-endpoints.md` (#33–37) để biết thứ tự tổng thể.
Hiện thực trên nhánh `backend-reviews` (code xong + test xanh, chưa merge).

## Ý tưởng trong 1 phút

- **Chỉ buyer đã nhận hàng mới được viết.** Đặt đơn chưa đủ — nhất là COD, hủy trước khi giao là bình thường. Chuỗi mở khóa: seller đi hết confirm → ship → deliver, rồi buyer xác nhận đã nhận (`received`). Thiếu một nửa thì chưa viết được.
- **Một buyer một bài cho một product**, sửa được, xóa được. Mua nhiều lần không viết được nhiều bài — chống spam đánh giá ảo từ đơn lặp.
- **Điểm là dữ liệu, chữ là tùy chọn.** Stars 1–5 bắt buộc; comment tối đa 2000 ký tự, null được. Ép viết chữ chỉ làm giảm số review mà không thêm gì cho sort/badge.
- **Trung bình tính lúc đọc, không lưu sẵn.** Detail product hiện `averageRating` + `reviewCount` tính live từ bảng review — không cột counter để lệch. Tên người viết chụp vào row lúc tạo (như order snapshot tên/giá/SKU), đổi tên sau không viết lại lịch sử.
- **Không ảnh, không trả lời shop, không vote hữu ích.** Scope sau.

## Ma trận quyền

| Endpoint | Auth | Điều kiện thêm |
|---|---|---|
| `PATCH /api/orders/{id}/received` | JWT | Chỉ buyer của đơn, đơn phải `DELIVERED`, xác nhận lại vẫn `200` |
| `POST /api/products/{productId}/reviews` | JWT | Đã nhận hàng này (đơn `DELIVERED` + đã xác nhận chứa product), chưa viết → trùng là `409` |
| `PUT /api/products/{productId}/reviews` | JWT | Chỉ bài của mình, chưa viết → `404` |
| `DELETE /api/products/{productId}/reviews` | JWT | Như trên |
| `GET /api/products/{productId}/reviews` | Không | Product ẩn/bị treo thì `404` như không tồn tại |

Chưa login gọi endpoint auth thì `401` body rỗng. Viết/sửa/xóa bài của người khác đều `404`, không `403`, để khỏi lộ ai đã viết gì.

## #33 — Xác nhận đã nhận hàng

`PATCH /api/orders/{id}/received` — buyer của đơn, `200` trả đơn sau khi xác nhận.

Luật:

- Chỉ đơn `DELIVERED` mới xác nhận được, còn lại `409` (PENDING/CONFIRMED/SHIPPING/CANCELLED đều không).
- Đơn чужой → `404`. Gọi lại nhiều lần vẫn `200`, không lỗi, không đổi gì thêm.
- Lưu ở cột `received_at` của `orders` (theo đúng pattern `confirmedAt`/`shippedAt`/`deliveredAt` có sẵn), không thêm status mới — máy trạng thái đơn giữ nguyên.

## #34 — Viết đánh giá

`POST /api/products/{productId}/reviews` — auth, `201`.

```json
{"stars": 5, "comment": "Vải đẹp"}
```

Luật:

- `stars` 1–5 (`400` nếu sai), `comment` null được, quá 2000 ký tự → `400`.
- Product không tồn tại → `404`. Chưa nhận hàng này (không có đơn `DELIVERED` + đã xác nhận chứa product) → `403`.
- Đã viết rồi → `409` (đi sửa bằng PUT).
- Tên hiển thị chụp vào bài lúc tạo.

## #35 + #36 + #37 — Sửa, xóa, liệt kê

- `PUT /api/products/{productId}/reviews` — auth, `200`. Chỉ bài của mình; cùng validation như lúc tạo.
- `DELETE /api/products/{productId}/reviews` — auth, `204`. Chỉ bài của mình.
- `GET /api/products/{productId}/reviews` — **public**, `200`, mới nhất trước, `limit` mặc định 20 kẹp 1–100 như search. Product ẩn/shop treo → `404`.

Response mỗi bài:

```json
[{"id": "uuid", "productId": "uuid", "buyerDisplayName": "An",
  "stars": 5, "comment": "Vải đẹp",
  "createdAt": "...", "updatedAt": "..."}]
```

## Rating trên product detail

`GET /api/products/{id}` giờ kèm thêm:

```json
{"averageRating": 4.5, "reviewCount": 2}
```

`averageRating` null khi chưa ai đánh giá (khác với 0.0 — "chưa có điểm" không phải "điểm 0"). Summary search (`ProductSummaryResponse`) không đổi — list nhẹ không gánh thêm.

## Lưới DB cuối (migration `V7__reviews`)

- `ALTER TABLE orders ADD COLUMN received_at TIMESTAMPTZ` (nullable, null = chưa xác nhận).
- Bảng `review`: `product_id` + `buyer_id` REFERENCES ... `ON DELETE CASCADE` cả hai (xóa product/profile thì bài đi theo), `buyer_display_name` TEXT NOT NULL (snapshot), `stars` CHECK 1–5, `comment` TEXT nullable, `created_at`/`updated_at`.
- Unique `uq_review_one_per_buyer ON (product_id, buyer_id)` + index `idx_review_product`.
- Đánh số `V7` vì `V4` (cart) và `V6` (wishlist) đã có chủ ở nhánh chưa merge — coi chừng đụng số nếu thứ tự merge đổi.

## Sơ đồ code

```
features/review/             # domain 1 entity + luật đủ điều kiện → để phẳng (như identity)
├── ReviewService.java       # create/update/delete/list/summary + kiểm tra đơn DELIVERED đã nhận
├── ReviewController.java    # GET public + POST/PUT/DELETE /api/products/{productId}/reviews
└── repository/
    └── ReviewRepository.java  # findByProductId*, findByProductIdAndBuyerId
model/
├── Review.java              # entity JDBC + behavior update()
└── Order.java               # thêm receivedAt + confirmReceipt() (chỉ DELIVERED)
dto/review/                 # Create/UpdateReviewRequest (@Min 1 @Max 5, comment 2000),
                             # ReviewResponse, RatingSummary(count + average @Nullable)
features/order/              # OrderService.confirmReceipt + PATCH /{id}/received (copy shape cancel)
features/catalog/            # CatalogService bơm RatingSummary vào ProductResponse (detail nào cũng có)
```

Test: `ReviewServiceTest` (12, DB thật đi full flow mua → nhận → viết) + `ReviewControllerTest` (5, slice) + 1 test endpoint received trong `OrderControllerTest`. Full suite 124/124 xanh.
