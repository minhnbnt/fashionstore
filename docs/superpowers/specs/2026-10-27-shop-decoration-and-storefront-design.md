# Thiết Kế Chi Tiết: Trang Trí Gian Hàng (Shop Decoration) & Khách Hàng Xem Shop (Storefront)

**Mã chức năng:** 2.1.3  
**Ngày lập:** 2026-10-27  
**Trạng thái:** Chờ phê duyệt  
**Tác giả:** Antigravity  

---

## 1. Mục tiêu & Bối cảnh (Intent & Why)

### 1.1. Bối cảnh
FashionStore Marketplace hiện đã có cấu trúc dữ liệu cơ bản cho trang trí gian hàng (`shop_decoration` table, `ShopDecorationService.java`, `SellerDecorationTab.tsx`, `ShopStorefront.tsx`). Tuy nhiên, trải nghiệm người dùng và tính năng thực tế còn nhiều hạn chế và dang dở:
1. **Thiếu cơ chế kéo-thả trực quan:** Studio trang trí của Người bán (`SellerDecorationTab.tsx`) mới chỉ có các nút bấm Lên/Xuống (ArrowUp/Down), chưa có tính năng kéo-thả (Drag & Drop) mượt mà theo mô tả nghiệp vụ.
2. **Dữ liệu giả lập (Mock Data) ở Storefront:** Khối Bộ sưu tập nổi bật (`ShopDecorationRenderer.tsx`) và tab Tất cả sản phẩm (`ShopProductsTab.tsx`) vẫn đang gắn cứng danh sách sản phẩm mẫu (`sampleProducts`), do Backend chưa có API cung cấp danh mục sản phẩm công khai theo từng Shop kèm bộ lọc thông minh.
3. **Thiếu thanh tìm kiếm nội bộ trong Shop:** Khách vào xem gian hàng có nhu cầu tìm kiếm nhanh sản phẩm của riêng shop đó (ví dụ "áo len", "quần jean") nhưng hiện tại chưa có ô tìm kiếm sản phẩm theo từ khóa trong phạm vi shop.
4. **Thiếu điểm chạm dẫn khách vào Shop:** Khách hàng duyệt sản phẩm trên trang chi tiết sản phẩm (`ProductDetail.tsx` / `ProductInfo.tsx`) không thấy thẻ thông tin Shop (Shop Card) để bấm chuyển hướng sang xem trang cá nhân gian hàng (`/shops/:slug`).
5. **Section Voucher & Tìm kiếm toàn sàn không phù hợp:** Section Voucher chưa có module quản lý khuyến mãi backend hỗ trợ; tính năng tìm kiếm shop trên search bar toàn sàn gây loãng trải nghiệm tìm kiếm hàng hóa thời trang.

### 1.2. Mục tiêu
- **Banner Slider (Tối đa 5 banner):** Người bán tải lên tối đa 5 hình ảnh banner quảng cáo (tỉ lệ 3:1 hoặc 16:9) qua S3 presigned URL, đặt tiêu đề, gắn link điều hướng (sản phẩm/danh mục) và sắp xếp thứ tự trình chiếu tự động có dot điều hướng.
- **Bộ sưu tập nổi bật tự động (Smart Collections):**
  - `NEW_ARRIVALS`: Tự động lấy các sản phẩm `ACTIVE` mới nhất (`created_at DESC`).
  - `BEST_SELLER`: Tự động lấy các sản phẩm có số lượng bán nhiều nhất từ bảng `order_item` của các đơn không bị hủy (`o.status != 'CANCELLED'`). Nếu shop chưa có lượt bán, tự động fallback về sản phẩm có điểm đánh giá trung bình cao nhất hoặc mới nhất.
  - `SALE_OFF`: Tự động lọc các sản phẩm có giảm giá (`compare_at_price > price`), xếp theo tỷ lệ phần trăm giảm giá cao nhất.
  - **Quy tắc rỗng (Empty State):** Tự động ẩn khối bộ sưu tập trên trang khách hàng nếu không có sản phẩm nào thỏa mãn điều kiện lọc.
