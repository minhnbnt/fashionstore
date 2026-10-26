# Design Doc: Chuẩn Hóa Nhập Địa Chỉ Seller & Tự Động Tra Cứu Tài Khoản Ngân Hàng Napas (VietQR)

- **Ngày tạo:** 2026-10-26
- **Nhánh áp dụng:** `feature/seller-registration-ekyc` (cả Backend và Frontend)
- **Tác giả:** Antigravity AI Pair Programmer & Lead Developer

---

## 1. Bối cảnh & Động lực (Intent & Why)

### 1.1 Vấn đề hiện tại
1. **Địa chỉ kho lấy hàng của Người bán:**
   - Tại form đăng ký mở shop eKYC (`ShopKycRegistrationModal.tsx`) và trang hồ sơ shop (`SellerProfileTab.tsx`), địa chỉ kho đang là một ô input text tự do (`address` / `pickupAddress`).
   - Người bán tự gõ tự do (VD: `123 Phố Huế, HN`), không ăn khớp với danh mục hành chính cấp Tỉnh/Xã sáp nhập 2025 (`/api/public/locations`) của hệ thống, dẫn đến khó khăn khi đồng bộ với đơn vị vận chuyển và không nhất quán với trải nghiệm nhập địa chỉ chuẩn của khách hàng (`AddressFormModal.tsx`).
2. **Thông tin tài khoản ngân hàng nhận tiền quyết toán:**
   - Danh sách ngân hàng đang bị giới hạn cứng 12 ngân hàng trong file nguồn (`VIETNAM_BANKS`).
   - Người bán phải tự tay gõ Tên chủ tài khoản (`bankHolder`). Hệ thống chỉ so sánh chuỗi với Tên CCCD (`legalName`) mà không kiểm chứng được tài khoản đó có thực sự tồn tại ở ngân hàng hay không, hoặc tên đăng ký tại ngân hàng có thực sự trùng với tài khoản nhận tiền hay không.

### 1.2 Mục tiêu (Goals)
- **Mục tiêu 1:** Người bán chọn địa chỉ kho lấy hàng theo danh mục chuẩn của hệ thống: Tỉnh/Thành phố (searchable dropdown) -> Phường/Xã (searchable dropdown) -> Số nhà, ngõ, tên đường (text input). Tự động tổng hợp thành `fullAddress` chuẩn định dạng `${detail}, ${ward}, ${province}`.
- **Mục tiêu 2:** Mở rộng danh mục lên toàn bộ ~50+ ngân hàng Việt Nam thuộc mạng lưới Napas (lấy danh mục chuẩn từ VietQR Public API). Cung cấp giao diện dropdown có tìm kiếm nhanh (gõ tên, mã viết tắt, mã BIN).
- **Mục tiêu 3:** Tự động tra cứu tên chủ tài khoản qua Napas 24/7 (VietQR Lookup API) ngay khi chọn ngân hàng và nhập số tài khoản. Tự động điền `bankHolder`, khóa không cho sửa tay, hiển thị huy hiệu xác thực Napas và đối soát 100% với Họ tên trên CCCD (`legalName`).
- **Mục tiêu 4:** Bảo mật API Key VietQR ở Backend qua mô hình Backend Proxy (`/api/public/banks/**`), có cơ chế fallback mock thông minh cho môi trường dev/test khi chưa cấu hình key.

### 1.3 Non-Goals & Anti-Goals
- **Non-Goals:** Không thay đổi cấu trúc bảng `shop` trong cơ sở dữ liệu (cột `address`, `bank_name`, `bank_account`, `bank_holder` giữ nguyên kiểu và định dạng chuỗi). Không thay đổi các luồng thanh toán đơn hàng của buyer.
- **Anti-Goals:** Không nhúng trực tiếp API Key VietQR vào mã nguồn Frontend (tránh lộ key và lỗi CORS). Không làm gián đoạn CI/CD hoặc các bài test tự động khi không có kết nối internet ra ngoài VietQR.

---

## 2. Các phương án kiến trúc & Lựa chọn (Approaches)

