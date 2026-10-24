# Multi-Shop Staff Membership, Enhanced Audit Logging & Strict RBAC Enforcement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cho phép 1 người dùng tham gia làm nhân viên và quản lý nhiều gian hàng cùng lúc (với giao diện quản lý trên Hồ sơ và Shop Switcher trên Kênh người bán), làm giàu chi tiết nhật ký hoạt động & sửa bộ lọc `CHANGE_MEMBER_ROLE`, và thắt chặt phân quyền tuyệt đối cho trang Hồ sơ shop (chỉ OWNER) và Tổng quan shop (cần REVENUE_READ).

**Architecture:** Mở rộng tầng Data Access với `SellerShopMembershipResponse` và query join `shop_member` với `shop` & `shop_role` để trả về toàn bộ vai trò và quyền của người dùng; thắt chặt quyền tại `ShopService.updateProfile` và `ShopStatsService` với `ShopPermission.REVENUE_READ`; đồng bộ mã hành động audit; bổ sung tab `MyShopsTab` trên trang cá nhân và component `ShopSwitcher` kèm auto-navigation trên Seller Dashboard.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JDBC, Postgres, Orval OpenAPI generator, React 19, TypeScript, Vite, Tailwind CSS v4, Phosphor Icons.

**Spec:** [docs/superpowers/specs/2026-10-25-seller-staff-multi-shop-and-rbac-fixes-design.md](file:///d:/fashionstore/fashionstore/docs/superpowers/specs/2026-10-25-seller-staff-multi-shop-and-rbac-fixes-design.md)

## Global Constraints

- Backend: tuân thủ nghiêm ngặt `fashionstore/AGENTS.md`, `docs/code-guide.md`, `docs/structure.md`, `docs/data.md`.
- Tuyệt đối cấm ternary operator (`? :`) trên cả Backend và Frontend. Dùng `if` + early `return`, trên JSX dùng `&&`.
- Entity cấm `@Data`, `@Setter`, `@EqualsAndHashCode`, `@Builder`.
- Mặc định non-null (NullAway). Hạn chế tối đa `@Nullable` — chỉ dùng khi nghiệp vụ thật sự cho phép thiếu.
- Exception/message tiếng Anh.
- Mọi endpoint mới đủ springdoc annotation: `@Tag`, `@Operation(operationId, summary, description)`, `@ApiResponse` từng mã, `@Parameter`, `@Schema` từng field DTO; endpoint cần auth gắn `@SecurityRequirement(name = "bearerAuth")`.
- `id` user luôn lấy từ JWT sub: `var subject = Objects.requireNonNull(jwt.getSubject()); var userId = UUID.fromString(subject);`.
- Testcontainers dùng image Postgres của project `ghcr.io/minhnbnt/postgresql:latest`.
- Frontend: chỉ dùng `pnpm`. Chạy `pnpm format`, `pnpm lint` (0 error, 0 warning), `pnpm build` xanh trước khi commit.
- Mọi git commit không có dòng `Co-Authored-By`.

## Review Focus

1. **Staff gọi `updateProfile` trái phép**: Một nhân viên gọi API `PUT /api/shops/{id}/profile` để đổi số tài khoản ngân hàng của chủ shop -> Kỳ vọng: Backend chặn ngay lập tức bằng `403 FORBIDDEN`.
2. **Staff không có quyền `REVENUE_READ` truy cập Tổng quan hoặc Thống kê**: Nhân viên chỉ có quyền Chat gọi API thống kê doanh thu hoặc mở tab `OVERVIEW` -> Kỳ vọng: Backend chặn `403 FORBIDDEN`, Frontend tự động điều hướng sang tab hợp lệ đầu tiên (ví dụ `CHAT` hoặc `ORDERS`).
3. **Staff bị vô hiệu hóa (`DEACTIVATED`) ở một shop**: Người dùng là nhân viên ở 2 shop A (DEACTIVATED) và B (ACTIVE) -> Kỳ vọng: Tab "Gian hàng của tôi" hiển thị đúng badge trạng thái từng shop; người dùng không thể vào Kênh người bán của shop A nhưng vẫn vào và làm việc bình thường tại shop B.
4. **Lọc nhật ký hoạt động theo `CHANGE_MEMBER_ROLE`**: Chủ shop đổi vai trò một nhân viên và vào tab Audit chọn bộ lọc "Đổi vai trò nhân viên" -> Kỳ vọng: Bảng hiển thị chính xác bản ghi với nội dung chi tiết: ai đã đổi vai trò của nhân viên nào thành vai trò mới gì.
5. **Chuyển đổi gian hàng trên Kênh người bán**: Người dùng thuộc 2 shop chuyển đổi qua lại bằng `ShopSwitcher` -> Kỳ vọng: URL cập nhật đúng `?shopId=...`, dữ liệu đơn hàng, sản phẩm, tin nhắn và quyền hạn cập nhật tức thì theo đúng shop được chọn mà không cần tải lại trang.

---

## Task Decomposition

### Task 1: Backend DTO & Repository Query cho Multi-Shop Memberships

**Files:**
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/shop/SellerShopMembershipResponse.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/shop/ShopMemberRepository.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/shop/ShopMemberRepositoryTest.java`

- [ ] **Step 1: Write failing test**
  Tạo test trong `ShopMemberRepositoryTest.java` kiểm tra query `findMembershipsByUser(userId)`:
  Seed một user thuộc 2 shop (1 shop là OWNER, 1 shop là STAFF có role). Xác minh hàm trả về đủ 2 bản ghi với đầy đủ `shopName`, `role`, `roleName`, `memberStatus`, `permissions`.
- [ ] **Step 2: Run test to confirm it fails**
  Chạy `./gradlew test --tests com.minhnbnt.fashionstore.features.shop.ShopMemberRepositoryTest` và xác nhận test không biên dịch được do chưa có method/DTO.
- [ ] **Step 3: Implement minimal code**
  - Tạo `SellerShopMembershipResponse.java` dạng Java record với đầy đủ `@Schema` cho từng trường.
  - Thêm `record UserShopMembershipRow` và method `findMembershipsByUser(UUID userId)` trong `ShopMemberRepository.java` sử dụng `jdbc.sql(...)`.
- [ ] **Step 4: Run test to confirm it passes**
  Chạy `./gradlew test --tests com.minhnbnt.fashionstore.features.shop.ShopMemberRepositoryTest` và xác nhận test PASS.
- [ ] **Step 5: Commit**
  `git commit -m "feat(shop): add multi-shop membership DTO and repository query"`

---

### Task 2: Backend Strict RBAC Enforcement trên Hồ sơ & Thống kê

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/shop/ShopService.java:170-195`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStatsService.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/shop/ShopSecurityRbacTest.java`

- [ ] **Step 1: Write failing test**
  Viết test kiểm tra phân quyền chặt chẽ trong `ShopSecurityRbacTest.java`:
  - Test 1: Nhân viên STAFF gọi `shopService.updateProfile(...)` phải ném `ResponseStatusException(HttpStatus.FORBIDDEN)`.
  - Test 2: Nhân viên STAFF không có quyền `REVENUE_READ` gọi `shopStatsService.getDashboard(...)` phải ném `ResponseStatusException(HttpStatus.FORBIDDEN)`.
  - Test 3: Chủ shop OWNER gọi 2 method trên thành công bình thường.
- [ ] **Step 2: Run test to confirm it fails**
  Chạy test và xác nhận thất bại do code hiện tại vẫn cho phép STAFF truy cập.
- [ ] **Step 3: Implement minimal code**
  - Trong `ShopService.java`, sửa `updateProfile`: đổi `access.requireShopRole(userId, id, MemberRole.OWNER, MemberRole.STAFF)` thành `access.requireShopRole(userId, id, MemberRole.OWNER)`.
  - Trong `ShopStatsService.java`, đổi các method thống kê sang `access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ)`.
- [ ] **Step 4: Run test to confirm it passes**
  Chạy lại `ShopSecurityRbacTest` và xác nhận tất cả các case PASS.
- [ ] **Step 5: Commit**
  `git commit -m "fix(security): restrict shop profile update to owner and require REVENUE_READ for statistics"`

---

### Task 3: Backend Làm Giàu Nhật Ký Hoạt Động (Detailed Audit Logging)

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStaffService.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/shop/ShopDecorationService.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/ReviewService.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/ReturnRequestService.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/shop/ShopStaffAuditDetailTest.java`

