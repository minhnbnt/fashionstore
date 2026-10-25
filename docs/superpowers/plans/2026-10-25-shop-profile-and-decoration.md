# Quản Lý Hồ Sơ Shop & Trang Trí Gian Hàng (Shop Profile & Decoration) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hoàn thiện trải nghiệm cho người bán: Quản lý hồ sơ Shop (rich text mô tả, upload logo/ảnh bìa, thời gian chuẩn bị hàng 1-3 ngày, chính sách đổi trả) và Trang trí gian hàng (Banner slider tối đa 5 ảnh, bộ sưu tập nổi bật New/Best Seller/Sale, sắp xếp layout linh hoạt).

**Architecture:** 
- Hồ sơ shop: Sử dụng API `PATCH /api/shops/{id}` (name, description, logoKey, bannerKey) và `PATCH /api/shops/{id}/profile` (address, returnPolicy, processingDays, bank info). Backend ràng buộc SLA chuẩn bị hàng từ 1 đến 3 ngày bằng Bean Validation. Frontend tích hợp `RichTextEditor` và `ImageUploadField` (presign S3 upload).
- Trang trí shop: Mở rộng JSON layout của `ShopDecoration` với 4 loại module chuẩn: `BANNER_SLIDER` (mảng tối đa 5 banners có link), `PRODUCT_COLLECTION` (Hàng mới, Bán chạy, Giảm giá sâu, Tùy chọn), `CATEGORY_GRID`, `VOUCHER_CAROUSEL`. Nâng cấp Studio `SellerDecorationTab` với quản lý banner, bộ sưu tập và preview responsive.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JDBC, Bean Validation; React 19, Vite, TanStack Query, Orval, Phosphor Icons, TailwindCSS.

**Spec:** `docs/superpowers/plans/2026-10-25-shop-profile-and-decoration.md`

## Global Constraints

- Backend: theo `AGENTS.md`, `docs/code-guide.md`, `docs/structure.md`, `docs/data.md`. Cấm ternary (`? :`); entity cấm `@Data/@Setter/@EqualsAndHashCode`; NullAway bật, hạn chế `@Nullable`; exception/message tiếng Anh; tiền VND `long`; thời gian `Instant`; id sinh trong factory; entity implement `Persistable` khi dùng Spring Data.
- Migration mới bắt đầu `V28`, không sửa migration đã merge. Testcontainers giữ image `ghcr.io/minhnbnt/postgresql:latest`.
- Mỗi endpoint mới đủ springdoc: `@Tag`, `@Operation(operationId, summary, description)`, `@ApiResponse` từng mã, `@Parameter`, `@Schema` field DTO; endpoint cần auth gắn `@SecurityRequirement(name = "bearerAuth")`.
- Service domain này không gọi repository domain khác; gọi service bên đó.
- UI: chỉ `pnpm`; cấm ternary (JSX dùng `&&`); `pnpm format && pnpm lint && pnpm build` xanh trước commit; `generated/` không sửa tay; copy tiếng Việt; giá dùng `formatVnd`.
- Commit message không có `Co-Authored-By`.
- Sử dụng validate của Spring (`@Min`, `@Max`, `@Size`, v.v.) thay vì tự code validate.
- Bảo mật 2 lớp cả FE + BE.

## Review Focus

1. **`processingDays` ngoài khoảng 1-3 ngày** — Backend phải từ chối với mã 400 Bad Request, Frontend giới hạn selector chỉ cho phép 1, 2, 3 ngày.
2. **Upload ảnh Logo và Ảnh bìa** — Phải sử dụng S3 presigned URL, kiểm tra kích thước tối đa 5MB, lưu S3 key vào database chứ không lưu public URL tuyệt đối.
3. **Banner Slider** — Giới hạn tối đa 5 banner, mỗi banner phải có ảnh hợp lệ, cho phép gán link liên kết (ví dụ link sản phẩm hoặc danh mục).
4. **Bộ sưu tập nổi bật** — Module phải cho phép chọn các tiêu chí: `NEW_ARRIVALS`, `BEST_SELLER`, `SALE_OFF` và hiển thị trực quan trong Studio Preview.
5. **Không dùng toán tử ba ngôi trong JSX** — Mọi điều kiện hiển thị trên UI phải dùng `&&` hoặc xử lý biến ở ngoài JSX.

