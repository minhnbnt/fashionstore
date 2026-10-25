# Quản lý tài khoản nhân viên (2.8.1) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hoàn thiện luồng mời nhân viên qua email tài khoản + thông báo trong website, enforce RBAC 5 nhóm quyền, vô hiệu hóa/kích hoạt nhân viên và ghi nhật ký mọi thao tác nghiệp vụ của shop.

**Architecture:** Giữ nguyên RBAC hiện có (`shop_role.permissions`), thêm enum `ShopPermission` và enforce tại service qua `ShopAccessChecker`. Lời mời tạo `shop_invitation` + `user_notification` (không SMTP). Audit dùng lại `shop_member_audit` làm activity log, ghi từ service bằng `ShopAuditService`. UI React dùng orval hooks, gate menu/nút theo endpoint `my-permissions`, thêm `NotificationBell` và trang `/invitations/:token`.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JDBC + JdbcClient, Flyway, Postgres (Testcontainers image dự án), JUnit 5 + AssertJ + MockMvc; React 19, TanStack Query, orval, Tailwind v4, vitest, pnpm.

**Spec:** `docs/superpowers/specs/2026-10-24-seller-staff-management-design.md` (đọc trước mỗi task).

## Global Constraints

- Backend: theo `AGENTS.md`, `docs/code-guide.md`, `docs/structure.md`, `docs/data.md`. Cấm ternary; entity cấm `@Data/@Setter/@EqualsAndHashCode`; NullAway bật, hạn chế `@Nullable`; exception/message tiếng Anh; tiền VND `long`; thời gian `Instant`; id sinh trong factory; entity implement `Persistable` khi dùng Spring Data.
- Migration mới bắt đầu `V28`, không sửa migration đã merge. Testcontainers giữ image `ghcr.io/minhnbnt/postgresql:latest`.
- Mỗi endpoint mới đủ springdoc: `@Tag`, `@Operation(operationId, summary, description)`, `@ApiResponse` từng mã, `@Parameter`, `@Schema` field DTO; endpoint cần auth gắn `@SecurityRequirement(name = "bearerAuth")`.
- Service domain này không gọi repository domain khác; gọi service bên đó. Riêng query join bảng trong `JdbcClient` read model thì giữ như pattern `ShopAdminQueryRepository`.
- UI: chỉ `pnpm`; cấm ternary (JSX dùng `&&`); `pnpm format && pnpm lint && pnpm build` xanh trước commit; `generated/` không sửa tay; copy tiếng Việt; giá dùng `formatVnd`.
- Commit message không có `Co-Authored-By`.
- Không gửi email/SMTP trong phạm vi này. Chỉ thông báo trong website.

## Review Focus

1. **STAFF có `role_id = null`** — phải bị từ chối mọi quyền, không fallback "cho qua". Test ở Task 2.
2. **Vô hiệu hóa nhân viên đang đăng nhập** — request kế tiếp phải `403` ngay, không cần logout. Test ở Task 6.
3. **Token mời bị người khác dùng** — sai email/invitee phải `403`, không tạo member. Test ở Task 5.
4. **Sửa/xóa role đang gán** — quyền mới phải có hiệu lực ngay; xóa role thì member mất quyền, không crash. Test ở Task 2 và Task 3.
5. **OWNER bị thao tác nhân sự** — không thể tự vô hiệu hóa, đổi vai trò hoặc xóa. Test ở Task 6.

---

### Task 1: Migration V28 + nền tảng dữ liệu

**Files:**
- Create: `src/main/resources/db/migration/V28__seller_staff_management.sql`
- Modify: `src/main/java/com/minhnbnt/fashionstore/model/ShopInvitation.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopInvitationRepository.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopMemberRepository.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationRepository.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/shop/StaffSchemaMigrationTest.java`

**Interfaces:**
- Produces: `ShopInvitation.create(UUID shopId, String email, UUID roleId, UUID createdBy, UUID inviteeId)`; `ShopInvitation.revoke()`, `isRevoked()`, `isPending()`; `ShopMemberRepository.MemberAccess(MemberRole role, @Nullable UUID roleId, String status, String[] permissions)`; `MemberAccess ShopMemberRepository.findAccess(UUID shopId, UUID userId)` (404/empty khi không có); `NotificationRepository.insert(UUID userId, String type, String title, String body, @Nullable String link, @Nullable UUID refId)`.
- Produces: bảng `user_notification`; cột `shop_invitation.invitee_id`, `revoked_at`; index `idx_shop_audit_shop_created`.

- [ ] **Step 1: Viết migration.**

