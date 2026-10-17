# Iteration 1 — Trang danh sách sản phẩm & Đăng nhập

## Mục tiêu

Iteration đầu tiên, giữ đơn giản: **đăng nhập** và **CRUD sản phẩm** kèm trang danh sách sản phẩm.

- Đăng nhập/đăng ký email + password qua **GoTrue** (self-host, dùng client `supabase/auth-js`).
- CRUD sản phẩm qua **Spring Boot REST** (JWT resource server đã cấu hình sẵn).
- Ảnh sản phẩm upload **thẳng từ browser lên S3 (versitygw)**, không truyền qua backend.

## Giả định (sửa lại issue nếu sai)

- Frontend: **Vite + React** nằm ở repo riêng **`fashionstore-ui/`** (git repo độc lập, không track trong repo này), dev server chạy ở cổng **3000** (khớp `GOTRUE_SITE_URL` trong `config/gotrue.env`), routing bằng `react-router`.
- Bất kỳ user nào đã đăng nhập đều được CRUD sản phẩm; phân quyền admin để iteration sau.
- Mỗi sản phẩm có **tối đa 1 ảnh tuỳ chọn**.
- `price` là số nguyên (VND, không có số lẻ).
- Spring Boot chạy ở cổng mặc định **8080**.

## Những gì phải đảm bảo (Acceptance Criteria)

### Đăng nhập

- [ ] Đăng ký + đăng nhập email/password qua GoTrue `http://localhost:9999` bằng `@supabase/supabase-js` (auth-js). GoTrue đang bật `GOTRUE_MAILER_AUTOCONFIRM=true` nên đăng ký xong có session ngay, không cần vòng verify email.
- [ ] Session còn đó sau khi refresh trang; access token được tự refresh trước khi hết hạn (`GOTRUE_JWT_EXP=3600`).
- [ ] Truy cập trang quản lý khi chưa đăng nhập → redirect về `/login`; đăng nhập xong quay lại trang cũ.
- [ ] Logout xoá sạch session ở client.

### Backend (CRUD sản phẩm)

- [ ] Flyway migration tạo bảng `products` trong schema `public` — **không đụng gì vào schema `auth`** của GoTrue.
- [ ] API đầy đủ: `GET /api/products`, `POST /api/products`, `GET/PUT/DELETE /api/products/{id}`.
- [ ] Mọi route `/api/products*` yêu cầu Bearer JWT hợp lệ (issuer `http://localhost:9999`, ES256 — resource server đã đọc issuer-uri sẵn). Không token / token sai → `401`.
- [ ] Validation tối thiểu: `name` không rỗng (≤ 200 ký tự), `price >= 0`. Sai → `400` kèm thông báo rõ ràng.
- [ ] Xoá sản phẩm không xoá object ảnh cũ trên S3 (chỉ bỏ tham chiếu — dọn rác để sau).

### Upload ảnh client-side

- [ ] Backend cấp **presigned PUT URL** (chỉ sinh URL, không nhận file). Secret key S3 **không bao giờ** xuất hiện ở browser.
- [ ] Chỉ chấp nhận `Content-Type` loại `image/*`, dung lượng ≤ 5MB; sai → từ chối cấp URL.
- [ ] Bucket `fashionstore` cho phép CORS `PUT` từ origin `http://localhost:3000` (versitygw: `VGW_CORS_ALLOW_ORIGIN` mặc định `*`, siết bằng PutBucketCors khi cần) — không CORS thì browser không PUT nổi.
- [ ] Chỉ lưu `image_key` vào DB **sau khi** PUT lên S3 trả về 200. PUT fail → báo lỗi, không tạo sản phẩm với ảnh hỏng.
- [ ] Ảnh hiển thị được công khai qua `http://localhost:3900/fashionstore/<key>` (bucket policy anonymous `s3:GetObject` — `s3-init` tự gắn khi `compose up`).
- [ ] Key do backend sinh (UUID + extension gốc), client không tự đặt key.

### Frontend

- [ ] Trang `/`: danh sách sản phẩm (ảnh, tên, giá), nút thêm/sửa/xoá.
- [ ] Form tạo/sửa sản phẩm có ô chọn file ảnh + preview.
- [ ] Xoá phải có confirm.
- [ ] Lỗi API (đặc biệt `401` hết phiên) hiển thị cho người dùng và đưa về `/login`.

## Chỉ dẫn thực hiện

### 1. Database

Migration `V2__products.sql` (Flyway):