- [ ] **Step 1: Write failing test**
  Tạo test trong `ShopStaffAuditDetailTest.java` kiểm tra nội dung cột `detail` trong `shop_member_audit`:
  - Khi mời nhân viên: `detail` phải chứa cả email và tên vai trò mời (`"Mời " + email + " vào vai trò " + roleName`).
  - Khi đổi vai trò: `detail` phải chứa email nhân viên bị đổi vai trò và tên vai trò mới.
  - Khi vô hiệu hóa: `detail` phải chứa email nhân viên bị khóa.
- [ ] **Step 2: Run test to confirm it fails**
  Chạy test và xác nhận thất bại do nội dung hiện tại chỉ là `"New role: ..."` hoặc `"Status: DEACTIVATED"`.
- [ ] **Step 3: Implement minimal code**
  - Trong `ShopStaffService.java`: Nâng cấp chi tiết cho `INVITE_STAFF`, `ACCEPT_INVITATION`, `REVOKE_INVITATION`, `CHANGE_MEMBER_ROLE`, `DEACTIVATE_MEMBER`, `ACTIVATE_MEMBER`, `CREATE_ROLE`, `UPDATE_ROLE`, `DELETE_ROLE`.
  - Trong `ShopDecorationService.java`: Bổ sung chi tiết cho `DECORATION_SAVE`, `DECORATION_PUBLISH`, `DECORATION_DISCARD`.
  - Trong `ReviewService.java`: Bổ sung chi tiết cho `REVIEW_REPLY`, `REVIEW_REPORT`.
  - Trong `ReturnRequestService.java`: Bổ sung chi tiết cho `RETURN_APPROVE`, `RETURN_REJECT`.