- **Sắp xếp Layout kéo-thả (Drag & Drop):** Cho phép người bán kéo-thả sắp xếp vị trí các section (`BANNER_SLIDER`, `PRODUCT_COLLECTION`, `CATEGORY_GRID`) bằng **Native HTML5 Drag & Drop** trực quan, bật/tắt hiển thị, cấu hình chi tiết, lưu bản nháp (`draft`) và xuất bản (`publish`).
- **Tìm kiếm nội bộ trong Shop (Search in Shop):** Cung cấp thanh tìm kiếm *"Tìm sản phẩm trong Shop này..."* tại Header gian hàng và Tab Tất cả sản phẩm, lọc tức thì các sản phẩm của riêng shop khớp với từ khóa nhập vào.
- **Khách hàng xem Shop (Storefront `/shops/:slug`):**
  - Hiển thị đầy đủ thông tin Header shop (Avatar logo, Banner bìa, Tên shop, Đánh giá sao, Số lượng người theo dõi, Thời gian chuẩn bị hàng DTS 1-3 ngày, nút "Theo dõi", nút "Chat ngay", thanh tìm kiếm nội bộ).
  - 3 Tab điều hướng: **Trang chủ** (Render layout trang trí đã xuất bản), **Tất cả sản phẩm** (Lưới sản phẩm thật của shop kèm ô tìm kiếm từ khóa, bộ lọc danh mục và sắp xếp), **Hồ sơ & Chính sách** (Mô tả Markdown, Chính sách đổi trả riêng của shop).
- **Điểm chạm tiếp cận (Customer Entry Point):** Tích hợp **Thẻ Shop (Shop Card)** tại trang Chi tiết sản phẩm (`ProductInfo.tsx`) gồm thông tin tóm tắt và nút "Xem Shop" dẫn đến `/shops/:slug`.

### 1.3. Non-goals & Anti-goals
- **Non-goals:**
  - Không triển khai Section Voucher trong đợt này (đã loại bỏ theo yêu cầu người dùng).
  - Không triển khai tìm kiếm Shop trên thanh Search toàn sàn (đã loại bỏ theo yêu cầu người dùng; thay vào đó là tìm kiếm sản phẩm bên trong phạm vi 1 shop).
  - Không cho phép người bán chọn thủ công từng ID sản phẩm vào bộ sưu tập (đã thống nhất: 100% tự động theo logic hệ thống).
- **Anti-goals:**
  - Không cài thêm thư viện kéo thả ngoài (`react-beautiful-dnd`, `@dnd-kit`) gây phình kích thước bundle và xung đột peer-dependency với React 19.3+.
  - Không làm chậm tốc độ tải trang Storefront; các câu truy vấn sản phẩm của shop phải có chỉ mục index và giới hạn phân trang rõ ràng (`limit <= 50`).
  - Tuyệt đối tuân thủ quy tắc coding: Không dùng toán tử ba ngôi `? :` trong JSX và code Java, không dùng `@Data/@Setter`, không vi phạm NullAway.

---

## 2. Các Phương Án Kỹ Thuật & Lý Do Lựa Chọn (Approaches)

### Phương án 1 (Đã được chọn): Endpoint chuyên biệt cho Shop Catalog & Tìm kiếm nội bộ + Kéo thả Native HTML5
- **Backend:**
  - Tạo endpoint `GET /api/shops/{shopId}/products`: Hỗ trợ phân trang, tìm kiếm từ khóa nội bộ `q`, lọc theo `collection` (`NEW_ARRIVALS`, `BEST_SELLER`, `SALE_OFF`), lọc theo danh mục `categoryId`, và sắp xếp (`NEWEST`, `PRICE_ASC`, `PRICE_DESC`, `BEST_SELLER`).
  - Tạo endpoint `GET /api/shops/{shopId}/categories`: Lấy danh sách các danh mục mà shop thực tế đang có sản phẩm `ACTIVE` kèm số lượng sản phẩm.
