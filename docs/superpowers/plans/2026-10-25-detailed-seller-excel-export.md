# Kế Hoạch Triển Khai: Chức Năng Xuất Báo Cáo Excel Chi Tiết Cho Gian Hàng (Seller Advanced Excel Export)

> **Mục tiêu:** Nâng cấp tính năng xuất báo cáo từ file CSV 3 cột đơn giản hiện tại thành file **Excel (.xlsx) đa sheet chuyên nghiệp**, chuẩn hóa mẫu biểu tài chính cho người bán (Seller), phục vụ hạch toán doanh thu, quản trị tồn kho và đối soát đơn hàng.

---

## 1. Phân Tích Thực Trạng & Nhu Cầu Nâng Cấp

### 1.1. Hiện trạng
- Backend hiện tại (`ShopStatsService.exportRevenueReport`) chỉ xuất file `.csv` gồm đúng 3 cột phẳng: `Ngày, Doanh thu (VND), Số đơn hàng`.
- Thiếu thông tin phân tích sản phẩm bán chạy, tồn kho, phương thức thanh toán, cơ cấu trạng thái đơn hàng (giao thành công, đơn hủy, đổi trả hoàn tiền) và sổ chi tiết từng đơn hàng.
- Khi mở file CSV tiếng Việt trên một số phiên bản Microsoft Excel (Windows) có thể gặp vấn đề về font hiển thị nếu không tương thích UTF-8 BOM.

### 1.2. Mục tiêu cải tiến
1. **Định dạng file chuẩn `.xlsx` (OpenXML):** Sử dụng thư viện **Apache POI (`poi-ooxml`)** trên backend Spring Boot để tạo file Excel nguyên bản, có màu sắc thương hiệu, định dạng số/tiền tệ tự động (`#,,##0 ₫`), cố định dòng tiêu đề (Freeze Panes) và tự động căn độ rộng cột (Auto-fit columns).
2. **Cấu trúc đa sheet (Multi-sheet Workbook):**
   - **Sheet 1: Tổng quan kinh doanh & Tài chính (Summary KPIs)**
   - **Sheet 2: Doanh thu theo chu kỳ ngày/tuần (Timeline Breakdown)**
   - **Sheet 3: Hiệu suất theo danh mục & sản phẩm (Products & Categories)**
   - **Sheet 4: Nhật ký đơn hàng chi tiết (Orders Log)**
3. **Tuỳ chọn phạm vi xuất linh hoạt:** Cho phép xuất theo khoảng thời gian tùy chọn (`from` -> `to`), chu kỳ (Hôm nay, 7 ngày qua, Tháng này) hoặc xuất toàn bộ lịch sử (All-time).

---

## 2. Thiết Kế Cấu Trúc File Excel Chi Tiết (.xlsx)

### Sheet 1: 📊 Tổng quan Kinh doanh (`Tong_Quan`)
- **Khối thông tin chung (Header Metadata):**
  - Tên gian hàng: `[Tên Shop]` (ID: `[Shop UUID]`)
  - Khoảng thời gian báo cáo: `Từ [dd/mm/yyyy] đến [dd/mm/yyyy]`
  - Thời điểm kết xuất: `[dd/mm/yyyy HH:mm:ss]` (Múi giờ: `Asia/Ho_Chi_Minh`)
  - Người xuất báo cáo: `[Tên / Email Seller/Staff]`
- **Bảng chỉ số kinh doanh chính (Core KPIs):**
  | Chỉ số | Giá trị | Đơn vị | Ghi chú |
  | :--- | :--- | :--- | :--- |
  | Tổng doanh thu bán hàng | `52.920.000` | VND | Doanh thu các đơn giao thành công |
  | Tổng số đơn hàng thành công | `86` | Đơn | Trạng thái `DELIVERED` |
  | Giá trị trung bình / đơn (AOV) | `615.348` | VND | `Tổng doanh thu / Số đơn thành công` |
  | Tổng giá trị đơn đã hủy | `7.440.000` | VND | 10 đơn bị hủy (`CANCELLED`) |
  | Tổng giá trị hoàn tiền đổi trả | `700.000` | VND | 2 yêu cầu hoàn tiền đã duyệt (`APPROVED`) |
  | Ước tính phí sàn (5%) | `2.646.000` | VND | Phí dịch vụ nền tảng nét |
  | Lợi nhuận tạm tính | `50.274.000` | VND | Doanh thu sau khi trừ phí sàn |
