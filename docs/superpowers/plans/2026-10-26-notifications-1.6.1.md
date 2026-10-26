# Thông báo đa kênh 1.6.1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mở rộng feature notification có sẵn trên master thành thông báo đa kênh: đơn hàng / tương tác cho buyer (Header), bán hàng cho seller (/shop), realtime qua WebSocket, kèm lời mời nhân viên realtime.

**Architecture:** Insert `user_notification` cùng transaction với nghiệp vụ qua `NotificationService.create(...)`; push realtime qua `NotificationRealtimePublisher` mới (pattern `ChatRealtimePublisher`: HTTP là nguồn chân lý, WS là lớp phủ, lỗi gửi nuốt). `NotificationBell` hiện có được mở rộng prop `categories` + tab + xóa + WS invalidate.

**Tech Stack:** Spring Boot 4 + JdbcClient + Flyway (V30), STOMP WebSocket có sẵn; React 19 + TanStack Query + orval codegen + vitest `renderToString`.

**Spec:** `docs/superpowers/specs/2026-10-26-notifications-design.md` (cả plan lẫn executor đọc kèm).

## Global Constraints

- Cấm ternary (`? :`): `if` + early `return`; JSX dùng `&&`. Cả 2 repo.
- Backend: indent 4 space, `var` local, `record` DTO, dòng ≤120 ký tự, NullAway non-null mặc định (`@Nullable` chỉ khi cột DB nullable: `link`, `readAt`, `refId`).
- Exception/message lỗi tiếng Anh; nội dung thông báo (title/body) tiếng Việt hiển thị nguyên văn.
- Lombok allowlist: `@Getter`, `@RequiredArgsConstructor`, `@Slf4j`; entity cấm `@Data`/`@Setter`.
- Endpoint mới đủ annotation springdoc: `@Operation(summary, description, operationId)`, `@ApiResponse` từng mã, `@Parameter`, `bearerAuth` cho auth.
- Migration Flyway: file mới `V30__notification_categories.sql`, KHÔNG sửa migration đã merge.
- Testcontainers giữ image `ghcr.io/minhnbnt/postgresql:latest`, không đổi.
- UI: chỉ `pnpm` (cấm `npm`), `generated/` cấm sửa tay (codegen), test component bằng `renderToString` + `vi.mock` (không thêm devDeps).
- Commit: conventional commits (`feat:`, `test:`, `docs:`…), mỗi task 1+ commit.
- Hoàn tất task nào chạy test task đó xanh; Task cuối chạy full suite cả 2 repo.

## Review Focus

Các đầu vào/failure mode mà test của task có thể bỏ sót — mỗi dòng gắn test vào task sở hữu:

1. **Xóa/đọc thông báo của người khác** → phải 404, không thao tác được dữ liệu người khác. Test: `deleteAnotherUsersNotificationReturnsNotFound`, `markReadOfAnotherUsersNotificationReturnsNotFound` (đã có) — Task 4, Task 2 (repo delete có `user_id = :user`).
2. **Param `categories` lạ** (giá trị không thuộc enum, khoảng trắng) → 400 rõ ràng chứ không lặng lẽ trả rỗng. Test: `listRejectsUnknownCategory` — Task 5.
3. **Nghiệp vụ rollback thì thông báo phải rollback theo** (insert cùng transaction). Test: `checkoutFailureLeavesNoNotification` — Task 6.
4. **Lời mời regression**: `notifyInvitation` cũ vẫn tạo row đúng link `/invitations/{token}` + `deleteByRefId` vẫn xóa được khi rút lời mời. Test: giữ nguyên suite hiện có phải xanh + `deleteByRefIdRemovesNotification` — Task 4.
5. **WS chết thì polling 30s còn sống** (badge tự cập nhật). Test renderToString không chạy interval — kiểm tay trong Task 12 (giữ nguyên `refetchInterval: 30000`).

---

### Task 1: Tạo nhánh cả 2 repo

**Files:** — (chỉ git)

