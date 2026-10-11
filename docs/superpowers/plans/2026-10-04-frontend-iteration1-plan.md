# Plan — Frontend Iteration 1 (FashionStore UI)

Spec nguồn: `docs/issues/iteration-1.md` (được user duyệt trong hội thoại ngày 2026-10-04).
Quyết định user: phạm vi = iteration 1 + khung mở rộng; stack = Vite + React TS + Tailwind + shadcn/ui;
style/palette/typography do ui-ux-pro-max `--design-system` chọn; **chỉ làm frontend** (backend CRUD do user tự lo).
Thư mục: `fashionstore-ui/` (đã có sẵn, trống) — lệch với issue ghi `frontend/` → sửa issue cho khớp.

## Design Direction (kết quả ui-ux-pro-max)

- Palette: Primary `#1C1917`, Accent/CTA `#A16207`, Background `#FAFAF9`, Card `#FFFFFF`,
  Border `#D6D3D1`, Muted `#E8ECF0` / fg `#475569`, Destructive `#DC2626`.
- Typography: Cormorant (heading) + Montserrat (body/UI).
- Style: Minimalism & Swiss (Liquid Glass bị loại — hướng Apple-platform, a11y rủi ro).
- Icons: lucide-react. Motion: 200–250ms, focus ring rõ, tôn trọng `prefers-reduced-motion`.

## Global Constraints

- Package manager: **pnpm**. Không dùng npm/yarn.
- Port dev: **3000** (khớp `GOTRUE_SITE_URL` + CORS Garage).
- ENV: `VITE_GOTRUE_URL=http://localhost:9999`, `VITE_API_URL=http://localhost:8080`,
  `VITE_ASSETS_URL=http://fashionstore.web.garage.localhost:3902`.
- Giá VND số nguyên, format `vi-VN`. Không dùng ternary (quy ước AGENTS.md). Theo style xung quanh.
- Ảnh mock: dùng URL Pexels trực tiếp (đã user duyệt), không tải file về repo.
- Backend chưa có CRUD → `lib/api.ts` phải có chế độ MOCK để UI chạy được mà không cần API.

## Tasks

### Task 1: Scaffold Vite + Tailwind + shadcn + router
- Tạo app Vite react-ts trong `fashionstore-ui/`, pnpm install.
- Tailwind v4 + shadcn/ui init, thêm button, card, dialog, form, input, label, sonner (toast).
- react-router; `.env` + `.env.example`; `.gitignore` cục bộ (node_modules, dist, .env).
- Expected: `pnpm build` thành công; `pnpm dev` chạy ở :3000.

### Task 2: Persist design system (ui-ux-pro-max)
- `search.py "fashion e-commerce elegant modern store" --design-system --persist -p "FashionStore" --output-dir fashionstore-ui`
- Expected: `fashionstore-ui/design-system/fashionstore/MASTER.md` tồn tại; token palette khớp Design Direction.

### Task 3: lib/ (auth, api, format) — TDD
- `lib/format.ts`: `formatVnd(number) -> "1.200.000 ₫"` (Intl vi-VN).
- `lib/auth.ts`: supabase client (GoTrue, apikey placeholder), `useSession` hook (loading/session/refresh).
- `lib/api.ts`: `apiFetch(path, opts)` gắn Bearer token; bắt 401 → signOut + redirect `/login?from=...`.
  Chế độ MOCK: khi `VITE_API_URL` trống hoặc `VITE_USE_MOCK=1` → trả mock products (ảnh Pexels).
- Test: Vitest + RTL cho format + api (mock fetch: 200 pass-through, 401 signOut, mock mode).

### Task 4: Layout + RequireAuth + khung mở rộng
- `components/RequireAuth` (chưa login → Navigate /login, giữ `from`).
- Layout: header (logo chữ Cormorant, nút logout), footer tối giản.
- Routes placeholder "coming soon" đúng design system: `/products/:id`, `/cart`, `/orders`, `/account`; `/404` NotFound.
- Expected: build pass; route bảo vệ redirect đúng.

### Task 5: Trang /login
- 1 form đăng nhập + đăng ký (toggle mode), email/password, inline validation, lỗi hiện rõ.
- Đăng ký xong có session ngay (GOTRUE_MAILER_AUTOCONFIRM=true).
- Search trước khi code: `"accessible authentication" --domain ux`, `"error summary validation" --domain ux`.

### Task 6: Trang danh sách sản phẩm `/`
- Grid card: ảnh (lazy), tên, giá VND, nút sửa/xoá; nút "Thêm sản phẩm".
- Loading skeleton + empty state + error state (401 → /login).
- Search: `"card grid image lazy" --domain ux`, `"loading skeleton" --domain ux`.

### Task 7: Form sản phẩm + upload ảnh
- `/products/new`, `/products/:id/edit` dùng chung `ProductForm`.
- Chọn file (image/*, ≤5MB — client check khớp rule backend) + preview.
- Flow: presign (`POST /api/uploads/presign`) → PUT Garage → lấy key → payload.
- Mock mode: bỏ qua upload, dùng URL Pexels trực tiếp làm `image_url`.

### Task 8: Xoá + confirm + xử lý lỗi API
- Xoá qua AlertDialog confirm; toast thành công/thất báo.
- Mọi lỗi API hiển thị cho user (sonner); 401 đưa về /login.
- Search: `"keyboard focus modal" --domain ux`, `"destructive confirmation" --domain ux`.

### Task 9: Checklist + responsive test + tổng kết
- Contrast ≥ 4.5:1, focus visible, reduced-motion; test 375/768/1024/1440 (chromium-devtools).
- Cập nhật `docs/issues/iteration-1.md`: `frontend/` → `fashionstore-ui/`.
- `pnpm build` + `pnpm test` xanh toàn bộ.

## Review Focus

- api.ts: 401 handling có loop refresh vô hạn không; mock mode có rò rỉ vào prod không.
- Auth flow: session restore khi refresh; redirect `from` có mất query không.
- A11y: dialog focus trap, label form, alt ảnh, icon button có accessible name.
- AGENTS.md: không ternary, một statement một dòng.
