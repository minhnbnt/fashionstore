# Thiết Kế Chi Tiết: Số Thông Báo Chưa Đọc Theo Từng Tab (Category) Cho Khách Hàng

## 1. Mục tiêu & Bối cảnh (Intent & Why)
- **Vấn đề**: Trên giao diện Khách hàng (Customer Header Bell), chuông thông báo hiện có 2 tab: **Đơn hàng** (`ORDER`) và **Hoạt động** (`ACTIVITY`). Quả chuông bên ngoài hiển thị tổng số unread, nhưng khi mở dropdown thì mặc định đang ở tab "Đơn hàng". Nếu tab "Hoạt động" có thông báo mới (ví dụ tin nhắn chat, lời mời tham gia shop, khuyến mãi/hoạt động), người dùng không nhìn thấy số lượng chưa đọc của tab này và dễ bỏ lỡ.
- **Mục tiêu**: Hiển thị badge con nhộng nhỏ màu sắc cạnh tên mỗi tab (chỉ hiển thị khi số lượng chưa đọc của tab đó > 0). Ví dụ: `Đơn hàng [ 2 ]`, `Hoạt động [ 5 ]`.
- **Phạm vi**: 
  - Khách hàng (Header `NotificationBell` có categories `['ORDER', 'ACTIVITY']`).
  - Phía người bán (`SELLING`) chỉ có 1 category duy nhất nên ẩn tab, không ảnh hưởng.

## 2. Phương Án Thiết Kế (Approaches & Trade-offs)
- **Phương án 1**: Query unread count riêng rẽ ở từng tab tại Frontend.
  - *Ưu điểm*: Không đổi backend.
  - *Nhược điểm*: Khi mở chuông phát sinh thêm nhiều HTTP queries con độc lập.
- **Phương án 2 (Đã chọn bởi User)**: Mở rộng Backend trả về chi tiết unread theo từng category trong cùng một response của `GET /api/notifications/unread-count`.
  - *Ưu điểm*: Chỉ 1 request duy nhất lấy cả tổng số unread và chi tiết từng category; đồng bộ nguyên tử, tiết kiệm network payload.
  - *Nhược điểm*: Cần sửa đổi DTO, xuất lại `openapi.json` và codegen Frontend.

## 3. Thiết Kế Chi Tiết (Detailed Design)

### 3.1. Backend (`fashionstore`)
1. **DTO `NotificationCountResponse.java`**:
   - Bổ sung trường `Map<String, Long> byCategory`:
     ```java
     public record NotificationCountResponse(
         @Schema(description = "Tổng số thông báo chưa đọc", example = "5")
         long unread,
         @Schema(description = "Số lượng chưa đọc theo từng nhóm (ORDER, ACTIVITY, SELLING)")
         Map<String, Long> byCategory
     )
     ```
2. **Repository `NotificationRepository.java`**:
   - Thêm method `countUnreadByCategory(UUID userId, @Nullable List<String> categories)`:
     - Thực hiện query SQL:
       ```sql
       SELECT category, COUNT(*) AS count
       FROM user_notification
       WHERE user_id = :user AND read_at IS NULL
       -- AND category IN (:categories) nếu categories != null
       GROUP BY category
       ```
     - Trả về `Map<String, Long>` (ví dụ `{"ORDER": 2, "ACTIVITY": 5}`).
3. **Service `NotificationService.java`**:
   - Thêm / cập nhật method trả về cả `unread` và `byCategory`:
     ```java
     public NotificationCountResponse unreadSummary(UUID userId, @Nullable List<String> categories)
     ```
4. **Controller `NotificationController.java`**:
   - `getUnreadNotificationCount` gọi `notificationService.unreadSummary(userId, parseCategories(categories))` và trả về `NotificationCountResponse`.
5. **OpenAPI Export**:
   - Chạy `OpenApiExportTest` để xuất schema OpenAPI mới nhất sang `fashionstore-ui/docs/openapi.json`.

### 3.2. Frontend (`fashionstore-ui`)
1. **Codegen**:
   - Chạy `pnpm build` hoặc `orval` để cập nhật `NotificationCountResponse` trong `generated/model`.
2. **Component `NotificationBell.tsx`**:
   - Lấy `byCategory` từ `unreadQuery.data`:
     ```tsx
     let countsByCategory: Record<string, number> = {}
     if (unreadQuery.data !== undefined) {
       const countData = takeData(unreadQuery.data)
       if (countData.byCategory !== undefined && countData.byCategory !== null) {
         countsByCategory = countData.byCategory
       }
     }
     ```
   - Khi render danh sách `categories.map((cat) => ...)`:
     ```tsx
     let catCount = 0
     if (countsByCategory[cat] !== undefined) {
       catCount = countsByCategory[cat]
     }
     ```
   - Nếu `catCount > 0`:
     - Render badge con nhộng nhỏ bên cạnh `{TAB_LABELS[cat]}`:
       ```tsx
       {catCount > 0 && (
         <span className="ml-1.5 inline-flex items-center justify-center rounded-full bg-indigo-100 px-1.5 py-0.5 text-[10px] font-bold text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300">
           {catCount > 99 ? '99+' : catCount}
         </span>
       )}
       ```
       *(Lưu ý: Không dùng toán tử ternary `? :` theo quy ước của dự án, dùng `if` block chuẩn)*:
       ```tsx
       let catBadgeText = String(catCount)
       if (catCount > 99) {
         catBadgeText = '99+'
       }
       // JSX:
       {catCount > 0 && (
         <span className="ml-1.5 inline-flex items-center justify-center rounded-full bg-indigo-100 px-1.5 py-0.5 text-[10px] font-bold text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300">
           {catBadgeText}
         </span>
       )}
       ```
3. **Kiểm thử**:
   - Cập nhật `NotificationBell.test.tsx` giả lập mock data có `byCategory` và kiểm tra hiển thị đúng badge cho từng tab.
   - Chạy `pnpm lint`, `pnpm build`, `pnpm test --run`.

## 4. Ràng Buộc & Quy Ước (Constraints)
- Cấm tuyệt đối toán tử 3 ngôi (`? :`) ở cả Backend và Frontend.
- Giữ vững kiểm tra NullAway (NonNull / Nullable) ở Backend.
- Sử dụng trình quản lý gói `pnpm` trong `fashionstore-ui`.