- [ ] **Step 4: Run test to confirm it passes**
  Chạy test và xác nhận các assert về audit detail PASS 100%.
- [ ] **Step 5: Commit**
  `git commit -m "feat(audit): enrich activity log details with context and user emails"`

---

### Task 4: Backend Endpoint `GET /api/seller/my-shops` & OpenAPI Export

**Files:**
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/shop/controller/SellerShopMembershipController.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStaffService.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/shop/controller/SellerShopMembershipControllerTest.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/OpenApiExportTest.java`

- [ ] **Step 1: Write failing test**
  Viết test controller `SellerShopMembershipControllerTest.java` kiểu slice (`@WebMvcTest`):
  Kiểm tra `GET /api/seller/my-shops` với JWT trả về `200 OK` cùng danh sách shop memberships của user.
- [ ] **Step 2: Run test to confirm it fails**
  Chạy test và xác nhận lỗi 404 do chưa có endpoint.
- [ ] **Step 3: Implement minimal code**
  - Thêm method `getMyMemberships(userId)` trong `ShopStaffService.java`.
  - Tạo `SellerShopMembershipController.java` với annotation Springdoc đầy đủ (`operationId = "getMyShops"`, `@Tag`, `@SecurityRequirement(name = "bearerAuth")`).
- [ ] **Step 4: Run test to confirm it passes**
  Chạy `SellerShopMembershipControllerTest` và xác nhận PASS.
- [ ] **Step 5: Export OpenAPI spec sang frontend**
  Chạy `./gradlew test --tests com.minhnbnt.fashionstore.OpenApiExportTest --rerun` để ghi lại `fashionstore-ui/docs/openapi.json`.
- [ ] **Step 6: Commit**
  `git commit -m "feat(api): expose GET /api/seller/my-shops and export openapi spec"`

---

### Task 5: Frontend Codegen & Đồng Bộ Bộ Lọc Audit Log

**Files:**
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/SellerStaffTab.tsx`
- Modify: `fashionstore-ui/src/lib/sellerPermissions.ts`
- Test: `fashionstore-ui/src/lib/__tests__/sellerPermissions.test.ts`

