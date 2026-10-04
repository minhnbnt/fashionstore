# Spec: Storefront public cho FashionStore

Ngày: 2026-10-05. Trạng thái: chờ review.

## 1. Bối cảnh

Iteration 1 xong: admin CRUD sau login (`/`, `/products/new`, `/products/:id/edit`) + placeholder
(`/products/:id`, `/cart`, `/orders`, `/account`). Trông "thiếu" vì chưa có mặt tiền cho khách.

Đã khảo sát thật bằng Chromium: adidas VN (home + PLP `/nam` + PDP Samba OG) và uniqlo US
(home + PLP tops). Pattern rút ra: header 3 tầng, hero full-bleed, tiles danh mục, carousel bán chạy,
PLP có lọc/sắp xếp + badge, PDP buy-box (màu/size/thêm giỏ), footer nhiều cột.
Không học theo: login-wall chặn PDP của adidas.

## 2. Quyết định đã chốt với user

- Route A: `/` thành storefront public, CRUD admin dời vào `/admin/*` (protected).
- Approach A: mặt tiền đầy đủ, data còn thiếu (category, badge, rating) suy ở frontend, không đụng backend.
- Giữ nguyên font (Cormorant + Montserrat) và token màu stone/gold trong `src/index.css`.
  Học đối thủ về bố cục + interaction, không copy visual đen-trắng-thô của adidas.

## 3. Kiến trúc route

| Route | Trước | Sau |
|---|---|---|
| `/` | ProductList admin (protected) | StorefrontHome (public) |
| `/products/:id` | ComingSoon | ProductDetail PDP (public) |
| `/cart` | ComingSoon | CartPage (public) |
| `/login` | public | giữ nguyên |
| `/admin` | chưa có | ProductList admin dời từ `/` (protected) |
| `/admin/products/new`, `/admin/products/:id/edit` | `/products/new`, `/products/:id/edit` | dời vào, giữ nguyên logic form |
| `/orders`, `/account` | ComingSoon | giữ ComingSoon |

Hai layout: `StorefrontLayout` (promo bar + header shop + footer đầy đủ, public) và `AdminLayout`
(header gọn hiện tại + RequireAuth). `RequireAuth` chỉ bọc nhánh `/admin/*`.
Lazy loading per route giữ nguyên như `routes.tsx` hiện tại.

## 4. Components

### 4.1 StorefrontLayout
- `PromoBar`: thanh đen mỏng, chữ gold: "Miễn phí vận chuyển đơn từ 500K".
- `StorefrontHeader` (sticky): logo Cormorant | nav anchor (Áo, Váy, Khoác, Phụ kiện — lọc lưới home)
  | search + account + giỏ (badge số lượng từ `useCart`). Hover gold.
- `StorefrontFooter`: 4 cột (Mua sắm / Hỗ trợ / Về FashionStore / Liên hệ + social) + copyright.
  Thay footer một-dòng hiện tại (chỉ áp dụng layout storefront).

### 4.2 Home (`/`)
1. `Hero`: ảnh Pexels full-bleed + headline Cormorant + 2 CTA ("Mua ngay" scroll tới lưới, "Khám phá").
2. `CategoryTiles`: 4 ô có ảnh (Áo / Váy / Khoác / Phụ kiện), click lọc lưới.
3. `BestsellerCarousel`: scroll-snap + nút prev/next, card có badge + tim wishlist (localStorage).
4. `CollectionBanner`: 1 ảnh editorial lớn + CTA.
5. `ProductGrid` + toolbar: ô tìm kiếm (tên), select sắp xếp (mới nhất / giá tăng / giá giảm),
   lọc khoảng giá, đếm số item kiểu uniqlo ("N sản phẩm").
6. `Commitments`: đổi trả 30 ngày / giao hàng toàn quốc / hàng chính hãng (thay khối SEO text).