### 2.1 Phương án 1 (ĐÃ CHỌN): Full-stack Backend Proxy & Component Dùng Chung
- **Backend:**
  - `GET /api/public/banks`: Cache danh sách ~50+ ngân hàng (gọi VietQR `/v2/banks` hoặc nạp fallback danh mục tĩnh nếu VietQR timeout).
  - `POST /api/public/banks/lookup-account`: Nhận `{ bin, accountNumber }`, backend dùng `RestClient` gọi sang VietQR `/v2/lookup` với `x-client-id` và `x-api-key` cấu hình ở `application.yml` / env. Trả về `accountName` chuẩn.
  - Khi chưa cấu hình key (dev/test): Tự động trả về mock name hợp lệ, đảm bảo test suite luôn chạy ổn định 100%.
- **Frontend:**
  - Xây dựng component `LocationAddressSelector`: Searchable dropdown Tỉnh/Xã + ô chi tiết, dùng chung cho Seller Registration và Seller Profile.
  - Xây dựng component `BankSearchSelector`: Modal/dropdown chọn nhanh ngân hàng kèm thanh tìm kiếm (logo, tên tắt VCB/MB/TCB/..., tên đầy đủ).
  - Tự động debounce tra cứu tài khoản khi người bán nhập số tài khoản.
- **Lý do chọn:** Bảo mật thông tin xác thực, tránh lỗi CORS từ trình duyệt, kiểm soát cache danh mục ngân hàng, test tự động tin cậy.

### 2.2 Phương án 2 (Bị loại): Client-Side Direct Gọi Thẳng VietQR
- Frontend gọi trực tiếp `api.vietqr.io`.
- **Lý do loại:** Lộ `x-client-id` và `x-api-key` trên browser, phụ thuộc CORS của VietQR, khó viết test tự động ở CI.

---

## 3. Thiết kế chi tiết Backend

### 3.1 Cấu hình (Configuration)
Trong `application.yml`:
```yaml
app:
  vietqr:
    base-url: ${VIETQR_BASE_URL:https://api.vietqr.io}
    client-id: ${VIETQR_CLIENT_ID:}
    api-key: ${VIETQR_API_KEY:}
    connect-timeout-ms: 5000
    read-timeout-ms: 8000
```

### 3.2 Data Transfer Objects (DTOs)
- **`BankResponse`**:
  ```java
  public record BankResponse(
      String id,
      String name,
      String code,
      String bin,
      String shortName,
      String logo,
      boolean lookupSupported
  ) {}
  ```
- **`LookupAccountRequest`**:
  ```java
  public record LookupAccountRequest(
      @NotBlank(message = "Mã BIN ngân hàng không được để trống")
      @Pattern(regexp = "^[0-9]{6}$", message = "Mã BIN phải gồm 6 chữ số")
      String bin,

      @NotBlank(message = "Số tài khoản không được để trống")
      @Size(min = 6, max = 25, message = "Số tài khoản từ 6 đến 25 ký tự")
      String accountNumber
  ) {}
  ```
- **`LookupAccountResponse`**:
  ```java
  public record LookupAccountResponse(
      String bankCode,
      String accountNumber,
      String accountName
  ) {}
  ```

### 3.3 Dịch vụ `BankService` & Client VietQR
- **Danh sách ngân hàng:**
  - Định nghĩa danh sách tĩnh fallback ~55 ngân hàng chính (Vietcombank, MB, Techcombank, ACB, BIDV, Agribank, VPBank, TPBank, Sacombank, VIB, OCB, SHB, SeABank, MSB, HDBank...) với mã BIN và logo chuẩn.
  - Khởi động hoặc gọi API `https://api.vietqr.io/v2/banks` với in-memory cache (thời gian sống 24h). Nếu lỗi mạng -> dùng fallback danh sách tĩnh.
