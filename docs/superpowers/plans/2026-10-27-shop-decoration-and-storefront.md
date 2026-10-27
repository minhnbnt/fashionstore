# Trang Trí Gian Hàng (Shop Decoration) & Khách Hàng Xem Shop (Storefront) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hoàn thiện tính năng Trang trí gian hàng (Kênh Người Bán: Kéo-thả sắp xếp các block layout bằng native HTML5, upload banner slider tối đa 5 ảnh, bộ sưu tập nổi bật tự động New Arrivals / Best Seller / Sale Off) và Trải nghiệm Khách hàng xem Shop (Storefront `/shops/:slug`: Tìm kiếm sản phẩm nội bộ trong shop, load dữ liệu sản phẩm thật từ API, tự động ẩn block rỗng, thẻ Shop Card tại trang chi tiết sản phẩm).

**Architecture:**
- **Backend:** Xây dựng tầng truy vấn chuyên biệt `ShopCatalogQueryRepository` sử dụng `JdbcClient` để tải sản phẩm public của shop (`p.status = 'ACTIVE'`), hỗ trợ tìm kiếm từ khóa nội bộ `q` (`p.name ILIKE`), lọc bộ sưu tập thông minh (`NEW_ARRIVALS`, `BEST_SELLER` từ `order_item`, `SALE_OFF` có `compare_at_price > price`), lọc danh mục và phân trang. Cung cấp API `GET /api/shops/{shopId}/products` và `GET /api/shops/{shopId}/categories` qua `ShopCatalogController`.
- **Frontend:** Nâng cấp `SellerDecorationTab.tsx` với cơ chế kéo-thả layout native HTML5 (kèm vạch highlight tím indigo, nút bấm mũi tên dự phòng), loại bỏ hoàn toàn block Voucher. Tại storefront khách hàng `/shops/:slug`, tích hợp thanh tìm kiếm nội bộ, kết nối API sản phẩm thật vào `ShopDecorationRenderer` và `ShopProductsTab`. Tạo thẻ `PdpShopCard` tại trang chi tiết sản phẩm (`ProductDetail.tsx`) để dẫn khách vào gian hàng.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JDBC, Springdoc OpenAPI; React 19, Vite, TanStack Query, Orval, Phosphor Icons, TailwindCSS.

**Spec:** `docs/superpowers/specs/2026-10-27-shop-decoration-and-storefront-design.md` (cả plan lẫn executor đọc kèm).

## Workspace & Git Worktree Isolation

- **Branch:** `feature/shop-decoration-storefront` (tạo từ nhánh `master`).
- **Backend Worktree Directory:** `fashionstore/.worktrees/shop-decoration-storefront`
- **Frontend Worktree Directory:** `fashionstore-ui/.worktrees/shop-decoration-storefront`
- Cả hai thư mục `.worktrees/` đã được cấu hình trong `.gitignore` của cả hai repo, đảm bảo cô lập hoàn toàn môi trường làm việc khỏi nhánh `master`.

## Global Constraints

- Backend: Tuân thủ tuyệt đối `AGENTS.md` và `docs/code-guide.md`. Cấm ternary (`? :`); entity cấm `@Data/@Setter/@EqualsAndHashCode`; NullAway bật, hạn chế tối đa `@Nullable`; Exception message tiếng Anh; tiền VND kiểu `long`; thời gian `Instant`; id sinh trong factory.
- Không sửa migration cũ; tận dụng các bảng hiện có (`shop_decoration`, `product`, `product_variant`, `order_item`, `category`).
- Mỗi endpoint mới phải có đầy đủ Springdoc annotations: `@Tag`, `@Operation(operationId, summary, description)`, `@ApiResponse` từng mã, `@Parameter`, `@Schema` trên DTO.
- UI: Chỉ dùng `pnpm`, cấm `npm`. Cấm ternary trong code TypeScript và JSX (trong JSX dùng `&&`, ngoài JSX dùng `if` + early `return`). `pnpm format && pnpm lint && pnpm build` xanh 100% trước khi hoàn thành.
- Không commit file tự sinh trong `dist/` hoặc sửa tay trong `generated/`. Đổi API backend thì chạy lại `pnpm codegen`.
- Commit message không có `Co-Authored-By`.