- [ ] **Step 1: Backend** — từ master:
```bash
git -C D:\fashionstore\fashionstore checkout master && git -C D:\fashionstore\fashionstore pull
git -C D:\fashionstore\fashionstore checkout -b feature/notifications-1.6.1
```
- [ ] **Step 2: UI** — từ master:
```bash
git -C D:\fashionstore\fashionstore-ui checkout master && git -C D:\fashionstore\fashionstore-ui pull
git -C D:\fashionstore\fashionstore-ui checkout -b feature/notifications-1.6.1
```
- [ ] **Step 3:** Copy design doc (file untracked trong workspace) vào nhánh backend và commit:
```bash
git -C D:\fashionstore\fashionstore add docs/superpowers/specs/2026-10-26-notifications-design.md
git -C D:\fashionstore\fashionstore commit -m "docs: add multi-channel notifications design"
```

### Task 2: V30 migration + `NotificationCategory` + repository category/delete + `NotificationResponse.category`

**Files:**
- Create: `fashionstore/src/main/resources/db/migration/V30__notification_categories.sql`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationCategory.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationRepository.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/notification/NotificationResponse.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/notification/NotificationRepositoryIntegrationTest.java` (mở rộng), `NotificationServiceTest.java` + `NotificationControllerTest.java` (sửa constructor `NotificationResponse` thêm field `category` — thêm `"ACTIVITY"` ngay sau `type` cho fixture `SHOP_INVITATION`)

**Interfaces:**
- Produces (Task 4, 5 dùng): enum `NotificationCategory { ORDER, ACTIVITY, SELLING }` (package `features.notification`); `NotificationRow` thêm field `String category` (vị trí sau `id`); `insert(...)` trả về `NotificationRow`; `delete(UUID userId, UUID id)`; các query nhận `@Nullable List<String> categories` (`null`/rỗng = không lọc).

- [ ] **Step 1: Migration** `V30__notification_categories.sql`:
```sql
ALTER TABLE user_notification ADD COLUMN category VARCHAR(12) NOT NULL DEFAULT 'ACTIVITY';
ALTER TABLE user_notification ADD CONSTRAINT chk_notification_category
    CHECK (category IN ('ORDER', 'ACTIVITY', 'SELLING'));
CREATE INDEX idx_notification_unread ON user_notification (user_id) WHERE read_at IS NULL;
```
- [ ] **Step 2: Viết test đỏ** — thêm vào `NotificationRepositoryIntegrationTest` (mirror style test hiện có, seed user bằng `UserService.upsert` như `NotificationServiceTest`):
  - `insertReturnsRowWithCategory`: `insert(user, NotificationCategory.ORDER, "ORDER_PLACED", ...)` trả row có `category() = "ORDER"`, `createdAt() != null`.
  - `findPageFiltersByCategories`: seed 1 row ORDER + 1 row ACTIVITY; `findPage(user, List.of("ORDER"), 0, 20)` → 1 item type `ORDER_PLACED`.
  - `unreadCountFiltersByCategories`: seed ORDER + ACTIVITY; `countUnread(user, List.of("ACTIVITY"))` = 1.
  - `markAllReadScopedByCategories`: như trên; `markAllRead(user, List.of("ORDER"))` → unread còn 1 (ACTIVITY).
  - `deleteRemovesOwnNotificationOnly`: delete(user, id) → row biến mất; delete(stranger, id) không ném nhưng row của owner còn (assert trước/sau).
  - `deleteByRefIdRemovesNotification` (regression lời mời — có thể đã có, thiếu thì thêm).
- [ ] **Step 3: Chạy test đỏ:**
```bash
./gradlew test --tests "com.minhnbnt.fashionstore.features.notification.*"
```
Expected: FAIL — compile error `NotificationCategory` chưa tồn tại / cột `category` chưa có.
- [ ] **Step 4: Implement.**
  - `NotificationRepository.NotificationRow` thêm `String category` sau `id`.
  - `insert`: `INSERT INTO user_notification (..., category, ...) VALUES (..., :category, ...) RETURNING id, category, type, title, body, link, read_at, created_at`, map về `NotificationRow` (dùng `toRow` hiện có, thêm đọc cột `category`). Tham số thứ 2 mới là `NotificationCategory category`.
  - `findPage/countUnread/markAllRead`: nhận `@Nullable List<String> categories`; dựng SQL bằng `StringBuilder` — điều kiện `AND category = ANY(:cats)` chỉ khi list != null && !isEmpty (param name `cats`, bind `categories.toArray(new String[0])`).
  - `delete(UUID userId, UUID id)`: `DELETE FROM user_notification WHERE user_id = :user AND id = :id`.
  - `NotificationResponse`: thêm field `String category` ngay sau `id`; các chỗ construct trong main/test thêm giá trị.
- [ ] **Step 5: Chạy lại suite notification + sửa mọi chỗ compile test khác nếu còn đỏ.**
```bash
./gradlew test --tests "com.minhnbnt.fashionstore.features.notification.*"
```
Expected: PASS (kể cả test cũ `markReadOfAnotherUsersNotificationReturnsNotFound`).
- [ ] **Step 6: Commit**
```bash
git add -A && git commit -m "feat(notification): category column, scoped queries and delete"
```

### Task 3: `NotificationRealtimePublisher`

**Files:**
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/infrastructure/realtime/NotificationRealtimePublisher.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/infrastructure/realtime/NotificationRealtimePublisherTest.java`

