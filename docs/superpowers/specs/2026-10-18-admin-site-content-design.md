# Module Quản trị — Banner, popup, trang tĩnh, FAQ

## Mục tiêu

Admin tự đổi nội dung marketing + thông tin của sàn mà không cần deploy:

1. **Banner** theo vị trí cố định trên trang chủ, có lịch chạy (bắt đầu/kết thúc), thứ tự, bật/tắt.
2. **Popup** khuyến mãi trên trang chủ: ảnh + nút CTA, đối tượng (khách/thành viên), tần suất hiện, độ trễ, ưu tiên, lịch chạy. Mỗi lượt xem chỉ hiện **tối đa 1 popup**.
3. **Trang tĩnh** (Giới thiệu, Chính sách đổi trả/bảo mật/vận chuyển, Điều khoản…): slug duy nhất, nội dung Markdown, nháp/xuất bản, SEO description, nhóm hiển thị ở footer.
4. **FAQ** dạng hỏi–đáp có cấu trúc theo nhóm, trang `/faq` dạng accordion.

Tham khảo: OpenCart banner (title, link, image, sort order theo vị trí), Salesforce Commerce Cloud content slot (lịch + thứ hạng), Klaviyo popup (đối tượng, tần suất lưu phía trình duyệt, priority → chỉ 1 popup thắng), Shopify/Adobe Commerce CMS page (URL key duy nhất, enable flag, meta description, không versioning), OpenCart Information (cờ hiện ở footer + sort order), FAQ dạng Q&A có nhóm thay vì 1 trang HTML dài.

Ngoài phạm vi: phân khúc khách hàng, A/B / xoay vòng ngẫu nhiên, đa ngôn ngữ, versioning nội dung, đếm lượt hiển thị/click, nhắm popup theo URL (chỉ trang chủ), exit-intent, redirect khi đổi slug, kéo-thả sắp xếp (nhập số thứ tự).

## Luật chung: lịch chạy (banner + popup)

Hiển thị khi `active AND (starts_at IS NULL OR starts_at <= now) AND (ends_at IS NULL OR ends_at > now)`.
Admin thấy trạng thái tính sẵn `scheduleState`:

| Giá trị | Điều kiện |
|---|---|
| `INACTIVE` | `active = false` |
| `SCHEDULED` | active, `starts_at > now` |
| `EXPIRED` | active, `ends_at <= now` |
| `RUNNING` | còn lại |

`ends_at` phải sau `starts_at` (`400`). Link chỉ nhận đường dẫn nội bộ bắt đầu bằng `/` (không phải `//`) hoặc URL `http(s)://` — chặn `javascript:`.

## Banner

| Vị trí (`BannerPlacement`) | Hiển thị |
|---|---|
| `HOME_CAROUSEL` | Carousel ngay dưới hero trang chủ, tỉ lệ 3:1 (mobile 4:3), tự chuyển |
| `HOME_STRIP` | Dải banner giữa trang chủ (sau lưới sản phẩm), tỉ lệ 4:1, xếp lưới |

Trường: `placement`, `title` (tên nội bộ + alt ảnh), `imageKey`, `mobileImageKey?` (thiếu thì dùng ảnh desktop), `linkUrl?`, `sortOrder` (nhỏ lên trước, hòa thì mới tạo trước), `active`, `startsAt?`, `endsAt?`.

## Popup

Trường: `name` (nội bộ), `title?`, `body?` (≤ 500), `imageKey?`, `ctaLabel?`, `linkUrl?`, `audience`, `frequency`, `delaySeconds` (0–60), `priority` (lớn thắng), `active`, `startsAt?`, `endsAt?`. Bắt buộc có ảnh hoặc tiêu đề (`400`).

- `audience`: `ALL` / `GUEST` (chưa đăng nhập) / `MEMBER` (đã đăng nhập).
- `frequency`: `EVERY_VISIT` (mỗi lần vào trang chủ) / `ONCE_PER_SESSION` / `ONCE_PER_DAY` / `ONCE` (một lần duy nhất trên trình duyệt). Như Klaviyo, đếm ở trình duyệt (`localStorage`/`sessionStorage`), xóa dữ liệu trình duyệt là reset.
- Backend trả mọi popup đang chạy theo `priority DESC, updated_at DESC`; frontend chọn popup **đầu tiên** khớp đối tượng và chưa vượt tần suất, chờ `delaySeconds` rồi hiện. Đóng hoặc bấm CTA đều tính là "đã xem".

## Trang tĩnh