## Review Focus

1. **Bộ sưu tập không có sản phẩm (Empty State):** Khi shop chưa có sản phẩm nào giảm giá (`SALE_OFF`) hoặc rỗng hàng, `ShopDecorationRenderer` phải tự động ẩn hoàn toàn khối đó khỏi giao diện khách hàng thay vì render khung trống.
2. **Tìm kiếm nội bộ không có kết quả:** Khi khách tìm kiếm từ khóa không khớp trong shop, `ShopProductsTab` phải hiển thị giao diện empty state trực quan kèm nút "Xóa bộ lọc" để quay lại danh mục đầy đủ.
3. **Kéo thả trên thiết bị không hỗ trợ DND:** Giao diện Studio phải duy trì song song cả sự kiện kéo thả HTML5 và các nút bấm di chuyển Lên/Xuống (ArrowUp/Down) để đảm bảo tính tiếp cận (Accessibility).
4. **Trạng thái Shop chưa duyệt hoặc bị khóa:** Khi khách truy cập vào `/shops/:slug` của shop đang `PENDING` hoặc `SUSPENDED`, Backend phải trả về `404 Not Found` và Frontend hiển thị trang báo không tìm thấy gian hàng.
5. **Giới hạn số lượng Banner Slider:** Modal cấu hình banner phải khóa nút "Thêm banner" ngay khi đạt ngưỡng tối đa 5 banner, upload ảnh trực tiếp qua presigned URL S3 và chỉ lưu S3 key.

---

### Task 1: Backend DTOs & `ShopCatalogQueryRepository`

**Files:**
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/ShopProductCollectionType.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/ShopCategorySummaryResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/ShopProductQueryRequest.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopCatalogQueryRepository.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/shop/ShopCatalogQueryRepositoryTest.java`

**Working Directory:** `fashionstore/.worktrees/shop-decoration-storefront`

**Interfaces:**
- Consumes: Database tables `product`, `product_variant`, `category`, `order_item`, `orders` qua Spring `JdbcClient`.
- Produces:
  - `ShopProductCollectionType` (`NEW_ARRIVALS`, `BEST_SELLER`, `SALE_OFF`).
  - `ShopCategorySummaryResponse(id, name, slug, productCount)`.
  - `ShopProductQueryRequest(q, collection, categoryId, sort, page, limit)`.
  - `ShopCatalogQueryRepository`:
    - `List<UUID> findShopProductIds(UUID shopId, ShopProductQueryRequest request)`
    - `long countShopProducts(UUID shopId, ShopProductQueryRequest request)`
    - `List<ShopCategorySummaryResponse> findShopCategories(UUID shopId)`

- [ ] **Step 1: Viết failing test cho `ShopCatalogQueryRepository`**

Tạo test `ShopCatalogQueryRepositoryTest.java` kiểm tra:
1. Tìm sản phẩm theo từ khóa `q` (so khớp tên sản phẩm `p.name ILIKE`).
2. Lọc theo collection: `NEW_ARRIVALS` (xếp theo `created_at DESC`), `SALE_OFF` (chỉ ra sản phẩm có `compare_at_price > price`), `BEST_SELLER` (xếp theo tổng `quantity` bán ra).
3. Đếm danh mục của shop có sản phẩm active kèm số lượng sản phẩm.

```java
@Test
void searchesProductsByKeywordWithinShop() {
    // Tìm kiếm với q = "áo" chỉ trả về sản phẩm của shop khớp từ khóa
}
```

- [ ] **Step 2: Chạy test để xác nhận fail**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.shop.ShopCatalogQueryRepositoryTest"`  
Expected: FAIL (Class/method not found).

