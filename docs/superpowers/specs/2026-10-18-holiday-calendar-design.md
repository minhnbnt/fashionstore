# Module Quản trị — Lịch nghỉ lễ (đồng bộ Calendarific)

## Mục tiêu

SLA đơn hàng (`OrderSlaPolicy`) không tính Chủ nhật và ngày lễ. Lịch lễ Việt Nam do Chính phủ chốt từng năm (nghỉ bù, hoán đổi ngày làm việc, ngày lễ mới như 24/11), nên:

1. Lưu ngày lễ trong DB thay vì `application.yml`.
2. Tự đồng bộ định kỳ từ [Calendarific](https://calendarific.com/api-documentation) (nguồn đầy đủ nhất cho VN: có nghỉ bù và ngày đi làm bù).
3. Admin xem theo năm, thêm/sửa/bỏ ngày, bấm đồng bộ tay. Sửa tay luôn thắng dữ liệu đồng bộ.

Ngoài phạm vi: lịch khác nhau theo khối nhà nước/doanh nghiệp, theo tỉnh; nhiều quốc gia.

## Dữ liệu

`public_holiday` (khóa là ngày):

| Cột | Ý nghĩa |
|---|---|
| `day` | Ngày (giờ VN) |
| `name` | Tên hiển thị |
| `kind` | `HOLIDAY` (nghỉ, không tính SLA) \| `WORKING_DAY` (đi làm bù: Chủ nhật vẫn tính SLA) |
| `source` | `SEED` (migration) \| `CALENDARIFIC` (đồng bộ) \| `MANUAL` (admin sửa) |
| `enabled` | `false` = admin đã bỏ ngày này; giữ dòng làm "bia mộ" để lần đồng bộ sau không thêm lại |

`holiday_sync` ghi lần đồng bộ gần nhất theo năm: lúc nào, thành công/thất bại, thông điệp lỗi, số ngày nhận về.

Migration seed lịch 2026 + phần đã công bố của 2027 (nguồn `SEED`), để chưa có API key vẫn chạy đúng.

## Đồng bộ

- `GET https://calendarific.com/api/v2/holidays?api_key=&country=VN&year=` — 1 lượt/năm. Gói free 500 lượt/tháng, lỗi cũng tính lượt, nên **không gọi khi tính SLA**, chỉ đồng bộ vào DB.
- Lọc: tên hoặc loại chứa "Working day" → `WORKING_DAY`; loại "National holiday" → `HOLIDAY`; còn lại (Observance, Season, lễ tôn giáo không nghỉ...) bỏ qua. `date.iso` có thể kèm giờ, lấy 10 ký tự đầu.
- Ghi trong 1 transaction cho năm đó:
  - Ngày nhận về chưa có → thêm `CALENDARIFIC`.
  - Đã có, nguồn `SEED`/`CALENDARIFIC` → cập nhật tên/loại, chuyển nguồn `CALENDARIFIC`.
  - Đã có, nguồn `MANUAL` → giữ nguyên (đếm "giữ bản sửa tay").
  - Dòng `CALENDARIFIC` của năm đó không còn trong kết quả → xóa (Calendarific đã rút lại).
  - Dòng `SEED` không có trong kết quả → giữ (nguồn chính thức có thể đầy đủ hơn).
- Tự động: ngày 1 hằng tháng 03:00 giờ VN, đồng bộ năm nay + năm sau (24 lượt/năm). Chưa cấu hình key thì bỏ qua + log.
- Key qua biến môi trường `CALENDARIFIC_API_KEY` (map vào `calendarific.api-key`), không commit.
- Lỗi: chưa có key → `503`; Calendarific lỗi/hết lượt → `502`, ghi `holiday_sync` thất bại kèm thông điệp.

## Áp dụng vào SLA

`OrderSlaPolicy` lấy lịch 90 ngày gần nhất từ `HolidayService` (chỉ dòng `enabled`), ngày làm việc khi:
`WORKING_DAY` → làm việc; Chủ nhật → nghỉ; `HOLIDAY` → nghỉ; còn lại → làm việc.

## API admin

| Method | Path | Mô tả |
|---|---|---|
| GET | `/api/admin/holidays?year=` | Ngày lễ của năm (mặc định năm nay) + lần đồng bộ gần nhất + đã cấu hình key chưa |
| PUT | `/api/admin/holidays/{day}` | `{name, kind}` thêm/sửa, nguồn `MANUAL`, bật lại nếu đã bỏ |
| DELETE | `/api/admin/holidays/{day}` | Bỏ ngày (`enabled=false`, nguồn `MANUAL`), `404` nếu chưa có |
| POST | `/api/admin/holidays/sync?year=` | Đồng bộ ngay, trả số thêm/cập nhật/xóa/giữ bản sửa tay |

## UI

`/admin/holidays`: chọn năm, nút "Đồng bộ Calendarific" (tắt + chú thích khi chưa có key), thông tin lần đồng bộ gần nhất, danh sách ngày (tên, loại, nguồn, đã bỏ), thêm/sửa/bỏ/bật lại.