- [ ] **Step 1: Chạy codegen frontend**
  Chạy `pnpm api:gen` tại `fashionstore-ui`. Xác nhận file `@generated/endpoints/seller-staff-roles/seller-staff-roles.ts` sinh ra hook `useGetMyShops`.
- [ ] **Step 2: Write failing test cho permissions & filters**
  Tạo test trong `sellerPermissions.test.ts`:
  - Test 1: `permissionLabel('CHANGE_MEMBER_ROLE')` phải trả về `'Đổi vai trò nhân viên'`.
  - Test 2: `canSeeTab('PROFILE', [], false)` phải trả về `false`.
  - Test 3: `canSeeTab('OVERVIEW', [], false)` phải trả về `false`.
  - Test 4: `canSeeTab('OVERVIEW', ['REVENUE_READ'], false)` phải trả về `true`.
  - Test 5: `canSeeTab('PROFILE', ['REVENUE_READ', 'PRODUCT_WRITE'], false)` phải trả về `false` (chỉ OWNER).
- [ ] **Step 3: Run test to confirm it fails**
  Chạy `pnpm test sellerPermissions` và xác nhận các test trên fail theo đúng kỳ vọng.
- [ ] **Step 4: Implement minimal code**
  - Trong `SellerStaffTab.tsx`: Đổi `'CHANGE_ROLE'` thành `'CHANGE_MEMBER_ROLE'` trong `AUDIT_ACTIONS`.
  - Trong `lib/sellerPermissions.ts`: Cập nhật `PERMISSION_LABELS` (thêm `CHANGE_MEMBER_ROLE`), sửa hàm `canSeeTab` thắt chặt `PROFILE` (chỉ `isOwner`) và `OVERVIEW` (cần `REVENUE_READ`).
- [ ] **Step 5: Run test to confirm it passes**
  Chạy `pnpm test sellerPermissions` và xác nhận 100% tests PASS.
- [ ] **Step 6: Commit**
  `git commit -m "fix(permissions): sync CHANGE_MEMBER_ROLE audit action and tighten tab access"`

---

### Task 6: Frontend Tab "Gian Hàng Của Tôi" trên Trang Hồ Sơ (`/account`)

**Files:**
- Create: `fashionstore-ui/src/components/account/MyShopsTab.tsx`
- Modify: `fashionstore-ui/src/pages/Account.tsx`
- Test: `fashionstore-ui/src/components/account/__tests__/MyShopsTab.test.tsx`

- [ ] **Step 1: Write component test**
  Tạo test trong `MyShopsTab.test.tsx`:
  Render danh sách 2 shop (1 shop là OWNER, 1 shop là STAFF). Kiểm tra hiển thị đúng tên shop, badge vai trò "Chủ gian hàng" và "Kho vận (Warehouse)", nút "Vào Kênh Người Bán" trỏ đúng URL `/shop?shopId=...`.
- [ ] **Step 2: Run test to confirm it fails**
  Chạy `pnpm test MyShopsTab` và xác nhận test fail do chưa có component.
- [ ] **Step 3: Implement `MyShopsTab.tsx`**
  - Tạo `MyShopsTab.tsx` gọi `useGetMyShops()`.
  - Thiết kế danh sách thẻ shop theo phong cách sang trọng, bo góc tròn, glassmorphism chuẩn Design System của shop.
  - Hiển thị badge trạng thái shop (`APPROVED`, `PENDING`, `REJECTED`), badge trạng thái thành viên (`ACTIVE`, `DEACTIVATED`).
  - Nút chuyển hướng Kênh Người Bán dẫn đến `/shop?shopId={shopId}` (với shop đã duyệt và thành viên ACTIVE).
  - Tích hợp tab chọn vào `Account.tsx`: Phân tách rõ ràng giữa thông tin cá nhân, danh sách gian hàng và sổ địa chỉ.
