# Thông báo đa kênh (In-app Notifications) — spec 1.6.1

## Bối cảnh quan trọng nhất

`feature/seller-staff-2.8.1` **đã merge vào master** (cả 2 repo). Master giờ có sẵn nền notification cho lời mời nhân viên — tính năng này **mở rộng nó**, không xây lại:

- Backend: `V28__seller_staff_management.sql` tạo bảng `user_notification` (id, user_id, type TEXT, title, body, link, ref_id, read_at, created_at) + index `(user_id, read_at, created_at DESC)`. `features/notification/` phẳng: `NotificationService` (chỉ có `notifyInvitation` type `SHOP_INVITATION`, `deleteByRefId`, page/unreadCount/markRead/markAllRead), `NotificationRepository` (**JdbcClient, không entity**), `NotificationController` (GET list, GET `/unread-count`, PATCH `/{id}/read`, PATCH `/read-all`).
- UI: `src/components/NotificationBell.tsx` (badge + panel dropdown, polling 30s, không tab, không xóa, không WebSocket), **đã gắn vào cả `Header.tsx` lẫn `SellerLayout.tsx`**. Link lời mời `/invitations/{token}` có route thật trên master. Test chuẩn trên master: `renderToString` + `vi.mock` (6 file `.test.tsx`, không jsdom, không testing-library).
- V28, V29 đã chiếm → migration mới là **V30**.

## Mục tiêu

Mở rộng thành thông báo đa kênh: đơn hàng (buyer), tương tác (buyer), bán hàng (seller), realtime qua WebSocket.

- Buyer: bell ở Header chung (đã có trên nhánh) — tab **Đơn hàng** + **Hoạt động**.
- Seller: bell trong `/shop` (Seller Center) — thông báo "có đơn mới", thay bell tĩnh placeholder hiện tại trong `SellerLayout`.

Ngoài phạm vi (chốt với chủ dự án):
- Tab "Hệ thống" (bảo trì, cập nhật chính sách), admin broadcast.
- Email / SMS / push.
- Thông báo chat mới / phản hồi đánh giá cho seller (trang chat seller đã có badge chưa đọc riêng).
- Feature Q&A riêng (không tồn tại; "shop trả lời câu hỏi" = phản hồi đánh giá + chat).
- Không đổi luồng lời mời nhân viên hiện có (chỉ gán category).

## Cách làm

Chủ dự án chọn **gọi service trực tiếp** (trong 3 cách đã trình bày): `OrderService`, `ReviewService`, `ChatService` gọi `NotificationService` ngay trong method nghiệp vụ; insert cùng transaction với thay đổi nghiệp vụ; realtime đẩy qua `NotificationRealtimePublisher` (copy pattern `ChatRealtimePublisher`: HTTP là nguồn chân lý, WS là lớp phủ, lỗi gửi nuốt + log warn).

## Dữ liệu — `V30__notification_categories.sql`

```sql
ALTER TABLE user_notification ADD COLUMN category VARCHAR(12) NOT NULL DEFAULT 'ACTIVITY';
ALTER TABLE user_notification ADD CONSTRAINT chk_notification_category
    CHECK (category IN ('ORDER', 'ACTIVITY', 'SELLING'));
CREATE INDEX idx_notification_unread ON user_notification (user_id) WHERE read_at IS NULL;
```

- Dòng `SHOP_INVITATION` cũ nhận `ACTIVITY` nhờ DEFAULT — luồng lời mời không đổi.
- 3 category: `ORDER` (đơn của buyer), `ACTIVITY` (tương tác cho buyer, gồm lời mời), `SELLING` (bán hàng cho seller) — tách luồng seller khỏi Header theo placement đã chốt.
- `category` có CHECK; `type` giữ TEXT không CHECK (loại sự kiện mở rộng, không phải máy trạng thái).
- title/body là nội dung end-user tiếng Việt (khác luật "exception tiếng Anh" — luật đó dành cho lỗi).

## Backend — mở rộng `features/notification/` hiện có

Theo pattern JdbcClient của nhánh (không thêm entity Data JDBC). Thêm `NotificationCategory` enum; type là hằng chuỗi: `ORDER_PLACED`, `ORDER_CONFIRMED`, `ORDER_SHIPPED`, `ORDER_DELIVERED`, `ORDER_CANCELLED`, `REVIEW_REPLIED`, `CHAT_MESSAGE` (`SHOP_INVITATION` giữ nguyên).