- [ ] **Step 3: Tạo DTOs và triển khai `ShopCatalogQueryRepository`**

1. Tạo `ShopProductCollectionType.java`, `ShopCategorySummaryResponse.java`, `ShopProductQueryRequest.java`.
2. Triển khai `ShopCatalogQueryRepository.java` với Spring `JdbcClient`:
   - Lọc bắt buộc `p.shop_id = :shopId AND p.status = 'ACTIVE'`.
   - Lọc từ khóa: `if (request.q() != null && !request.q().isBlank()) { sql += " AND (p.name ILIKE :qLike OR p.description ILIKE :qLike)"; }`.
   - Lọc `categoryId` nếu có.
   - Lọc `SALE_OFF`: `EXISTS (SELECT 1 FROM product_variant pv WHERE pv.product_id = p.id AND pv.compare_at_price IS NOT NULL AND pv.compare_at_price > pv.price)`.
   - Sắp xếp linh hoạt theo `collection` hoặc `sort` (`NEWEST`, `PRICE_ASC`, `PRICE_DESC`, `BEST_SELLER`).
   - Phân trang `LIMIT :limit OFFSET :offset`.
   - Truy vấn `findShopCategories` group by category và đếm số sản phẩm active.

- [ ] **Step 4: Chạy test để xác nhận pass**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.shop.ShopCatalogQueryRepositoryTest"`  
Expected: PASS.

- [ ] **Step 5: Commit code Task 1**

```bash
git add src/main/java/com/minhnbnt/fashionstore/dto/shop/ShopProductCollectionType.java src/main/java/com/minhnbnt/fashionstore/dto/shop/ShopCategorySummaryResponse.java src/main/java/com/minhnbnt/fashionstore/dto/shop/ShopProductQueryRequest.java src/main/java/com/minhnbnt/fashionstore/features/shop/ShopCatalogQueryRepository.java src/test/java/com/minhnbnt/fashionstore/features/shop/ShopCatalogQueryRepositoryTest.java
git commit -m "feat(shop): implement ShopCatalogQueryRepository for shop product filtering and categories"
```

---

### Task 2: Backend `ShopCatalogService` & `ShopCatalogController`

**Files:**
- Create: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopCatalogService.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/features/shop/controller/ShopCatalogController.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/shop/ShopCatalogControllerTest.java`

**Working Directory:** `fashionstore/.worktrees/shop-decoration-storefront`

**Interfaces:**
- Consumes: `ShopCatalogQueryRepository`, `ShopRepository`, `CatalogService` (hoặc `CatalogMapper`).
- Produces:
  - `GET /api/shops/{shopId}/products`: Trả về `PageResponse<ProductResponse>`.
  - `GET /api/shops/{shopId}/categories`: Trả về `List<ShopCategorySummaryResponse>`.

- [ ] **Step 1: Viết failing test cho `ShopCatalogController`**

Tạo test `ShopCatalogControllerTest.java`:
1. `GET /api/shops/{shopId}/products` trả về 200 OK với danh sách sản phẩm phân trang.
2. `GET /api/shops/{shopId}/products?q=ao` trả về danh sách sản phẩm khớp từ khóa.
3. `GET /api/shops/{shopId}/categories` trả về 200 OK danh mục của shop.
4. Trả về 404 NOT FOUND nếu shop không tồn tại hoặc trạng thái khác `APPROVED`.

- [ ] **Step 2: Chạy test để xác nhận fail**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.shop.ShopCatalogControllerTest"`  
Expected: FAIL.

- [ ] **Step 3: Triển khai `ShopCatalogService` và `ShopCatalogController`**