- **Frontend:**
  - Triển khai kéo-thả native bằng sự kiện `dragstart`, `dragover`, `dragleave`, `drop`, `dragend` của HTML5 với vạch highlight vị trí thả trực quan, kết hợp nút bấm mũi tên dự phòng.
  - Loại bỏ hoàn toàn khối `VOUCHER_CAROUSEL` khỏi Studio và Renderer.
  - Tích hợp thanh tìm kiếm nội bộ trong Shop tại `ShopStorefrontHeader.tsx` và `ShopProductsTab.tsx`.
  - Tích hợp `ShopCard` trên trang Chi tiết sản phẩm (PDP) để dẫn khách vào `/shops/:slug`.
- **Lý do chọn:** Kiến trúc phân tầng rõ ràng, hiệu năng cao, tìm kiếm nội bộ shop hoạt động nhẹ nhàng trên tập sản phẩm của shop mà không cần can thiệp phức tạp vào chỉ mục BM25 toàn sàn, không phụ thuộc thư viện ngoài, tương thích tuyệt đối với React 19.

### Phương án 2 (Đã loại bỏ): Mở rộng endpoint Search BM25 toàn sàn + Cài đặt thư viện ngoài `@dnd-kit`
- Bị loại vì tăng độ phức tạp không cần thiết cho repository BM25 toàn sàn, tiềm ẩn xung đột dependencies của `@dnd-kit` với React 19, và khó tối ưu query riêng cho từng shop.

---

## 3. Thiết Kế Chi Tiết Backend (`fashionstore`)

### 3.1. Database & Schema
- Bảng `shop_decoration`, `product`, `product_variant`, `category`, `order_item` đã có đầy đủ cấu trúc. Không cần tạo migration mới làm thay đổi schema bảng.
- Đảm bảo index hiện có: `idx_product_shop_status (shop_id, status)` và `idx_variant_product (product_id)` được tận dụng tối đa.

### 3.2. Cập Nhật DTO & Enum
1. **`ShopProductCollectionType.java`**:
   ```java
   package com.minhnbnt.fashionstore.dto.shop;

   public enum ShopProductCollectionType {
       NEW_ARRIVALS,
       BEST_SELLER,
       SALE_OFF
   }
   ```

2. **`ShopCategorySummaryResponse.java`**:
   ```java
   package com.minhnbnt.fashionstore.dto.shop;

   import io.swagger.v3.oas.annotations.media.Schema;
   import java.util.UUID;

   public record ShopCategorySummaryResponse(
       @Schema(description = "Id danh mục") UUID id,
       @Schema(description = "Tên danh mục") String name,
       @Schema(description = "Slug danh mục") String slug,
       @Schema(description = "Số lượng sản phẩm của shop trong danh mục") long productCount
   ) {}
   ```

3. **`ShopProductQueryRequest.java`**:
   ```java
   package com.minhnbnt.fashionstore.dto.shop;

   import org.jspecify.annotations.Nullable;
   import java.util.UUID;

   public record ShopProductQueryRequest(
       @Nullable String q,
       @Nullable ShopProductCollectionType collection,
       @Nullable UUID categoryId,
       @Nullable String sort,
       int page,
       int limit
   ) {}
   ```