---

### Task 1: Backend Ràng Buộc Thời Gian Chuẩn Bị Hàng (1-3 Ngày) & Chính Sách Đổi Trả

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/shop/UpdateShopProfileRequest.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/Shop.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/shop/ShopService.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/shop/ShopProfileValidationTest.java`

**Interfaces:**
- Consumes: `UpdateShopProfileRequest(phone, address, returnPolicy, processingDays, ...)`
- Produces: Ràng buộc `processingDays` phải từ 1 đến 3 ngày (`@Min(1) @Max(3)`), `returnPolicy` tối đa 5000 ký tự.

- [x] **Step 1: Viết failing test cho validation `processingDays` (ngoài 1-3 ngày bị reject)**

Tạo test `ShopProfileValidationTest.java`:
```java
@Test
void rejectsProcessingDaysOutsideAllowedRange() {
    // 0 ngày hoặc 4 ngày phải bị reject 400
}
```

- [x] **Step 2: Chạy test để xác nhận fail**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.shop.ShopProfileValidationTest"`

- [x] **Step 3: Thêm annotation Bean Validation vào `UpdateShopProfileRequest.java` và logic cập nhật trong `Shop.java`**

```java
@Schema(description = "Số ngày chuẩn bị hàng (1-3 ngày)", nullable = true)
@Min(value = 1, message = "Processing days must be at least 1")
@Max(value = 3, message = "Processing days cannot exceed 3")
@Nullable
Integer processingDays,
```
Trong `Shop.java:updateProfile`:
```java
if (processingDays != null) {
    if (processingDays < 1 || processingDays > 3) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Processing days must be between 1 and 3 days");
    }
    this.processingDays = processingDays;
}
```

- [x] **Step 4: Chạy test để xác nhận pass**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.shop.ShopProfileValidationTest"`
Expected: PASS

---

### Task 2: Frontend Nâng Cấp Hồ Sơ Shop (RichTextEditor, Upload S3 Logo/Bìa, DTS 1-3 ngày, Chính sách đổi trả)