```sql
CREATE TABLE products (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    price       BIGINT NOT NULL CHECK (price >= 0),   -- VND
    image_key   TEXT,                                  -- object key trên S3, nullable
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 2. Backend

- Thêm 1 dependency duy nhất: `software.amazon.awssdk:s3` — chỉ dùng `S3Presigner`, không thêm gì khác.
- Cấu hình mới trong `config/springboot.yml`:
  - `s3.endpoint=http://localhost:3900`, `s3.region=us-east-1` (bắt buộc `pathStyleAccess(true)`), `s3.bucket=fashionstore`, `s3.accessKey`/`s3.secretKey` (nằm trong `compose.yml` env của service `s3`).
- Endpoint cấp URL upload: `POST /api/uploads/presign` với body `{"contentType": "image/png"}` → trả `{"key": "...", "url": "..."}`. TTL presign 10 phút.
- Controller/service/repository cho products: DTO dùng `record`, entity dùng JPA như code hiện tại. CORS đã mở sẵn trong `SecurityConfig`, không đổi.
- Test: MockMvc test cho CRUD + validation (JWT test util có sẵn từ `spring-boot-starter-security-oauth2-resource-server-test`).

### 3. S3 (tự bootstrap khi `compose up`)

Service one-shot `s3-init` tự tạo bucket `fashionstore` + gắn policy anonymous `s3:GetObject` (`config/versitygw-init.sh`, idempotent). Cần chạy lại thì:

```bash
podman compose run --rm s3-init
```

### 4. Frontend (`fashionstore-ui/`, repo riêng — dùng **pnpm**)

- Scaffold: `pnpm create vite fashionstore-ui --template react-ts`. Trong `vite.config.ts` đặt `server.port = 3000` (khớp `GOTRUE_SITE_URL` và CORS).
- Env trong `fashionstore-ui/.env`: `VITE_GOTRUE_URL=http://localhost:9999`, `VITE_API_URL=http://localhost:8080`, `VITE_ASSETS_URL=http://localhost:3900/fashionstore` (Vite chỉ expose biến có prefix `VITE_`).
- Dependency: `@supabase/auth-js` (dùng `GoTrueClient`, **không** dùng `createClient` của `@supabase/supabase-js` — supabase-js ghép `/auth/v1` sai path với GoTrue bare) + `react-router` + `@tanstack/react-query` + shadcn/ui.
- Data-fetching gom trong `src/hooks/` (`useProducts`, `useProduct`, `useSaveProduct`, `useDeleteProduct`, `useAuth`); `src/lib/api.ts` giữ transport (Bearer + 401 → signOut + về `/login`) và mock mode (`VITE_USE_MOCK=1`).
- Routes: `/login` (đăng nhập + đăng ký cùng 1 form), `/` (danh sách), `/products/new`, `/products/:id/edit`, placeholder `/products/:id`, `/cart`, `/orders`, `/account`. Chưa đăng nhập mà vào route quản lý → `<Navigate to="/login">`.
- Mọi call API kèm `Authorization: Bearer <session.access_token>`; query/mutation qua hooks react-query (cache + invalidate `['products']`).
- Flow upload: chọn file → `POST /api/uploads/presign` → `PUT` presigned URL (đúng `Content-Type` đã ký, không gắn Bearer) → lấy `key` đưa vào payload tạo/sửa sản phẩm.
- URL ảnh = `${VITE_ASSETS_URL}/${image_key}` (absolute URL pass-through).

### 5. Kiểm tra end-to-end

```bash
podman compose up -d
./gradlew bootRun
cd fashionstore-ui && pnpm dev   # http://localhost:3000
```

1. `curl -i http://localhost:8080/api/products` → **401** (không token).
2. Đăng ký user mới → vào thẳng trang danh sách (mock mode: 6 sản phẩm mẫu; real mode: rỗng).
3. Tạo sản phẩm kèm ảnh → ảnh hiện trên list qua `localhost:3900/fashionstore`.
4. Responsive đã kiểm tra bằng Chromium ở 375 / 768 / 1024 / 1440 (`/`, `/login`, `/products/new`) + luồng xoá end-to-end (dialog → toast → list refresh).
5. Sửa tên/giá, thay ảnh → OK. Xoá sản phẩm → khỏi list.
6. Refresh trang giữa chừng → vẫn đăng nhập (token tự refresh).

## Ngoài phạm vi (để iteration sau)

- Phân quyền admin/vai trò, ai được CRUD gì.
- Phân trang, tìm kiếm, nhiều ảnh, thumbnail/resize.
- Email verification, đổi mật khẩu.
- Dọn object mồ côi trên S3 khi xoá/thay ảnh.
- Thu hẹp CORS `*` của backend, config production cho GoTrue/S3.
