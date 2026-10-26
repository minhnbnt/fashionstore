# Seller Address Standardization & Napas (VietQR) Bank Lookup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chuẩn hóa nhập địa chỉ kho người bán bằng danh mục địa phương hệ thống (Tỉnh/Xã/Chi tiết) và tự động tra cứu tên chủ tài khoản ngân hàng Napas 24/7 qua VietQR API cho toàn bộ ~50+ ngân hàng Việt Nam.

**Architecture:** 
- **Backend:** Module `bank` cung cấp `GET /api/public/banks` (cache danh sách ngân hàng kèm fallback tĩnh) và `POST /api/public/banks/lookup-account` (proxy an toàn sang VietQR `/v2/lookup` bảo vệ client-id/api-key, tự động mock khi dev/test chưa có key).
- **Frontend:** Xây dựng `LocationAddressSelector` dùng chung (tích hợp `/api/public/locations`) và `BankSearchSelector` (dropdown tìm kiếm nhanh ngân hàng theo tên/mã) kèm logic debounce tự động tra cứu tên chủ tài khoản thật, điền vào `bankHolder` và đối soát với CCCD.

**Tech Stack:** Java 21, Spring Boot 3.4, RestClient, Vitest, React 19, TypeScript, Orval, Tailwind CSS v4, Phosphor Icons.

**Spec:** `docs/superpowers/specs/2026-10-26-seller-address-and-napas-bank-lookup-design.md`

## Repos / Worktrees
- Backend: `d:\fashionstore\.worktrees\fashionstore-seller-registration-ekyc` (nhánh `feature/seller-registration-ekyc`)
- Frontend: `d:\fashionstore\.worktrees\fashionstore-ui-seller-registration-ekyc` (nhánh `feature/seller-registration-ekyc`)

## Global Constraints
- Database schema: Giữ nguyên bảng `shop` (cột `address`, `bank_name`, `bank_account`, `bank_holder`).
- Chuỗi địa chỉ: Định dạng chuẩn `${detailAddress}, ${ward}, ${province}`.
- Không lộ `x-client-id` và `x-api-key` ở Frontend.
- Mã nguồn frontend tuân thủ AGENTS.md: không dùng ternary trong JSX, dùng `clsx`/template literal, không dùng inline style bừa bãi.
- Tất cả test suites backend và frontend phải PASS sau mỗi task.

## Review Focus
1. Số tài khoản chứa ký tự đặc biệt hoặc quá ngắn: Backend validate 6-25 ký tự chữ/số, frontend chặn gõ ký tự lạ.
2. VietQR API bên ngoài bị timeout hoặc lỗi mạng: Backend bắt exception, trả 400 Bad Request kèm thông báo thân thiện; danh sách ngân hàng fallback danh sách tĩnh ~55 ngân hàng.
3. Môi trường test/CI chưa cấu hình API key VietQR: Backend tự động fallback trả về tên tài khoản giả lập hợp lệ, không làm đỏ test.
4. Tên tài khoản trả về có dấu/không dấu/khoảng trắng thừa: Chuẩn hóa bằng `normalizeName` trước khi đối soát với `legalName` CCCD.
5. Địa chỉ cũ chỉ có 1 chuỗi không theo format chuẩn: Hàm `splitFullAddress` phân tách thông minh, không làm crash form khi mở hồ sơ shop cũ.

---

### Task 1: Backend DTOs & Configuration
**Files:**
- Modify: `src/main/resources/application.yml`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/bank/BankResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/bank/LookupAccountRequest.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/bank/LookupAccountResponse.java`

**Interfaces:**
- Produces:
  - `BankResponse(String id, String name, String code, String bin, String shortName, String logo, boolean lookupSupported)`
  - `LookupAccountRequest(String bin, String accountNumber)`
  - `LookupAccountResponse(String bankCode, String accountNumber, String accountName)`

- [ ] **Step 1: Thêm cấu hình VietQR vào `application.yml`**
  Thêm khối `app.vietqr` với `base-url`, `client-id`, `api-key`, `connect-timeout-ms`, `read-timeout-ms`.
- [ ] **Step 2: Tạo DTO records trong package `dto.bank`**
  Tạo `BankResponse`, `LookupAccountRequest` (kèm validation `@Pattern(regexp = "^[0-9]{6}$")` cho bin, `@Size(min = 6, max = 25)` cho accountNumber), và `LookupAccountResponse`.
- [ ] **Step 3: Compile verification**
  Run: `.\gradlew compileJava` -> BUILD SUCCESSFUL.
- [ ] **Step 4: Commit**
  `feat(bank): add VietQR configuration and bank DTO records`

---

### Task 2: Backend `BankService` & Fallback Data
**Files:**
- Create: `src/main/java/com/minhnbnt/fashionstore/features/bank/BankService.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/features/bank/VietQrBanksData.java`
- Create: `src/test/java/com/minhnbnt/fashionstore/features/bank/BankServiceTest.java`

**Interfaces:**
- Consumes: Task 1 DTOs & VietQR config
- Produces:
  - `BankService.listBanks(): List<BankResponse>`
  - `BankService.lookupAccount(String bin, String accountNumber): LookupAccountResponse`

- [ ] **Step 1: Viết test failing `BankServiceTest`**
  Test danh sách ngân hàng fallback, test tra cứu tài khoản thành công với mock RestClient, test tra cứu thất bại ném 400, test fallback mock khi key rỗng.
- [ ] **Step 2: Tạo `VietQrBanksData` chứa danh sách tĩnh ~55 ngân hàng chuẩn Napas**
  Bao gồm các ngân hàng lớn: VCB (970436), MB (970422), TCB (970407), ACB (970416), BIDV (970418), Agribank (970405), VPBank (970432), TPBank (970423), Sacombank (970403), VIB (970441), HDBank (970437), OCB (970448), SHB (970443), SeABank (970440), MSB (970426)...
- [ ] **Step 3: Cài đặt `BankService`**
  Dùng `RestClient` gọi `https://api.vietqr.io/v2/banks` và `https://api.vietqr.io/v2/lookup`. Cache danh sách ngân hàng in-memory. Bắt lỗi kết nối fallback `VietQrBanksData`. Nếu `client-id` rỗng -> trả về mock response hợp lệ.
