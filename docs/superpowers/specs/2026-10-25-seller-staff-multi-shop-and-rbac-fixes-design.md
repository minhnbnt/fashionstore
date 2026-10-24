# Design Specification: Multi-Shop Staff Membership, Enhanced Audit Logging & Strict RBAC Enforcement

- **Feature**: Multi-Shop Staff Membership, Enhanced Audit Logging & Strict RBAC Enforcement
- **Date**: 2026-10-25
- **Branch**: `feature/seller-staff-2.8.1`
- **Scope**: Backend (`fashionstore`) & Frontend (`fashionstore-ui`)
- **Authority**: Follows `fashionstore/AGENTS.md`, `fashionstore-ui/AGENTS.md`, `docs/code-guide.md`, `docs/structure.md`, `docs/data.md`.

---

## 1. Problem Statement & Business Goals

### 1.1 Vấn đề 1: Một người dùng chưa thể làm nhân viên hoặc quản lý nhiều shop
- **Hiện trạng Database**: Bảng `shop_member(shop_id, user_id)` đã có khóa chính là `(shop_id, user_id)`, cho phép 1 user thuộc về nhiều shop khác nhau.
- **Hiện trạng API**: Chỉ có `GET /api/shops/mine` trả về danh sách `ShopResponse` đơn thuần, thiếu thông tin về vai trò (`role`: OWNER/STAFF), tên vai trò (`roleName`: "Quản lý", "Kho vận"), trạng thái thành viên (`ACTIVE`/`DEACTIVATED`), ngày gia nhập và danh sách quyền trong từng shop.
- **Hiện trạng Frontend**:
  - Trang Hồ sơ người dùng (`/account` - [`Account.tsx`](file:///d:/fashionstore/fashionstore-ui/src/pages/Account.tsx)): Cột bên phải chỉ lấy `shops[0]`, coi như người dùng chỉ sở hữu tối đa 1 shop. Thiếu hẳn tab quản lý danh sách các gian hàng mà người dùng tham gia (cả vai trò Chủ shop và vai trò Nhân viên).
  - Kênh Người Bán (`/shop` - [`ShopDashboard.tsx`](file:///d:/fashionstore/fashionstore-ui/src/pages/ShopDashboard.tsx)): Cũng chỉ lấy `shops[0]`, không hỗ trợ chọn shop qua URL query param `shopId` và không có bộ chuyển đổi shop (**Shop Switcher**).
- **Mục tiêu**:
  - Backend cung cấp endpoint `GET /api/seller/my-shops` trả về danh sách shop kèm membership detail.
  - Frontend bổ sung tab **"Gian hàng của tôi"** trong trang Hồ sơ cá nhân (`/account`).
  - Hỗ trợ tham số `shopId` trên URL Kênh Người Bán (`/shop?shopId=...&tab=...`) và tích hợp **Shop Switcher** trên thanh điều hướng.

### 1.2 Vấn đề 2: Nhật ký hoạt động còn sơ sài & Lỗi bộ lọc hành động
- **Hiện trạng**:
  - Khi chủ shop đổi vai trò nhân viên, backend ghi `action = "CHANGE_MEMBER_ROLE"`, nhưng frontend định nghĩa trong `AUDIT_ACTIONS` và `PERMISSION_LABELS` là `'CHANGE_ROLE'`. Khi người dùng lọc theo "Đổi vai trò nhân viên", request gửi `?action=CHANGE_ROLE` dẫn tới kết quả rỗng.
  - Cột `detail` của rất nhiều hành động quan trọng trong hệ thống bị để trống (`null`) hoặc ghi quá vắn tắt:
    - `DECORATION_SAVE`, `DECORATION_PUBLISH`, `DECORATION_DISCARD`: `detail` bị null.
    - `REVIEW_REPLY`, `REVIEW_REPORT`: `detail` bị null.
    - `RETURN_APPROVE`, `RETURN_REJECT`, `RETURN_COMPLETE`: `detail` bị null.
    - `INVITE_STAFF`: Chỉ ghi email, chưa ghi vai trò được mời.
    - `ACCEPT_INVITATION`: Chỉ ghi "Joined as staff", chưa ghi email/tên và vai trò.
    - `CHANGE_MEMBER_ROLE`: Chỉ ghi vai trò mới, chưa ghi email/tên nhân viên bị đổi vai trò.
    - `DEACTIVATE_MEMBER` / `ACTIVATE_MEMBER`: Chỉ ghi status, chưa ghi email nhân viên.
- **Mục tiêu**:
  - Đồng bộ chuẩn hóa toàn bộ 28 mã hành động giữa backend và frontend (sửa `CHANGE_ROLE` thành `CHANGE_MEMBER_ROLE`).
  - Làm giàu chi tiết cột `detail` cho mọi hành động nghiệp vụ để người quản trị biết rõ ai đã làm gì, lúc nào, tác động lên đối tượng nào và nội dung cụ thể ra sao.

### 1.3 Vấn đề 3: Lỗ hổng phân quyền tại trang Tổng quan shop (OVERVIEW) và Hồ sơ shop (PROFILE)
- **Hiện trạng**:
  - Trong [`lib/sellerPermissions.ts`](file:///d:/fashionstore/fashionstore-ui/src/lib/sellerPermissions.ts):
    ```typescript
    if (tab === 'OVERVIEW' || tab === 'PROFILE') {
      return true
    }
    ```
    Cho phép TẤT CẢ nhân viên (kể cả nhân viên chỉ có quyền Chat hoặc không có quyền nào) vào được `OVERVIEW` và `PROFILE`.
  - Trong [`ShopService.java`](file:///d:/fashionstore/fashionstore/src/main/java/com/minhnbnt/fashionstore/features/shop/ShopService.java): `updateProfile` cho phép cả `MemberRole.STAFF` cập nhật tài khoản ngân hàng, CCCD eKYC và địa chỉ kho của shop.
  - Trong [`ShopStatsService.java`](file:///d:/fashionstore/fashionstore/src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStatsService.java): Mọi endpoint thống kê doanh thu, khách hàng, performance đều cho phép `STAFF` bất kỳ truy cập thay vì yêu cầu quyền `ShopPermission.REVENUE_READ`.
- **Mục tiêu**:
  - `PROFILE`: Chỉ `MemberRole.OWNER` mới có quyền xem và sửa. Nhân viên (`STAFF`) bị ẩn hoàn toàn tab này và bị chặn `403 FORBIDDEN` nếu gọi API cập nhật hồ sơ shop.
  - `OVERVIEW`: Chỉ `isOwner` hoặc nhân viên có quyền `REVENUE_READ` mới được xem. Tự động điều hướng nhân viên sang tab đầu tiên họ được phép xem khi truy cập vào shop.
  - `ShopStatsService.java`: Mọi method thống kê yêu cầu quyền `ShopPermission.REVENUE_READ`.

---

## 2. Technical Architecture & Component Design

### 2.1 Backend Data Structures & APIs

#### 2.1.1 DTO `SellerShopMembershipResponse`
File: `com.minhnbnt.fashionstore.dto.shop.SellerShopMembershipResponse`
```java
@Schema(description = "Thông tin gian hàng và vai trò thành viên của người dùng hiện tại")
public record SellerShopMembershipResponse(
    @Schema(description = "Id gian hàng", example = "e3a6a4f4-aca9-4612-88f7-097896db6aef")
    @NonNull UUID shopId,

    @Schema(description = "Tên gian hàng", example = "Nét Boutique")
    @NonNull String shopName,

    @Schema(description = "Slug gian hàng", example = "net-boutique")
    @NonNull String shopSlug,

    @Schema(description = "S3 key logo gian hàng")
    @Nullable String logoKey,

    @Schema(description = "S3 key ảnh bìa gian hàng")
    @Nullable String bannerKey,

    @Schema(description = "Trạng thái phê duyệt gian hàng", example = "APPROVED")
    @NonNull ShopStatus shopStatus,

    @Schema(description = "Vai trò trong shop: OWNER hoặc STAFF", example = "STAFF")
    @NonNull MemberRole role,

    @Schema(description = "Id vai trò tùy chỉnh (nếu là STAFF)")
    @Nullable UUID roleId,

    @Schema(description = "Tên vai trò hiển thị", example = "Kho vận (Warehouse)")
    @NonNull String roleName,

    @Schema(description = "Trạng thái thành viên: ACTIVE hoặc DEACTIVATED", example = "ACTIVE")
    @NonNull String memberStatus,

    @Schema(description = "Thời điểm gia nhập shop")
    @NonNull Instant joinedAt,

    @Schema(description = "Danh sách quyền thao tác trong shop")
    @NonNull List<String> permissions
) {}
```

#### 2.1.2 Controller `SellerShopMembershipController`
File: `com.minhnbnt.fashionstore.features.shop.controller.SellerShopMembershipController`
- `@RestController`
- `@RequestMapping("/api/seller/my-shops")`
- `@Tag(name = "Seller - Staff & Roles", description = "Quản lý nhân viên gian hàng, phân quyền và lịch sử thao tác")`
- Method:
  ```java
  @GetMapping
  @Operation(
      operationId = "getMyShops",
      summary = "Danh sách gian hàng của tôi",
      description = "Trả về danh sách tất cả gian hàng mà người dùng hiện tại là Chủ sở hữu (OWNER) hoặc Nhân viên (STAFF)."
  )
  @ApiResponse(responseCode = "200", description = "Danh sách gian hàng và vai trò")
  @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
  @SecurityRequirement(name = "bearerAuth")
  public List<SellerShopMembershipResponse> getMyShops(@AuthenticationPrincipal Jwt jwt)
  ```

#### 2.1.3 Repository Query in `ShopMemberRepository`
Method: `public List<UserShopMembershipRow> findMembershipsByUser(UUID userId)`
Query:
```sql
SELECT 
    sm.shop_id,
    s.name as shop_name,
    s.slug as shop_slug,
    s.logo_key,
    s.banner_key,
    s.status as shop_status,
    sm.role,
    sm.role_id,
    sr.name as role_name,
    sm.status as member_status,
    sm.joined_at,
    sr.permissions
FROM shop_member sm
JOIN shop s ON s.id = sm.shop_id
LEFT JOIN shop_role sr ON sr.id = sm.role_id AND sr.shop_id = sm.shop_id
WHERE sm.user_id = :user
ORDER BY sm.joined_at DESC
```

#### 2.1.4 Strict Security Checks in `ShopService` and `ShopStatsService`
1. `ShopService.updateProfile`:
   - Thay `access.requireShopRole(userId, id, MemberRole.OWNER, MemberRole.STAFF)` bằng:
     ```java
     access.requireShopRole(userId, id, MemberRole.OWNER);
     ```
2. `ShopStatsService`:
   - Thay toàn bộ `access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF)` bằng:
     ```java
     access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);
     ```

#### 2.1.5 Detailed Audit Log Messages
- `INVITE_STAFF`: `"Mời " + invitation.getEmail() + " vào vai trò " + role.getName()`
- `ACCEPT_INVITATION`: `"Nhân viên " + user.getEmail() + " chấp nhận lời mời, vai trò " + role.getName()`
- `REVOKE_INVITATION`: `"Thu hồi lời mời của " + invitation.getEmail()`
- `CHANGE_MEMBER_ROLE`: `"Đổi vai trò nhân viên " + targetUser.getEmail() + " thành " + role.getName()`
- `DEACTIVATE_MEMBER`: `"Vô hiệu hóa tài khoản nhân viên " + targetUser.getEmail()`
- `ACTIVATE_MEMBER`: `"Kích hoạt lại tài khoản nhân viên " + targetUser.getEmail()`
- `DECORATION_SAVE`: `"Lưu bản nháp bố cục gian hàng"`
- `DECORATION_PUBLISH`: `"Xuất bản bố cục trang trí gian hàng"`
- `DECORATION_DISCARD`: `"Hủy bỏ bản nháp trang trí gian hàng"`
- `REVIEW_REPLY`: `"Phản hồi đánh giá của khách hàng (Đánh giá #" + found.id() + ")"`
- `REVIEW_REPORT`: `"Báo cáo vi phạm đánh giá khách hàng (Đánh giá #" + found.id() + ")"`
- `ORDER_CONFIRM`: `"Xác nhận đơn hàng #" + saved.getCode()`
- `ORDER_CANCEL`: `"Hủy đơn hàng #" + saved.getCode() + (reason != null ? " - Lý do: " + reason : "")`
- `RETURN_APPROVE`: `"Chấp nhận yêu cầu trả hàng #" + saved.getId()`
- `RETURN_REJECT`: `"Từ chối yêu cầu trả hàng #" + saved.getId() + (reason != null ? " - Lý do: " + reason : "")`

---

### 2.2 Frontend Architecture & UI Components

#### 2.2.1 Tab "Gian hàng của tôi" trên Trang Hồ Sơ (`/account`)
- Component mới: `d:\fashionstore\fashionstore-ui\src\components\account\MyShopsTab.tsx`
- Tích hợp vào `Account.tsx` qua hệ thống tab:
  - Tab 1: **"Thông tin cá nhân"** (Avatar, email, đổi mật khẩu).
  - Tab 2: **"Gian hàng của tôi"** (`MyShopsTab.tsx` - hiển thị toàn bộ shop người dùng đang tham gia).
  - Tab 3: **"Sổ địa chỉ"** (`AddressBookTab.tsx`).
- Mỗi gian hàng hiển thị dạng Card:
  - Header: Logo shop, Tên shop, Slug URL.
  - Badges:
    - Vai trò: `Chủ gian hàng` (badge màu chàm/xanh) hoặc `Nhân viên: {roleName}` (badge màu tím).
    - Trạng thái thành viên: `Đang hoạt động` (xanh lá) hoặc `Đã bị khóa` (xám/đỏ).
    - Trạng thái phê duyệt shop: `ĐÃ DUYỆT`, `CHỜ THẨM ĐỊNH`, `BỊ TỪ CHỐI`.
  - Nút bấm:
    - Nếu `memberStatus === 'ACTIVE'` và `shopStatus === 'APPROVED'`: Nút **"Vào Kênh Người Bán"** dẫn tới `/shop?shopId={shopId}`.
    - Nếu `role === 'OWNER'` và `shopStatus === 'PENDING'`: Nút "Xem tiến độ thẩm định".
    - Nếu `role === 'OWNER'` và `shopStatus === 'REJECTED'`: Nút "Chỉnh sửa & Nộp lại hồ sơ".

#### 2.2.2 Bộ chọn Gian hàng (Shop Switcher) trên Seller Center
- Component mới: `d:\fashionstore\fashionstore-ui\src\components\seller\ShopSwitcher.tsx`
- Đặt tại thanh Header của `SellerLayout.tsx`.
- Hiển thị:
  - Dropdown menu với danh sách các shop `ACTIVE` của người dùng.
  - Khi người dùng click chọn shop khác, điều hướng sang `/shop?shopId={newShopId}&tab={activeTab}`.

#### 2.2.3 Khắc phục Phân quyền và Tự động điều hướng (`ShopDashboard.tsx` & `lib/sellerPermissions.ts`)
- **`lib/sellerPermissions.ts`**:
  ```typescript
  export function canSeeTab(tab: SellerTabId, permissions: string[], isOwner: boolean): boolean {
    if (isOwner) {
      return true
    }

    // PROFILE: Chỉ chủ shop
    if (tab === 'PROFILE') {
      return false
    }

    // OVERVIEW: Cần quyền xem doanh thu/thống kê
    if (tab === 'OVERVIEW') {
      return permissions.includes('REVENUE_READ')
    }

    // STAFF: Chỉ chủ shop
    if (tab === 'STAFF') {
      return false
    }

    // Thống kê & Tài chính
    if (
      tab === 'REVENUE' ||
      tab === 'STATS_REVENUE' ||
      tab === 'STATS_PRODUCTS' ||
      tab === 'STATS_CUSTOMERS'
    ) {
      return permissions.includes('REVENUE_READ') || permissions.includes('REVENUE_WITHDRAW')
    }

    // Các tab nghiệp vụ khác kiểm tra theo đúng permission tương ứng
    // ...
  }
  ```
- **Auto-Navigation trong `ShopDashboard.tsx`**:
  - Khi nhân viên mở shop, nếu `canSeeTab(activeTab, permissions, isOwner) === false`:
    - Tìm kiếm tab đầu tiên được phép xem theo danh sách ưu tiên: `ORDERS`, `PRODUCTS`, `INVENTORY`, `CHAT`, `REVIEWS`, `RETURNS`, `DECORATION`...
    - Tự động gọi `setSearchParams({ shopId, tab: sellerTabParam(foundTab) })`.
    - Nếu không có quyền bất kỳ: Hiển thị thông báo "Bạn chưa được cấp quyền thao tác tại gian hàng này".

#### 2.2.4 Đồng bộ Bộ lọc Hoạt động (Audit Action Filter)
- Trong `SellerStaffTab.tsx`:
  - Đổi `'CHANGE_ROLE'` thành `'CHANGE_MEMBER_ROLE'` trong mảng `AUDIT_ACTIONS`.
- Trong `lib/sellerPermissions.ts`:
  - Thêm `CHANGE_MEMBER_ROLE: 'Đổi vai trò nhân viên'` vào `PERMISSION_LABELS`.

---

## 3. Global Constraints & Coding Rules (from AGENTS.md)

1. **Tuyệt đối cấm Ternary Operator (`? :`)**:
   - Backend: Dùng `if` + early `return`.
   - Frontend: Dùng `if` + early `return`, trong JSX dùng toán tử `&&`.
2. **Backend Entity & DTO Guidelines**:
   - Cấm `@Data`, `@Setter`, `@EqualsAndHashCode`, `@Builder` trên entity.
   - Mặc định non-null (NullAway). Hạn chế tối đa `@Nullable`.
   - Exception/message tiếng Anh.
   - Springdoc OpenAPI đầy đủ: `@Tag`, `@Operation(operationId, summary, description)`, `@ApiResponse`, `@Parameter`, `@Schema`, `@SecurityRequirement(name = "bearerAuth")`.
3. **Frontend Guidelines**:
   - Chỉ sử dụng `pnpm`, cấm `npm`.
   - Đổi API backend thì chạy xuất `docs/openapi.json` rồi `pnpm codegen`.
   - Chạy `pnpm format`, `pnpm lint` (0 error, 0 warning), `pnpm build` xanh 100%.
   - Tuân thủ Design System (màu chàm-xanh băng, Inter font, bo góc, glassmorphism, Phosphor Icons).