**Files:**
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/SellerProfileTab.tsx`
- Modify: `fashionstore-ui/src/types/seller.ts`
- Test: `fashionstore-ui/src/components/seller-dashboard-tab/__tests__/SellerProfileTab.test.tsx`

**Interfaces:**
- Consumes: `useUpdateProfile`, `useUpdateShop`, `uploadImage`, `RichTextEditor`, `ImageUploadField`
- Produces: Giao diện hồ sơ chuyên nghiệp, cho phép sửa tên, mô tả bằng RichText, upload Logo/Bìa S3, chọn DTS 1-3 ngày, nhập chính sách đổi trả.

- [x] **Step 1: Viết test cho `SellerProfileTab` kiểm tra hiển thị đúng các trường và giới hạn DTS 1-3 ngày**

- [x] **Step 2: Chạy test để xác nhận fail**

Run: `pnpm test -- SellerProfileTab`

- [x] **Step 3: Triển khai giao diện cập nhật trong `SellerProfileTab.tsx`**

1. Thay thế input URL bằng `ImageUploadField` cho Logo (tỉ lệ 1:1) và Ảnh bìa (tỉ lệ 3:1).
2. Tích hợp `RichTextEditor` cho mô tả gian hàng.
3. Thêm trường nhập "Chính sách đổi trả riêng của Shop" (`returnPolicy`) kèm banner hướng dẫn tuân thủ quy định tối thiểu của sàn nét (tối thiểu 7 ngày đổi trả với lỗi do nhà sản xuất hoặc giao sai mẫu).
4. Cập nhật selector DTS:
   - 1 ngày (Giao hàng hỏa tốc / Chuẩn bị ngay trong ngày)
   - 2 ngày (Tiêu chuẩn sàn)
   - 3 ngày (Mức tối đa cho phép)
5. Xử lý đồng thời lưu thông tin cơ bản (`useUpdateShop`: name, description, logoKey, bannerKey) và thông tin vận hành (`useUpdateProfile`: address, returnPolicy, processingDays, bank info).
6. Tuân thủ 100% quy tắc JSX: Không dùng toán tử ba ngôi `? :`.

- [x] **Step 4: Chạy test và lint để xác nhận pass**

Run: `pnpm lint && pnpm test -- SellerProfileTab`
Expected: PASS

---

### Task 3: Cấu Trúc Dữ Liệu Trang Trí Gian Hàng (Banner Slider, Bộ Sưu Tập Nổi Bật)

**Files:**
- Modify: `fashionstore-ui/src/types/seller.ts`
- Modify: `fashionstore-ui/src/lib/sellerAdapters.ts`
- Test: `fashionstore-ui/src/lib/__tests__/sellerAdapters.test.ts`

**Interfaces:**
- `DecorationBlock`:
  - `type: 'BANNER_SLIDER'`: chứa `banners: Array<{ id: string, imageKey: string, title?: string, linkUrl?: string }>` (tối đa 5 banner).
  - `type: 'PRODUCT_COLLECTION'`: chứa `collectionType: 'NEW_ARRIVALS' | 'BEST_SELLER' | 'SALE_OFF' | 'CUSTOM'`, `limit: number`, `displayStyle: 'GRID' | 'SLIDER'`.
  - `type: 'CATEGORY_GRID'`: danh mục sản phẩm nổi bật của shop.
  - `type: 'VOUCHER_CAROUSEL'`: mã giảm giá shop.

- [x] **Step 1: Viết test cho adapters chuyển đổi JSON layout có chứa Banner Slider & Collection**

- [x] **Step 2: Chạy test xác nhận fail**

Run: `pnpm test -- sellerAdapters`

- [x] **Step 3: Cập nhật type definition và parser/serializer trong `sellerAdapters.ts`**

Hỗ trợ parse và serialize chuẩn hóa các trường mở rộng của `BANNER_SLIDER` (tối đa 5 banner items) và `PRODUCT_COLLECTION`.

- [x] **Step 4: Chạy test xác nhận pass**

Run: `pnpm test -- sellerAdapters`
Expected: PASS

---

### Task 4: Nâng Cấp Studio Trang Trí Gian Hàng (SellerDecorationTab)

**Files:**
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/SellerDecorationTab.tsx`
- Create: `fashionstore-ui/src/components/seller-dashboard-tab/decoration/BannerSliderConfigModal.tsx`
- Create: `fashionstore-ui/src/components/seller-dashboard-tab/decoration/CollectionConfigModal.tsx`
- Test: `fashionstore-ui/src/components/seller-dashboard-tab/__tests__/SellerDecorationTab.test.tsx`

**Interfaces:**
- Consumes: `useSaveDraft`, `usePublish`, `useDiscardDraft`, `uploadImage`, `ImageUploadField`
- Produces: Studio trang trí trực quan, có modal cấu hình banner slider (upload tối đa 5 banner, nhập link), modal cấu hình bộ sưu tập nổi bật (chọn loại New/Best Seller/Sale Off), và điều chỉnh sắp xếp layout linh hoạt.

- [x] **Step 1: Tạo modal cấu hình Banner Slider `BannerSliderConfigModal.tsx`**
  - Cho phép thêm tối đa 5 banner.
  - Mỗi banner có nút upload ảnh trực tiếp lên S3, ô nhập tiêu đề và link điều hướng.
  - Hỗ trợ xóa banner, di chuyển thứ tự banner trong slider.