- [ ] **Step 4: Chạy test verify**
  Run: `.\gradlew test --tests "com.minhnbnt.fashionstore.features.bank.BankServiceTest"` -> PASS.
- [ ] **Step 5: Commit**
  `feat(bank): implement BankService with VietQR lookup and static fallback`

---

### Task 3: Backend `BankController` & OpenAPI Export
**Files:**
- Create: `src/main/java/com/minhnbnt/fashionstore/features/bank/BankController.java`
- Create: `src/test/java/com/minhnbnt/fashionstore/features/bank/BankControllerTest.java`
- Modify: `src/test/java/com/minhnbnt/fashionstore/OpenApiExportTest.java` (chạy export spec mới)

**Interfaces:**
- Produces REST endpoints:
  - `GET /api/public/banks`
  - `POST /api/public/banks/lookup-account`

- [ ] **Step 1: Viết failing test `BankControllerTest` (`@WebMvcTest`)**
  Test endpoint `GET /api/public/banks` trả 200 và array ngân hàng. Test `POST /api/public/banks/lookup-account` với body hợp lệ trả 200, với body thiếu mã bin trả 400.
- [ ] **Step 2: Cài đặt `BankController`**
  Đánh dấu `@RestController`, `@RequestMapping("/api/public/banks")`, `@Tag(name = "Banks")`, `@Operation(operationId = "listBanks")`, `@Operation(operationId = "lookupAccount")`.
- [ ] **Step 3: Chạy test controller**
  Run: `.\gradlew test --tests "com.minhnbnt.fashionstore.features.bank.BankControllerTest"` -> PASS.
- [ ] **Step 4: Chạy `OpenApiExportTest` cập nhật openapi.json**
  Run: `.\gradlew test --tests "com.minhnbnt.fashionstore.OpenApiExportTest"` -> Tạo openapi.json mới nhất.
- [ ] **Step 5: Commit**
  `feat(bank): expose public bank list and account lookup REST endpoints`

---

### Task 4: Frontend OpenAPI Codegen
**Files:**
- Copy: `fashionstore/docs/openapi.json` sang `fashionstore-ui/docs/openapi.json`
- Generated: `src/generated/endpoints/banks/banks.ts`
- Generated: `src/generated/model/*`

- [ ] **Step 1: Sao chép file `openapi.json` sang repo frontend worktree**
- [ ] **Step 2: Chạy `pnpm codegen` (orval)**
  Run: `pnpm codegen` -> sinh các hook `useListBanks`, `useLookupAccount`.
- [ ] **Step 3: Commit**
  `chore(api): update openapi spec and generate bank client hooks`

---

### Task 5: Frontend `LocationAddressSelector` Component & Tests
**Files:**
- Create: `src/components/common/LocationAddressSelector.tsx`
- Create: `src/components/common/LocationAddressSelector.test.tsx` (hoặc test adapter tương ứng)

**Interfaces:**
- Consumes: `useListProvinces()`, `useListWards(provinceCode)` từ `@generated/endpoints/locations/locations`
- Produces:
  ```typescript
  export interface LocationAddressSelectorProps {
    value: string
    onChange: (fullAddress: string) => void
    required?: boolean
    disabled?: boolean
    idPrefix?: string
    error?: string
  }
  ```

- [ ] **Step 1: Viết test cho logic ghép và phân rã chuỗi địa chỉ của selector**
- [ ] **Step 2: Cài đặt `LocationAddressSelector`**
  - Searchable dropdown chọn Tỉnh/Thành phố (dùng `removeVietnameseAccents` để tìm kiếm nhanh).
  - Searchable dropdown chọn Phường/Xã (chỉ mở khi đã chọn Tỉnh).
  - Input số nhà, tên đường chi tiết.
  - Tự động ghép thành chuỗi `${detailAddress}, ${ward}, ${province}` khi bất kỳ trường nào thay đổi.
  - Giao diện chuẩn token pill bo tròn, dark mode navy, focus xanh dương đồng nhất Design System.
