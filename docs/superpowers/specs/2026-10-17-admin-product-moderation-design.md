# Module Quản trị — Gỡ sản phẩm vi phạm

## Mục tiêu

Admin kiểm duyệt hàng hóa trên sàn như các marketplace lớn (Shopee "Sản phẩm vi phạm", Lazada "Policy violation"):

1. Tìm/lọc sản phẩm toàn sàn (trạng thái, shop, từ khóa tên/slug/shop/SKU).
2. **Gỡ** sản phẩm vi phạm kèm **loại vi phạm** + **lý do**: sản phẩm biến mất khỏi sàn ngay (search, PDP, checkout).
3. Shop thấy sản phẩm ở trạng thái `BANNED` cùng loại vi phạm + lý do, sửa lại nội dung được nhưng **không tự đăng bán lại** được.
4. Admin **khôi phục** khi gỡ nhầm hoặc shop đã khắc phục.
5. Mọi thao tác có **lịch sử kiểm duyệt**: ai, lúc nào, từ trạng thái nào sang trạng thái nào, loại vi phạm, lý do.

Ngoài phạm vi: shop gửi khiếu nại/kháng nghị trong hệ thống, điểm phạt shop (penalty point), quét vi phạm tự động, gửi thông báo/email.

## Máy trạng thái

```
ACTIVE ─┐
        ├─ban(type, reason)──▶ BANNED ──restore──▶ HIDDEN ──(shop tự đăng bán)──▶ ACTIVE
HIDDEN ─┘                        │
                                 └─(shop xóa mềm)──▶ DELETED
```

- Gỡ được từ `ACTIVE` hoặc `HIDDEN` (hàng đang ẩn vẫn có thể vi phạm, gỡ để shop không bật lại được). `BANNED`/`DELETED` → `409`.
- Khôi phục `BANNED` → `HIDDEN`, không về thẳng `ACTIVE`: shop kiểm tra lại rồi tự đăng bán, tránh admin vô tình bật món shop đang chủ động ẩn.
- Shop không `activate`/`hide` được sản phẩm `BANNED` (`409`), vẫn sửa tên/mô tả/biến thể và xóa mềm được.
- Đơn đang xử lý chứa sản phẩm bị gỡ **vẫn tiếp tục** (giống khóa shop), để người mua không bị treo đơn. Checkout mới bị chặn: sản phẩm không `ACTIVE` → `409`.
- `product.violation_type` + `product.status_reason` lưu lần gỡ gần nhất, xóa khi khôi phục.

## Loại vi phạm (`ProductViolationType`)

| Mã | Ý nghĩa |
|---|---|
| `PROHIBITED` | Hàng cấm / hạn chế kinh doanh |
| `COUNTERFEIT` | Hàng giả, hàng nhái |
| `IP_INFRINGEMENT` | Vi phạm sở hữu trí tuệ (thương hiệu, hình ảnh) |
| `MISLEADING` | Thông tin, hình ảnh, giá sai lệch / gây hiểu nhầm |
| `WRONG_CATEGORY` | Đăng sai danh mục |
| `SPAM` | Đăng trùng lặp, spam từ khóa |
| `OTHER` | Khác (ghi rõ trong lý do) |

## API

| Method | Path | Mô tả |
|---|---|---|
| GET | `/api/admin/products?status=&shopId=&q=&page=&size=` | Danh sách phân trang, mới cập nhật trước, `q` tìm tên/slug sản phẩm, tên shop, SKU |
| GET | `/api/admin/products/{id}` | Chi tiết: thông tin + shop + biến thể + ảnh + lịch sử kiểm duyệt |
| PATCH | `/api/admin/products/{id}/ban` | body `{violationType, reason}`, `ACTIVE`/`HIDDEN` → `BANNED` |
| PATCH | `/api/admin/products/{id}/restore` | `BANNED` → `HIDDEN` |

Lý do bắt buộc 5–500 ký tự (giống kiểm duyệt shop). Đồng thời: khóa dòng `SELECT ... FOR UPDATE`, hai admin bấm cùng lúc thì người sau nhận `409`.

`GET /api/seller/shops/{shopId}/products` trả thêm `violationType`, `statusReason`; lọc `status=BANNED` để xem hàng bị gỡ.

## Code

- Migration `V10__product_moderation.sql`: thêm `BANNED` vào CHECK, cột `violation_type`, `status_reason`, bảng `product_moderation_history`.
- `model/Product`: `ban(type, reason)`, `restore()`; `activate()` chặn `BANNED`.
- `features/catalog/`: luật gỡ/khôi phục + lịch sử trong `CatalogService`, read model admin ở `ProductAdminQueryRepository`.
- `features/admin/`: `AdminProductController` + `AdminProductService` điều phối, giống `AdminShopService`.

## UI (`fashionstore-ui`)

- `/admin/products`: tab trạng thái (Đang bán / Đang ẩn / Bị gỡ / Tất cả), ô tìm kiếm, danh sách phân trang.
- Xem chi tiết (ảnh, mô tả, biến thể, lịch sử) để admin đánh giá trước khi gỡ.
- Hộp thoại gỡ: chọn loại vi phạm, lý do mẫu theo loại + ô ghi chú; khôi phục có hộp xác nhận.
- Thanh điều hướng admin: Tổng quan / Kiểm duyệt sản phẩm.