- **Bảng phân bổ theo phương thức thanh toán:**
  | Phương thức thanh toán | Số lượng đơn | Doanh thu (VND) | Tỷ trọng (%) |
  | :--- | :--- | :--- | :--- |
  | Thanh toán khi nhận hàng (COD) | `150` | `89.060.000 ₫` | `95.5%` |
  | Thanh toán trực tuyến (VNPay/Momo) | `8` | `4.200.000 ₫` | `4.5%` |

---

### Sheet 2: 📈 Doanh thu Theo Chu kỳ (`Doanh_Thu_Theo_Ngay`)
- Chi tiết diễn biến từng ngày trong chu kỳ lọc:
  | Ngày (dd/mm/yyyy) | Số đơn thành công | Doanh thu tiền hàng (VND) | Phí vận chuyển (VND) | Tổng thu (VND) | Đơn hủy | Tỷ lệ hủy (%) |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | `20/10/2026` | 12 | 7.200.000 ₫ | 360.000 ₫ | 7.560.000 ₫ | 1 | 7.7% |
  | `21/10/2026` | 18 | 14.200.000 ₫ | 540.000 ₫ | 14.740.000 ₫ | 0 | 0.0% |
  | ... | ... | ... | ... | ... | ... | ... |
  | **TỔNG CỘNG** | **86** | **50.400.000 ₫** | **2.520.000 ₫** | **52.920.000 ₫** | **10** | **10.4%** |
*(Áp dụng công thức Excel `SUM(...)` tự động cho dòng Tổng cộng)*

---

### Sheet 3: 👗 Hiệu suất Sản phẩm & Danh mục (`San_Pham_Ban_Chay`)
- Danh sách hiệu suất bán lẻ sắp xếp theo doanh thu giảm dần:
  | STT | Mã SKU | Tên sản phẩm | Danh mục | Đơn giá TB (VND) | Số lượng đã bán | Doanh thu (VND) | Tồn kho hiện tại |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | 1 | `AK-WIND-01` | Áo khoác gió Unisex chống nước | Áo khoác | 450.000 ₫ | 94 | 42.300.000 ₫ | 45 |
  | 2 | `TSH-COT-02` | Áo thun Oversize Cotton 100% | Áo | 220.000 ₫ | 114 | 25.150.000 ₫ | 120 |
  | 3 | `JNS-STR-03` | Quần Jean ống suông cạp cao | Quần | 380.000 ₫ | 42 | 16.200.000 ₫ | 18 |
  | 4 | `DRS-FLW-04` | Váy hoa nhí Vintage | Váy & Đầm | 380.000 ₫ | 2 | 760.000 ₫ | 3 |
  | 5 | `ACC-BELT-05`| Thắt lưng da retro | Phụ kiện | 75.000 ₫ | 2 | 150.000 ₫ | 50 |

---

### Sheet 4: 📦 Sổ Chi tiết Đơn hàng (`Chi_Tiet_Don_Hang`)
- Danh sách toàn bộ các đơn hàng phát sinh trong khoảng thời gian:
  | Mã đơn hàng | Thời gian đặt | Tên người nhận | SĐT | Trạng thái | Hình thức thanh toán | Tiền hàng (VND) | Phí ship (VND) | Giảm giá (VND) | Tổng thanh toán (VND) |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | `ORD-20261021-001` | 21/10/2026 09:15 | Nguyễn Văn A | 098****123 | Hoàn thành | COD | 450.000 ₫ | 30.000 ₫ | 0 ₫ | 480.000 ₫ |
  | `ORD-20261021-002` | 21/10/2026 10:40 | Trần Thị B | 091****456 | Đã hủy | COD | 740.000 ₫ | 30.000 ₫ | 50.000 ₫ | 720.000 ₫ |

