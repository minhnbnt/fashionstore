# Address API — sổ địa chỉ giao hàng

Thuộc Flow 1 (Identity): buyer quản lý địa chỉ giao hàng của chính mình.
Hiện thực trên nhánh `backend-address-list` (JDBC, đồng bộ `master`, đã push).

## Ý tưởng trong 1 phút

- **Default = địa chỉ lâu đời nhất trên tài khoản**, không phải thứ shopper phải chọn tay. Địa chỉ đầu tiên từng có tự thành default; các địa chỉ sau vào với `defaultAddress = false`.
- **Xóa default không bao giờ để checkout phải đoán:** còn địa chỉ khác thì địa chỉ lâu đời nhất còn lại lên thay; xóa cái cuối cùng thì danh sách rỗng, địa chỉ thêm tiếp theo lại thành default.
- **Sửa là merge:** field nào gửi rỗng (thiếu, null, chuỗi trắng) thì giữ giá trị cũ. Không có cách xóa trắng một field sau khi đã đặt — cố tình, để frontend gửi thiếu không bao giờ làm mất dữ liệu.
- **Đổi default là thao tác riêng** (`PUT .../default`), không nằm trong sửa địa chỉ.
- **Không có tạo profile ở đây.** Tạo profile là `POST /api/users` của Flow 1 (mirror GoTrue → `user_profile`). Sổ địa chỉ chỉ gắn vào profile đã tồn tại. Phone/birth-date không thuộc feature này (đã bỏ theo quyết định, `user_profile` hiện chỉ có email/displayName/avatar).

## Ma trận quyền

| Endpoint | Auth | Điều kiện thêm |
|---|---|---|
| `GET /api/users/me/addresses` | JWT | Chỉ đọc địa chỉ của chính caller |
| `POST /api/users/me/addresses` | JWT | Luôn ghi vào profile của chính caller |
| `PUT /api/users/me/addresses/{id}` | JWT | Địa chỉ phải thuộc caller, lạ hoặc của người khác → `404` như không tồn tại |
| `PUT /api/users/me/addresses/{id}/default` | JWT | Như trên |
| `DELETE /api/users/me/addresses/{id}` | JWT | Như trên |

Quy ước theo `catalog.md`: tài nguyên của người khác trả `404`, không phải `403`, để khỏi lộ sự tồn tại. Chưa login thì `401` body rỗng từ security filter.

## GET /api/users/me/addresses — liệt kê

Auth, `200`. Xếp theo thứ tự tạo, lâu đời nhất trước. Chưa có địa chỉ nào thì mảng rỗng.

```json
[{"id": "uuid", "houseNumber": "12", "street": "Nguyễn Trãi", "ward": "Bến Thành",
  "district": "Quận 1", "city": "Sài Gòn", "defaultAddress": true}]
```

## POST /api/users/me/addresses — thêm địa chỉ

Auth, `201`. 5 field địa chỉ đều tùy chọn (số nhà, đường, phường, quận, thành phố).

Luật:

- Tài khoản đang rỗng (chưa có default) → địa chỉ mới thành default; đã có → `false`.
- Trả về object địa chỉ vừa tạo (kèm `id` và `defaultAddress`).

## PUT /api/users/me/addresses/{id} — sửa địa chỉ (merge)

Auth, `200`. Sửa được mọi địa chỉ kể cả default; default vẫn là default sau khi sửa.

```json
{"city": "Đà Nẵng"}
```

Luật:

- Chỉ field có nội dung mới ghi đè; field thiếu/null/trắng giữ nguyên. Muốn đổi tên đường thì gửi mỗi `street`, các field khác không mất.
- Hệ quả: không có cách xóa trắng field đã đặt. Muốn có thì cần quy ước riêng (chưa làm).
- `id` lạ hoặc của shopper khác → `404`.

## PUT /api/users/me/addresses/{id}/default — đổi default

Auth, `200`. Bỏ default cũ, đặt địa chỉ này làm default, trả về nó.

Luật:

- `id` lạ hoặc của shopper khác → `404`.
- Lưới cuối ở DB (index unique có điều kiện, xem dưới). Code clear hết bằng bulk update rồi mới đặt cái mới, và xóa xong mới promote — thứ tự câu lệnh tường minh nên bất biến "mỗi user đúng 1 default" không vỡ (bài học từ bản JPA cũ từng 500 vì flush sai thứ tự).

## DELETE /api/users/me/addresses/{id} — xóa địa chỉ

Auth, `204`, không body.

Luật:

- Xóa địa chỉ thường: default hiện tại giữ nguyên.
- Xóa đúng default mà còn địa chỉ khác: địa chỉ lâu đời nhất còn lại lên thay.
- Xóa cái cuối cùng: danh sách rỗng, không default; địa chỉ thêm tiếp theo thành default.
- `id` lạ hoặc của shopper khác → `404`.

## Lưới DB cuối (migration `V5__address_book`)

- Bảng `address`: `user_id` UUID NOT NULL REFERENCES `user_profile (id)` ON DELETE CASCADE (xóa profile thì địa chỉ đi theo), 5 cột địa chỉ TEXT nullable, `is_default` NOT NULL DEFAULT FALSE, `created_at`/`updated_at`.
- Index `uq_address_one_default_per_user ON address (user_id) WHERE is_default = TRUE`: mỗi user tối đa 1 default ở tầng DB.
- Đánh số `V5` vì `V4` đã giữ cho nhánh cart (`V4__cart.sql`) — coi chừng đụng số nếu thứ tự merge đổi.

## Sơ đồ code

```
features/address/            # domain 1 entity + CRUD có luật default → để phẳng (như identity)
├── AddressService.java      # add/update/delete/changeDefault + list
├── AddressController.java   # GET/POST/PUT/DELETE /api/users/me/addresses (+ /{id}/default)
└── repository/
    └── AddressRepository.java  # findByUserId*, findByIdAndUserId, clearDefaults (bulk)
model/
└── Address.java             # entity JDBC: factory create + @PersistenceCreator,
                             # cấm @Setter, behavior updateDetails/makeDefault/clearDefault trong entity
dto/address/                # AddressRequest (5 field @Nullable), AddressResponse (defaultAddress)
```

Không dùng `@MappedCollection` (JDBC đòi thêm cột thứ tự lạ) — item query qua repository riêng, nhất quán với `OrderItem`.

Test: `AddressServiceTest` (10, DB thật qua Testcontainers image Postgres của project) + `AddressControllerTest` (6, slice mock service). Full suite 106/106 xanh cùng `master` mới nhất.