- [x] **Step 2: Tạo modal cấu hình Bộ sưu tập nổi bật `CollectionConfigModal.tsx`**
  - Cho phép chọn loại: Hàng mới về (`NEW_ARRIVALS`), Bán chạy (`BEST_SELLER`), Giảm giá sốc (`SALE_OFF`).
  - Chọn số lượng sản phẩm hiển thị (4, 8, 12).
  - Chọn kiểu hiển thị: Lưới 2 hàng hoặc Hàng cuộn ngang.

- [x] **Step 3: Cập nhật khung Studio Preview trong `SellerDecorationTab.tsx`**
  - Xem trước Banner Slider với hiệu ứng dot chuyển động mượt mà.
  - Xem trước Bộ sưu tập nổi bật với badge và thẻ sản phẩm mô phỏng sinh động.
  - Thao tác di chuyển lên/xuống hoặc kéo thả thứ tự module mượt mà.
  - Nút "Lưu nháp" (`saveDraft`), "Xuất bản" (`publish`), "Bỏ thay đổi" (`discardDraft`) hoạt động nhất quán.
  - Tuân thủ quy tắc JSX: Không dùng toán tử ba ngôi `? :`.

- [x] **Step 4: Chạy test và lint frontend**

Run: `pnpm lint && pnpm test -- SellerDecorationTab`
Expected: PASS

---

### Task 5: Kiểm Thử Toàn Diện & Đảm Bảo Không Vi Phạm Quy Tắc

- [x] **Step 1: Chạy kiểm thử tự động Backend**
  Run: `.\gradlew.bat test -x processTestAot`
  Xác nhận: Tất cả test pass 100%, không vi phạm ternary, không có message tiếng Việt trong Exception.

- [x] **Step 2: Chạy kiểm thử tự động Frontend**
  Run: `pnpm lint && pnpm test -- --run && pnpm build`
  Xác nhận: 0 lint errors/warnings, toàn bộ 188 unit tests pass, bundle build thành công.

- [x] **Step 3: Kiểm thử tương tác trên trình duyệt (Browser Subagent)**
  - Mở trang Quản lý hồ sơ Shop, kiểm tra Rich Text editor, upload ảnh logo/ảnh bìa, chọn DTS 1-3 ngày, lưu chính sách đổi trả.
  - Mở trang Trang trí gian hàng, cấu hình banner slider (tối đa 5 banner), tạo bộ sưu tập nổi bật, lưu nháp và xuất bản.

---

### Task 6: Kế Hoạch & Thiết Kế Triển Khai Giao Diện Gian Hàng Phía Khách Hàng (Customer Storefront Shop Page)

**Goal:** Xây dựng trang mua sắm công khai `/shops/:slug` dành cho khách hàng với trải nghiệm thương mại điện tử cao cấp (hero shop profile, tabs Trang chủ / Tất cả sản phẩm / Hồ sơ & Đánh giá, dynamic layout decoration renderer, theo dõi shop, mở chat CSKH).

**Files:**
- Create: `fashionstore-ui/src/pages/ShopStorefront.tsx`
- Create: `fashionstore-ui/src/components/shop-storefront/ShopStorefrontHeader.tsx`
- Create: `fashionstore-ui/src/components/shop-storefront/ShopDecorationRenderer.tsx`
- Create: `fashionstore-ui/src/components/shop-storefront/ShopProductsTab.tsx`
- Create: `fashionstore-ui/src/components/shop-storefront/ShopAboutTab.tsx`
- Modify: `fashionstore-ui/src/App.tsx` (Route `/shops/:slug` và Header visibility)
- Test: `fashionstore-ui/src/pages/__tests__/ShopStorefront.test.tsx`

**Interfaces:**
- Consumes:
  - `GET /api/shops/{slug}` (Lấy thông tin shop: name, logoKey, bannerKey, rating, followerCount, processingDays, returnPolicy, description)
  - `GET /api/shops/{shopId}/decoration` (Lấy layout các block đã xuất bản: `BANNER_SLIDER`, `PRODUCT_COLLECTION`, `CATEGORY_GRID`, `VOUCHER_CAROUSEL`)
  - `POST /api/shops/{id}/follow` & `DELETE /api/shops/{id}/follow` (Theo dõi / hủy theo dõi)
  - `GET /api/search/products?q=...` hoặc `GET /api/products` (Tải danh sách sản phẩm)