1. `ShopCatalogService.java`:
   - Kiểm tra shop tồn tại và `status == ShopStatus.APPROVED`. Nếu không, ném `ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found")`.
   - Gọi `repository.findShopProductIds` và `repository.countShopProducts`.
   - Map danh sách ID sang `ProductResponse` chi tiết (ảnh, variants, giá, rating).
   - Gọi `repository.findShopCategories`.
2. `ShopCatalogController.java`:
   - `@RestController` tại `@RequestMapping("/api/shops/{shopId}")`.
   - Đầy đủ Springdoc annotations (`@Tag`, `@Operation`, `@ApiResponse`, `@Parameter`).

- [ ] **Step 4: Chạy test để xác nhận pass**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.shop.ShopCatalogControllerTest"`  
Expected: PASS.

- [ ] **Step 5: Xuất bản OpenAPI spec mới sang Frontend Worktree**

Tải hoặc xuất file `docs/openapi.json` mới có chứa `getShopProducts` và `getShopCategories`, sau đó tại frontend worktree chạy `pnpm codegen`.

- [ ] **Step 6: Commit code Task 2**

```bash
git add src/main/java/com/minhnbnt/fashionstore/features/shop/ShopCatalogService.java src/main/java/com/minhnbnt/fashionstore/features/shop/controller/ShopCatalogController.java src/test/java/com/minhnbnt/fashionstore/features/shop/ShopCatalogControllerTest.java docs/openapi.json
git commit -m "feat(shop): add ShopCatalogController for public shop products and categories"
```

---

### Task 3: Frontend Studio Trang Trí: Kéo Thả Section Native HTML5 & Loại Bỏ Voucher

**Files:**
- Modify: `src/components/seller-dashboard-tab/SellerDecorationTab.tsx`
- Modify: `src/lib/sellerAdapters.ts`
- Test: `src/components/seller-dashboard-tab/SellerDecorationTab.test.tsx`

**Working Directory:** `fashionstore-ui/.worktrees/shop-decoration-storefront`

**Interfaces:**
- Consumes: HTML5 Drag & Drop events (`onDragStart`, `onDragOver`, `onDragLeave`, `onDrop`, `onDragEnd`), `useSaveDraft`, `usePublish`.
- Produces: Studio giao diện người bán hỗ trợ kéo thả trực quan các section, hiển thị đường kẻ highlight điểm thả, loại bỏ hoàn toàn block `VOUCHER_CAROUSEL`.

- [ ] **Step 1: Viết failing test cho kéo-thả và loại bỏ voucher trong `SellerDecorationTab.test.tsx`**

1. Kiểm tra không còn tùy chọn `VOUCHER_CAROUSEL` trong modal thêm module.
2. Kiểm tra các thẻ module có thuộc tính `draggable="true"` và các hàm xử lý sự kiện kéo thả.

- [ ] **Step 2: Chạy test để xác nhận fail**

Run: `pnpm test -- SellerDecorationTab`  
Expected: FAIL.

- [ ] **Step 3: Triển khai kéo thả HTML5 và làm sạch module voucher trong `SellerDecorationTab.tsx`**

1. Khai báo state: `draggedIdx: number | null`, `dragOverIdx: number | null`.
2. Viết các handler kéo thả HTML5 (`handleDragStart`, `handleDragOver`, `handleDrop`, `handleDragEnd`).
3. Gắn icon `DotsSixVertical` kèm vạch highlight `border-t-2 border-indigo-600` khi `dragOverIdx === idx`.
4. Duy trì các nút bấm `handleMoveUp` và `handleMoveDown` dự phòng.
5. Xóa bỏ hoàn toàn option `VOUCHER_CAROUSEL` khỏi modal `handleAddBlock` và danh sách selector.

- [ ] **Step 4: Chạy test và lint để xác nhận pass**

Run: `pnpm lint && pnpm test -- SellerDecorationTab`  
Expected: PASS (0 lint error/warning).

- [ ] **Step 5: Commit code Task 3**