Trường: `slug` (kebab-case `^[a-z0-9]+(-[a-z0-9]+)*$`, ≤ 80, duy nhất → `409`), `title` (≤ 150), `content` (Markdown ≤ 50 000), `metaDescription?` (≤ 160), `footerGroup` (`ABOUT` "Về nét" / `POLICY` "Chính sách" / `SUPPORT` "Hỗ trợ" / `NONE` không hiện footer), `sortOrder`, `status` (`DRAFT` / `PUBLISHED`), `publishedAt`, `updatedBy`.

- Nội dung lưu **Markdown**, frontend render không cho HTML thô → không cần sanitize, không có XSS từ nội dung admin.
- URL public `/pages/{slug}` nên không đụng route app, không cần danh sách slug cấm.
- Xuất bản/gỡ xuất bản bằng endpoint riêng. Chỉ xóa được trang `DRAFT` (`409`), tránh làm gãy link đang chạy.
- Migration seed sẵn 5 trang đã xuất bản (Giới thiệu, Chính sách đổi trả, Chính sách bảo mật, Chính sách vận chuyển, Điều khoản sử dụng) + vài FAQ mẫu để footer có link ngay, admin sửa lại nội dung.

## FAQ

Trường: `category` (`ORDER` Đặt hàng, `PAYMENT` Thanh toán, `SHIPPING` Vận chuyển, `RETURN` Đổi trả & hoàn tiền, `ACCOUNT` Tài khoản, `OTHER` Khác — enum cố định, đủ cho sàn quần áo, khỏi bảng danh mục), `question` (≤ 300), `answer` (Markdown ≤ 5 000), `sortOrder`, `published`.

## API

Admin (`hasRole('ADMIN')`, không phân trang vì số bản ghi nhỏ):

| Method | Path | Mô tả |
|---|---|---|
| GET/POST | `/api/admin/banners?placement=` | Danh sách theo vị trí rồi thứ tự / tạo |
| PUT/DELETE | `/api/admin/banners/{id}` | Sửa toàn bộ / xóa |
| GET/POST | `/api/admin/popups` | Danh sách theo ưu tiên / tạo |
| PUT/DELETE | `/api/admin/popups/{id}` | Sửa / xóa |
| GET/POST | `/api/admin/pages` | Danh sách (không kèm nội dung) / tạo nháp |
| GET/PUT/DELETE | `/api/admin/pages/{id}` | Chi tiết kèm nội dung / sửa / xóa (chỉ `DRAFT`) |
| PATCH | `/api/admin/pages/{id}/publish`, `/unpublish` | Đổi trạng thái |
| GET/POST | `/api/admin/faqs` | Danh sách theo nhóm rồi thứ tự / tạo |
| PUT/DELETE | `/api/admin/faqs/{id}` | Sửa / xóa |

Public (`/api/public/**`, không cần đăng nhập):

| Method | Path | Mô tả |
|---|---|---|
| GET | `/api/public/banners?placement=` | Banner đang chạy, đã sắp xếp |
| GET | `/api/public/popups` | Popup đang chạy theo ưu tiên |
| GET | `/api/public/pages` | Trang đã xuất bản (slug, title, nhóm footer) cho footer |
| GET | `/api/public/pages/{slug}` | Nội dung trang đã xuất bản, nháp → `404` |
| GET | `/api/public/faqs` | FAQ đã xuất bản theo nhóm rồi thứ tự |

Ảnh: dùng lại `POST /api/uploads/presign` → PUT lên S3 → lưu key.

## Code

- Migration `V12__site_content.sql`: `site_banner`, `site_popup`, `site_page`, `faq_item` + seed. Module giám sát đơn được merge sau ở `V15`.
- `model/`: `SiteBanner`, `SitePopup`, `SitePage`, `FaqItem` + enum; luật lịch chạy dùng chung ở `ContentSchedule`.
- `features/content/`: service + repository + `PublicContentController`. Admin controller ở `features/admin/` gọi thẳng service content (không có điều phối liên domain nên không thêm lớp `Admin*Service` chuyển tiếp).

## UI (`fashionstore-ui`)

- `/admin/content` (tab Banner / Popup / Trang tĩnh / FAQ), thêm mục "Nội dung" vào thanh điều hướng admin.
- Banner/popup: danh sách có ảnh thu nhỏ + nhãn trạng thái lịch, form tạo/sửa trong hộp thoại (upload ảnh, lịch, bật/tắt).
- Trang tĩnh: danh sách + trình soạn Markdown có tab Xem trước, nút Xuất bản/Gỡ xuất bản.
- FAQ: danh sách theo nhóm, form hộp thoại.
- Storefront: carousel + dải banner trên trang chủ, popup trang chủ, route `/pages/:slug`, route `/faq` (accordion + ô lọc), footer lấy link trang theo nhóm + link FAQ.