---

## 3. Kiến Trúc Backend (Spring Boot & Apache POI)

### 3.1. Thư viện phụ thuộc (`build.gradle.kts`)
- Thêm dependency `org.apache.poi:poi-ooxml`:
```kotlin
implementation("org.apache.poi:poi-ooxml:5.3.0")
```

### 3.2. Cấu trúc các thành phần mới
```
com.minhnbnt.fashionstore.features.shop
├── excel/
│   ├── ExcelExportService.java            // Core service tạo Workbook, CellStyle, Header, AutoSize
│   ├── SellerExcelReportBuilder.java      // Builder chuyên cho 4 sheet của Seller
│   └── ExcelStyleHelper.java              // Palette màu, Font Segoe UI/Calibri, Border, DataFormat
├── ShopStatsRepository.java               // Bổ sung query chi tiết đơn hàng theo khoảng ngày
├── ShopStatsService.java                  // Điều phối thu thập dữ liệu & gọi ExcelExportService
└── controller/
    └── SellerStatsController.java         // Cập nhật endpoint xuất báo cáo
```

### 3.3. API Endpoint Specification
- **URL:** `GET /api/seller/shops/{shopId}/stats/revenue/export`
- **Query Parameters:**
  - `format`: `"XLSX"` (mặc định) hoặc `"CSV"`.
  - `from`: `LocalDate` (tùy chọn, mặc định 30 ngày trước).
  - `to`: `LocalDate` (tùy chọn, mặc định hôm nay).
- **Responses:**
  - `200 OK`: `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (với XLSX) hoặc `text/csv; charset=UTF-8` (với CSV).
  - Header: `Content-Disposition: attachment; filename="bao-cao-doanh-thu-[shopSlug]-[from]-[to].xlsx"`
  - `403 FORBIDDEN`: Thiếu quyền `REVENUE_READ`.

---

## 4. Thiết Kế Giao Diện Frontend (React / TailwindCSS)

### 4.1. Nâng cấp Modal Xuất Báo Cáo (`ExportExcelModal.tsx`)
- Thay vì nút click tải ngay file CSV đơn điệu, khi click **Xuất Báo Cáo** sẽ mở modal tùy chọn:
  1. **Khoảng thời gian:**
     - Nút chọn nhanh: *Hôm nay* / *7 ngày qua* / *Tháng này* / *Tùy chọn từ ngày - đến ngày*.
  2. **Định dạng tải về:**
     - 🌟 **Báo cáo Excel Đầy đủ (.xlsx)** *(Khuyên dùng)*: 4 sheet chi tiết, chuẩn hóa kế toán, tự động tính tổng.
     - **Tệp dữ liệu CSV (.csv)**: Nhẹ, xuất nhanh cho các công cụ xử lý thô.
  3. **Tùy chọn kèm theo:**
     - Ẩn bớt số điện thoại khách hàng (Masking PII: `098***123`) để bảo vệ dữ liệu cá nhân.
4.2. Trạng thái tải:
   - Hiển thị spinner và tiến trình `Đang tổng hợp dữ liệu...`
   - Toast thông báo khi file tải xuống thành công hoặc hiển thị lỗi chi tiết nếu thời gian không hợp lệ.

---

## 5. Kế Hoạch Kiểm Thử (Test Plan)

1. **Backend Unit & Integration Tests:**
   - Test kiểm tra workbook tạo ra có đủ 4 sheet với tên chuẩn xác.
   - Test kiểm tra định dạng tiền tệ (`#,,##0 ₫`) và các ô số học.
   - Test quyền RBAC: Nhân viên không có quyền `REVENUE_READ` bị chặn `403`.
2. **Frontend Component Tests:**
   - Test đóng/mở Modal xuất báo cáo.
   - Test validation ngày `to >= from`.
   - Test tải file blob qua URL Object và giải phóng bộ nhớ.