**Interfaces:**
- Consumes: `NotificationResponse` (Task 2).
- Produces (Task 4 dùng): `void publish(UUID recipientId, NotificationResponse notification)` — destination `/queue/notifications`, qua `convertAndSendToUser(recipientId.toString(), ...)`, exception nuốt + `log.warn` (comment giải thích như `ChatRealtimePublisher`: HTTP là nguồn chân lý).

- [ ] **Step 1: Test đỏ** (plain JUnit + Mockito, mock `SimpMessagingTemplate`):
  - `publishSendsToUserQueue`: verify `convertAndSendToUser("<id>", "/queue/notifications", notification)`.
  - `publishSwallowsMessagingFailure`: mock ném RuntimeException → không ném ra ngoài.
- [ ] **Step 2: Chạy đỏ** — `./gradlew test --tests "*NotificationRealtimePublisherTest*"` → compile error.
- [ ] **Step 3: Implement** theo signature ở trên (copy comment + `@Slf4j @Service @RequiredArgsConstructor` của `ChatRealtimePublisher`).
- [ ] **Step 4: Chạy xanh.** — [ ] **Step 5: Commit** `feat(notification): realtime publisher for /queue/notifications`.

### Task 4: `NotificationService.create(...)` + lời mời đi qua đường chung

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationService.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/notification/NotificationServiceTest.java` (mở rộng)

**Interfaces:**
- Consumes: `NotificationRepository.insert/inserts` (Task 2), `NotificationRealtimePublisher.publish` (Task 3).
- Produces (Task 6, 7 dùng — signature cố định):
```java
public void create(
    Collection<UUID> recipients,
    NotificationCategory category,
    String type,
    String title,
    String body,
    @Nullable String link,
    @Nullable UUID refId
)
```
  Loop từng recipient: `insert` rồi `publish(recipientId, toResponse(row))`. Cùng task này thêm service method `public void deleteNotification(UUID userId, UUID id)` — pattern như `markRead`: `exists(userId, id)` sai → `ResponseStatusException(NOT_FOUND, "Notification not found")`, đúng → `notifications.delete(userId, id)` (Task 5 controller gọi method này). `page/unreadCount/markAllRead` đổi chữ ký nhận `@Nullable List<String> categories` (Task 5 controller gọi với giá trị này). Private `NotificationResponse toResponse(NotificationRow row)`.

- [ ] **Step 1: Test đỏ** trong `NotificationServiceTest`:
  - `createPersistsPerRecipientAndPushes`: `@MockitoBean NotificationRealtimePublisher`; `create(Set.of(a, b), ORDER, "ORDER_PLACED", ...)` → 2 user nhận đúng 1 row, publisher.verify × 2 với `NotificationResponse.category = "ORDER"`.
  - `createIgnoresEmptyRecipients`: `create(Set.of(), ...)` → không row, publisher không gọi.
  - `notifyInvitationUsesCommonPath`: giữ test cũ về row/link; thêm verify publisher được gọi 1 lần với category `"ACTIVITY"`.
- [ ] **Step 2: Chạy đỏ** (compile error `create` chưa tồn tại).
- [ ] **Step 3: Implement** `create(...)`; refactor `notifyInvitation` thành 1 dòng gọi `create(Set.of(userId), NotificationCategory.ACTIVITY, "SHOP_INVITATION", title, body, "/invitations/" + token, invitationId)`; `page/unreadCount/markAllRead` thêm tham số `categories` truyền xuống repo. Javadoc class sửa: "Thông báo trong website — mọi loại thông báo đi qua create(), realtime push qua NotificationRealtimePublisher."
- [ ] **Step 4: Chạy xanh** cả suite notification.
- [ ] **Step 5: Commit** `feat(notification): shared create path with realtime push`.

### Task 5: Controller — param `categories` + endpoint DELETE

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationController.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/notification/NotificationControllerTest.java` (mở rộng)