```sql
ALTER TABLE shop_invitation ADD COLUMN invitee_id UUID REFERENCES user_profile(id) ON DELETE CASCADE;
ALTER TABLE shop_invitation ADD COLUMN revoked_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_shop_invitation_pending
    ON shop_invitation (shop_id, lower(email))
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

CREATE TABLE user_notification (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    link TEXT,
    ref_id UUID,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_user ON user_notification (user_id, read_at, created_at DESC);

CREATE INDEX idx_shop_audit_shop_created ON shop_member_audit (shop_id, created_at DESC);

UPDATE shop_role SET permissions = ARRAY[
    'PRODUCT_READ','PRODUCT_WRITE','INVENTORY_READ','INVENTORY_WRITE',
    'ORDER_READ','ORDER_PROCESS','ORDER_CANCEL','RETURN_PROCESS',
    'REVENUE_READ','REVENUE_WITHDRAW','REVIEW_READ','REVIEW_REPLY',
    'CHAT_ACCESS','DECORATION_WRITE'
] WHERE is_system = TRUE AND name = 'Quản lý (Manager)';

UPDATE shop_role SET permissions = ARRAY['ORDER_READ','REVIEW_READ','REVIEW_REPLY','CHAT_ACCESS']
WHERE is_system = TRUE AND name = 'Chăm sóc khách hàng (CSKH)';

UPDATE shop_role SET permissions = ARRAY['ORDER_READ','ORDER_PROCESS','INVENTORY_READ','INVENTORY_WRITE']
WHERE is_system = TRUE AND name = 'Kho vận (Warehouse)';

UPDATE shop_role SET permissions = ARRAY['PRODUCT_READ','DECORATION_WRITE','REVENUE_READ']
WHERE is_system = TRUE AND name = 'Marketing';
```

