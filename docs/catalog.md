# Catalog API — hàng hóa, ảnh, tìm kiếm

Luồng 3 của sàn (`features/catalog/`). Đọc cùng `api-endpoints.md` (#10–16) để biết thứ tự tổng thể.

## Ý tưởng trong 1 phút

- **Giá nằm ở variant, không nằm ở product.** Cùng một cái áo, size M đen với size XL trắng giá khác nhau là bình thường. `minPrice` trong search chỉ là giá trị suy ra (`min(price)` các variant) để hiện "chỉ từ 99k".
- **Đăng hàng 2 bước:** tạo product kèm variants trước (#11) → xin URL upload ảnh (#13) → gắn key ảnh vào product (#14). Giữa 2 bước product đã `ACTIVE` nhưng chưa ảnh, frontend tự chịu (hiện placeholder).
- **Không còn DRAFT.** Migration `V3` bỏ trạng thái nháp, product đẻ ra là `ACTIVE` luôn. Muốn gỡ hàng thì flip sang `HIDDEN`, muốn bán lại thì flip về. Gate duy nhất còn lại là shop phải `APPROVED` mới được đăng và hiện hàng.
- **DB chỉ lưu S3 key, không lưu URL.** Frontend tự ghép `{public-base-url}/{key}`.

## Ma trận quyền

| Endpoint | Auth | Điều kiện thêm |
|---|---|---|
| `GET /api/categories` | Không | — |
| `GET /api/products/{id}` | Không | Chỉ thấy hàng `ACTIVE` của shop `APPROVED`, còn lại 404 như không tồn tại |
| `GET /api/search/products` | Không | Như trên |
| `POST /api/shops/{shopId}/products` | JWT | `OWNER`/`STAFF` của shop + shop `APPROVED` |
| `PUT /api/shops/{shopId}/products/{id}` | JWT | Như trên + product phải thuộc đúng `shopId` trên path |
| `POST .../products/{id}/images` | JWT | Như trên |
| `POST /api/uploads/presign` | JWT | Chỉ nhận `image/*` |

Quy ước mã lỗi quyền: không phải thành viên hoặc shop chưa duyệt → `403`; product không thuộc shop trên path → `404` (cố tình, để khỏi lộ sự tồn tại); hàng bị ẩn hoặc shop bị treo với khách public → `404`, không phải `403`.

## #10 — Liệt kê danh mục

`GET /api/categories` — public, xếp theo `sort_order`. Không có CRUD: admin seed tay bằng SQL (`Category.seed(...)` chỉ dùng trong test/seed).

Response:

```json
[{"id": "uuid", "name": "Áo", "slug": "ao", "sortOrder": 0}]
```

## #11 — Tạo product kèm variants

`POST /api/shops/{shopId}/products` — auth, `201`.

Body:

```json
{
  "name": "Áo thun đen",
  "slug": "ao-thun-den",
  "description": "Cotton 100%",
  "categoryId": "uuid-hoặc-null",
  "variants": [
    {"sku": "AO-DEN-M", "size": "M", "color": "Đen",
     "price": 100000, "compareAtPrice": 150000,
     "stockQuantity": 10, "defaultVariant": true}
  ]
}
```

Luật:

- `slug` chữ thường + số + gạch ngang (`^[a-z0-9-]+$`), duy nhất **trong shop** → trùng là `409`.
- `sku` duy nhất **trong shop** → trùng là `409`.
- Ít nhất 1 variant, **đúng 1** `defaultVariant = true` → sai là `400`.
- `compareAtPrice` là "giá gốc" gạch ngang để hiện % giảm, nên phải **lớn hơn** `price` (null = không sale) → sai là `400`. DB giữ CHECK tương ứng làm lưới cuối.
- `categoryId` không tồn tại → `404`.

## #12 — Sửa product

`PUT /api/shops/{shopId}/products/{id}` — auth, `200`. Field nào null là giữ nguyên:

```json
{
  "name": "Tên mới",
  "description": null,
  "categoryId": null,
  "status": "HIDDEN",
  "variants": [{"id": "uuid-variant", "stockQuantity": 25, "defaultVariant": true}]
}
```

Luật:

- `status` chỉ nhận `ACTIVE` hoặc `HIDDEN`. `HIDDEN` = gỡ khỏi public/search, hàng trong đơn cũ không ảnh hưởng.
- Sửa variant theo `id`, variant lạ hoặc của shop khác → `404`.
- Đặt variant khác làm default thì default cũ tự nhả. Gỡ default duy nhất mà không chỉ default mới → `400`.

## #13 + #14 — Upload ảnh 3 bước

1. `POST /api/uploads/presign` (auth) với `{"contentType": "image/jpeg"}` → nhận `{"key", "url", "expiresAt"}`. Content-type ngoài `image/*` → `400`.
2. Frontend `PUT` file trực tiếp lên `url` (không qua backend).
3. `POST /api/shops/{shopId}/products/{id}/images` (auth, `201`) để gắn key:

```json
{"images": [{"s3Key": "abc123.jpg", "variantId": null, "sortOrder": 0}]}
```

`variantId` null = ảnh chung product, có giá trị = ảnh riêng variant đó (phải thuộc đúng product, không là `404`). `sortOrder` quyết định ảnh đại diện (nhỏ nhất lên trước).

## #15 — Chi tiết sản phẩm

`GET /api/products/{id}` — public, `200`. Trả full variants + ảnh (xếp theo `sortOrder`) + `categoryId` (null nếu chưa gán, frontend tự tra tên/slug từ `/api/categories` để dựng breadcrumb). Hàng `HIDDEN` hoặc shop không `APPROVED` → `404` giống như không tồn tại, để khách không dò được hàng bị gỡ.

## #16 — Tìm kiếm + lọc + sắp xếp

`GET /api/search/products?q=áo&category=ao-khoac&size=M&color=Đen&minPrice=100000&maxPrice=500000&sort=PRICE_ASC&page=0&limit=20` — public, `200`. Mọi tham số đều tùy chọn.

| Tham số | Ý nghĩa |
|---|---|
| `q` | Từ khóa. Có thì **chỉ trả hàng khớp** (BM25 < 0) và mặc định xếp `RELEVANCE`. Bỏ trống/chỉ khoảng trắng = duyệt theo bộ lọc. |
| `category` | Slug danh mục (khớp chính xác, chưa tính danh mục con). |
| `size`, `color` | Khớp chính xác giá trị variant (lấy từ `/api/search/facets`). |
| `minPrice`, `maxPrice` | Khoảng giá variant (VND, ≥ 0). `minPrice > maxPrice` → `400`. |
| `sort` | `RELEVANCE` \| `NEWEST` \| `PRICE_ASC` \| `PRICE_DESC`. Thiếu `q` mà xin `RELEVANCE` thì tự về `NEWEST`. Giá trị lạ → `400`. |
| `page`, `limit` | Trang đếm từ 0 (0–10000), `limit` 1–100, mặc định 20. Ngoài khoảng → `400`. |

Luật:

- Xếp hạng bằng index `idx_product_bm25` trên `name + description`, query bắt buộc `to_bm25query(q, 'idx_product_bm25')` + lọc `status = 'ACTIVE'`. Expression trong query phải giữ nguyên `coalesce(...) || ' ' || coalesce(...)` y hệt lúc tạo index (hằng `DOCUMENT` trong `ProductSearchRepository`), đổi là planner bỏ qua index.
- Doc không chứa token nào của query có điểm `0`, nên có `q` thì lọc `rank < 0` — trước đây search trả cả hàng không khớp xếp cuối.
- **Size, màu, khoảng giá phải khớp trên cùng một variant** (một `EXISTS` gộp): lọc "L + Trắng" không trả áo chỉ có "L Đen" và "S Trắng".
- Join lọc shop `APPROVED`, nên hàng của shop bị treo tự biến khỏi kết quả.
- Sort giá theo `minPrice` (giá variant rẻ nhất). Mọi kiểu sort có `p.id` cuối để thứ tự ổn định giữa các trang.

Response là 1 trang summary nhẹ (không kèm full variants):

```json
{"items": [{"id": "uuid", "shopId": "uuid", "name": "Áo thun đen", "slug": "ao-thun-den",
            "minPrice": 100000, "defaultImageKey": "abc123.jpg", "rank": -0.42}],
 "total": 37, "page": 0, "limit": 20}
```

`rank` càng nhỏ càng khớp (là khoảng cách BM25, không phải điểm 10), `null` khi không có `q`. `total` là tổng khớp bộ lọc, không tính phân trang. `defaultImageKey` null khi product chưa gắn ảnh.

## #16b — Lựa chọn cho bộ lọc

`GET /api/search/facets?q=áo&category=ao-khoac` — public, `200`. Tính trên tập khớp `q` + `category`, **bỏ qua** size/màu/giá đang chọn để người dùng vẫn thấy lựa chọn khác mà đổi.

```json
{"sizes": ["S", "M", "XL", "Freesize"], "colors": ["Đen", "Trắng"], "minPrice": 100000, "maxPrice": 980000}
```

`sizes` xếp XS → XXXL rồi tới size lạ theo A-Z. `minPrice`/`maxPrice` null khi không có kết quả.

## Sơ đồ code

```
features/catalog/
├── CatalogService.java      # luật nghiệp vụ + check quyền
├── CatalogMapper.java       # map entity → DTO thuần, không logic
├── controller/              # 5 controller mỏng (Category, ShopProduct, PublicProduct, Search, Upload)
└── repository/              # CrudRepository + ProductSearchRepository (JdbcClient, query BM25)
model/                       # Product, ProductVariant, ProductImage, Category, ProductStatus
dto/catalog/                 # request/response records
```

Behavior để trong entity (`product.hide()/activate()`, `variant.updatePricing()/unsetDefault()`), cạnh tranh đồng thời để dành flow order (trừ kho nguyên tử). Service catalog không gọi repository shop trực tiếp — cần gì về shop thì qua `ShopService` hoặc `ShopAccessChecker`.