```bash
git add src/components/seller-dashboard-tab/SellerDecorationTab.tsx src/components/seller-dashboard-tab/SellerDecorationTab.test.tsx src/lib/sellerAdapters.ts
git commit -m "feat(seller): implement native HTML5 drag and drop in decoration studio and remove voucher module"
```

---

### Task 4: Frontend Thẻ Shop (`PdpShopCard`) Tại Trang Chi Tiết Sản Phẩm & Link Giỏ Hàng

**Files:**
- Create: `src/components/pdp/PdpShopCard.tsx`
- Modify: `src/pages/ProductDetail.tsx`
- Test: `src/components/pdp/PdpShopCard.test.tsx`

**Working Directory:** `fashionstore-ui/.worktrees/shop-decoration-storefront`

**Interfaces:**
- Consumes: `useBySlug` từ `@generated/endpoints/shops/shops`, prop `shopId: string`.
- Produces: Component `PdpShopCard` hiển thị avatar, tên shop, đánh giá, DTS 1-3 ngày, nút "Xem Shop" dẫn đến `/shops/:slug`, nút "Chat ngay".

- [ ] **Step 1: Viết test cho `PdpShopCard.test.tsx`**

1. Render avatar, tên shop, số lượng đánh giá, thời gian chuẩn bị hàng (DTS).
2. Kiểm tra link "Xem Shop" có `href="/shops/shop-slug"`.
3. Kiểm tra tuân thủ JSX: không dùng ternary operator `? :`.

- [ ] **Step 2: Chạy test để xác nhận fail**

Run: `pnpm test -- PdpShopCard`  
Expected: FAIL.

- [ ] **Step 3: Triển khai component `PdpShopCard.tsx` và gắn vào `ProductDetail.tsx`**

1. Tạo `PdpShopCard.tsx`:
   - Nhận prop `{ shopId: string }`.
   - Gọi `useBySlug(shopId)`.
   - Hiển thị card sang trọng chuẩn UI nét: Logo bo tròn, tên shop đậm, badge "Chính hãng", số sao đánh giá, nút "Xem Shop" (`/shops/${shop.slug}`), nút "Chat với Shop".
2. Tích hợp `PdpShopCard` vào `ProductDetail.tsx` (ngay dưới khu vực `ProductGallery` + `ProductInfo` và trên `ProductTabs`).
3. Đảm bảo 100% điều kiện hiển thị dùng `&&` hoặc xử lý biến bên ngoài JSX.

- [ ] **Step 4: Chạy test và lint để xác nhận pass**

Run: `pnpm lint && pnpm test -- PdpShopCard`  
Expected: PASS.

- [ ] **Step 5: Commit code Task 4**

```bash
git add src/components/pdp/PdpShopCard.tsx src/components/pdp/PdpShopCard.test.tsx src/pages/ProductDetail.tsx
git commit -m "feat(pdp): add PdpShopCard with storefront navigation and seller info"
```

---

### Task 5: Frontend Storefront Khách Hàng: Tích Hợp API Thật & Thanh Tìm Kiếm Nội Bộ

**Files:**
- Modify: `src/components/shop-storefront/ShopDecorationRenderer.tsx`
- Modify: `src/components/shop-storefront/ShopStorefrontHeader.tsx`
- Modify: `src/pages/ShopStorefront.tsx`
- Test: `src/pages/ShopStorefront.test.tsx`

**Working Directory:** `fashionstore-ui/.worktrees/shop-decoration-storefront`

**Interfaces:**
- Consumes: `useGetShopProducts`, `useGetShopCategories`, prop `shopId`.
- Produces:
  - Khối `PRODUCT_COLLECTION`: Tải sản phẩm thật theo `collectionType`, tự động ẩn khối nếu rỗng hàng (`items.length === 0`).
  - Khối `CATEGORY_GRID`: Hiển thị danh mục thực tế của shop kèm số lượng sản phẩm.
  - Loại bỏ hoàn toàn khối `VOUCHER_CAROUSEL`.
  - Thanh tìm kiếm nội bộ: Nhập từ khóa tại Header hoặc Tab sản phẩm, tự động chuyển tab và lọc theo `q`.