- [ ] **Step 2: Test đỏ** `StaffSchemaMigrationTest` (`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, dùng `JdbcClient`):
  - `notificationTableExists`: `SELECT count(*) FROM information_schema.tables WHERE table_name = 'user_notification'` = 1.
  - `invitationHasInviteeAndRevokedColumns`: 2 cột tồn tại.
  - `managerRoleHasAllPermissions`: role system `Quản lý (Manager)` có `REVENUE_WITHDRAW` và `ORDER_CANCEL`.

- [ ] **Step 3: Chạy** `./gradlew test --tests "*StaffSchemaMigrationTest"` → FAIL vì chưa có migration/entity.

- [ ] **Step 4: Implement.**
  - `ShopInvitation`: thêm `@Nullable UUID inviteeId`, `@Nullable Instant revokedAt`; cập nhật constructor `@PersistenceCreator`; `create(...)` nhận thêm `inviteeId`; `revoke()` set `revokedAt = Instant.now()`; `isRevoked()`; `isPending()` = `acceptedAt == null && revokedAt == null && !isExpired()`.
  - `ShopInvitationRepository`: thêm `List<ShopInvitation> findByShopIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(UUID shopId);` và `Optional<ShopInvitation> findByShopIdAndId(UUID shopId, UUID id);`.
  - `ShopMemberRepository.findAccess`: một query `SELECT m.role, m.role_id, m.status, r.permissions FROM shop_member m LEFT JOIN shop_role r ON r.id = m.role_id WHERE m.shop_id = :shop AND m.user_id = :user`; `permissions` null → `new String[0]`.
  - `NotificationRepository` dùng `JdbcClient`: `insert`, `findPage(UUID userId, int page, int size)`, `countUnread(UUID userId)`, `markRead(UUID userId, UUID id)`, `markAllRead(UUID userId)`, `deleteByRefId(UUID refId)`; trả record `NotificationRow(UUID id, String type, String title, String body, @Nullable String link, @Nullable Instant readAt, Instant createdAt)`.

- [ ] **Step 5: Chạy lại** → PASS.

- [ ] **Step 6: Commit** `feat(staff): migration V28 cho loi moi, thong bao va audit`.

---

### Task 2: Permission model + `ShopAccessChecker` + `my-permissions`

**Files:**
- Create: `src/main/java/com/minhnbnt/fashionstore/model/ShopPermission.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/security/ShopAccessChecker.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStaffService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/controller/SellerStaffController.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/MyPermissionsResponse.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/shop/ShopAccessCheckerTest.java`

**Interfaces:**
- Produces: `enum ShopPermission { PRODUCT_READ, PRODUCT_WRITE, INVENTORY_READ, INVENTORY_WRITE, ORDER_READ, ORDER_PROCESS, ORDER_CANCEL, RETURN_PROCESS, REVENUE_READ, REVENUE_WITHDRAW, REVIEW_READ, REVIEW_REPLY, CHAT_ACCESS, DECORATION_WRITE }`.
- Produces: `ShopAccessChecker.requireShopPermission(UUID userId, UUID shopId, ShopPermission... required)` (any-of, giống `requireShopRole`), `requireApprovedShopPermission(...)`, `Set<ShopPermission> permissionsOf(UUID userId, UUID shopId)`, `boolean isOwner(UUID userId, UUID shopId)`.
- Produces: `ShopStaffService.myPermissions(UUID userId, UUID shopId): MyPermissionsResponse`.
- Produces: `record MyPermissionsResponse(boolean isOwner, List<String> permissions)`.
- API: `GET /api/seller/shops/{shopId}/my-permissions`, `operationId = "getMyPermissions"`.

- [ ] **Step 1: Test đỏ** `ShopAccessCheckerTest` (Mockito, mock `ShopMemberRepository`, `ShopRepository`):
  - `ownerHasAllPermissions`: `findAccess` trả `OWNER` → `permissionsOf` chứa `REVENUE_WITHDRAW`.
  - `staffUsesRolePermissions`: `STAFF` + role permissions `[ORDER_READ]` → `requireShopPermission(..., ORDER_READ)` pass; `REVENUE_WITHDRAW` ném `403`.
  - `staffWithoutRoleHasNoPermission`: `STAFF`, `roleId = null`, permissions rỗng → mọi `requireShopPermission` ném `403`.
  - `deactivatedMemberIsDenied`: `status = 'DEACTIVATED'` → `403` dù có role.
  - `unknownPermissionStringIsIgnored`: permissions `["NOT_A_REAL_PERMISSION"]` → không crash, `permissionsOf` rỗng.

- [ ] **Step 2: Chạy** `./gradlew test --tests "*ShopAccessCheckerTest"` → FAIL.

- [ ] **Step 3: Implement.**
  - `ShopPermission`: enum + `static @Nullable ShopPermission parse(String raw)` trả null nếu lạ.
  - `ShopAccessChecker`:
    - `permissionsOf`: load `MemberAccess`; nếu không có hoặc status != `ACTIVE` → `Set.of()`; nếu `OWNER` → `EnumSet.allOf(ShopPermission.class)`; nếu `STAFF` → parse mảng, bỏ null, trả `EnumSet`.
    - `requireShopPermission`: `Set<ShopPermission> granted = permissionsOf(...)`; nếu `granted.isEmpty()` → `403 "Not a member of this shop"`; nếu không chứa bất kỳ `required` → `403 "Insufficient shop permission"`.
    - `isOwner`: dựa `MemberAccess.role() == OWNER && status == ACTIVE`.
    - `requireApprovedShopPermission`: `requireShopApproved(shopId)` rồi `requireShopPermission`.
    - Giữ `requireShopRole` cho các chỗ chưa chuyển (Task 3/4 chuyển dần).
  - `ShopStaffService.myPermissions`: `access.permissionsOf` + `access.isOwner`; trả list tên enum đã sort.
  - Controller: endpoint mới, `@Operation(operationId = "getMyPermissions")`, `@SecurityRequirement(name = "bearerAuth")`.

- [ ] **Step 4: Chạy lại** → PASS.

- [ ] **Step 5: Commit** `feat(staff): them ShopPermission va enforce o ShopAccessChecker`.

---

### Task 3: Enforce quyền sản phẩm + tồn kho

**Files:**
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/catalog/CatalogService.java`
- Modify: `src/test/java/com/minhnbnt/fashionstore/features/catalog/CatalogServiceTest.java`
- Create: `src/test/java/com/minhnbnt/fashionstore/features/catalog/SellerProductPermissionIntegrationTest.java`

**Interfaces:**
- Consumes: `ShopAccessChecker.requireApprovedShopPermission(...)`, `ShopPermission`.
- Produces: mọi endpoint seller catalog trả `403` khi thiếu quyền tương ứng.

- [ ] **Step 1: Test đỏ** `SellerProductPermissionIntegrationTest` (`@SpringBootTest` + Testcontainers):
  - Seed owner, shop APPROVED, staff, role `PRODUCT_READ` only.
  - `staffWithReadCannotCreateProduct`: gọi `catalog.createProduct(staffId, shopId, request)` → `ResponseStatusException` 403.
  - `staffWithReadCanListProducts`: `catalog.listSellerProducts(staffId, shopId, ...)` → không ném.
  - `staffWithoutInventoryWriteCannotAdjustStock`: `catalog.adjustStock(...)` → 403.
  - `ownerCanCreateProduct`: owner → pass.
- Cập nhật `CatalogServiceTest` nếu đang verify `requireApprovedShopRole`.

- [ ] **Step 2: Chạy** `./gradlew test --tests "*SellerProductPermissionIntegrationTest"` → FAIL.

- [ ] **Step 3: Implement.** Thay trong `CatalogService`:
  - `createProduct`, `updateProduct`, `deleteProduct`, `toggleProductStatus`, `attachImages`, `deleteProductImage`, `bulkUpdateProductStatus`, `bulkDeleteProducts`, `bulkUpdatePrices`, `bulkImport`: `requireApprovedShopPermission(..., PRODUCT_WRITE)`.
  - `listSellerProducts` (cả 2 overload), `getSellerProductDetail`, `exportCsv`, `getAuditLogs`: `requireShopPermission(..., PRODUCT_READ)`.
  - `listInventory`, `listStockMovements`: `requireShopPermission(..., INVENTORY_READ)`.
  - `adjustStock`, `bulkUpdateStocks`: `requireApprovedShopPermission(..., INVENTORY_WRITE)`.
  - Không đổi các endpoint admin/public.

- [ ] **Step 4: Chạy lại** → PASS.

- [ ] **Step 5: Commit** `feat(staff): enforce quyen san pham va ton kho`.

---

### Task 4: Enforce quyền đơn hàng, trả hàng, doanh thu, chat, đánh giá, trang trí

**Files:**
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/ReturnRequestService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/RevenueService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/chat/ChatService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/review/ReviewService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopDecorationService.java`
- Create: `src/test/java/com/minhnbnt/fashionstore/features/shop/SellerPermissionEnforcementTest.java`

**Interfaces:**
- Consumes: `ShopAccessChecker.requireShopPermission`, `requireApprovedShopPermission`.
- Produces: mọi thao tác seller trả `403` khi thiếu quyền.

- [ ] **Step 1: Test đỏ** `SellerPermissionEnforcementTest`:
  - Seed shop APPROVED, owner, staff với role `ORDER_READ` + `CHAT_ACCESS` (không có `ORDER_PROCESS`, `REVENUE_*`, `REVIEW_REPLY`, `DECORATION_WRITE`).
  - `staffCanReadOrders`: `order.listSellerOrdersFiltered(staffId, shopId, ...)` → pass.
  - `staffCannotConfirmOrder`: `order.confirm(staffId, orderId)` → 403.
  - `staffCannotWithdraw`: `revenue.createWithdrawal(staffId, shopId, request)` → 403.
  - `staffCannotReplyReview`: `review.reply(staffId, reviewId, request)` → 403.
  - `staffCanSendChat`: `chat.sendMessage(staffId, conversationId, request)` → pass.
  - `staffCannotSaveDecoration`: `decoration.saveDraft(staffId, shopId, "[]")` → 403.

- [ ] **Step 2: Chạy** → FAIL.

- [ ] **Step 3: Implement.**
  - `OrderService`: `listSellerOrders`, `listSellerOrdersFiltered` → `ORDER_READ`; `confirm`, `ship`, `deliver` → `ORDER_PROCESS`; `sellerCancel` → `ORDER_CANCEL`. Buyer/admin methods giữ nguyên.
  - `ReturnRequestService`: `listSellerReturns`, `getDetail` (khi user là seller), `approve`, `reject`, `complete` → `RETURN_PROCESS`.
  - `RevenueService`: `getSummary`, `getDailyRevenue`, `getTopProducts`, `listSettlements`, `listWithdrawals` → `REVENUE_READ`; `createWithdrawal` → `REVENUE_WITHDRAW`. `createSettlementForOrder` là nội bộ, không check.
  - `ChatService`: nhánh seller của `listSellerConversations`, `getMessages`, `sendMessage`, `markRead` → `CHAT_ACCESS`; nhánh buyer giữ nguyên (phân biệt bằng việc user có phải member shop hay không).
  - `ReviewService`: `listForShop` → `REVIEW_READ`; `reply`, `report` → `REVIEW_REPLY`.
  - `ShopDecorationService`: `getSellerDecoration` → chỉ cần member active; `saveDraft`, `publish`, `discardDraft` → `DECORATION_WRITE`.

- [ ] **Step 4: Chạy lại** → PASS.

- [ ] **Step 5: Commit** `feat(staff): enforce quyen don hang, tai chinh, CSKH va trang tri`.

---

### Task 5: Luồng mời nhân viên + thông báo trong website

**Files:**
- Create: `src/main/java/com/minhnbnt/fashionstore/features/identity/IdentityDirectoryService.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/identity/AccountInfo.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/identity/UserProfileRepository.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStaffService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/controller/SellerStaffController.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/controller/StaffInvitationController.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/InvitationDetailResponse.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/dto/shop/StaffInvitationResponse.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/shop/ShopStaffServiceUnitTest.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/shop/StaffInvitationIntegrationTest.java`

**Interfaces:**
- Produces: `record AccountInfo(UUID id, String email, String displayName)`.
- Produces: `IdentityDirectoryService.findByEmail(String email): Optional<AccountInfo>` (DB trước, GoTrue fallback nếu có key), `findById(UUID id): Optional<AccountInfo>`.
- Produces: `NotificationService.notifyInvitation(UUID userId, UUID invitationId, String shopName, String roleName, String ownerName, String token)`, `deleteByRefId(UUID refId)`.
- Produces: `ShopStaffService.inviteStaff(userId, shopId, request)` giờ trả `StaffInvitationResponse` có `inviteeId`; `listInvitations(userId, shopId): List<StaffInvitationResponse>`; `revokeInvitation(userId, shopId, invitationId)`; `getInvitationDetail(userId, token): InvitationDetailResponse`.
- Produces: `record InvitationDetailResponse(UUID shopId, String shopName, String roleName, String email, Instant expiresAt, String status)`; status `PENDING|ACCEPTED|EXPIRED|REVOKED`.
- API mới: `GET /api/seller/shops/{shopId}/invitations` (`listInvitations`), `DELETE /api/seller/shops/{shopId}/invitations/{invitationId}` (`revokeInvitation`, 204), `GET /api/invitations/{token}` (`getInvitation`, authenticated).

- [ ] **Step 1: Test đỏ** `ShopStaffServiceUnitTest` thêm:
  - `inviteStaffRejectsUnknownEmail`: `identity.findByEmail` empty → 404.
  - `inviteStaffCreatesNotification`: tạo inv + `verify(notifications).notifyInvitation(...)`.
  - `inviteStaffRejectsExistingMember`: `members.findMember` có → 409.
  - `acceptInvitationRejectsWrongEmail`: inv `inviteeId = A`, user B → 403.
  - `acceptInvitationMarksNotificationRead`: accept thành công → `verify(notifications).deleteByRefId(inv.getId())`.
  - `acceptInvitationReactivatesDeactivatedMember`: member cũ status `DEACTIVATED` → update status `ACTIVE`, không insert trùng.
  - `acceptInvitationRejectsRevoked`: inv `revokedAt` khác null → 410.

  `StaffInvitationIntegrationTest`:
  - `inviteExistingAccountCreatesNotification`: seed user qua `UserService.upsert`, gọi invite, query `user_notification` có 1 dòng `type = 'SHOP_INVITATION'` và `link` chứa `/invitations/`.
  - `revokeRemovesNotification`: revoke → notification bị xóa, accept sau đó 410.

- [ ] **Step 2: Chạy** → FAIL.

- [ ] **Step 3: Implement.**
  - `IdentityDirectoryService`: `findByEmail` dùng `UserProfileRepository.findByEmailIgnoreCase`; nếu empty và `gotrue.isConfigured()` → `gotrue.findUserIdByEmail`, `users.insertIfAbsent(id, email, email)`, đọc lại. `findById` dùng `users.findById`.
  - `NotificationService`: `notifyInvitation` tạo title `"Lời mời tham gia shop " + shopName`, body `ownerName + " mời bạn làm " + roleName + " tại " + shopName + "."`, link `"/invitations/" + token`, type `"SHOP_INVITATION"`, refId invitationId.
  - `ShopStaffService.inviteStaff`: trim/lower email; check role; check owner không tự mời; check member tồn tại; check pending invitation; lookup account; tạo `ShopInvitation.create(..., inviteeId)`; gọi notification; audit.
  - `acceptInvitation`: load token; 404/410/409; lấy email user hiện tại; so `inv.getInviteeId()` (nếu có) hoặc email; nếu khác → 403 `"Invitation does not belong to this account"`; nếu member `DEACTIVATED` → `members.updateStatus(..., "ACTIVE")` + `updateRole`; nếu chưa có → `members.add(ShopMember.staff(...))`; `inv.accept()`; xóa notification; audit `ACCEPT_INVITATION`.
  - `revokeInvitation`: owner; load theo shop+id; nếu accepted → 409; `inv.revoke()`; `notifications.deleteByRefId(inv.getId())`; audit `REVOKE_INVITATION`.
  - `listInvitations`: owner; trả các pending (repository đã lọc accepted/revoked); lọc thêm `!isExpired()`.
  - `getInvitationDetail`: load token; 404 nếu không có; trả status theo thứ tự revoked → accepted → expired → pending; lấy shop name + role name.
  - `StaffInvitationResponse`: thêm `@Nullable UUID inviteeId`; giữ token.
  - Controller: thêm endpoint + springdoc.

- [ ] **Step 4: Chạy lại** → PASS.

- [ ] **Step 5: Commit** `feat(staff): moi nhan vien qua tai khoan va thong bao trong website`.

---

### Task 6: Vô hiệu hóa / kích hoạt nhân viên + email trong danh sách

**Files:**
- Modify: `src/main/java/com/minhnbnt/fashionstore/model/ShopMember.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopMemberRepository.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStaffService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/controller/SellerStaffController.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/dto/shop/ShopMemberDetailResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/UpdateMemberStatusRequest.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/shop/ShopStaffServiceUnitTest.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/shop/StaffMemberStatusIntegrationTest.java`

**Interfaces:**
- Produces: `record ShopMemberDetailResponse(UUID shopId, UUID userId, String email, String displayName, String role, @Nullable UUID roleId, @Nullable String roleName, @Nullable UUID invitedBy, @Nullable Instant invitedAt, String status, Instant joinedAt)`.
- Produces: `record UpdateMemberStatusRequest(@Pattern(regexp = "ACTIVE|DEACTIVATED") String status)`.
- Produces: `ShopStaffService.updateMemberStatus(UUID actorId, UUID shopId, UUID memberId, String status)`; `removeMember` gọi `updateMemberStatus(..., "DEACTIVATED")`.
- Produces: `ShopMemberRepository.updateStatus(UUID shopId, UUID userId, String status)`.
- API: `PATCH /api/seller/shops/{shopId}/members/{memberId}/status`, `operationId = "updateMemberStatus"`; `DELETE /members/{memberId}` giữ nguyên nhưng là vô hiệu hóa.

- [ ] **Step 1: Test đỏ**:
  - `deactivateMemberSetsStatus`: owner gọi `removeMember` → `members.updateStatus(..., "DEACTIVATED")`, không gọi `remove`.
  - `activateMemberSetsStatus`: `updateMemberStatus(..., "ACTIVE")`.
  - `cannotDeactivateOwner`: target OWNER → 409.
  - `deactivatedMemberLosesAccess`: sau khi deactivate, `ShopAccessChecker.requireShopPermission` ném 403 (integration test).
  - `memberListReturnsEmail`: `listMembers` trả email/displayName từ `user_profile`.

- [ ] **Step 2: Chạy** → FAIL.

- [ ] **Step 3: Implement.**
  - `ShopMemberRepository`: thêm `findMemberViews(UUID shopId)` trả record `ShopMemberView(ShopMember member, String email, String displayName)` bằng SQL join `user_profile`; `updateStatus`.
  - `ShopStaffService.listMembers`: dùng `findMemberViews`; map email/displayName.
  - `updateMemberStatus`: owner check; target tồn tại; OWNER → 409; chỉ nhận `ACTIVE|DEACTIVATED`; update; audit `ACTIVATE_MEMBER`/`DEACTIVATE_MEMBER`.
  - `removeMember`: gọi `updateMemberStatus(..., "DEACTIVATED")`, giữ audit action `DEACTIVATE_MEMBER`; không xóa DB.
  - Controller: endpoint mới + springdoc; cập nhật mô tả DELETE thành "vô hiệu hóa".

- [ ] **Step 4: Chạy lại** → PASS.

- [ ] **Step 5: Commit** `feat(staff): vo hieu hoa va kich hoat nhan vien`.

---

### Task 7: Nhật ký hoạt động dùng chung

**Files:**
- Create: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopAuditService.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopActivityQueryRepository.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/ShopActivityResponse.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStaffService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/controller/SellerStaffController.java`
- Modify: `CatalogService`, `OrderService`, `ReturnRequestService`, `RevenueService`, `ChatService`, `ReviewService`, `ShopDecorationService`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/shop/ShopAuditIntegrationTest.java`

**Interfaces:**
- Produces: `ShopAuditService.record(UUID shopId, UUID actorId, String action, String targetType, @Nullable UUID targetId, @Nullable String detail)`.
- Produces: `ShopActivityQueryRepository.page(UUID shopId, @Nullable String action, int page, int size): PageResponse<ShopActivityResponse>`.
- Produces: `record ShopActivityResponse(UUID id, UUID actorId, String actorEmail, String actorName, String action, @Nullable String targetType, @Nullable UUID targetId, @Nullable String detail, Instant createdAt)`.
- API: `GET /api/seller/shops/{shopId}/audit?action=&page=&size=`, `operationId = "listAuditLogs"`, trả `PageResponse<ShopActivityResponse>`.

- [ ] **Step 1: Test đỏ** `ShopAuditIntegrationTest`:
  - `productCreateIsRecorded`: owner tạo product → audit có `action = 'PRODUCT_CREATE'`, `actorEmail = owner email`, `targetType = 'PRODUCT'`.
  - `orderConfirmIsRecorded`: staff có `ORDER_PROCESS` confirm → `ORDER_CONFIRM`.
  - `withdrawalIsRecorded`: owner rút tiền → `WITHDRAWAL_CREATE`.
  - `auditPageFiltersByAction`: page `action=PRODUCT_CREATE` chỉ trả đúng action; total đúng.
  - `auditReturnsActorEmailAndName`.

- [ ] **Step 2: Chạy** → FAIL.

- [ ] **Step 3: Implement.**
  - `ShopAuditService`: `audits.save(ShopMemberAudit.record(shopId, actorId, action, targetType, targetId, detail))`.
  - `ShopActivityQueryRepository`: count + page join `user_profile`; `action` null thì bỏ filter; sort `created_at DESC`; page size clamp 1..100.
  - `ShopStaffService.listAuditLogs`: đổi signature `(userId, shopId, @Nullable String action, int page, int size)`; owner check; gọi query repo.
  - Ghi audit tại các điểm:
    - Catalog: `PRODUCT_CREATE`, `PRODUCT_UPDATE`, `PRODUCT_DELETE`, `PRODUCT_STATUS`, `PRODUCT_IMAGE_DELETE`, `INVENTORY_ADJUST`, `PRODUCT_STATUS_BULK`, `PRODUCT_DELETE_BULK`, `PRODUCT_PRICE_BULK`, `INVENTORY_ADJUST_BULK`, `PRODUCT_IMPORT`.
    - Order: `ORDER_CONFIRM`, `ORDER_SHIP`, `ORDER_DELIVER`, `ORDER_CANCEL`.
    - Return: `RETURN_APPROVE`, `RETURN_REJECT`, `RETURN_COMPLETE`.
    - Chat: `CHAT_REPLY` khi sender là seller.
    - Review: `REVIEW_REPLY`, `REVIEW_REPORT`.
    - Revenue: `WITHDRAWAL_CREATE`.
    - Decoration: `DECORATION_SAVE`, `DECORATION_PUBLISH`, `DECORATION_DISCARD`.
  - Detail ngắn gọn: tên sản phẩm, mã đơn, số tiền rút, tên role… Không log nội dung chat/đánh giá đầy đủ.
  - Controller: thêm `@RequestParam(required = false) String action`, `page = 0`, `size = 20`; springdoc.

- [ ] **Step 4: Chạy lại** → PASS.

- [ ] **Step 5: Commit** `feat(staff): nhat ky hoat dong nghiep vu cua shop`.

---

### Task 8: API thông báo trong website

**Files:**
- Create: `src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationController.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/notification/NotificationResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/notification/NotificationCountResponse.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationService.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/notification/NotificationControllerTest.java`

**Interfaces:**
- Produces: `NotificationService.page(UUID userId, int page, int size): PageResponse<NotificationResponse>`; `unreadCount(UUID userId): long`; `markRead(UUID userId, UUID notificationId)` (404 nếu không phải của user); `markAllRead(UUID userId)`.
- Produces: `record NotificationResponse(UUID id, String type, String title, String body, @Nullable String link, @Nullable Instant readAt, Instant createdAt)`; `record NotificationCountResponse(long unread)`.
- API:
  - `GET /api/notifications?page=&size=` → `listNotifications`.
  - `GET /api/notifications/unread-count` → `getUnreadNotificationCount`.
  - `PATCH /api/notifications/{id}/read` → `markNotificationRead`, 204.
  - `PATCH /api/notifications/read-all` → `markAllNotificationsRead`, 204.

- [ ] **Step 1: Test đỏ** `NotificationControllerTest` (`@WebMvcTest` + `@Import(SecurityConfig.class)` + `@MockitoBean JwtDecoder`, `@MockitoBean NotificationService`):
  - `listRequiresAuth`: không JWT → 401.
  - `listReturnsOwnNotifications`: JWT sub A → service gọi với A, JSON `$.items[0].title`.
  - `unreadCountReturnsNumber`: `$.unread = 3`.
  - `markReadRequiresOwnNotification`: service ném 404 → response 404.
  - `markAllReturns204`.
- `NotificationService` unit test: `markRead` của user khác → 404; `page` clamp size.

- [ ] **Step 2: Chạy** → FAIL.

- [ ] **Step 3: Implement.** Controller lấy user id từ JWT `sub` như các controller khác; springdoc đầy đủ; `@Tag(name = "Notifications")`.

- [ ] **Step 4: Chạy lại** → PASS.

- [ ] **Step 5: Commit** `feat(notification): API thong bao trong website`.

---

### Task 9: Frontend — codegen, NotificationBell, trang chấp nhận lời mời

**Files:**
- Modify: `fashionstore-ui/docs/openapi.json` (tải từ backend)
- Generated (không sửa tay): `fashionstore-ui/generated/**`
- Create: `fashionstore-ui/src/components/NotificationBell.tsx`
- Create: `fashionstore-ui/src/pages/InvitationAccept.tsx`
- Modify: `fashionstore-ui/src/App.tsx`
- Modify: `fashionstore-ui/src/components/Header.tsx`
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/SellerLayout.tsx`
- Modify: `fashionstore-ui/src/lib/auth.ts` (nếu cần helper)
- Test: `fashionstore-ui/src/lib/notificationLink.test.ts` (nếu tách helper)

**Interfaces:**
- Consumes: hooks `useListNotifications`, `useGetUnreadNotificationCount`, `useMarkNotificationRead`, `useMarkAllNotificationsRead`, `useGetInvitation`, `useAcceptInvitation`.
- Produces: `NotificationBell` không props, tự lấy dữ liệu; bấm item → mark read + `navigate(link)`.
- Produces: route `/invitations/:token` trong `App.tsx`, bọc `RequireAuth`.
- Produces: `Header` hiện link "Kênh Người Bán" khi `useMine()` có ít nhất 1 shop (kể cả staff), không chỉ khi GoTrue role `shop`.
- Produces: `SellerLayout` nhận thêm `notificationsSlot?: ReactNode` hoặc render `NotificationBell` thay bell tĩnh.

- [ ] **Step 1: Codegen.** Chạy backend, tải `http://localhost:8080/v3/api-docs` ghi đè `fashionstore-ui/docs/openapi.json`, chạy `pnpm codegen`. Kiểm tra có hook `useGetMyPermissions`, `useListNotifications`, `useGetUnreadNotificationCount`, `useGetInvitation`.
- [ ] **Step 2: Viết `NotificationBell`.** TanStack Query `refetchInterval: 30000`; dropdown đóng khi click ngoài; item hiện `title`, `body`, thời gian; badge `unread`; nút "Đánh dấu tất cả đã đọc"; item chưa đọc có nền khác.
- [ ] **Step 3: Trang `InvitationAccept`.** `useParams` token; `useGetInvitation`; hiện shop/role/email/expiry/status; nếu `PENDING` hiện nút "Chấp nhận"; mutation thành công → `navigate('/shop')`; lỗi 403 → thông báo "Lời mời dành cho email X, bạn đang đăng nhập bằng Y"; 410 → "Lời mời đã hết hạn hoặc bị thu hồi".
- [ ] **Step 4: Gắn UI.** `Header` thay bell tĩnh (nếu có) bằng `NotificationBell`; `SellerLayout` thay bell tĩnh; thêm route `/invitations/:token`.
- [ ] **Step 5: Chạy** `pnpm test`, `pnpm lint`, `pnpm build` → xanh.
- [ ] **Step 6: Commit** `feat(ui): thong bao trong website va trang chap nhan loi moi`.

---

### Task 10: Frontend — SellerStaffTab + gate quyền

**Files:**
- Modify: `fashionstore-ui/src/types/seller/staff.ts`
- Modify: `fashionstore-ui/src/lib/sellerAdapters.ts`
- Create: `fashionstore-ui/src/lib/sellerPermissions.ts`
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/SellerStaffTab.tsx`
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/SellerLayout.tsx`
- Modify: `fashionstore-ui/src/pages/ShopDashboard.tsx`
- Modify: `fashionstore-ui/src/types/seller/index.ts` (nếu export type mới)
- Test: `fashionstore-ui/src/lib/sellerPermissions.test.ts`
- Test: `fashionstore-ui/src/lib/sellerAdapters.test.ts`

**Interfaces:**
- Consumes: `useGetMyPermissions(shopId)`, `useListInvitations`, `useRevokeInvitation`, `useUpdateMemberStatus`, `useUpdateRole`, `useDeleteRole`.
- Produces: `ShopMemberItem { id, email, displayName, roleId, roleName, status: 'ACTIVE'|'DEACTIVATED', joinedAt }`.
- Produces: `canSeeTab(tab: SellerTabId, permissions: string[], isOwner: boolean): boolean`; owner luôn true.
- Produces: `permissionLabel(key: string): string` (hiển thị tiếng Việt cho audit/role).
- Produces: `SellerLayout` props thêm `permissions: string[]`, `isOwner: boolean`; menu item có `requiredAny?: string[]`.

- [ ] **Step 1: Test đỏ**:
  - `sellerPermissions.test.ts`: owner thấy tất cả; staff chỉ `ORDER_READ` thấy ORDERS, không thấy REVENUE/STAFF; `PRODUCT_WRITE` thấy PRODUCTS + ADD_PRODUCT; `INVENTORY_READ` thấy INVENTORY; không quyền chỉ thấy OVERVIEW.
  - `sellerAdapters.test.ts`: `memberItem` lấy `email` từ response; status `DEACTIVATED` map đúng; thiếu email fallback `userId`.
- [ ] **Step 2: Chạy** `pnpm test` → FAIL.
- [ ] **Step 3: Implement adapter/types.** Cập nhật `ShopMemberItem`; `memberItem` dùng `member.email` và `member.displayName`; `roleItem` giữ nguyên.
- [ ] **Step 4: Implement `sellerPermissions.ts`.** Bảng map tab → quyền; `canSeeTab`; `permissionLabel`.
- [ ] **Step 5: Cập nhật `ShopDashboard`.** Gọi `useGetMyPermissions(shopId)`; truyền `permissions`/`isOwner` xuống `SellerLayout`; nếu tab hiện tại không được phép → hiện khối "Bạn không có quyền truy cập mục này" + nút về Tổng quan; không tự đổi URL.
- [ ] **Step 6: Cập nhật `SellerLayout`.** Filter `menuGroups` theo `canSeeTab`; nhóm rỗng thì ẩn; thêm `NotificationBell`; ẩn nút chat nhanh nếu không có `CHAT_ACCESS`.
- [ ] **Step 7: Cập nhật `SellerStaffTab`.**
  - Danh sách: email + displayName, badge `ACTIVE/DEACTIVATED`; OWNER khóa; STAFF có select role, nút "Vô hiệu hóa"/"Kích hoạt lại".
  - Tab "Lời mời": danh sách pending, email, role, hạn, nút "Thu hồi".
  - Modal mời: bỏ copy "gửi email"; ghi "Hệ thống gửi thông báo trong website cho tài khoản này"; hiện lỗi backend khi email chưa đăng ký.
  - Vai trò: custom role có nút "Sửa" (mở modal với dữ liệu cũ) và "Xóa"; system role chỉ xem.
  - Audit: hiện `actorEmail`/`actorName`, filter action, phân trang.
- [ ] **Step 8: Gate nút hành động trong các tab khác.** Orders: confirm/ship/deliver cần `ORDER_PROCESS`, cancel cần `ORDER_CANCEL`; Returns cần `RETURN_PROCESS`; Revenue nút rút tiền cần `REVENUE_WITHDRAW`; Reviews reply/report cần `REVIEW_REPLY`; Products add/edit/delete cần `PRODUCT_WRITE`; Inventory adjust cần `INVENTORY_WRITE`; Decoration save/publish cần `DECORATION_WRITE`.
- [ ] **Step 9: Chạy** `pnpm test`, `pnpm format`, `pnpm lint`, `pnpm build` → xanh.
- [ ] **Step 10: Commit** `feat(ui): quan ly nhan vien va gate quyen seller center`.

---

### Task 11: Docs + verification toàn bộ

**Files:**
- Modify: `docs/api-endpoints.md` (Flow 12: thêm endpoint, mô tả vô hiệu hóa/thông báo)
- Modify: `docs/data.md` (V28)
- Modify: `docs/seller-center.md` (mục 2.7)
- Modify: `fashionstore-ui/UI-FEATURES.md` (đánh dấu mục Nhân sự & phân quyền)
- Modify: `fashionstore-ui/docs/openapi.json` (đã tải ở Task 9)

- [ ] **Step 1: Cập nhật docs.** Ghi rõ: mời qua tài khoản đã đăng ký + thông báo trong website; 5 nhóm quyền; vô hiệu hóa thay vì xóa; audit toàn bộ nghiệp vụ.
- [ ] **Step 2: Chạy toàn bộ backend** `./gradlew test` → xanh.
- [ ] **Step 3: Chạy toàn bộ UI** `pnpm format && pnpm lint && pnpm test && pnpm build` → xanh.
- [ ] **Step 4: Smoke test tay.**
  - Seed dev (`app.demo-seed=true`) chạy được, `DemoShopOpsSeeder` không lỗi.
  - Đăng nhập owner → mời email `hanh@demo.local` → thấy thông báo ở tài khoản Hạnh → accept → vào `/shop` thấy menu theo role.
  - Vô hiệu hóa Hạnh → Hạnh reload `/shop` bị chặn quyền; audit có `DEACTIVATE_MEMBER`.
  - Sửa role Hạnh → quyền menu đổi ngay.
- [ ] **Step 5: Commit** `docs(staff): cap nhat tai lieu quan ly nhan vien 2.8.1`.

---

## Self-Review

- **Spec coverage:** mời qua email tài khoản (Task 5), phân quyền 5 nhóm (Task 2–4), vô hiệu hóa (Task 6), audit (Task 7), thông báo website (Task 5, 8, 9), UI (Task 9–10), docs (Task 11). Đủ.
- **Không còn TBD:** mọi endpoint, DTO, tên method, test name đã chốt.
- **Type consistency:** `ShopPermission`, `MemberAccess`, `MyPermissionsResponse`, `NotificationRow`, `ShopActivityResponse` được định nghĩa ở task trước và dùng lại ở task sau.
- **Review Focus:** 5 rủi ro đều có test tương ứng (Task 2, 5, 6).
