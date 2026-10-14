# Address API — sổ địa chỉ giao hàng

Thuộc Flow 1 (Identity): shopper tự đăng ký → có profile → quản lý địa chỉ giao hàng của chính mình.
Hiện thực trên nhánh `backend-address-list` (chưa merge vào `master`).

## Ý tưởng trong 1 phút

- **Default = địa chỉ lâu đời nhất trên tài khoản**, không phải thứ shopper phải chọn tay. Địa chỉ đầu tiên shopper từng có tự thành default; các địa chỉ sau vào với `default = false`.
- **Xóa default không bao giờ để checkout phải đoán:** còn địa chỉ khác thì địa chỉ lâu đời nhất còn lại lên thay; xóa cái cuối cùng thì danh sách rỗng, địa chỉ thêm tiếp theo lại thành default.
- **Sửa là merge:** field nào gửi rỗng (thiếu, null, chuỗi trắng) thì giữ giá trị cũ. Không có cách xóa trắng một field sau khi đã đặt — cố tình, để frontend gửi thiếu không bao giờ làm mất dữ liệu.
- **Đổi default là thao tác riêng** (`PUT .../default`), không nằm trong sửa địa chỉ.

## Ma trận quyền

| Endpoint | Auth | Điều kiện thêm |
|---|---|---|
| `POST /api/users` | JWT | Mỗi auth id chỉ 1 profile, trùng là `409` |
| `POST /api/users/me/addresses` | JWT | Luôn ghi vào profile của chính caller |
| `PUT /api/users/me/addresses/{id}` | JWT | Địa chỉ phải thuộc caller, lạ hoặc của người khác → `404` như không tồn tại |
| `PUT /api/users/me/addresses/{id}/default` | JWT | Như trên |
| `DELETE /api/users/me/addresses/{id}` | JWT | Như trên |

Quy ước theo `catalog.md`: tài nguyên của người khác trả `404`, không phải `403`, để khỏi lộ sự tồn tại. Chưa login thì `401` body rỗng từ security filter.

## POST /api/users — tạo profile sau khi signup GoTrue

GoTrue giữ account name + password và cấp JWT; endpoint này tạo nửa còn lại (profile), nối bằng `auth_id` lấy từ JWT sub. `201`.

Body (`name` bắt buộc, còn lại tùy chọn):

```json
{
  "name": "Nguyễn Văn A",
  "dateOfBirth": "1990-05-20",
  "phone": "0901234567",
  "addresses": [
    {"houseNumber": "12", "street": "Nguyễn Trãi", "ward": "Bến Thành",
     "district": "Quận 1", "city": "Sài Gòn"}
  ]
}
```

Luật:

- `name` trống → `400`. `dateOfBirth` tương lai → `400` (cần `spring-boot-starter-validation`, đã thêm vào build).
- 5 field địa chỉ đều tùy chọn, kể cả mảng `addresses` (tài khoản có thể bắt đầu với 0 địa chỉ).
- Địa chỉ đầu tiên trong request thành default, các địa chỉ sau `false`. Không có địa chỉ nào thì chưa có default.
- Gọi lại với cùng JWT (đã có profile cho auth id đó) → `409`.

Response (`201`), địa chỉ nào cũng kèm cờ `default`:

```json
{"id": 1, "name": "Nguyễn Văn A", "dateOfBirth": "1990-05-20", "phone": "0901234567",
 "addresses": [{"id": 1, "houseNumber": "12", "street": "Nguyễn Trãi", "ward": "Bến Thành",
                "district": "Quận 1", "city": "Sài Gòn", "default": true}]}
```

## POST /api/users/me/addresses — thêm địa chỉ

Auth, `201`. Cùng 5 field tùy chọn như trên.

Luật:

- Tài khoản đang rỗng → địa chỉ mới thành default; đã có địa chỉ → `false`.
- Trả về object địa chỉ vừa tạo (kèm `id` và `default`).

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
- Lưới cuối ở DB (xem dưới) nên bất biến "mỗi user đúng 1 default" không vỡ kể cả request đồng thời: code clear hết trước bằng bulk update rồi mới đặt cái mới.

## DELETE /api/users/me/addresses/{id} — xóa địa chỉ

Auth, `204`, không body.

Luật:

- Xóa địa chỉ thường: default hiện tại giữ nguyên.
- Xóa đúng default mà còn địa chỉ khác: địa chỉ lâu đời nhất còn lại (id nhỏ nhất) lên thay.
- Xóa cái cuối cùng: danh sách rỗng, không default; địa chỉ thêm tiếp theo thành default.
- `id` lạ hoặc của shopper khác → `404`. Code flush lệnh xóa trước khi promote để bất biến default không vỡ giữa chừng.

## Lưới DB cuối (migration `V1__create_users_and_addresses`)

- Bảng `users` (`auth_id` UNIQUE, `name` NOT NULL) và `addresses` (FK `user_id`, 5 cột địa chỉ nullable, `is_default` NOT NULL DEFAULT FALSE).
- Index `uq_addresses_one_default_per_user ON addresses (user_id) WHERE is_default = TRUE`: mỗi user tối đa 1 default ở tầng DB, code có bug thứ tự flush cũng chỉ nhận 500 chứ không bao giờ lưu sai.

## Sơ đồ code (nhánh `backend-address-list`)

```
users/                    # aggregate user + address
├── User.java             # entity users + OneToMany addresses (orphanRemoval)
├── Address.java          # entity addresses
├── UserRepository.java   # existsByAuthId + findByAuthId
├── AddressRepository.java# findByIdAndUserAuthId + clearDefaultAddresses (bulk)
├── UserService.java      # createUser/add/update/delete/changeDefault + copy/mergeFields
├── *Request.java         # CreateUserRequest (@NotBlank/@Past), AddressRequest
├── UserResponse.java     # DTO trả về, cờ default serialize thành "default"
└── *Exception.java       # DuplicateUserException 409, User/AddressNotFoundException 404
controllers/
├── UserController.java       # POST /api/users (201, đọc auth id từ JWT sub)
└── AddressController.java    # POST/PUT/DELETE /api/users/me/addresses (+ /{id}/default)
```

Test: `UserControllerTest` (8) + `AddressControllerTest` (12), Testcontainers Postgres, đã xanh 23/23 cùng suite cũ. Từng bắt được 1 bug 500 thật ở change-default (flush sai thứ tự, đã sửa bằng bulk clear + flush-before-promote, verify lại bằng smoke test live với GoTrue thật).

## Lưu ý khi merge vào `master`

- Nhánh này dùng **JPA + bảng `users`**; `master` đã chuyển sang **Data JDBC + bảng `user_profile`** (Flow 1, endpoint #1/#2). Merge = viết lại tầng persistence theo `docs/data.md`, giữ nguyên semantics endpoint trong file này.
- Lỗi trả theo `@ResponseStatus`/mặc định Spring Boot, **chưa theo ProblemDetail** như hợp đồng ở `api-endpoints.md` — cần đổi khi viết lại.
- Code hiện tại vi phạm vài luật `code-guide.md`/`AGENTS.md` (`@Setter` trên entity, thiếu annotation springdoc): dọn khi viết lại, không sửa ở nhánh này nữa.