- **Tra cứu tài khoản:**
  - Kiểm tra `client-id` và `api-key`:
    - Nếu có cấu hình: Gọi `POST https://api.vietqr.io/v2/lookup`.
      - VietQR trả `code == "00"` -> Trả về `accountName` (chuỗi chuẩn hóa in hoa).
      - VietQR trả lỗi tài khoản không tìm thấy -> Ném `ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy tài khoản tại ngân hàng đã chọn")`.
    - Nếu KHÔNG có cấu hình (chế độ demo / test): Trả về kết quả mock `NGUYEN VAN A` hoặc tên giả lập theo số tài khoản, kèm log WARN nhắc cấu hình key.

### 3.4 REST Controller `BankController`
- `@RequestMapping("/api/public/banks")`
- `GET /api/public/banks` (Summary: "Danh sách ngân hàng hỗ trợ Napas / VietQR"): Trả về `List<BankResponse>`.
- `POST /api/public/banks/lookup-account` (Summary: "Tra cứu tên chủ tài khoản Napas 24/7"): Nhận `LookupAccountRequest`, trả về `LookupAccountResponse`.

---

## 4. Thiết kế chi tiết Frontend

### 4.1 Component `LocationAddressSelector`
- **Mục đích:** Thay thế ô nhập text địa chỉ tự do bằng bộ chọn 3 thành phần chuẩn hóa.
- **Props:**
  ```typescript
  interface LocationAddressSelectorProps {
    value: string // Chuỗi fullAddress (VD: "123 Phố Huế, Phường Hàng Bài, Thành phố Hà Nội")
    onChange: (fullAddress: string) => void
    required?: boolean
    disabled?: boolean
    idPrefix?: string
  }
  ```
- **Hành vi:**
  - Khởi tạo: Dùng hàm `splitFullAddress(value)` tách thành `{ province, ward, detail }`.
  - Dropdown Tỉnh/Thành phố: Gọi `useListProvinces()`, có ô tìm kiếm lọc nhanh không dấu (`removeVietnameseAccents`).
  - Dropdown Phường/Xã: Gọi `useListWards(provinceCode)`, chỉ hiển thị khi đã chọn Tỉnh.
  - Ô Số nhà, tên đường (`detailAddress`): Nhập text có placeholder gợi ý.
  - Khi có thay đổi ở bất kỳ ô nào -> Tự động ghép lại: `[detail, ward, province].filter(Boolean).join(', ')` và gọi `onChange(fullAddress)`.

### 4.2 Component `BankSearchSelector` & Tra cứu Napas
- **Query danh sách ngân hàng:** Gọi `useListBanks()` từ `@generated/endpoints/banks/banks`.
- **Giao diện chọn ngân hàng:**
  - Nút trigger hiển thị: Logo ngân hàng (ảnh nhỏ 24x24 hoặc icon Bank) + Tên viết tắt in đậm (`shortName`) + Tên đầy đủ mờ + icon `CaretDown`.
  - Khi click: Mở Modal/Popover có ô input tìm kiếm nhanh:
    - Tìm kiếm theo: Tên viết tắt (VCB, MB, TCB...), tên đầy đủ (Quân Đội, Ngoại Thương...), hoặc mã BIN (970436...).
    - Danh sách hiển thị cuộn mượt, phân tách rõ ràng, hỗ trợ dark mode chuẩn Design System của dự án.
- **Trường Số tài khoản & Kích hoạt tra cứu:**
  - Ô input số tài khoản: Chỉ cho phép nhập số/chữ cái hợp lệ.
  - Kích hoạt tra cứu: Khi chọn xong ngân hàng VÀ số tài khoản có từ 6 ký tự trở lên (khi blur khỏi ô input hoặc bấm nút "Kiểm tra tài khoản" / debounce 600ms).
  - Trạng thái loading: Spinner `ArrowClockwise` xoay nhẹ nhàng.
  - Trạng thái thành công:
    - Tự động gán kết quả `accountName` vào state `bankHolder`.
    - Ô `Tên chủ tài khoản` bị khóa (`readOnly`) kèm badge xanh `Xác thực bởi Napas 24/7`.
    - Đối soát tự động: So sánh `normalizeName(accountName)` với `normalizeName(legalName)` (CCCD). Nếu trùng khớp 100% -> Hiện badge xanh "Họ tên trùng khớp với CCCD". Nếu không khớp -> Báo cảnh báo màu vàng/đỏ và vô hiệu hóa nút chuyển tiếp Bước 4.
  - Trạng thái thất bại:
    - Báo lỗi đỏ dưới ô input: "Không tìm thấy tài khoản hoặc ngân hàng đang bảo trì. Vui lòng kiểm tra lại số tài khoản."
    - Reset trường `bankHolder` về rỗng.

