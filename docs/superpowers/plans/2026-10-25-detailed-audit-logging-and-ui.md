# Chi Tiết Hóa Nhật Ký Hoạt Động (Detailed Audit Logging) & Giao Diện Hoạt Động (Activity Timeline) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nâng cấp hệ thống ghi nhật ký hoạt động (Audit Trail) của Kênh người bán:
1. Ghi nhận tường minh và chi tiết: **Ai đã làm gì, sửa cái gì, sửa dữ liệu nào (trước -> sau)** trên tất cả các luồng nghiệp vụ: Sản phẩm & Kho, Đơn hàng & Đổi trả, Phân quyền & Nhân sự, CSKH & Rút tiền.
2. Thiết kế lại giao diện Frontend (`SellerStaffTab.tsx`) theo phong cách **Activity Timeline hiện đại**: Phân loại màu sắc/icon theo nhóm hành động, Việt hóa đối tượng (`targetType`), hộp hiển thị chi tiết thay đổi nổi bật và thêm ô tìm kiếm nhanh từ khóa.

**Architecture Constraints:**
- **Không dùng toán tử ba ngôi (`? :`)** ở bất kỳ đâu trên cả Java và TypeScript/TSX (dùng `if` + early return hoặc `&&` trong JSX).
- **NullAway:** Không dùng `@NonNull` trên record components; chỉ dùng `@Nullable` khi dữ liệu có thể null.
- **Conventional Commits:** Không kèm dòng `Co-Authored-By`.

---

## File Structure & Proposed Changes

### Backend (`fashionstore`)
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/catalog/CatalogService.java`
  - Ghi chi tiết cho `PRODUCT_CREATE`, `PRODUCT_UPDATE` (so sánh diff giá, tên, danh mục, phân loại), `PRODUCT_STATUS` (trạng thái cũ -> mới), `PRODUCT_DELETE`, `PRODUCT_IMAGE_DELETE`, `INVENTORY_ADJUST`.
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java`
  - Ghi chi tiết cho `ORDER_CANCEL` (lý do hủy, số lượng hoàn kho), `ORDER_CONFIRM` (tổng tiền, số món), `ORDER_SHIP`, `ORDER_DELIVER`.
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStaffService.java`
  - Ghi chi tiết cho `CREATE_ROLE` (kèm danh sách quyền), `UPDATE_ROLE` (so sánh quyền thêm mới và quyền bị gỡ, đổi tên), `DELETE_ROLE`.
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/chat/ChatService.java`
  - Ghi chi tiết hội thoại cho `CHAT_REPLY`.
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/shop/ShopAuditIntegrationTest.java`
  - Bổ sung test kiểm tra nội dung `detail` của các hành động được làm giàu.

### Frontend (`fashionstore-ui`)
- Modify: `fashionstore-ui/src/lib/sellerPermissions.ts`
  - Bổ sung `REVIEW_REPORT`, `PRODUCT_STATUS_BULK` vào `PERMISSION_LABELS`.
  - Thêm helper `getAuditActionBadgeMeta(action)` phân loại 5 nhóm màu sắc (`danger`, `success`, `info`, `warning`, `neutral`).
  - Thêm helper `formatTargetType(targetType)` Việt hóa đối tượng.
- Test: `fashionstore-ui/src/lib/sellerPermissions.test.ts`
  - Kiểm thử `getAuditActionBadgeMeta` và `formatTargetType`.
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/SellerStaffTab.tsx`
  - Thay thế danh sách audit log thô bằng **Activity Timeline**: Avatar người thực hiện, Badge phân màu, Tag đối tượng tiếng Việt, Box chi tiết thay đổi dữ liệu, Icon thời gian.
  - Bổ sung ô tìm kiếm từ khóa (`auditSearchQuery`) lọc tức thì.
- Test: `fashionstore-ui/src/pages/__tests__/ShopDashboardNavigation.test.tsx` hoặc component test.

---

## Tasks