`infrastructure/realtime/NotificationRealtimePublisher` (mới): `SimpMessagingTemplate.convertAndSendToUser(recipientId, "/queue/notifications", payload)`, nuốt lỗi như `ChatRealtimePublisher`.

Service thêm `create(recipients, category, type, title, body, link, refId?)`: insert từng recipient rồi push realtime (per recipient). Các method list/unread/read-all nhận filter categories.

`NotificationResponse` thêm field `category` (tương thích ngược — field mới).

### Loại thông báo

| type | category | recipient | title (ví dụ) | link |
|---|---|---|---|---|
| `ORDER_PLACED` | SELLING | member của shop | "Có đơn hàng mới #<mã>" | `/shop?tab=orders` |
| `ORDER_CANCELLED` (buyer hủy) | SELLING | member của shop | "Đơn #<mã> đã bị người mua hủy" | `/shop?tab=orders` |
| `ORDER_CONFIRMED` | ORDER | buyer | "Đơn #<mã> đã được shop xác nhận" | `/account` |
| `ORDER_SHIPPED` | ORDER | buyer | "Đơn #<mã> đang được giao" | `/account` |
| `ORDER_DELIVERED` | ORDER | buyer | "Đơn #<mã> đã được giao" | `/account` |
| `ORDER_CANCELLED` (seller/admin hủy) | ORDER | buyer | "Đơn #<mã> đã bị hủy" | `/account` |
| `REVIEW_REPLIED` | ACTIVITY | tác giả review | "Shop đã phản hồi đánh giá của bạn" | `/product/{productId}` |
| `CHAT_MESSAGE` | ACTIVITY | buyer (khi shop nhắn) | "Tin nhắn mới từ <shop>" | null — client mở widget chat |
| `SHOP_INVITATION` (hiện có) | ACTIVITY | người được mời | "Lời mời tham gia shop …" | `/invitations/{token}` |

- Body: mô tả ngắn; nội dung chat/review lưu nguyên văn — panel đang dùng CSS `line-clamp` nên không cần cắt tay.
- Member của shop resolve qua service domain shop (thêm method trả user id các member nếu chưa có — service gọi service, không gọi repository chéo).
- `CHAT_MESSAGE` chỉ tạo cho buyer khi phía shop gửi (kể cả auto-reply); buyer gửi thì không tạo thông báo cho seller.
- Link = đường dẫn nội bộ backend sinh, không nhận URL từ client.
- **Lời mời nhân viên realtime (yêu cầu thêm)**: `notifyInvitation` chuyển qua cùng đường `create(...)` — insert xong push realtime tới người được mời; user đang mở web thấy lời mời ngay không đợi polling 30s. Hành vi cũ của lời mời (row, title, link `/invitations/{token}`, xóa theo `refId` khi rút lời mời) giữ nguyên.

### API

| Method | Path | Mô tả |
|---|---|---|
| GET | `/api/notifications?categories=&page=&size=` | Thêm param `categories` (comma-separated, bỏ trống = tất cả — tương thích cũ) |
| GET | `/api/notifications/unread-count?categories=` | Thêm param `categories` — badge riêng cho từng bell; endpoint cũ không param vẫn chạy |
| PATCH | `/api/notifications/{id}/read` | Giữ nguyên |
| PATCH | `/api/notifications/read-all?categories=` | Thêm param `categories` (bỏ trống = tất cả) |
| DELETE | `/api/notifications/{id}` | **Mới** — xóa 1 thông báo của mình (id người khác → 404) |

Không thêm endpoint summary/tab-badge riêng — bell badge dùng `unread-count` + `categories`. Đủ annotation springdoc, bearerAuth, lỗi RFC 9457.

### Điểm móc (cùng transaction với nghiệp vụ)

- `OrderService.checkout` → `ORDER_PLACED` cho member shop.
- `OrderService.cancel` (buyer hủy) → `ORDER_CANCELLED` cho member shop.
- `OrderService.confirm` / `ship` / `deliver` → buyer.
- `OrderService.sellerCancel` + `cancelByAdmin` → buyer.
- `ReviewService.reply` → tác giả review.
- `ChatService.sendMessage` + auto-reply → buyer.
- `ShopStaffService.inviteStaff` → lời mời (nền có sẵn trên master) — giờ kèm realtime push qua publisher chung.