### 4.3 Tích hợp vào các màn hình
1. **`ShopKycRegistrationModal.tsx`**:
   - **Step 1:** Thay thế `<input id="input-shop-address" />` bằng `<LocationAddressSelector value={address} onChange={setAddress} />`.
   - **Step 3:** Thay thế select 12 ngân hàng tĩnh và ô nhập tay `bankHolder` bằng `BankSearchSelector` và quy trình tra cứu Napas tự động.
2. **`SellerProfileTab.tsx`**:
   - Thay thế `<input id="pickupAddressField" />` bằng `<LocationAddressSelector value={formData.pickupAddress} onChange={(val) => setFormData({ ...formData, pickupAddress: val })} />`.

---

## 5. Kế hoạch kiểm thử & Tiêu chuẩn chất lượng (TDD & Verification)

### 5.1 Backend Tests
- **`BankServiceTest`:**
  - Test lấy danh sách ngân hàng: gọi thành công trả về danh sách có mã BIN, logo, shortName; test fallback khi VietQR offline.
  - Test tra cứu tài khoản: test trường hợp thành công (trả đúng `accountName`), test tài khoản không tồn tại (ném 400), test fallback khi chưa cấu hình key.
- **`BankControllerTest` (`@WebMvcTest`):**
  - Test `GET /api/public/banks` trả 200 OK và JSON array.
  - Test `POST /api/public/banks/lookup-account` với body hợp lệ và body không hợp lệ (mã BIN sai format -> 400).
- **`ShopServiceTest`:**
  - Đảm bảo logic kiểm tra `normalizeName(bankHolder) == normalizeName(legalName)` vẫn hoạt động chính xác và an toàn.

### 5.2 Frontend Tests
- **`LocationAddressSelector.test.tsx` (hoặc test adapter/format liên quan):**
  - Test ghép chuỗi địa chỉ từ các thành phần Tỉnh, Xã, Chi tiết.
  - Test parse chuỗi ngược lại khi truyền địa chỉ có sẵn.
- **`BankSelector.test.tsx` / `bankAdapters.test.ts`:**
  - Test lọc tìm kiếm ngân hàng theo tên viết tắt, tên tiếng Việt không dấu, mã BIN.
  - Test logic đối soát tên tài khoản Napas với tên CCCD.
- **Verification Gates:**
  - `pnpm format && pnpm lint`: 0 error, 0 warning.
  - `pnpm test`: Toàn bộ các test suites frontend xanh.
  - `.\gradlew test`: Toàn bộ các test suites backend xanh.

---

## 6. Phân bổ công việc chi tiết cho Builder

Tài liệu thiết kế này đủ độc lập để một kỹ sư phần mềm có thể lập kế hoạch chi tiết (`writing-plans`) và thực hiện theo từng task:
1. **Backend:** Tạo `BankController`, `BankService`, DTOs, cấu hình `application.yml`, viết tests slice & unit. Xuất OpenAPI spec mới.
2. **Frontend Codegen:** Cập nhật `openapi.json` và chạy `pnpm codegen`.
3. **Frontend Components:**
   - Xây dựng `LocationAddressSelector` và test liên quan.
   - Xây dựng `BankSearchSelector` với dropdown tìm kiếm ngân hàng và hook tra cứu tài khoản.
4. **Tích hợp Form:**
   - Cập nhật `ShopKycRegistrationModal.tsx` (Step 1 & Step 3).
   - Cập nhật `SellerProfileTab.tsx`.
5. **Xác minh toàn diện:** Chạy toàn bộ test suites BE + FE.