### 3.3. Tầng Repository: `ShopCatalogQueryRepository.java`
Tạo repository sử dụng Spring `JdbcClient` để truy vấn tối ưu cho sản phẩm công khai của shop:
- **`findShopProducts(UUID shopId, ShopProductQueryRequest request)`**:
  - Điều kiện tiên quyết: `p.shop_id = :shopId AND p.status = 'ACTIVE'`.
  - **Tìm kiếm từ khóa nội bộ shop:** Nếu `request.q() != null && !request.q().isBlank()`:
    ```sql
    AND (p.name ILIKE '%' || :q || '%' OR p.description ILIKE '%' || :q || '%')
    ```
  - Nếu có `categoryId`: thêm `AND p.category_id = :categoryId`.
  - Nếu `collection == SALE_OFF`:
    ```sql
    AND EXISTS (
        SELECT 1 FROM product_variant pv 
        WHERE pv.product_id = p.id 
          AND pv.compare_at_price IS NOT NULL 
          AND pv.compare_at_price > pv.price
    )
    ```
  - Sắp xếp (`ORDER BY`):
    - `NEW_ARRIVALS` hoặc `sort == "NEWEST"`: `p.created_at DESC`
    - `BEST_SELLER`: `COALESCE(total_sold, 0) DESC, p.created_at DESC` (với `total_sold` tính từ subquery `SUM(oi.quantity)` join `orders o` với `o.status != 'CANCELLED'`)
    - `SALE_OFF`: Tỷ lệ giảm giá `MAX((pv.compare_at_price - pv.price)::float / pv.compare_at_price) DESC`
    - `PRICE_ASC`: Giá variant thấp nhất tăng dần
    - `PRICE_DESC`: Giá variant thấp nhất giảm dần
- **`findShopCategories(UUID shopId)`**:
  - Truy vấn các danh mục có sản phẩm `ACTIVE` của shop:
    ```sql
    SELECT c.id, c.name, c.slug, COUNT(p.id) AS product_count
    FROM category c
    JOIN product p ON p.category_id = c.id
    WHERE p.shop_id = :shopId AND p.status = 'ACTIVE'
    GROUP BY c.id, c.name, c.slug
    HAVING COUNT(p.id) > 0
    ORDER BY product_count DESC, c.name ASC
    ```

### 3.4. Tầng Service: `ShopCatalogService.java`
- Đóng gói logic chuyển đổi kết quả query sang `PageResponse<ProductResponse>` tái sử dụng mapper `CatalogMapper` để ánh xạ ảnh, variants, rating trung bình.
- Validate shop tồn tại và ở trạng thái `APPROVED`. Nếu shop chưa duyệt hoặc bị khóa (`SUSPENDED`/`PENDING`), trả về lỗi `404 Not Found`.

### 3.5. Tầng Controller: `ShopCatalogController.java`
- `@RestController` với `@RequestMapping("/api/shops/{shopId}")`:
  - `GET /products`:
    - `@Operation(operationId = "getShopProducts", summary = "Lấy danh sách sản phẩm công khai của shop (kèm tìm kiếm nội bộ)")`
    - Nhận:
      - `@PathVariable UUID shopId`
      - `@RequestParam(required = false) @Nullable String q` (Từ khóa tìm kiếm trong shop)
      - `@RequestParam(required = false) @Nullable ShopProductCollectionType collection`
      - `@RequestParam(required = false) @Nullable UUID categoryId`
      - `@RequestParam(required = false) @Nullable String sort`
      - `@RequestParam(defaultValue = "0") int page`
      - `@RequestParam(defaultValue = "12") int limit`
    - Trả về `PageResponse<ProductResponse>`.
  - `GET /categories`:
    - `@Operation(operationId = "getShopCategories", summary = "Lấy danh mục sản phẩm của shop")`
    - Trả về `List<ShopCategorySummaryResponse>`.

---

## 4. Thiết Kế Chi Tiết Frontend (`fashionstore-ui`)

### 4.1. Nâng Cấp Studio Trang Trí: Kéo Thả Section (`SellerDecorationTab.tsx`)
1. **Loại bỏ Voucher:** Xóa bỏ tùy chọn `VOUCHER_CAROUSEL` khỏi modal thêm module và danh sách hiển thị.
2. **Cơ chế kéo-thả Native HTML5:**
   - State quản lý: `draggedIndex: number | null`, `dragOverIndex: number | null`.
   - Mỗi thẻ module trong danh sách có icon kéo (GripVertical / DotsSixVertical) kèm thuộc tính:
     - `draggable={can('DECORATION_WRITE')}`
     - `onDragStart={(e) => handleDragStart(idx)}`
     - `onDragOver={(e) => handleDragOver(e, idx)}`
     - `onDragLeave={() => setDragOverIndex(null)}`
     - `onDrop={(e) => handleDrop(idx)}`
     - `onDragEnd={() => handleDragEnd()}`
   - Hiệu ứng thị giác: Khi kéo module qua vị trí khác, hiển thị đường kẻ màu tím indigo (`border-t-2 border-indigo-600`) báo hiệu điểm thả.
   - Vẫn duy trì nút bấm mũi tên Lên/Xuống (`handleMoveUp`, `handleMoveDown`) để đảm bảo khả năng tiếp cận (Accessibility) hoặc khi thao tác trên thiết bị cảm ứng.