- [ ] **Step 1: Viết test cho tìm kiếm nội bộ và tự động ẩn bộ sưu tập rỗng trong `ShopStorefront.test.tsx`**

1. Test thanh tìm kiếm nội bộ: Nhập từ khóa, nhấn Enter -> chuyển activeTab sang `PRODUCTS` và truyền `searchQuery`.
2. Test bộ sưu tập: Khi API trả về mảng rỗng -> không hiển thị tiêu đề bộ sưu tập (khối bị ẩn).
3. Test khối voucher đã bị gỡ bỏ hoàn toàn.

- [ ] **Step 2: Chạy test để xác nhận fail**

Run: `pnpm test -- ShopStorefront`  
Expected: FAIL.

- [ ] **Step 3: Cập nhật `ShopDecorationRenderer.tsx`, `ShopStorefrontHeader.tsx`, `ShopStorefront.tsx`**

1. Trong `ShopDecorationRenderer.tsx`:
   - Thay thế mock data trong `ProductCollectionBlock` bằng hook gọi API `useGetShopProducts({ shopId, collection: block.collectionType, limit: block.itemLimit ?? 8 })`.
   - Nếu `items.length === 0`, return `null`.
   - Trong `CategoryGridBlock`: Gọi API `useGetShopCategories(shopId)` để hiển thị các danh mục thực tế. Bấm vào danh mục kích hoạt callback `onSelectCategory(slug)`.
   - Xóa bỏ hoàn toàn component `VoucherCarouselBlock` và case render tương ứng.
2. Trong `ShopStorefrontHeader.tsx`:
   - Thêm ô tìm kiếm *"Tìm sản phẩm trong Shop này..."* kèm icon kính lúp và nút xóa nhanh (Clear).
   - Khi submit form tìm kiếm, gọi callback `onSearch(keyword)` truyền lên component cha.
3. Trong `ShopStorefront.tsx`:
   - Quản lý state `searchKeyword: string` và `selectedCategoryId: string | null`.
   - Khi tìm kiếm hoặc chọn danh mục, tự động `setActiveTab('PRODUCTS')`.

- [ ] **Step 4: Chạy test và lint để xác nhận pass**

Run: `pnpm lint && pnpm test -- ShopStorefront`  
Expected: PASS.

- [ ] **Step 5: Commit code Task 5**

```bash
git add src/components/shop-storefront/ShopDecorationRenderer.tsx src/components/shop-storefront/ShopStorefrontHeader.tsx src/pages/ShopStorefront.tsx src/pages/ShopStorefront.test.tsx
git commit -m "feat(storefront): integrate real shop catalog API, internal search bar, and empty state handling"
```

---

### Task 6: Frontend Tab Tất Cả Sản Phẩm (`ShopProductsTab.tsx`)

**Files:**
- Modify: `src/components/shop-storefront/ShopProductsTab.tsx`
- Test: `src/components/shop-storefront/__tests__/ShopProductsTab.test.tsx`

**Working Directory:** `fashionstore-ui/.worktrees/shop-decoration-storefront`

**Interfaces:**
- Consumes: `useGetShopProducts`, `useGetShopCategories`, props `shopId`, `initialSearchQuery`, `initialCategoryId`.
- Produces: Giao diện tab sản phẩm hoàn chỉnh: Ô tìm kiếm từ khóa, danh mục lọc nhanh, sắp xếp giá/mới nhất, phân trang và empty state khi không tìm thấy hàng.

- [ ] **Step 1: Viết test cho `ShopProductsTab` với dữ liệu API thật và bộ lọc**