- [ ] **Step 3: Chạy test verify**
  Run: `pnpm test` -> PASS.
- [ ] **Step 4: Commit**
  `feat(ui): add LocationAddressSelector reusable component with search dropdowns`

---

### Task 6: Frontend `BankSearchSelector` Component & Tests
**Files:**
- Create: `src/components/seller/BankSearchSelector.tsx`
- Create: `src/lib/bankSearch.ts`
- Create: `src/lib/bankSearch.test.ts`

**Interfaces:**
- Consumes: `BankResponse` từ `@generated/model/bankResponse`
- Produces:
  ```typescript
  export interface BankSearchSelectorProps {
    banks: BankResponse[]
    selectedBankCode: string
    onSelectBank: (bank: BankResponse) => void
    disabled?: boolean
    error?: string
  }
  ```

- [ ] **Step 1: Viết test cho `filterBanks(banks, query)` trong `bankSearch.test.ts`**
  Test lọc theo tên viết tắt (VCB, MB, TCB), tên đầy đủ không dấu, mã BIN 6 số.
- [ ] **Step 2: Cài đặt `filterBanks` trong `bankSearch.ts`**
- [ ] **Step 3: Cài đặt `BankSearchSelector` component**
  - Nút trigger hiển thị logo ngân hàng + Tên viết tắt in đậm + Tên đầy đủ.
  - Modal/Popover tìm kiếm có ô search input, icon kính lúp, danh sách cuộn mượt.
  - Thiết kế chuẩn Design System, hỗ trợ dark mode.
- [ ] **Step 4: Chạy test verify**
  Run: `pnpm test` -> PASS.
- [ ] **Step 5: Commit**
  `feat(seller): add BankSearchSelector with instant search and logo support`

---

### Task 7: Tích hợp Address Selector & Napas Lookup vào `ShopKycRegistrationModal`
**Files:**
- Modify: `src/components/ShopKycRegistrationModal.tsx`

**Interfaces:**
- Consumes: `LocationAddressSelector`, `BankSearchSelector`, `useLookupAccount`, `useListBanks`

- [ ] **Step 1: Cập nhật Step 1 (Thông tin shop & Địa chỉ)**
  Thay thế ô input text `input-shop-address` bằng component `<LocationAddressSelector value={address} onChange={setAddress} />`.
- [ ] **Step 2: Cập nhật Step 3 (Tài khoản ngân hàng & eKYC)**
  - Tích hợp `BankSearchSelector` lấy danh sách từ `useListBanks()`.
  - Thêm state lưu mã BIN của ngân hàng được chọn.
  - Khi nhập xong số tài khoản (blur hoặc click "Tra cứu tài khoản Napas"):
    - Gọi `lookupAccountMutation({ data: { bin, accountNumber } })`.
    - Khi thành công: tự động điền `accountName` vào state `bankHolder`, khóa ô `bankHolder` (`readOnly`), hiện badge xanh `Đã xác thực Napas 24/7`.
    - Đối soát tự động với Họ tên trên CCCD (`legalName`): nếu trùng khớp hiện tick xanh hợp lệ; nếu sai lệch hiện cảnh báo và vô hiệu hóa nút Tiếp tục.
    - Khi thất bại: hiển thị thông báo lỗi từ server, reset `bankHolder`.
- [ ] **Step 3: Kiểm tra format code & lint**
  Run: `pnpm format && pnpm lint` -> 0 errors.
- [ ] **Step 4: Commit**
  `feat(seller): integrate address selector and Napas auto-lookup into shop KYC modal`

---

### Task 8: Tích hợp Address Selector vào `SellerProfileTab`
**Files:**
- Modify: `src/components/seller-dashboard-tab/SellerProfileTab.tsx`
- Modify: `src/components/seller-dashboard-tab/SellerProfileTab.test.tsx`

- [ ] **Step 1: Cập nhật trường địa chỉ kho lấy hàng trong `SellerProfileTab.tsx`**
  Thay thế input text `pickupAddressField` bằng `<LocationAddressSelector value={formData.pickupAddress} onChange={(val) => setFormData({ ...formData, pickupAddress: val })} />`.
- [ ] **Step 2: Cập nhật và chạy lại test `SellerProfileTab.test.tsx`**
  Run: `pnpm test src/components/seller-dashboard-tab/SellerProfileTab.test.tsx` -> PASS.
- [ ] **Step 3: Commit**
  `feat(seller): integrate LocationAddressSelector into SellerProfileTab`

---

### Task 9: Toàn diện Verification (Iron Law Gate)
**Files:** — (Toàn bộ workspace)

- [ ] **Step 1: Chạy toàn bộ test suite Backend**
  Run: `.\gradlew test` trong worktree backend -> BUILD SUCCESSFUL (tất cả test suites pass 100%).
- [ ] **Step 2: Chạy kiểm tra Frontend (Format, Lint, Test, Build)**
  Run: `pnpm format:check && pnpm lint && pnpm test && pnpm build` trong worktree frontend -> 0 errors, tất cả test passed, build bundle thành công.
- [ ] **Step 3: Báo cáo kết quả và đối soát với kế hoạch**
