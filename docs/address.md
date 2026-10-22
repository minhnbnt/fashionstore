# Address API — sổ địa chỉ giao hàng

Thuộc Flow 1 (Identity): buyer quản lý địa chỉ giao hàng của chính mình.

## Ý tưởng trong 1 phút

- **Địa chỉ là MỘT chuỗi hoàn chỉnh (`fullAddress`)** do frontend tự build từ API danh mục `/api/public/locations` (34 tỉnh/thành + xã/phường sau sáp nhập 2025). Backend **không lưu dữ liệu hành chính** (tỉnh, phường...) thành cột riêng — chỉ nhận chuỗi. Format gợi ý: `"{số nhà}, {xã/phường}, {tỉnh/thành}"`; frontend giữ format cố định để khi sửa địa chỉ có thể tách chuỗi pre-fill lại dropdown.
- **Không có null trong hợp đồng:** mọi field request đều bắt buộc (`@NotBlank`/`@NotNull`), `defaultAddress` là `boolean` primitive và phải gửi tường minh. Jackson 3 chặn việc thiếu field primitive (`FAIL_ON_NULL_FOR_PRIMITIVES` bật mặc định) → thiếu là `400`.
- **Default = địa chỉ lâu đời nhất trên tài khoản**, không phải thứ shopper phải chọn tay. Địa chỉ đầu tiên từng có tự thành default; các địa chỉ sau vào với `defaultAddress = false`.
- **Xóa default không bao giờ để checkout phải đoán:** còn địa chỉ khác thì địa chỉ lâu đời nhất còn lại lên thay; xóa cái cuối cùng thì danh sách rỗng, địa chỉ thêm tiếp theo lại thành default.
- **Sửa là thay thế toàn bộ (full replacement):** client gửi đủ mọi field, không còn kiểu "field trống giữ giá trị cũ". Muốn đổi chỉ 1 field thì frontend gửi lại cả object.
- **Đổi default là thao tác riêng** (`PUT .../default`), không nằm trong sửa địa chỉ.
- **Không có tạo profile ở đây.** Tạo profile là `POST /api/users` của Flow 1 (mirror GoTrue → `user_profile`). Sổ địa chỉ chỉ gắn vào profile đã tồn tại.

## Ma trận quyền

| Endpoint | Auth | Điều kiện thêm |
|---|---|---|
| `GET /api/users/me/addresses` | JWT | Chỉ đọc địa chỉ của chính caller |
| `POST /api/users/me/addresses` | JWT | Luôn ghi vào profile của chính caller; tối đa 10 địa chỉ |
| `PUT /api/users/me/addresses/{id}` | JWT | Địa chỉ phải thuộc caller, lạ hoặc của người khác → `404` như không tồn tại |
| `PUT /api/users/me/addresses/{id}/default` | JWT | Như trên |
| `DELETE /api/users/me/addresses/{id}` | JWT | Như trên |

Quy ước theo `catalog.md`: tài nguyên của người khác trả `404`, không phải `403`, để khỏi lộ sự tồn tại. Chưa login thì `401` body rỗng từ security filter.

## Request/Response

Request (tất cả required):

```json
{
  "recipientName": "Nguyễn Văn A",
  "phoneNumber": "0912345678",
  "fullAddress": "Số 18 Duy Tân, Phường Dịch Vọng, Hà Nội",
  "label": "HOME",
  "defaultAddress": false
}
```

Response:

```json
{
  "id": "uuid",
  "recipientName": "Nguyễn Văn A",
  "phoneNumber": "0912345678",
  "fullAddress": "Số 18 Duy Tân, Phường Dịch Vọng, Hà Nội",
  "label": "HOME",
  "defaultAddress": true
}
```

`label` ∈ `HOME`, `OFFICE`, `OTHER` (`model/AddressLabel`).

## POST /api/users/me/addresses — thêm địa chỉ

Auth, `201`.

Luật:

- Đã đủ 10 địa chỉ → `400` "Maximum of 10 addresses reached".
- Tài khoản đang rỗng (chưa có default) → địa chỉ mới thành default; đã có → `false`, trừ khi `defaultAddress = true` thì clear default cũ rồi đặt cái mới.
- Service trim `recipientName`/`phoneNumber`/`fullAddress`.
- Thiếu field hoặc chuỗi trắng → `400` (bean validation + Jackson strict primitive).

## PUT /api/users/me/addresses/{id} — sửa địa chỉ (thay thế toàn bộ)

Auth, `200`. Gửi đủ mọi field; giá trị mới ghi đè hết giá trị cũ.

Luật:

- Default giữ nguyên như trước khi sửa, trừ khi `defaultAddress = true` → clear default cũ, địa chỉ này thành default.
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

## Lưới DB cuối (migration `V5__address_book` + `V23__address_full_address`)

- Bảng `address`: `user_id` UUID NOT NULL REFERENCES `user_profile (id)` ON DELETE CASCADE (xóa profile thì địa chỉ đi theo), `recipient_name`/`phone_number`/`full_address` TEXT NOT NULL, `label` TEXT NOT NULL DEFAULT 'HOME' CHECK IN ('HOME','OFFICE','OTHER') (từ `V22`), `is_default` NOT NULL DEFAULT FALSE, `created_at`/`updated_at`.
- `V23` drop các cột hành chính rời (`house_number`, `street`, `ward`, `district`, `city` — sinh thời `V5`/`V22`), rename `detail_address` → `full_address` + `SET NOT NULL`; dev rows placeholder từ `V22` bị xóa sạch.
- Danh mục tỉnh/xã nằm ở bảng seed `province`/`ward` (`V20__administrative_units`, nguồn provinces.open-api.vn) — address **không** tham chiếu tới chúng, frontend dùng API `/api/public/locations`.
- Index `uq_address_one_default_per_user ON address (user_id) WHERE is_default = TRUE`: mỗi user tối đa 1 default ở tầng DB.

## Sơ đồ code

```
features/address/            # domain 1 entity + CRUD có luật default → để phẳng (như identity)
├── AddressService.java      # add/update/delete/changeDefault + list
├── AddressController.java   # GET/POST/PUT/DELETE /api/users/me/addresses (+ /{id}/default)
└── repository/
    └── AddressRepository.java  # findByUserId*, findByIdAndUserId, clearDefaults (bulk), countByUserId
model/
└── Address.java             # entity JDBC: factory create + @PersistenceCreator,
                             # cấm @Setter, behavior updateDetails/makeDefault/clearDefault trong entity
dto/address/                 # AddressRequest (5 field required), AddressResponse
```

Không dùng `@MappedCollection` (JDBC đòi thêm cột thứ tự lạ) — item query qua repository riêng, nhất quán với `OrderItem`.

Test: `AddressServiceTest` (11, DB thật qua Testcontainers image Postgres của project) + `AddressControllerTest` (8, slice mock service). Full suite 393/393 xanh.