1. Hiển thị danh sách sản phẩm lấy từ API.
2. Khi có từ khóa tìm kiếm: Hiển thị badge *"Kết quả tìm kiếm cho: '[từ khóa]' "* kèm nút *"Xóa bộ lọc"*.
3. Khi danh sách rỗng: Hiển thị thông báo *"Không tìm thấy sản phẩm nào trong shop phù hợp với tiêu chí lọc"*.

- [ ] **Step 2: Chạy test để xác nhận fail**

Run: `pnpm test -- ShopProductsTab`  
Expected: FAIL.

- [ ] **Step 3: Triển khai cập nhật trong `ShopProductsTab.tsx`**

1. Xóa bỏ hoàn toàn mảng `products` hardcoded mock.
2. Gọi hook `useGetShopProducts` với các tham số `{ shopId, q: searchQuery, categoryId, sort, page, limit: 12 }`.
3. Tích hợp thanh chọn danh mục ngang của shop (lấy từ `useGetShopCategories`).
4. Thanh sắp xếp: *Mới nhất* (`NEWEST`), *Bán chạy nhất* (`BEST_SELLER`), *Giá tăng dần* (`PRICE_ASC`), *Giá giảm dần* (`PRICE_DESC`).
5. Render lưới sản phẩm thẻ card đẹp mắt, chuẩn UI nét, có nhãn giảm giá `-X%` nếu `compareAtPrice > price`.
6. Phân trang trang trước / trang sau.
7. Xử lý empty state khi không có sản phẩm.
8. Tuân thủ 100% quy tắc JSX: Không dùng toán tử ba ngôi `? :`.

- [ ] **Step 4: Chạy test và lint để xác nhận pass**

Run: `pnpm lint && pnpm test -- ShopProductsTab`  
Expected: PASS.

- [ ] **Step 5: Commit code Task 6**

```bash
git add src/components/shop-storefront/ShopProductsTab.tsx src/components/shop-storefront/__tests__/ShopProductsTab.test.tsx
git commit -m "feat(storefront): integrate real product catalog, pagination, and filters into ShopProductsTab"
```

---

### Task 7: Kiểm Thử Tích Hợp Toàn Bộ & Xác Nhận Bằng Trình Duyệt (Browser Subagent)

**Files:**
- Toàn bộ các file đã tạo và chỉnh sửa ở Backend và Frontend Worktrees.

- [ ] **Step 1: Chạy toàn bộ kiểm thử tự động Backend trong worktree**

Run: `.\gradlew.bat test -x processTestAot`  
Expected: 100% test pass xanh, 0 lỗi, không vi phạm ternary và NullAway.

- [ ] **Step 2: Chạy kiểm thử tự động và Build Frontend trong worktree**

Run: `pnpm format:check && pnpm lint && pnpm test && pnpm build`  
Expected: 0 lint errors, 0 lint warnings, toàn bộ unit test frontend pass, build bundle thành công.

- [ ] **Step 3: Kiểm thử trực quan trên Trình duyệt bằng Browser Subagent**

1. Mở Kênh Người Bán -> Tab Trang Trí Gian Hàng:
   - Thử kéo thả di chuyển vị trí các block Banner Slider và Bộ sưu tập nổi bật.
   - Bấm cấu hình Banner Slider (kiểm tra tối đa 5 banner, upload ảnh S3).
   - Bấm "Lưu nháp" và "Áp dụng gian hàng".
2. Mở Trang Chi Tiết Sản Phẩm (PDP):
   - Kiểm tra hiển thị Thẻ Shop (PdpShopCard), bấm nút "Xem Shop".
3. Mở Trang Gian Hàng Khách Hàng (`/shops/:slug`):
   - Kiểm tra Banner Slider chạy tự động xoay vòng.
   - Kiểm tra Bộ sưu tập hiển thị sản phẩm thật.
   - Nhập từ khóa vào thanh *"Tìm sản phẩm trong Shop này..."* và Enter -> xác nhận chuyển sang tab Sản phẩm và hiển thị đúng kết quả lọc.
