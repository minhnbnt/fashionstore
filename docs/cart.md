# Cart API — giỏ hàng và checkout nhiều shop

Luồng 5 của sàn (`features/cart/`). Đọc cùng `api-endpoints.md` (#24–29) để biết thứ tự tổng thể.
Hiện thực trên nhánh `backend-carts-and-orders` (đã push, chưa merge).

## Ý tưởng trong 1 phút

- **Giỏ của buyer, nhiều shop.** Mỗi buyer login có đúng 1 giỏ persistent (`cart` + `cart_item`), chứa variant + số lượng từ bao nhiêu shop cũng được. Không có giỏ vãng lai — app này ai cũng login bằng GoTrue nên khỏi đồng bộ cookie.
- **Giỏ không giữ hàng.** Giá và tồn hiện theo dữ liệu live lúc đọc, validate lại toàn bộ lúc checkout. Đặt trước (reservation) là timer + race condition, không làm.
- **Checkout là cổng kiểm tra rồi mới đặt.** Quét hết mọi item trước: hỏng chỗ nào thì **không đặt đơn nào** và trả về từng item lỗi để shopper gỡ ra checkout lại. Qua cổng thì tách 1 đơn 1 shop đi qua flow order cũ (`POST /api/orders` từng shop), trong **một transaction** nên crash giữa chừng cũng rollback hết.
- **Địa chỉ giao hàng là text thuần, không phải addressId.** Vì sổ địa chỉ chưa có trên `master` (nó nằm ở nhánh `backend-address-list` chưa merge), checkout nhận `shippingName`/`shippingPhone`/`shippingAddress` y hệt buy-now và chụp nguyên văn vào từng đơn. Khi sổ địa chỉ lên `master` thì frontend điền 3 chuỗi này từ sổ + truyền snapshot — xem "Nâng cấp sau" ở cuối.

## Ma trận quyền

| Endpoint | Auth | Điều kiện thêm |
|---|---|---|
| `GET /api/cart` | JWT | Luôn đọc giỏ của chính caller, chưa có thì trả giỏ rỗng (không tạo row) |
| `POST /api/cart/items` | JWT | Variant phải tồn tại, còn lại `404` |
| `PUT /api/cart/items/{variantId}` | JWT | Item phải có trong giỏ, không thì `404` |
| `DELETE /api/cart/items/{variantId}` | JWT | Như trên |
| `DELETE /api/cart` | JWT | Xóa sạch, luôn `200` giỏ rỗng |
| `POST /api/cart/checkout` | JWT | Xem luật cổng dưới đây |

Chưa login thì `401` body rỗng từ security filter, như mọi endpoint auth khác.

## #24 — Xem giỏ

`GET /api/cart` — auth, `200`. Item xếp theo thứ tự thêm vào.

Response:

```json
{"items": [
  {"variantId": "uuid", "productName": "Áo thun đen", "variantLabel": "M / Đen",
   "sku": "AO-DEN-M", "unitPrice": 100000, "quantity": 2, "stockQuantity": 9}
 ],
 "itemCount": 2, "subtotal": 200000}
```

`unitPrice`/`stockQuantity` là số live lúc đọc. `subtotal` tính theo giá live.

## #25 — Thêm vào giỏ

`POST /api/cart/items` — auth, `200` trả cả giỏ.

```json
{"variantId": "uuid", "quantity": 2}
```

Luật:

- Thêm variant đã có thì **cộng dồn** số lượng, không tạo dòng mới.
- `quantity` tối thiểu 1 → sai là `400`. Variant không tồn tại → `404`.

## #26 — Đổi số lượng

`PUT /api/cart/items/{variantId}` — auth, `200` trả cả giỏ.

```json
{"quantity": 3}
```

Luật:

- `quantity` tối thiểu 1 (`400` nếu sai). Muốn xóa hẳn thì dùng DELETE.
- Variant không có trong giỏ → `404` (kể cả khi variant đó tồn tại ở shop — 404 ở đây nghĩa là "không có trong giỏ của bạn").

## #27 — Xóa item + #28 — Xóa sạch giỏ

`DELETE /api/cart/items/{variantId}` — auth, `200` trả cả giỏ; id lạ → `404`.
`DELETE /api/cart` — auth, `200` trả giỏ rỗng. Dùng sau checkout thất bại muốn làm lại từ đầu, hoặc khi buyer đổi ý.

## #29 — Checkout cả giỏ

`POST /api/cart/checkout` — auth, `201` trả danh sách đơn vừa đặt (mỗi shop 1 đơn, dùng nguyên `OrderResponse` của flow order).

```json
{"shippingName": "An", "shippingPhone": "0900000000",
 "shippingAddress": "12 Nguyễn Trãi, Sài Gòn", "shippingFee": null}
```

`shippingFee` tùy chọn (null = 0), áp cho **mỗi** đơn được tạo — cùng ngữ nghĩa với buy-now.

Luật cổng (chạy hết trước khi đặt, hỏng 1 item là dừng cả đợt):

- Giỏ rỗng → `400`.
- Mỗi item kiểm tra theo thứ tự: variant còn tồn tại, product còn `ACTIVE`, shop đã `APPROVED`, tồn đủ số lượng. Hỏng bước nào ghi nhận item đó + lý do rồi xét tiếp item sau.
- Có bất kỳ item hỏng nào → `409` ProblemDetail, **không đặt đơn nào**, body có mảng `items` để UI chỉ đúng chỗ cần gỡ:

```json
{"type": "about:blank", "title": "Checkout blocked", "status": 409,
 "detail": "Checkout blocked, remove failed items and retry",
 "items": [{"variantId": "uuid", "reason": "Insufficient stock, available: 0"}]}
```

- Qua cổng → đặt từng shop bằng đúng logic buy-now (`OrderService.checkout`: snapshot tên/giá/SKU theo giá lúc đặt, trừ kho ở bước seller confirm chứ không trừ ở đây), địa chỉ giao hàng chụp nguyên văn vào từng đơn (sửa sổ địa chỉ sau này không viết lại lịch sử đơn), xong xóa sạch giỏ.

## Sơ đồ code

```
features/cart/
├── CartService.java        # luật giỏ + cổng checkout + tách đơn theo shop
├── CartCheckoutBlockedException.java  # mang List<CheckoutBlockedItem>
├── controller/
│   └── CartController.java # 6 endpoint mỏng + @ExceptionHandler -> 409 ProblemDetail có items
└── repository/
    ├── CartRepository.java      # findByBuyerId
    └── CartItemRepository.java  # findByCartId*, findByCartIdAndVariantId, deleteByCartId
model/                       # Cart, CartItem (JDBC, factory create, id sinh ở Java)
dto/cart/                    # request/response records + CheckoutBlockedItem
```

Không dùng `@MappedCollection` cho items (JDBC đòi thêm cột thứ tự lạ) — làm repo riêng như `OrderItem`, nhất quán với code xung quanh. Mỗi flow chỉ save root đúng 1 lần, trước khi ghi items (ngược lại là vỡ FK hoặc double-INSERT — đã từng dính cả hai, test bắt được).

Test: `CartServiceTest` (9, DB thật) + `CartControllerTest` (6, slice mock service). Full suite 91/91 xanh.

## Nâng cấp sau (không làm đợt này)

- **Cắm sổ địa chỉ:** khi address book lên `master`, checkout nhận thêm `addressId` (thuộc caller), snapshot 3 trường shipping từ đó; giữ fallback text thuần cho tương thích. Nhớ lúc đó cũng gắn link từ đơn về address id để tra cứu (hiện tại đơn chỉ giữ text).
- **Mua 1-click giữ nguyên** (`POST /api/orders`), không đi qua giỏ.