### Task 1: Backend - Chi Tiết Hóa Nhật Ký Sản Phẩm & Kho (`CatalogService.java`)

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/catalog/CatalogService.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/catalog/CatalogServiceAuditTest.java` (hoặc thêm vào test tích hợp)

- [x] **Step 1: Write test cho Catalog audit logging**
  Viết test kiểm tra khi `createProduct`, `updateProduct`, `changeProductStatus`:
  - `PRODUCT_CREATE` ghi rõ tên sản phẩm, danh mục, giá min, tổng tồn kho.
  - `PRODUCT_UPDATE` ghi rõ các trường thay đổi (ví dụ: tên cũ -> tên mới, giá cũ -> giá mới).
  - `PRODUCT_STATUS` ghi rõ trạng thái cũ -> trạng thái mới.
- [x] **Step 2: Run test to confirm it fails**
  Chạy test và xác nhận fail do dữ liệu detail hiện tại chỉ là tên sản phẩm.
- [x] **Step 3: Implement minimal code trong `CatalogService.java`**
  - Trong `createProduct`: Tính toán tổng tồn kho ban đầu và giá min, ghi log:
    `"Tạo sản phẩm: \"" + product.getName() + "\" | Danh mục: " + categoryName + " | Giá: " + formatVnd(minPrice) + " | Tồn kho ban đầu: " + totalStock + " (" + variants.size() + " phân loại)"`
  - Trong `updateProduct`: So sánh đối tượng `existing` với `request`:
    - Tạo danh sách thay đổi `changes`:
      - Nếu đổi tên: `"Tên: \"" + existing.getName() + "\" -> \"" + request.name() + "\""`
      - Nếu đổi mô tả: `"Cập nhật mô tả"`
      - Nếu đổi danh mục: `"Đổi danh mục"`
      - Nếu đổi giá: `"Cập nhật giá và phân loại"`
    - Ghi log: `"Cập nhật sản phẩm: \"" + saved.getName() + "\" | Thay đổi: " + diffSummary`
  - Trong `changeProductStatus`:
    `"Đổi trạng thái sản phẩm \"" + product.getName() + "\": [" + oldStatus + "] -> [" + targetStatus.name() + "]"`
  - Trong `deleteProduct`:
    `"Xóa sản phẩm: \"" + product.getName() + "\" (Mã ID: " + productId + ")"`
  - Trong `deleteImage`:
    `"Xóa ảnh của sản phẩm: \"" + product.getName() + "\" (Vị trí thứ " + (position + 1) + ")"`
- [x] **Step 4: Run test to confirm it passes**
  Chạy lại test và xác nhận PASS.
- [x] **Step 5: Commit**
  `git commit -m "feat(audit): enrich product and inventory audit log details"`

---

### Task 2: Backend - Chi Tiết Hóa Nhật Ký Đơn Hàng, Đổi Trả, Vai Trò & CSKH

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStaffService.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/chat/ChatService.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/shop/ShopAuditIntegrationTest.java`

- [x] **Step 1: Write integration test**
  Trong `ShopAuditIntegrationTest.java`:
  - Kiểm tra `updateRole`: ghi nhận rõ quyền được thêm (+), quyền bị bỏ (-), tên đổi.
  - Kiểm tra `cancelOrder`: ghi nhận rõ mã đơn, lý do hủy đơn.
  - Kiểm tra `chatReply`: không còn ghi null detail.
- [x] **Step 2: Run test to confirm it fails**
  Chạy test và xác nhận fail.
- [x] **Step 3: Implement minimal code**
  - Trong `OrderService.java`:
    - `cancelOrder`: Ghi log:
      `"Hủy đơn hàng #" + saved.getCode() + ". Lý do: " + reason + " | Khôi phục tồn kho cho " + items.size() + " mặt hàng"`
    - `confirmOrder`:
      `"Xác nhận đơn hàng #" + saved.getCode() + " | Tổng tiền: " + formatVnd(saved.getTotal()) + " (" + items.size() + " món)"`
    - `shipOrder`:
      `"Giao đơn hàng #" + saved.getCode() + " cho đơn vị vận chuyển"`
    - `deliverOrder`:
      `"Đánh dấu đơn hàng #" + saved.getCode() + " giao thành công"`
  - Trong `ShopStaffService.java`:
    - `createRole`: Ghi log kèm danh sách quyền:
      `"Tạo vai trò: " + role.getName() + " gồm " + perms.size() + " quyền: " + permsJoined`
    - `updateRole`: So sánh quyền cũ (`oldPerms`) và quyền mới (`newPerms`):
      - Tìm quyền mới thêm: `addedPerms` -> `"+Quyền A, +Quyền B"`
      - Tìm quyền bị gỡ: `removedPerms` -> `"-Quyền C"`
      - Nếu đổi tên: `"Đổi tên: [" + oldName + "] -> [" + newName + "]"`
      - Ghi log: `"Cập nhật vai trò: " + role.getName() + " | Thay đổi: " + diffStr`
  - Trong `ChatService.java`:
    - `sendMessage`: Ghi log: `"Gửi tin nhắn phản hồi tới khách hàng trong hội thoại #" + conv.getId()`
- [x] **Step 4: Run test to confirm it passes**
  Chạy `./gradlew test --tests com.minhnbnt.fashionstore.features.shop.ShopAuditIntegrationTest` và xác nhận PASS.
- [x] **Step 5: Commit**
  `git commit -m "feat(audit): enrich order, role and chat audit log details with field changes"`

---

### Task 3: Frontend - Phân Nhóm Màu Sắc, Icon & Việt Hóa Đối Tượng (`sellerPermissions.ts`)

**Files:**
- Modify: `fashionstore-ui/src/lib/sellerPermissions.ts`
- Test: `fashionstore-ui/src/lib/sellerPermissions.test.ts`