### 4.2. Khách Hàng Xem Gian Hàng & Tìm Kiếm Nội Bộ (`ShopStorefront.tsx` & Components)
1. **Thanh Tìm Kiếm Trong Shop:**
   - Tích hợp tại thanh điều hướng `ShopStorefrontHeader.tsx` và đầu tab `ShopProductsTab.tsx`.
   - Gồm input tìm kiếm kèm icon kính lúp, placeholder *"Tìm trong Shop này..."*, nút xóa nhanh từ khóa (Clear X).
   - Khi khách gõ từ khóa và submit (Enter hoặc bấm nút tìm):
     - Tự động kích hoạt chuyển sang tab `PRODUCTS` ("Tất cả sản phẩm").
     - Gọi API `useGetShopProducts({ shopId, q: searchKeyword, ... })`.
     - Hiển thị thông báo kết quả tìm kiếm: *"Kết quả tìm kiếm cho '[từ khóa]' trong shop ([số lượng] sản phẩm)"*, kèm nút *"Xóa bộ lọc"* để quay lại danh mục đầy đủ.
2. **Render Layout Trang Trí (`ShopDecorationRenderer.tsx`):**
   - Khối `BANNER_SLIDER`: Giữ nguyên hiệu ứng xoay vòng carousel (tối đa 5 banner), dot điều hướng, linkUrl chuyển trang.
   - Khối `PRODUCT_COLLECTION`:
     - Tích hợp hook gọi API `GET /api/shops/{shopId}/products?collection={collectionType}&limit={itemLimit}`.
     - Hiển thị Skeleton loading khi đang tải.
     - **Quy tắc rỗng:** Nếu kết quả trả về không có sản phẩm nào (`items.length === 0`), component return `null` (tự động ẩn hoàn toàn khối đó khỏi giao diện khách hàng).
   - Khối `CATEGORY_GRID`:
     - Tích hợp hook gọi API `GET /api/shops/{shopId}/categories`.
     - Hiển thị danh sách danh mục thực tế của shop kèm số lượng sản phẩm. Bấm vào danh mục sẽ chuyển sang tab "Tất cả sản phẩm" và tự động lọc theo danh mục đó.
   - Xóa bỏ `VoucherCarouselBlock`.
3. **Tab Tất Cả Sản Phẩm (`ShopProductsTab.tsx`):**
   - Thay thế toàn bộ mảng `products` mock bằng API `useGetShopProducts({ shopId, q, categoryId, sort, page, limit })`.
   - Cung cấp thanh lọc nhanh theo danh mục của shop và dropdown sắp xếp: *Mới nhất*, *Giá tăng dần*, *Giá giảm dần*, *Bán chạy nhất*.
   - Phân trang đầy đủ chuẩn UI nét.