**Interfaces:**
- Consumes: service mới (Task 4). 
- Produces: API contract cho UI codegen (Task 8):
  - `GET /api/notifications?categories=&page=&size=` — `@RequestParam(required = false) String categories`.
  - `GET /api/notifications/unread-count?categories=` — như trên.
  - `PATCH /api/notifications/read-all?categories=` — như trên.
  - `DELETE /api/notifications/{id}` → 204, `operationId = "deleteNotification"`, 404 khi không phải của mình.

- [ ] **Step 1: Test đỏ** trong `NotificationControllerTest`:
  - `listPassesCategoriesToService`: `GET /api/notifications?categories=ORDER,ACTIVITY` → verify service gọi với `List.of("ORDER","ACTIVITY")`.
  - `listRejectsUnknownCategory`: `?categories=BOGUS` → 400.
  - `deleteReturns204AndVerifiesService`: DELETE `/{id}` → 204 + verify; mock service ném `ResponseStatusException(404)` → 404.
  - Sửa các test cũ truyền service mới chữ ký (categories null).
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Implement:**
  - Private helper: `@Nullable List<String> parseCategories(@Nullable String categories)` — blank/null → null; split `","`, trim, bỏ phần rỗng; từng phần phải `NotificationCategory.valueOf` được (sai → `ResponseStatusException(BAD_REQUEST, "Unknown notification category: " + part)`).
  - 3 endpoint GET/PATCH thêm `@Parameter(description = "...")` cho `categories`, gọi service với parsed value.
  - DELETE endpoint theo contract trên (gọi `deleteNotification(userId, id)` của Task 4).
- [ ] **Step 4: Chạy xanh.** — [ ] **Step 5: Commit** `feat(notification): categories filter and delete endpoint`.

### Task 6: Hook OrderService (7 điểm)

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/order/OrderServiceTest.java` (mở rộng)

**Interfaces:**
- Consumes: `NotificationService.create(...)` (Task 4), `ShopService.findActiveUserIdsByShop(shopId)` (đã có trên master), `Order.getCode()/getShopId()/getBuyerId()` (đã có).
- Injection: `OrderService` thêm field `NotificationService notifications` (qua `@RequiredArgsConstructor`).

Copy từng sự kiện (title/body khớp chuẩn đây, đặt ngay sau lần `histories.save` tương ứng hoặc sau `audit.record` của confirm/ship/deliver):

| Sự kiện | Category | Type | Recipient | Title | Body | Link |
|---|---|---|---|---|---|---|
| `checkout` | SELLING | `ORDER_PLACED` | `findActiveUserIdsByShop(order.getShopId())` | `Có đơn hàng mới #<code>` | `Khách hàng vừa đặt đơn #<code> tại shop của bạn.` | `/shop?tab=orders` |
| `cancel` (buyer) | SELLING | `ORDER_CANCELLED` | member shop | `Đơn #<code> đã bị hủy` | `Khách hàng đã hủy đơn #<code>.` | `/shop?tab=orders` |
| `confirm` | ORDER | `ORDER_CONFIRMED` | `Set.of(order.getBuyerId())` | `Đơn #<code> đã được xác nhận` | `Shop đã xác nhận đơn #<code>, đang chuẩn bị hàng.` | `/account` |
| `ship` | ORDER | `ORDER_SHIPPED` | buyer | `Đơn #<code> đang được giao` | `Shop đã bàn giao đơn #<code> cho đơn vị vận chuyển.` | `/account` |
| `deliver` | ORDER | `ORDER_DELIVERED` | buyer | `Đơn #<code> đã được giao` | `Đơn #<code> đã giao thành công. Đừng quên đánh giá sản phẩm!` | `/account` |
| `sellerCancel` | ORDER | `ORDER_CANCELLED` | buyer | `Đơn #<code> đã bị hủy` | `Đơn #<code> đã bị hủy: <reason>` | `/account` |
| `cancelByAdmin` | ORDER | `ORDER_CANCELLED` | buyer | `Đơn #<code> đã bị hủy` | `Đơn #<code> đã bị hủy: <reason>` | `/account` |