- [x] **Step 1: Write unit tests**
  Tạo test trong `sellerPermissions.test.ts`:
  - Test `formatTargetType('SHOP_MEMBER')` -> `'Thành viên'`
  - Test `formatTargetType('RETURN_REQUEST')` -> `'Yêu cầu đổi trả'`
  - Test `formatTargetType('PRODUCT')` -> `'Sản phẩm'`
  - Test `getAuditActionBadgeMeta('PRODUCT_DELETE')` -> `{ type: 'danger', colorClass: '...' }`
  - Test `getAuditActionBadgeMeta('ORDER_DELIVER')` -> `{ type: 'success', colorClass: '...' }`
  - Test `getAuditActionBadgeMeta('CHANGE_MEMBER_ROLE')` -> `{ type: 'info', colorClass: '...' }`
- [x] **Step 2: Run test to confirm it fails**
  Chạy `pnpm test sellerPermissions` và xác nhận fail do chưa có hàm.
- [x] **Step 3: Implement minimal code trong `sellerPermissions.ts`**
  - Bổ sung `REVIEW_REPORT: 'Báo cáo đánh giá vi phạm'` và `PRODUCT_STATUS_BULK: 'Đổi trạng thái hàng loạt'`.
  - Triển khai `formatTargetType(targetType: string | null | undefined): string` chuyển đổi sang tiếng Việt.
  - Triển khai `getAuditActionBadgeMeta(action: string)` trả về `colorClass`, `iconType` (danger/success/info/warning/neutral).
- [x] **Step 4: Run test to confirm it passes**
  Chạy lại test và xác nhận PASS 100%.
- [x] **Step 5: Commit**
  `git commit -m "feat(permissions): add audit action badge metadata and target type localization"`

---

### Task 4: Frontend - Thiết Kế Lại Giao Diện Activity Timeline & Tìm Kiếm Từ Khóa (`SellerStaffTab.tsx`)

**Files:**
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/SellerStaffTab.tsx`

- [x] **Step 1: Thiết kế lại Sub-tab AUDIT**
  - Thay thế danh sách phẳng hiện tại bằng **Activity Timeline Card**:
    - Avatar người thực hiện:
      - Chữ cái viết hoa đầu của `actorName` hoặc `actorEmail`, bo tròn mềm mại.
      - Nếu không có người thực hiện (thao tác tự động): hiển thị icon `Robot` / `Hệ thống`.
    - Hàng tiêu đề thẻ:
      - Badge hành động mang màu sắc theo nhóm (`getAuditActionBadgeMeta`).
      - Tên người thực hiện (in đậm) kèm email.
      - Badge đối tượng (`formatTargetType`) nhỏ gọn, thanh lịch.
      - Thời gian thực hiện kèm icon `Clock` ở góc phải.
    - Hộp nội dung chi tiết (`detail`):
      - Khối `div` bo góc `rounded-xl border border-zinc-200/60 bg-zinc-50/70 p-2.5 dark:border-white/10 dark:bg-white/[0.03]`.
      - Text hiển thị rõ nét, phân tách các vế `|` bằng ký tự hoặc badge nhấn mạnh.
  - Bổ sung thanh công cụ:
    - Ô tìm kiếm từ khóa (`auditSearchQuery`): Lọc theo tên người thực hiện, email, hoặc chuỗi chi tiết `detail`.
    - Dropdown lọc theo loại hành động hiện có.
- [x] **Step 2: Run test & lint**
  - Chạy `pnpm lint` (0 error, 0 warning).
  - Chạy `pnpm test` (xác nhận toàn bộ test suite pass).
- [x] **Step 3: Commit**
  `git commit -m "feat(seller): redesign activity audit log as modern timeline card with search"`

---

### Task 5: Verification & E2E Validation Toàn Diện

- [ ] **Step 1: Backend test suite**
  Chạy `./gradlew test --tests com.minhnbnt.fashionstore.features.shop.ShopAuditIntegrationTest`.
- [ ] **Step 2: Frontend quality gate**
  Chạy `pnpm format:check && pnpm lint && pnpm test && pnpm build`.
- [ ] **Step 3: Trình duyệt thực tế (Browser Subagent)**
  Khởi chạy subagent kiểm thử trên Chrome:
  - Mở `/shop?tab=staff` -> Sub-tab "Nhật ký hoạt động".
  - Kiểm tra hiển thị danh sách dạng Timeline với màu sắc badge phân biệt, avatar người thực hiện, tag đối tượng tiếng Việt và hộp chi tiết thay đổi dữ liệu.
  - Thử gõ tìm kiếm từ khóa trong ô tìm kiếm mới.
  - Chụp ảnh màn hình lưu artifact.
- [ ] **Step 4: Hoàn tất branch & báo cáo**