- Produces: Trang Storefront hoàn chỉnh chuẩn UI nét, responsive, không dùng ternary trong JSX.

**Chi tiết các bước thực hiện (TDD Steps):**

- [x] **Step 1: Viết test cho component `ShopStorefront.tsx` và `ShopStorefrontHeader.tsx`**
  - Giả lập dữ liệu shop (`ShopResponse`) và layout decoration (`DecorationResponse`).
  - Kiểm tra hiển thị đầy đủ tên shop, logo, banner, rating, follower count, nút "Theo dõi" và các tab ("Trang chủ", "Tất cả sản phẩm", "Hồ sơ & Chính sách").
  - Test tương tác chuyển tab và toggle trạng thái "Theo dõi / Đang theo dõi".

- [x] **Step 2: Chạy test xác nhận fail**
  - Run: `pnpm test -- ShopStorefront`

- [x] **Step 3: Xây dựng Header gian hàng `ShopStorefrontHeader.tsx`**
  - Banner nền mờ sang trọng kèm gradient overlay.
  - Avatar logo shop hình tròn nổi bật, tên gian hàng, badge uy tín.
  - Thống kê: Đánh giá sao (⭐⭐⭐⭐⭐ kèm số lượt đánh giá), Số lượng người theo dõi, Thời gian chuẩn bị hàng (DTS).
  - Nút bấm hành động: Nút "Theo dõi" / "Đang theo dõi" (kết nối API follow/unfollow), nút "Chat với Shop" (mở modal/tab chat CSKH).
  - Thanh tab điều hướng: "Trang chủ" (Home/Decoration), "Tất cả sản phẩm" (All Products), "Hồ sơ & Chính sách" (About & Policy).

- [x] **Step 4: Xây dựng bộ Renderer giao diện trang trí `ShopDecorationRenderer.tsx`**
  - Khối `BANNER_SLIDER`: Trình chiếu carousel tự động với dot điều hướng, hỗ trợ click chuyển trang theo linkUrl.
  - Khối `PRODUCT_COLLECTION`: Hiển thị bộ sưu tập nổi bật (Hàng mới về, Bán chạy, Giảm giá sâu) dạng lưới hoặc cuộn ngang.
  - Khối `CATEGORY_GRID`: Lưới danh mục sản phẩm thời trang của riêng shop.
  - Khối `VOUCHER_CAROUSEL`: Thẻ voucher ưu đãi giảm giá độc quyền của shop kèm nút "Lưu mã".

- [x] **Step 5: Xây dựng Tab Tất Cả Sản Phẩm `ShopProductsTab.tsx` & Tab Hồ Sơ `ShopAboutTab.tsx`**
  - `ShopProductsTab`: Lưới sản phẩm của shop với bộ lọc nhanh (Mới nhất, Bán chạy, Giá tăng dần, Giá giảm dần) và phân trang.
  - `ShopAboutTab`: Mô tả chi tiết giới thiệu gian hàng (render RichText Markdown), Chính sách đổi trả riêng của shop (cam kết tối thiểu 7 ngày), thông tin kho hàng và thời gian làm việc.

- [x] **Step 6: Tích hợp vào `ShopStorefront.tsx` và cấu hình Routing trong `App.tsx`**
  - Thêm route: `<Route path="/shops/:slug" element={<ShopStorefront />} />`.
  - Cập nhật điều kiện Header trong `App.tsx` (chỉ ẩn Header ở route `/shop` của seller dashboard, còn `/shops/:slug` vẫn giữ Header chính của sàn nét cho khách hàng).
  - Đảm bảo tuân thủ 100% quy tắc JSX (không ternary operator, dùng `&&`).

- [x] **Step 7: Chạy test và lint để xác nhận pass**
  - Run: `pnpm lint && pnpm test -- ShopStorefront`
  - Expected: PASS 100%.