- [ ] **Step 1: Test đỏ** trong `OrderServiceTest` (autowire thêm `NotificationRepository`; fixture có sẵn `mirroredUser`/`approvedShop`/`variantInShop` — dùng để checkout 1 đơn thật):
  - `checkoutNotifiesShopMembers`: sau checkout, member shop (owner) có đúng 1 row SELLING/`ORDER_PLACED`, link `/shop?tab=orders`; buyer không nhận row này.
  - `confirmShipDeliverNotifyBuyer`: sau từng thao tác, buyer có row ORDER đúng type; member shop không nhận.
  - `buyerCancelNotifiesShopMembers` + `sellerCancelNotifiesBuyer` (body chứa lý do).
  - `checkoutFailureLeavesNoNotification`: checkout ném (ví dụ shop chưa APPROVED) → `NotificationRepository.countByUser(member)` = 0.
- [ ] **Step 2: Chạy đỏ** (`--tests "*OrderServiceTest*"`).
- [ ] **Step 3: Implement** 7 điểm móc theo bảng (mỗi nơi ~4 dòng gọi `notifications.create(...)`).
- [ ] **Step 4: Chạy xanh.** — [ ] **Step 5: Commit** `feat(order): notify shop and buyer on order lifecycle`.

### Task 7: Hook ReviewService + ChatService

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/ReviewService.java`, `features/chat/ChatService.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/chat/ChatServiceUnitTest.java`, test review hiện có (tìm file test của `ReviewService` trong `features/review/` — mở rộng; nếu chỉ có controller test thì assert qua service test mới)

**Interfaces:**
- Consumes: `create(...)`; `Review.getBuyerId()/getProductId()`; `ChatService` có sẵn `shops` (ShopService) → `shops.requireShop(conv.getShopId()).getName()`.
- Copy: `REVIEW_REPLIED` — ACTIVITY, recipient `Set.of(review.getBuyerId())`, title `Shop đã phản hồi đánh giá của bạn`, body = `request.reply()`, link `/product/<productId>`. `CHAT_MESSAGE` — ACTIVITY, recipient `Set.of(conv.getBuyerId())` **chỉ khi `isSeller`** (kể cả auto-reply), title `Tin nhắn mới từ <shopName>`, body = nội dung tin nhắn, link `null` (kèm comment: client mở widget qua CustomEvent).

- [ ] **Step 1: Test đỏ:**
  - Chat (`ChatServiceUnitTest`, mock `NotificationService` như các mock hiện có): `sendMessageFromShopNotifiesBuyer` — seller gửi → verify `create` với buyerId; `sendMessageFromBuyerDoesNotNotifySeller` — buyer gửi → verify `create` KHÔNG được gọi.
  - Review: seller reply → row ACTIVITY/`REVIEW_REPLIED` của tác giả review, link `/product/<productId>` (test integration style như OrderServiceTest).
- [ ] **Step 2: Chạy đỏ.** — [ ] **Step 3: Implement** 3 điểm gọi (reply, sendMessage, auto-reply).
- [ ] **Step 4: Chạy xanh.** — [ ] **Step 5: Commit** `feat(review,chat): activity notifications for reply and chat`.

### Task 8: Full backend + xuất openapi

**Files:** — (verification; openapi.json xuất tự động vào `../fashionstore-ui/docs/openapi.json` qua `OpenApiExportTest`)

- [ ] **Step 1:** `./gradlew test` — toàn bộ xanh.
- [ ] **Step 2:** `docs/openapi.json` đã được test ghi lại — `git diff --stat docs/` không có gì (openapi ở repo UI, test tự ghi qua đường tương đối). Xác nhận file `fashionstore-ui/docs/openapi.json` mới chứa `"deleteNotification"`:
```bash
Select-String -Path ..\fashionstore-ui\docs\openapi.json -Pattern deleteNotification | Measure-Object | Select-Object -ExpandProperty Count
```
Expected: ≥1.

### Task 9: UI — codegen

**Files:**
- Modify: `fashionstore-ui/docs/openapi.json` (đã ghi bởi Task 8)
- Modify: `fashionstore-ui/generated/**` (tự sinh)

- [ ] **Step 1:** `pnpm codegen` trong `fashionstore-ui/` — generated có `useListNotifications`, `useGetUnreadNotificationCount`, `useMarkNotificationRead`, `useMarkAllNotificationsRead`, `useDeleteNotification` (keys: `getListNotificationsQueryKey`, `getGetUnreadNotificationCountQueryKey`, `getDeleteNotificationMutationKey`), model `notificationResponse` có field `category`.
- [ ] **Step 2:** `pnpm build` xanh (code cũ chưa gọi gì mới — build vẫn phải pass).
- [ ] **Step 3:** Commit `docs: refresh openapi spec for notifications 1.6.1`.

### Task 10: UI — `NotificationBell` categories + tab + xóa + WS

**Files:**
- Modify: `fashionstore-ui/src/components/NotificationBell.tsx`
- Test: `fashionstore-ui/src/components/NotificationBell.test.tsx` (mới — mirror `MyShopsTab.test.tsx`: `createElement` + `renderToString` + `MemoryRouter` + `vi.mock` hooks generated)

**Interfaces:**
- Consumes: hooks generated (Task 9), `useWebSocket` (`src/hooks/useWebSocket.ts`), `useSessionQuery`, `takeData`, `formatDateTime` (đã có trong file).
- Produces: `<NotificationBell categories={['ORDER','ACTIVITY']} />` (Header) và `categories={['SELLING']}` (SellerLayout) — prop tùy chọn `categories?: Array<'ORDER' | 'ACTIVITY' | 'SELLING'>`, không truyền = hành vi cũ.

Chi tiết chốt:
- `const TAB_LABELS = { ORDER: 'Đơn hàng', ACTIVITY: 'Hoạt động', SELLING: 'Bán hàng' }` (module-level).
- Badge unread: `useGetUnreadNotificationCount` với `categories = categories?.join(',')` — đếm trên toàn bộ categories được cấp, không theo tab.
- List: `useListNotifications({ categories: tabListParam, page: 0, size: 20 })` với `tabListParam = activeTab` khi có nhiều category (tab lọc server-side), bằng `categories?.join(',')` khi 1 category, `undefined` khi legacy.
- Tab chỉ render khi `categories && categories.length > 1`; đổi tab → set state + query đổi param (TanStack refetch tự động vì key đổi); giữ `"Đã đọc tất cả"` scope theo param list hiện tại.
- Xóa từng item: hàng item hiện là `<button>` → bọc `div.relative`, vùng chính vẫn button (mark read + navigate), thêm icon-button `X` `aria-label="Xóa thông báo"` góc phải → `useDeleteNotification` + invalidate list/unread.
- WS: `useWebSocket('/user/queue/notifications', function () { void queryClient.invalidateQueries({ queryKey: getListNotificationsQueryKey() }); void queryClient.invalidateQueries({ queryKey: getGetUnreadNotificationCountQueryKey() }); })` — giữ nguyên `refetchInterval: 30000` làm backstop.
- Click item type `CHAT_MESSAGE` && `link == null`: `window.dispatchEvent(new CustomEvent('fashionstore:open-chat'))` rồi vẫn mark read, không navigate.

- [ ] **Step 1: Test đỏ** `NotificationBell.test.tsx` (mock 5 hooks generated + `useWebSocket` no-op + `useSessionQuery` trả session):
  - `rendersTabsWhenCategoriesProvided`: `categories={['ORDER','ACTIVITY']}` → HTML chứa "Đơn hàng" và "Hoạt động", KHÔNG chứa "Bán hàng".
  - `singleCategoryRendersNoTabs`: `categories={['SELLING']}` → không có tab, HTML chứa title thông báo SELLING fixture.
  - `legacyModeRendersNoTabs`: không prop → không tab.
  - `unreadBadgeShowsCount`: fixture 2 unread → HTML chứa `2`.
  - `readItemRendersDimmed`: item có `readAt` → class chứa `opacity-80`.
- [ ] **Step 2:** `pnpm test -- NotificationBell` (vitest run file) → đỏ.
- [ ] **Step 3: Implement** theo chi tiết chốt trên (giữ style code hiện có: không ternary, `if`/`let` local).
- [ ] **Step 4:** `pnpm test` toàn bộ xanh.
- [ ] **Step 5: Commit** `feat(ui): notification bell categories, tabs, delete and websocket`.

### Task 11: UI — gắn prop vào 2 placement + mở chat từ thông báo

**Files:**
- Modify: `fashionstore-ui/src/components/Header.tsx:188` — `<NotificationBell />` → `<NotificationBell categories={['ORDER', 'ACTIVITY']} />`
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/SellerLayout.tsx:255` — `<NotificationBell />` → `<NotificationBell categories={['SELLING']} />`
- Modify: `fashionstore-ui/src/components/chat/BuyerChatWidget.tsx` — thêm `useEffect` nghe `window.addEventListener('fashionstore:open-chat', handler)` mở panel (set open state `true`), cleanup removeEventListener; đặt trong component giữ state `open` (tìm state `open` trong file, đặt cạnh các effect sẵn có).

- [ ] **Step 1:** Sửa 2 placement + BuyerChatWidget listener.
- [ ] **Step 2:** `pnpm format && pnpm lint` — 0 error 0 warning.
- [ ] **Step 3:** `pnpm build` xanh.
- [ ] **Step 4:** `pnpm test` xanh.
- [ ] **Step 5: Commit** `feat(ui): wire notification bells for buyer and seller placements`.

### Task 12: Kiểm tay end-to-end (manual checklist — không automate được phần realtime)

**Files:** — 

- [ ] **Step 1:** Chạy backend (`./gradlew bootRun` với demo-seed local) + UI (`pnpm dev`); 2 trình duyệt: buyer đăng nhập, seller/shop đăng nhập.
- [ ] **Step 2:** Buyer đặt đơn → bell seller (trong /shop) hiện badge + thông báo "Có đơn hàng mới" không đợi 30s (WS).
- [ ] **Step 3:** Seller confirm/ship/deliver → bell buyer cập nhật tương ứng từng trạng thái; click item → về `/account`.
- [ ] **Step 4:** Seller reply review + nhắn tin cho buyer (kể cả auto-reply) → tab "Hoạt động" của buyer có item; click item chat → BuyerChatWidget mở.
- [ ] **Step 5:** Tab "Đơn hàng"/"Hoạt động" lọc đúng; "Đã đọc tất cả" theo tab; Xóa từng item biến mất ngay; buyer hủy đơn → seller thấy thông báo hủy.
- [ ] **Step 6:** Mời nhân viên (SellerStaffTab) → user được mời online thấy lời mời realtime; click → trang `/invitations/<token>`.
- [ ] **Step 7:** Nếu phát hiện lỗi → sửa + test đỏ/xanh theo quy trình rồi commit `fix: ...`; không có lỗi thì kết thúc.

## Self-review notes

- Spec coverage: 8 loại thông báo (7 mới + lời mời realtime) — Task 6, 7, 4; tab/đọc/xóa — Task 10; realtime/WS — Task 3, 4, 10; API — Task 5; V30 — Task 2; nhánh — Task 1; verification — Task 8, 12.