Realtime: insert xong push `NotificationResponse` đến recipient; client nhận sự kiện → invalidate query, không tự bóp state.

## Frontend

- **`NotificationBell.tsx`** (mở rộng component hiện có trên master), thêm prop `categories?: Array<'ORDER' | 'ACTIVITY' | 'SELLING'>` — không truyền = hành vi cũ (tất cả, không tab):
  - Khi có `categories`: tab trong panel ("Đơn hàng" / "Hoạt động" / "Bán hàng"); chọn tab đổi param `categories` của query (server filter, invalidate khi đổi tab); badge bell đếm unread trên tất cả categories được cấp.
  - Thêm nút **Xóa** từng item (mutation DELETE + invalidate) — cả ở chế độ không tab.
  - "Đã đọc tất cả" scope theo categories của bell/tab đang mở.
  - **Realtime**: `useWebSocket('/user/queue/notifications')` → invalidate unread-count + list. Giữ `refetchInterval: 30000` sẵn có làm backstop (che trường hợp mất kết nối WS).
  - Click item: mark read rồi navigate `link` nếu có; `CHAT_MESSAGE` không link → dispatch CustomEvent (`fashionstore:open-chat`) mở BuyerChatWidget.
- **Header**: `<NotificationBell categories={['ORDER','ACTIVITY']} />` (component đã gắn sẵn trên master, chỉ thêm prop).
- **SellerLayout**: `<NotificationBell categories={['SELLING']} />` (component đã gắn sẵn trên master, chỉ thêm prop); `/shop?tab=orders` đã được dashboard đọc từ URL nên link chạy.
- **BuyerChatWidget**: nghe CustomEvent trên để mở panel.
- API client: cập nhật `docs/openapi.json` từ backend rồi `pnpm codegen`.
- **Test theo chuẩn master** (renderToString + `vi.mock` generated hooks, không thêm devDeps — 5 file `.test.tsx` đang theo kiểu này): `NotificationBell.test.tsx` — badge hiện/ẩn theo unread, tab render đúng categories, panel rỗng, item có/xóa.

## Test backend (TDD)

- Controller slice (`@WebMvcTest` + mock service) cho các endpoint, đủ happy + 404 + filter categories.
- Repository/service test Testcontainers: category filter, unread theo category, read-all scope, delete; regression luồng lời mời (`notifyInvitation` → category ACTIVITY, panel cũ vẫn đọc được) + assert lời mời được push realtime (mock publisher).
- Hook test: test `OrderService`/`ReviewService`/`ChatService` hiện có thêm assert hàng `user_notification` đúng recipient/category.
- Publisher: unit test mock `SimpMessagingTemplate` (destination + recipient đúng, lỗi không ném ra).

## Nhánh

- Cả 2 repo: tạo `feature/notifications-1.6.1` từ `master` (đã chứa seller-staff 2.8.1: bảng `user_notification`, API notification, `NotificationBell`, V28/V29). Design doc commit trên nhánh backend.

## Quyết định do tôi chốt (check giúp khi đọc)

1. Mở rộng nền notification có sẵn trên master (sau merge seller-staff 2.8.1); migration mới đánh **V30**.
2. Reuse bảng `user_notification` + pattern JdbcClient (không thêm entity Data JDBC) — đúng pattern hiện có.
3. 3 category `ORDER`/`ACTIVITY`/`SELLING` — tách luồng seller khỏi Header; lời mời thuộc `ACTIVITY`.
4. `type` TEXT không CHECK — thêm loại mới không cần migration.
5. Không endpoint summary riêng; badge qua `unread-count` + `categories`. Tab trong panel không có badge số riêng.
6. 1 notification mỗi tin nhắn chat, không coalesce — volume nhỏ; cần thì thêm sau.
7. Tiêu đề/nội dung backend sinh tiếng Việt (nội dung end-user).
8. Click thông báo chat mở BuyerChatWidget qua CustomEvent (không có route chat).
9. Link thông báo đơn hàng của buyer về `/account` (trang tồn tại; chưa có anchor/tab đơn hàng — nâng lên khi tab Đơn hàng ra đời).
10. Giữ polling 30s làm backstop bên cạnh WebSocket invalidate.