### 4.3. Điểm Chạm Vào Shop: Thẻ Shop Tại Trang Chi Tiết Sản Phẩm
1. **Component `PdpShopCard.tsx`:**
   - Được nhúng vào trang [ProductDetail.tsx](file:///d:/fashionstore/fashionstore-ui/src/pages/ProductDetail.tsx) (ngay phía dưới `ProductInfo` và trên `ProductTabs`).
   - Hiển thị:
     - Avatar logo shop (tròn) + Tên shop + Badge "Chính hãng / Yêu thích".
     - Thống kê: Đánh giá ⭐ trung bình, Số lượng sản phẩm, Thời gian chuẩn bị hàng (DTS 1-3 ngày).
     - 2 nút bấm tương tác:
       - Nút **"Xem Shop"** (Outline button): Điều hướng đến `/shops/${shop.slug}`.
       - Nút **"Chat Ngay"** (Ghost button): Mở modal chat trực tiếp với Shop.
2. **Liên kết từ Giỏ Hàng (`Cart.tsx`):**
   - Tại mỗi nhóm sản phẩm theo shop trong giỏ hàng, tên shop được bọc trong thẻ `<Link to={`/shops/${shopSlug}`}>` để khách bấm trực tiếp vào gian hàng.

---

## 5. Kế Hoạch Kiểm Thử & Xác Nhận (Verification)

### 5.1. Backend Testing
- **Unit Test:**
  - `ShopCatalogQueryRepositoryTest`: Kiểm tra:
    - Tìm kiếm theo từ khóa `q` (so khớp tên/mô tả sản phẩm).
    - Lọc theo `collection`: `NEW_ARRIVALS`, `BEST_SELLER`, `SALE_OFF`.
    - Lọc theo danh mục `categoryId` và sắp xếp theo giá/mới nhất.
    - Truy vấn danh mục của shop có sản phẩm active kèm số lượng sản phẩm.
  - `ShopCatalogControllerTest`: Kiểm tra response API `GET /api/shops/{shopId}/products` (có và không có `q`) và `GET /api/shops/{shopId}/categories` trả về mã 200 kèm DTO chuẩn OpenAPI, kiểm tra trả về 404 nếu shop không tồn tại hoặc chưa duyệt.
- **Quy tắc kiểm tra:** Chạy `./gradlew test` xanh 100%, không vi phạm ternary `? :`, không vi phạm NullAway.

### 5.2. Frontend Testing
- **Unit & Component Test (Vitest):**
  - `SellerDecorationTab.test.tsx`: Kiểm tra render Studio, kéo-thả reorder các block, không còn block Voucher, lưu nháp và xuất bản thành công.
  - `ShopStorefront.test.tsx`: Kiểm tra render Header, thanh tìm kiếm nội bộ hoạt động, chuyển tab và tìm kiếm sản phẩm theo từ khóa `q`, gọi API tải sản phẩm thật cho bộ sưu tập, ẩn bộ sưu tập khi rỗng.
  - `PdpShopCard.test.tsx`: Kiểm tra hiển thị thông tin shop và nút chuyển hướng đến đúng đường dẫn `/shops/:slug`.
- **Lint & Build:** Chạy `pnpm format && pnpm lint && pnpm build` đạt 0 error, 0 warning.

### 5.3. Kiểm Thử Trực Quan (Browser Subagent)
- Mở Kênh Người Bán -> Studio Trang Trí Gian Hàng:
  - Kiểm tra upload banner slider (tối đa 5 banner).
  - Thao tác kéo-thả thay đổi thứ tự các module, kiểm tra vạch highlight vị trí thả.
  - Bấm "Lưu nháp" và "Áp dụng gian hàng".
- Mở Trang Chi Tiết Sản Phẩm (PDP):
  - Kiểm tra hiển thị Thẻ Shop (Shop Card), bấm "Xem Shop".
- Mở Trang Storefront Khách Hàng (`/shops/:slug`):
  - Kiểm tra Banner slider tự động chạy, Bộ sưu tập hiển thị sản phẩm thật.
  - Nhập từ khóa vào ô *"Tìm trong Shop này..."* và Enter -> Kiểm tra hiển thị đúng sản phẩm khớp từ khóa.

---

## 6. Những Việc Giao Lại Cho Người Triển Khai (What's Left to Builder)
- Tinh chỉnh khoảng cách CSS pixel và hiệu ứng bóng mờ (box-shadow) trên `PdpShopCard` sao cho hài hòa với theme hiện tại.
- Xử lý cache invalidation trên TanStack Query khi người bán vừa xuất bản giao diện mới để khách hàng xem được ngay lập tức.