### 4.3 PDP (`/products/:id`)
- Breadcrumb (Trang chủ / Danh mục / Tên SP). Trái: ảnh lớn (giữ 1 ảnh, khung sẵn cho nhiều ảnh sau).
- Phải buy-box: tên Cormorant, giá `formatVnd`, mô tả, chọn số lượng, nút "Thêm vào giỏ" (gold, to),
  wishlist. Khách xem thoải mái, không login-wall.
- Accordion shadcn: Chi tiết / Giao hàng & đổi trả.
- `RelatedCarousel`: cùng danh mục suy ra, trừ SP hiện tại. 404 nếu id không tồn tại (tái dùng NotFound).

### 4.4 Giỏ hàng
- `CartDrawer` (shadcn `Sheet`): mở từ header, dòng SP + tăng/giảm/xoá + tổng + link sang `/cart`.
- `CartPage`: đầy đủ hơn, nút "Đặt hàng" → toast "Thanh toán ra mắt ở iteration sau".
- State: hook mới `useCart` + localStorage (`fashionstore:cart`), không gọi backend.
  Phiên bản này chưa trừ tồn kho, chưa login cũng mua được (giỏ guest).

### 4.5 Admin
- Dời nguyên `ProductList`, `ProductForm` vào `/admin/*`, giữ toàn bộ logic + test.
  Bỏ nút Sửa/Xoá khỏi mọi card public (hiện card public là component mới, không tái dùng ProductCard admin).

## 5. Data frontend-only (ghi rõ là tạm)

- `categoryOf(product)`: suy từ keyword tên (`áo`, `váy`, `khoác`, `túi`…), fallback "Khác".
  Khi backend có field `category` thật thì thay một chỗ trong `src/lib/catalog.ts`.
- Badge "Mới": `createdAt` trong 30 ngày. "Bán chạy": thứ tự mock ban đầu (ghi chú trong code).
- Rating/review: chưa có backend → PDP/Carousel không hiện sao số, chỉ giữ khung accordion Đánh giá
  với text "Chưa có đánh giá".
- Wishlist: localStorage, tim trên card (học adidas/uniqlo).

## 6. shadcn cần thêm

`pnpm dlx shadcn@latest add sheet badge accordion select -y`.
Carousel tự làm bằng CSS scroll-snap, không thêm dep. Sau khi sửa code chạy `pnpm lint`,
bật rule nào vi phạm thì fix (không tắt rule để cho qua).

## 7. Files chạm tới (dự kiến)

- Mới: `src/components/storefront/*` (PromoBar, StorefrontHeader, StorefrontFooter, Hero,
  CategoryTiles, BestsellerCarousel, CollectionBanner, Commitments, CartDrawer),
  `src/pages/StorefrontHome.tsx`, `src/pages/ProductDetail.tsx`, `src/pages/CartPage.tsx`,
  `src/hooks/useCart.ts`, `src/lib/catalog.ts` (+ test), `src/components/AdminLayout.tsx`.
- Sửa: `src/routes.tsx` (chia 2 layout, dời admin), `src/components/Layout.tsx` (gọn thành admin
  hoặc đổi tên), `src/pages/ComingSoon.tsx` (link về `/` storefront thay vì list admin).
- Test theo pattern cũ (mock mode + RTL + sonner Toaster khi assert toast).

## 8. Acceptance

- Khách chưa login xem được `/`, PDP, giỏ; vào `/admin/*` bị đá về `/login`, login xong quay lại.
- `pnpm test` 100% pass (thêm test `useCart`, catalog derivation, PDP 404), `pnpm lint` 0 error,
  `pnpm build` xanh.
- Soi lại Chromium 375/768/1024/1440 (home, PDP, cart drawer + page) như Task 9.

## 9. Ngoài phạm vi

- Filter facet đầy đủ (size/màu thật), variant SP, tồn kho: cần backend, để sau.
- Thanh toán, đơn hàng thật (`/orders` vẫn ComingSoon), email verify, phân quyền admin.
- SEO text dài kiểu adidas, đa ngôn ngữ.