- [ ] **Step 4: Run test to confirm it passes**
  Chạy `pnpm test MyShopsTab` và kiểm tra `pnpm lint` 0 lỗi, 0 warning.
- [ ] **Step 5: Commit**
  `git commit -m "feat(account): add MyShopsTab allowing users to manage multiple shops"`

---

### Task 7: Frontend Shop Switcher & Auto-Navigation trên Kênh Người Bán

**Files:**
- Create: `fashionstore-ui/src/components/seller/ShopSwitcher.tsx`
- Modify: `fashionstore-ui/src/pages/ShopDashboard.tsx`
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/SellerLayout.tsx`
- Test: `fashionstore-ui/src/pages/__tests__/ShopDashboardNavigation.test.tsx`

- [ ] **Step 1: Write test cho Auto-Navigation**
  Tạo test trong `ShopDashboardNavigation.test.tsx`:
  - Giả lập nhân viên chỉ có quyền `CHAT_ACCESS` vào gian hàng: Hệ thống tự động chuyển hướng sang `tab=chat`, không render `SellerOverviewTab`.
  - Giả lập URL có param `shopId`: Dashboard tải đúng thông tin gian hàng theo `shopId`.
- [ ] **Step 2: Run test to confirm it fails**
  Chạy test và xác nhận fail.
- [ ] **Step 3: Implement `ShopSwitcher.tsx` & Auto-Navigation**
  - Tạo `ShopSwitcher.tsx`: Dropdown chọn gian hàng đặt trên Header của `SellerLayout.tsx`. Cho phép chuyển đổi linh hoạt giữa các gian hàng người dùng tham gia.
  - Cập nhật `ShopDashboard.tsx`:
    - Đọc `shopId` từ search params.
    - Tìm shop tương ứng trong danh sách `useGetMyShops()`.
    - Kiểm tra `canSeeTab(activeTab, permissions, isOwner)`: Nếu `false`, tự động tìm tab được phép đầu tiên và điều hướng sang tab đó.
    - Nếu nhân viên không có bất kỳ quyền nào trong shop, hiển thị thông báo thân thiện "Bạn chưa được cấp quyền thao tác tại gian hàng này".
- [ ] **Step 4: Run test to confirm it passes**
  Chạy lại test và xác nhận PASS.
- [ ] **Step 5: Commit**
  `git commit -m "feat(seller): add ShopSwitcher and smart auto-navigation based on permissions"`

---

### Task 8: Verification & E2E Validation Toàn Diện

**Files:**
- Verify All

- [ ] **Step 1: Full Backend Test Suite**
  Chạy `./gradlew test` trên backend. Xác nhận 100% tests pass qua Testcontainers PostgreSQL.
- [ ] **Step 2: Full Frontend Quality Gate**
  Chạy `pnpm format && pnpm lint && pnpm test && pnpm build` trên `fashionstore-ui`. Xác nhận 0 error, 0 warning, build thành công bundle.
- [ ] **Step 3: Trình duyệt thực tế (Browser Subagent)**
  Khởi chạy subagent kiểm thử:
  - Đăng nhập tài khoản tham gia 2 shop.
  - Vào `/account`, kiểm tra tab "Gian hàng của tôi".
  - Click vào shop 1 -> Chuyển sang Kênh người bán shop 1.
  - Dùng Shop Switcher chuyển sang shop 2 -> Kiểm tra đổi shop thành công.
  - Đăng nhập nhân viên không có `REVENUE_READ` -> Xác nhận không vào được Tổng quan và Hồ sơ shop, tự động điều hướng sang Đơn hàng/Chat.
  - Vào tab Nhật ký hoạt động -> Lọc theo `CHANGE_MEMBER_ROLE` -> Xác nhận hiển thị đúng bản ghi đổi vai trò với thông tin chi tiết.
- [ ] **Step 4: Commit & Hoàn tất branch**
  Kiểm tra git working tree sạch sẽ trên cả hai repo.
