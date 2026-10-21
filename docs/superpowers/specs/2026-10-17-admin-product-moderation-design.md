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

## Nguồn phát hiện vi phạm (2026-10-18)

Admin không tự lướt tìm vi phạm: sản phẩm nghi vấn được đưa vào **hàng đợi "Bị báo cáo"** từ 2 nguồn.

### GĐ1 — Người mua báo cáo

- `POST /api/products/{id}/reports` body `{reason, description}` (đăng nhập). `reason` dùng chung 7 loại vi phạm, mô tả ≤ 1000 ký tự.
- Chỉ báo cáo được sản phẩm đang hiển thị (ACTIVE + shop APPROVED), không thì `404`. Thành viên shop không tự báo cáo hàng của shop mình (`409`).
- Mỗi người 1 báo cáo **đang mở** / sản phẩm (`409` nếu trùng); báo cáo cũ đã xử lý thì báo lại được.

### GĐ2 — Hệ thống tự gắn cờ theo từ khóa cấm

- Admin quản lý danh sách từ khóa (`/api/admin/banned-keywords`), mỗi từ khóa gắn 1 loại vi phạm.
- Shop tạo/sửa/import sản phẩm → quét tên + mô tả: khớp nguyên từ, không phân biệt hoa thường, chuẩn hóa Unicode NFC ("fake" không khớp "fakebook"). Khớp thì tạo báo cáo `source=SYSTEM` (mỗi sản phẩm tối đa 1 cờ hệ thống đang mở), mô tả = các từ khóa khớp.
- Thêm từ khóa mới thì quét luôn sản phẩm ACTIVE/HIDDEN hiện có, trả số sản phẩm bị gắn cờ.
- Chỉ gắn cờ, **không tự gỡ**: admin vẫn là người quyết định (tránh gỡ nhầm vì từ khóa đa nghĩa).

### Xử lý ở phía admin

- `GET /api/admin/products?reported=true`: chỉ sản phẩm có báo cáo đang mở, xếp nhiều báo cáo nhất → mới nhất. Mỗi dòng có `openReportCount`, `topReportReason` (lý do bị báo nhiều nhất), `lastReportedAt`, `systemFlagged`.
- Chi tiết sản phẩm kèm danh sách báo cáo (ai báo, lý do, mô tả, trạng thái xử lý).
- **Gỡ** → mọi báo cáo đang mở chuyển `ACTION_TAKEN`. **Bác bỏ** (`PATCH /api/admin/products/{id}/reports/dismiss`, ghi chú tùy chọn) → `DISMISSED`, sản phẩm giữ nguyên.
- Dashboard: số báo cáo chờ xử lý (`openProductReports`).

Ngoài phạm vi: cổng khiếu nại chủ thương hiệu, thông báo kết quả cho người báo cáo, tự gắn cờ theo đánh giá/tỉ lệ trả hàng.

## Code

- Migration `V14__product_moderation.sql`: thêm `BANNED` vào CHECK, cột `violation_type`, `status_reason`, bảng `product_moderation_history`.
- Migration `V15__product_reports.sql`: `product_report` (partial unique: 1 báo cáo mở/người/sản phẩm, 1 cờ hệ thống mở/sản phẩm), `banned_keyword` (unique theo `lower(keyword)`).
- `features/catalog/`: `ProductReportService` (báo cáo, bác bỏ), `BannedKeywordService` (CRUD + quét lại), `KeywordScanner` (so khớp + gắn cờ, `CatalogService` gọi khi tạo/sửa).
- `model/Product`: `ban(type, reason)`, `restore()`; `activate()` chặn `BANNED`.
- `features/catalog/`: luật gỡ/khôi phục + lịch sử trong `CatalogService`, read model admin ở `ProductAdminQueryRepository`.
- `features/admin/`: `AdminProductController` + `AdminProductService` điều phối, giống `AdminShopService`.

## UI (`fashionstore-ui`)

- `/admin/products`: tab trạng thái (Đang bán / Đang ẩn / Bị gỡ / Tất cả), ô tìm kiếm, danh sách phân trang.
- Xem chi tiết (ảnh, mô tả, biến thể, lịch sử) để admin đánh giá trước khi gỡ.
- Hộp thoại gỡ: chọn loại vi phạm, lý do mẫu theo loại + ô ghi chú; khôi phục có hộp xác nhận.
- Thanh điều hướng admin: Tổng quan / Kiểm duyệt sản phẩm.
