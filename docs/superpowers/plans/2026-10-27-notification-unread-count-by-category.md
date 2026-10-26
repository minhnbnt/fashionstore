# Category Notification Unread Count Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hiển thị badge con nhộng nhỏ màu sắc cạnh tên mỗi tab thông báo khách hàng (Đơn hàng, Hoạt động) phản ánh số lượng chưa đọc của từng tab, lấy từ API `GET /api/notifications/unread-count` đã mở rộng.

**Architecture:** Mở rộng DTO `NotificationCountResponse` ở Backend Spring Boot với `Map<String, Long> byCategory`, bổ sung query `countUnreadByCategory` ở `NotificationRepository`, xuất OpenAPI qua `OpenApiExportTest` sang `fashionstore-ui`, và cập nhật UI component `NotificationBell.tsx` hiển thị pill badge unread per tab.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring JDBC Client, PostgreSQL, OpenAPI/Springdoc, Vite, React 19, TypeScript, Orval, Vitest, TailwindCSS.

**Spec:** [docs/superpowers/specs/2026-10-27-notification-unread-count-by-category-design.md](file:///d:/fashionstore/fashionstore/docs/superpowers/specs/2026-10-27-notification-unread-count-by-category-design.md)

## Global Constraints

- Cấm tuyệt đối toán tử 3 ngôi (`? :`) ở cả Backend Java và Frontend TypeScript/TSX.
- Giữ vững tuân thủ NullAway annotations (`@Nullable`, `@NonNull`) ở Backend.
- Sử dụng biến cục bộ `var` trong Java theo quy ước codebase.
- Sử dụng trình quản lý gói `pnpm` trong `fashionstore-ui` (không dùng `npm`).
- Không để lại cảnh báo lint (`pnpm lint` 0 warnings, 0 errors).

## Review Focus

- Trường hợp người dùng không có thông báo chưa đọc nào (`byCategory` rỗng hoặc unread = 0): tab không hiển thị badge con nhộng.
- Trường hợp số lượng unread của 1 tab vượt quá 99: hiển thị `99+`.
- Trường hợp người dùng click tab hoặc click "Đã đọc tất cả": query invalidation làm mới lại badge từng tab và chuông tổng.
- Khi chỉ có 1 category (như trang Người bán `['SELLING']`): tabs ẩn, badge ngoài hoạt động bình thường như cũ.
- Khi filter categories truyền vào endpoint (ví dụ `categories=ORDER,ACTIVITY`), chỉ đếm các category được yêu cầu.

---

### Task 1: Backend DTO & Repository for Unread by Category

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/notification/NotificationCountResponse.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationRepository.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/notification/NotificationRepositoryIntegrationTest.java`

**Interfaces:**
- Consumes: `user_notification` table with columns `user_id`, `category`, `read_at`.
- Produces: 
  - `NotificationCountResponse(long unread, Map<String, Long> byCategory)`
  - `NotificationRepository.countUnreadByCategory(UUID userId, @Nullable List<String> categories) -> Map<String, Long>`

- [ ] **Step 1: Write the failing repository integration test**

In `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/notification/NotificationRepositoryIntegrationTest.java`:
Add test `countUnreadByCategoryGroupsCorrectly()` creating notifications for `ORDER` and `ACTIVITY`, verifying that `countUnreadByCategory` returns `{ "ORDER": 1, "ACTIVITY": 2 }` and filtering with `categories` list returns only requested subset.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.minhnbnt.fashionstore.features.notification.NotificationRepositoryIntegrationTest"`
Expected: FAIL (compilation error: `countUnreadByCategory` does not exist).

- [ ] **Step 3: Update `NotificationCountResponse` and implement `countUnreadByCategory`**

1. In `NotificationCountResponse.java`:
```java
public record NotificationCountResponse(
    @Schema(description = "Tổng số thông báo chưa đọc", example = "5")
    long unread,

    @Schema(description = "Số lượng chưa đọc theo từng nhóm (ORDER, ACTIVITY, SELLING)")
    Map<String, Long> byCategory
) {
    public NotificationCountResponse(long unread) {
        this(unread, Map.of());
    }
}
```
2. In `NotificationRepository.java`:
Implement `countUnreadByCategory(UUID userId, @Nullable List<String> categories)` using `SELECT category, count(*) AS cnt FROM user_notification WHERE user_id = :user AND read_at IS NULL GROUP BY category` (appending `AND category = ANY(:cats)` if `categories != null && !categories.isEmpty()`).

- [ ] **Step 4: Run repository tests to verify they pass**

Run: `./gradlew test --tests "com.minhnbnt.fashionstore.features.notification.NotificationRepositoryIntegrationTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/minhnbnt/fashionstore/dto/notification/NotificationCountResponse.java src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationRepository.java src/test/java/com/minhnbnt/fashionstore/features/notification/NotificationRepositoryIntegrationTest.java
git commit -m "feat(notification): add countUnreadByCategory in repository and response"
```

---

### Task 2: Backend Service & Controller for Unread Summary

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationService.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationController.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/notification/NotificationControllerTest.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/notification/NotificationServiceTest.java`

**Interfaces:**
- Consumes: `NotificationRepository.countUnread` and `NotificationRepository.countUnreadByCategory`.
- Produces: 
  - `NotificationService.unreadSummary(UUID userId, @Nullable List<String> categories) -> NotificationCountResponse`
  - `GET /api/notifications/unread-count` returns `NotificationCountResponse` with `unread` and `byCategory`.

- [ ] **Step 1: Write failing controller test for `byCategory` JSON response**

In `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/notification/NotificationControllerTest.java`:
Update `unreadCountReturnsNumber()` and `unreadCountPassesCategoriesToService()` to mock `notificationService.unreadSummary` returning `new NotificationCountResponse(3L, Map.of("ORDER", 2L, "ACTIVITY", 1L))` and assert `jsonPath("$.byCategory.ORDER").value(2)` and `jsonPath("$.byCategory.ACTIVITY").value(1)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.minhnbnt.fashionstore.features.notification.NotificationControllerTest"`
Expected: FAIL (method `unreadSummary` not found on service).

- [ ] **Step 3: Implement `unreadSummary` in `NotificationService` and update `NotificationController`**

1. In `NotificationService.java`:
Add method:
```java
public NotificationCountResponse unreadSummary(UUID userId, @Nullable List<String> categories) {
    var unread = notifications.countUnread(userId, categories);
    var byCategory = notifications.countUnreadByCategory(userId, categories);
    return new NotificationCountResponse(unread, byCategory);
}
```
2. In `NotificationController.java`:
Update `getUnreadNotificationCount` to call `notificationService.unreadSummary(userId, parseCategories(categories))`.

- [ ] **Step 4: Run all notification tests to verify they pass**

Run: `./gradlew test --tests "com.minhnbnt.fashionstore.features.notification.*"`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationService.java src/main/java/com/minhnbnt/fashionstore/features/notification/NotificationController.java src/test/java/com/minhnbnt/fashionstore/features/notification/NotificationControllerTest.java src/test/java/com/minhnbnt/fashionstore/features/notification/NotificationServiceTest.java
git commit -m "feat(notification): return unread summary with byCategory in controller"
```

---

### Task 3: OpenAPI Spec Export & Frontend Codegen

**Files:**
- Modify: `fashionstore-ui/docs/openapi.json`
- Modify: `fashionstore-ui/generated/model/notificationCountResponse.ts`

**Interfaces:**
- Consumes: Backend Springdoc OpenAPI definition via `OpenApiExportTest`.
- Produces: Updated `NotificationCountResponse` TypeScript model containing `byCategory?: { [key: string]: number }`.

- [ ] **Step 1: Run `OpenApiExportTest` to export updated `openapi.json`**

Run: `./gradlew test --tests "com.minhnbnt.fashionstore.OpenApiExportTest" --rerun`
Expected: BUILD SUCCESSFUL, and `fashionstore-ui/docs/openapi.json` is updated containing `"byCategory"`.

- [ ] **Step 2: Run codegen in `fashionstore-ui`**

In `fashionstore-ui`:
Run: `pnpm build`
Expected: Orval regenerates endpoints and models, TypeScript builds cleanly without errors.

- [ ] **Step 3: Verify updated generated model**

Check `fashionstore-ui/generated/model/notificationCountResponse.ts` to ensure `byCategory` exists.

- [ ] **Step 4: Commit**

```bash
git add docs/openapi.json generated/
git commit -m "chore(api): update openapi spec and regenerate models with byCategory"
```

---

### Task 4: UI Tab Badges & Unit Tests

**Files:**
- Modify: `fashionstore-ui/src/components/NotificationBell.tsx`
- Test: `fashionstore-ui/src/components/NotificationBell.test.tsx`

**Interfaces:**
- Consumes: `NotificationCountResponse.byCategory`.
- Produces: Capsule pill badge on each tab when `count > 0`.

- [ ] **Step 1: Write failing tests for tab unread badges in `NotificationBell.test.tsx`**

Add tests:
1. `rendersUnreadBadgeOnTabsWhenCountsGreaterThanZero`: mock unread count response with `unread: 5, byCategory: { ORDER: 2, ACTIVITY: 3 }`, render with `categories: ['ORDER', 'ACTIVITY']`, verify HTML contains `2` and `3` inside tab elements with badge class.
2. `hidesUnreadBadgeOnTabsWhenCountIsZero`: mock `byCategory: { ORDER: 0, ACTIVITY: 1 }`, verify `ORDER` tab does not have badge, while `ACTIVITY` tab shows `1`.
3. `rendersCapAt99Plus`: mock `byCategory: { ORDER: 120 }`, verify tab badge text is `99+`.

- [ ] **Step 2: Run test to verify it fails**

In `fashionstore-ui`:
Run: `pnpm test --run src/components/NotificationBell.test.tsx`
Expected: FAIL (badges not rendered yet).

- [ ] **Step 3: Implement category unread pill badges in `NotificationBell.tsx`**

1. Read `byCategory` from `unreadQuery.data` (via `takeData`):
```tsx
let countsByCategory: Record<string, number> = {}
if (unreadQuery.data !== undefined) {
  const countData = takeData(unreadQuery.data)
  if (countData.byCategory !== undefined && countData.byCategory !== null) {
    countsByCategory = countData.byCategory
  }
}
```
2. In tab map loop (`categories.map((cat) => ...)`):
```tsx
let catCount = 0
if (countsByCategory[cat] !== undefined) {
  catCount = countsByCategory[cat]
}
let catBadgeText = String(catCount)
if (catCount > 99) {
  catBadgeText = '99+'
}
```
Render pill badge next to `TAB_LABELS[cat]`:
```tsx
<span className="flex items-center gap-1.5">
  <span>{TAB_LABELS[cat]}</span>
  {catCount > 0 && (
    <span className="inline-flex items-center justify-center rounded-full bg-indigo-100 px-1.5 py-0.2 text-[10px] font-bold text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300">
      {catBadgeText}
    </span>
  )}
</span>
```
Ensure strictly NO ternary operators (`? :`) are used.

- [ ] **Step 4: Run tests, lint, and build in `fashionstore-ui`**

In `fashionstore-ui`:
Run:
`pnpm lint` -> Expected: 0 warnings, 0 errors.
`pnpm test --run` -> Expected: All tests pass.
`pnpm build` -> Expected: Build succeeds.

- [ ] **Step 5: Commit**

```bash
git add src/components/NotificationBell.tsx src/components/NotificationBell.test.tsx
git commit -m "feat(ui): display category unread pill badge on notification tabs"
```
