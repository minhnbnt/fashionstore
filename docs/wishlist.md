# Wishlist API — sản phẩm buyer thích

Luồng 6 của sàn (`features/wishlist/`). Đọc cùng `api-endpoints.md` (#30–32) để biết thứ tự tổng thể.
Hiện thực trên nhánh `backend-wishlist` (đã push hay chưa thì xem git, code đã xong + test xanh).

## Ý tưởng trong 1 phút

- **Lưu theo product, không theo variant.** Trái tim nằm ở card sản phẩm — shopper thích cái áo trước, chọn size/màu sau lúc bỏ vào giỏ. Vì thế wishlist không dính dáng gì đến variant.
- **Một dòng cho một cặp (buyer, product).** Unique constraint lo dedup: lưu lại món đã lưu không lỗi, chỉ là no-op (toggle thì thành bỏ lưu).
- **Món tạm không mua được vẫn hiện, nhưng gắn cờ.** Ẩn hẳn khỏi list thì shopper hoang mang ("sao mất tiêu món tôi thích"), nên list trả hết kèm `available` + `unavailableReason`. Giá và ảnh chỉ hiện khi còn mua được — row `available: false` không bao giờ trông như mua được.
- **Wishlist chỉ để lưu.** Chuyển vào giỏ vẫn làm ở trang product (cần chọn variant), không có "chuyển cả list vào giỏ".

## Ma trận quyền

| Endpoint | Auth | Điều kiện thêm |
|---|---|---|
| `GET /api/wishlist` | JWT | Chỉ đọc món của chính caller |
| `POST /api/wishlist/items` | JWT | Product phải xem được (ẩn/bị treo/cút → `404` như không tồn tại) |
| `DELETE /api/wishlist/items/{productId}` | JWT | Chưa lưu thì `404` |

Mọi query scope theo buyerId từ JWT sub nên không tồn tại khái niệm "đụng món của người khác" — khỏi cần test 403/404 kiểu address. Chưa login thì `401` body rỗng từ security filter.

## #30 — Liệt kê wishlist

`GET /api/wishlist` — auth, `200`. Xếp theo thứ tự lưu, cũ nhất trước. Chưa lưu gì thì mảng rỗng.

```json
[{"productId": "uuid", "name": "Áo thun đen", "slug": "ao-thun-den",
  "minPrice": 100000, "defaultImageKey": "abc123.jpg",
  "available": true, "unavailableReason": null}]
```

Luật:

- `minPrice` = giá variant rẻ nhất **hiện tại**, `defaultImageKey` = ảnh sort đầu **hiện tại**. Không snapshot — giá/ảnh đổi thì lần đọc sau phản ánh luôn.
- Món bị ẩn/bị treo sau khi lưu vẫn trả về, nhưng `available: false` + `unavailableReason` ("Product is hidden" / "Shop is not approved for selling"), `minPrice`/`defaultImageKey` null, chỉ còn `name`/`slug` để nhận ra. Product bị xóa hẳn khỏi DB (chỉ làm tay mới có) thì `name`/`slug` cũng null, reason "Product no longer exists".
- Xóa profile thì wishlist đi theo (`ON DELETE CASCADE`).

## #31 — Lưu / bỏ lưu (toggle)

`POST /api/wishlist/items` — auth, `200`.

```json
{"productId": "uuid"}
```

Response:

```json
{"productId": "uuid", "saved": true}
```

Luật:

- Chưa lưu thì lưu (`saved: true`), đã lưu thì bỏ (`saved: false`). Frontend khỏi cần biết trạng thái trước, một nút một call.
- Product không tồn tại, đang ẩn, hoặc shop chưa duyệt → `404` (quy ước catalog: không xem được = không tồn tại).

## #32 — Xóa khỏi wishlist

`DELETE /api/wishlist/items/{productId}` — auth, `204`, không body. Chưa lưu thì `404` ("Product not in wishlist"), nhất quán với delete của cart.

## Lưới DB cuối (migration `V6__wishlist`)

- Bảng `wishlist_item`: `buyer_id` REFERENCES `user_profile (id)` + `product_id` REFERENCES `product (id)`, cả hai `ON DELETE CASCADE`, `created_at`/`updated_at`.
- Unique `uq_wishlist_one_per_product ON (buyer_id, product_id)` + index `idx_wishlist_buyer`.
- Đánh số `V6` vì `V4` (cart, chưa merge) và `V5` (address, đã merge) đã có chủ.

## Sơ đồ code

```
features/wishlist/           # domain 1 entity, CRUD mỏng + rule available → để phẳng (như identity)
├── WishlistService.java     # toggle/get/remove + resolve available (dùng repo catalog trực tiếp, không qua getDetail vì getDetail 404 hàng ẩn)
├── WishlistController.java  # GET /api/wishlist, POST/DELETE /api/wishlist/items
└── repository/
    └── WishlistRepository.java  # findByBuyerId*, findByBuyerIdAndProductId
model/
└── WishlistItem.java        # entity JDBC: factory create + @PersistenceCreator, cấm @Setter
dto/wishlist/                # WishlistToggleRequest/Response, WishlistItemResponse (available + reason @Nullable)
```

Test: `WishlistServiceTest` (9, DB thật: toggle, flag hidden/unapproved, remove) + `WishlistControllerTest` (4, slice mock service). Full suite 119/119 xanh.

## Nâng cấp sau (không làm đợt này)

- **Chuyển cả list vào giỏ:** cần chọn variant từng món nên để buyer làm ở trang product. Muốn làm thì thêm endpoint nhận map productId → variantId + quantity.
- **Nhắc khi món yêu thích giảm giá / có hàng lại:** cần job quét + notification, ngoài phạm vi backend hiện tại.
