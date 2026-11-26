# Multi-Garment Upload & Virtual Try-On Garment Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cho phép người bán tải lên nhiều ảnh Garment tự do (1–9 ảnh) cho một sản phẩm, và cho phép người mua trên trang Phòng Thử Đồ Ảo AI (`/try-on/:id`) nhìn hình và bấm chọn mẫu ảnh Garment muốn mặc thử.

**Architecture:**
1. **Backend (`fashionstore`)**: Tạo bảng `product_garment` (`id`, `product_id`, `upload_id REFERENCES upload(id)`, `sort_order`, `created_at`) kèm migration backfill từ `product.garment_upload_id`. Mở rộng `CreateProductRequest`, `UpdateProductRequest` nhận `garmentUploadIds: List<UUID>` và `ProductResponse` trả về `garments: List<GarmentImageResponse>` (đồng thời tự động đồng bộ phần tử đầu tiên với `garmentUploadId` / `garmentUrl` để tương thích ngược 100%).
2. **Frontend Seller (`fashionstore-ui`)**: Nâng cấp `ProductMediaUploader.tsx`, `SellerAddProductTab.tsx`, và `SellerEditProductTab.tsx` để hỗ trợ chọn nhiều file ảnh Garment cùng lúc (tối đa 9 ảnh), sắp xếp thứ tự, xóa từng ảnh, và gửi `garmentUploadIds` + `garmentUploadId` lên API.
3. **Frontend Buyer (`VirtualTryOn.tsx`)**: Ở Cột 1 (`1. Trang phục đang thử`), hiển thị dải hình thu nhỏ (thumbnails) của tất cả các ảnh Garment đã tải lên để người mua bấm chọn mẫu muốn thử; khi bấm `"Bắt đầu Thử Đồ Ảo AI"`, đính kèm chính xác URL ảnh Garment đang chọn (`activeGarmentUrl`) vào yêu cầu gọi tool `tryOn`.

**Tech Stack:** Spring Boot 4 / Java 25, Spring Data JDBC, Flyway (PostgreSQL), React 19, TypeScript, Tailwind CSS v4, Orval OpenAPI generator, Vitest + Testing Library.

## Global Constraints

- **Cấm tuyệt đối toán tử 3 ngôi (`? :`)** trong mọi file Java và TypeScript/TSX. Luôn dùng `if / else` hoặc `&&` trong JSX.
- Luôn dùng `pnpm` trong `d:\fashionstore\fashionstore-ui` và `.\gradlew.bat` trong `d:\fashionstore\fashionstore`.
- Tương thích ngược hoàn toàn với các sản phẩm cũ chỉ có `garmentUploadId` / `garmentUrl` đơn lẻ.

## Review Focus

1. **Tương thích ngược dữ liệu cũ**: Sản phẩm cũ chỉ có `product.garment_upload_id` phải được backfill sang `product_garment` qua Flyway `V51__product_garment_images.sql`, và nếu API chỉ truyền `garmentUploadId` đơn lẻ thì backend vẫn tự hiểu là danh sách 1 phần tử.
2. **Bảo vệ file S3 khỏi Garbage Collector (`upload_gc`)**: Cột `product_garment.upload_id` bắt buộc có `REFERENCES upload (id)` (không `ON DELETE CASCADE`) giống `product_image.upload_id` để consumer `upload_gc` không xóa nhầm file đang gắn với sản phẩm.
3. **Ràng buộc khi bật `tryOnEnabled = true`**: Cả Backend và Frontend phải chặn lưu nếu người bán bật `tryOnEnabled = true` nhưng xóa hết toàn bộ ảnh Garment (`garments.length === 0`).
4. **Giới hạn số lượng ảnh Garment**: Tối đa 9 ảnh Garment trên mỗi sản phẩm; nếu người bán chọn vượt quá 9 ảnh thì báo lỗi tiếng Việt rõ ràng và không làm mất danh sách ảnh hiện có.
5. **Chọn ảnh Garment khi thử đồ**: Khi người mua bấm chọn ảnh Garment thứ 2 hoặc thứ 3 ở Cột 1 trang `/try-on/:id`, khung ảnh lớn phải đổi ngay sang ảnh đó và lệnh gửi tới AI khi bấm `"Bắt đầu Thử Đồ Ảo AI"` phải chứa đúng URL của ảnh Garment đã chọn.

---

### Task 1: Backend Multi-Garment Persistence & API (`V51__product_garment_images.sql`, `ProductGarment`, `CatalogService`, OpenAPI)

**Files:**
- Create: `d:\fashionstore\fashionstore\src\main\resources\db\migration\V51__product_garment_images.sql`
- Create: `d:\fashionstore\fashionstore\src\main\java\com\minhnbnt\fashionstore\model\ProductGarment.java`
- Create: `d:\fashionstore\fashionstore\src\main\java\com\minhnbnt\fashionstore\features\catalog\repository\ProductGarmentRepository.java`
- Create: `d:\fashionstore\fashionstore\src\main\java\com\minhnbnt\fashionstore\dto\catalog\GarmentImageResponse.java`
- Modify: `d:\fashionstore\fashionstore\src\main\java\com\minhnbnt\fashionstore\dto\catalog\CreateProductRequest.java`
- Modify: `d:\fashionstore\fashionstore\src\main\java\com\minhnbnt\fashionstore\dto\catalog\UpdateProductRequest.java`
- Modify: `d:\fashionstore\fashionstore\src\main\java\com\minhnbnt\fashionstore\dto\catalog\ProductResponse.java`
- Modify: `d:\fashionstore\fashionstore\src\main\java\com\minhnbnt\fashionstore\features\catalog\CatalogMapper.java`
- Modify: `d:\fashionstore\fashionstore\src\main\java\com\minhnbnt\fashionstore\features\catalog\CatalogService.java`
- Modify: `d:\fashionstore\fashionstore\src\test\java\com\minhnbnt\fashionstore\features\catalog\CatalogServiceTest.java`

**Interfaces:**
- Produces:
  - `GarmentImageResponse(UUID id, String url, UUID uploadId, Integer sortOrder)`
  - `CreateProductRequest.garmentUploadIds()` & `UpdateProductRequest.garmentUploadIds()` (`@Nullable @Size(max = 9) List<UUID>`)
  - `ProductResponse.garments()` (`List<GarmentImageResponse>`)
  - Updated `openapi.json` synced to `d:\fashionstore\fashionstore-ui\openapi.json`

- [ ] **Step 1: Tạo migration `V51__product_garment_images.sql`, entity `ProductGarment`, repository `ProductGarmentRepository`, và DTO `GarmentImageResponse`**
- Tạo bảng `product_garment` và backfill từ `product.garment_upload_id`.
- Tạo `ProductGarment`, `ProductGarmentRepository`, và `GarmentImageResponse`.

- [ ] **Step 2: Mở rộng `CreateProductRequest`, `UpdateProductRequest`, `ProductResponse`, `CatalogMapper`, và `CatalogService` (giữ constructor tương thích ngược)**
- Bổ sung `garmentUploadIds` vào `CreateProductRequest` và `UpdateProductRequest` (kèm constructor nạp chồng để toàn bộ code/test cũ không cần sửa).
- Bổ sung `List<GarmentImageResponse> garments` vào `ProductResponse` và `CatalogMapper.toResponse`.
- Trong `CatalogService.createProduct` và `CatalogService.updateProduct`:
  - Chuẩn hóa danh sách `effectiveGarmentUploadIds` từ `request.garmentUploadIds()` (nếu có và khác rỗng) hoặc từ `request.garmentUploadId()` (nếu có).
  - Nếu `tryOnEnabled` bật mà `effectiveGarmentUploadIds.isEmpty()` thì ném `400 BAD_REQUEST "Garment image is required when virtual try-on is enabled"`.
  - Đồng bộ `primaryGarmentUploadId` (phần tử đầu tiên hoặc `null`) vào `product.garmentUploadId`, đồng thời lưu danh sách `ProductGarment` theo `sortOrder = 0..N-1`.

- [ ] **Step 3: Viết unit test trong `CatalogServiceTest.java` kiểm tra tạo/cập nhật nhiều ảnh Garment và chạy test**
Run (in `d:\fashionstore\fashionstore`):
```bash
.\gradlew.bat test --tests com.minhnbnt.fashionstore.features.catalog.CatalogServiceTest
```
Expected: PASS.

- [ ] **Step 4: Cập nhật `openapi.json` và chạy `orval` ở `fashionstore-ui`**
Cập nhật schema `CreateProductRequest`, `UpdateProductRequest`, `ProductResponse`, `GarmentImageResponse` trong `openapi.json` và chạy `pnpm run api:gen` (hoặc `orval`) để sinh TypeScript models.

---

### Task 2: Giao diện Người bán hỗ trợ Upload nhiều ảnh Garment (`ProductMediaUploader.tsx`, `SellerAddProductTab.tsx`, `SellerEditProductTab.tsx`)

**Files:**
- Modify: `d:\fashionstore\fashionstore-ui\src\components\seller\ProductMediaUploader.tsx`
- Modify: `d:\fashionstore\fashionstore-ui\src\components\seller\ProductMediaUploader.test.tsx`
- Modify: `d:\fashionstore\fashionstore-ui\src\components\seller-dashboard-tab\products\SellerAddProductTab.tsx`
- Modify: `d:\fashionstore\fashionstore-ui\src\components\seller-dashboard-tab\products\SellerEditProductTab.tsx`
- Modify: `d:\fashionstore\fashionstore-ui\src\components\seller-dashboard-tab\products\SellerEditProductTab.tryOn.test.tsx`

**Interfaces:**
- Consumes: `CreateProductRequest.garmentUploadIds`, `UpdateProductRequest.garmentUploadIds`, `ProductResponse.garments`
- Produces: Multi-garment uploader UI (1–9 images, reorder left/right, delete individual garment image, multi-file input)

- [ ] **Step 1: Viết thêm unit test trong `ProductMediaUploader.test.tsx` và `SellerEditProductTab.tryOn.test.tsx` cho phép upload nhiều ảnh Garment**
- Kiểm tra chọn nhiều file ảnh Garment cùng lúc, giới hạn tối đa 9 ảnh Garment, xóa ảnh Garment và gửi `garmentUploadIds` khi lưu ở `SellerEditProductTab`.

- [ ] **Step 2: Cập nhật `ProductMediaUploader.tsx`, `SellerAddProductTab.tsx`, và `SellerEditProductTab.tsx`**
- Trong `ProductMediaUploader.tsx`: hỗ trợ mảng `garments: ProductMediaItem[]` và `onGarmentsChange: (garments: ProductMediaItem[]) => void` (giữ tương thích với prop `garment`/`onGarmentChange` cũ nếu truyền vào). Hiển thị lưới ảnh Garment (tối đa 9 ảnh), cho phép chọn nhiều ảnh một lúc (`multiple`), di chuyển trái/phải và xóa từng ảnh.
- Trong `SellerAddProductTab.tsx` và `SellerEditProductTab.tsx`: quản lý state `garments: ProductMediaItem[]`, prefill từ `p.garments` (fallback `p.garmentUrl` + `p.garmentUploadId`), validate có ít nhất 1 ảnh Garment khi `tryOnEnabled === true`, và gửi `garmentUploadIds` + `garmentUploadId` (ảnh đầu tiên) lên backend.

- [ ] **Step 3: Chạy test xác nhận PASS**
Run (in `d:\fashionstore\fashionstore-ui`):
```bash
pnpm test src/components/seller/ProductMediaUploader.test.tsx src/components/seller-dashboard-tab/products/SellerEditProductTab.tryOn.test.tsx
```
Expected: PASS.

---

### Task 3: Giao diện Người mua chọn ảnh Garment trên Trang Phòng Thử Đồ Ảo AI (`VirtualTryOn.tsx`, `VirtualTryOn.test.tsx`)

**Files:**
- Modify: `d:\fashionstore\fashionstore-ui\src\pages\VirtualTryOn.tsx`
- Modify: `d:\fashionstore\fashionstore-ui\src\pages\VirtualTryOn.test.tsx`

**Interfaces:**
- Consumes: `ProductResponse.garments` (fallback `product.garmentUrl`, `product.images`)
- Produces: Garment thumbnail selector in Column 1 of `/try-on/:id` and passes the selected garment URL in the AI try-on request

- [ ] **Step 1: Viết unit test trong `src/pages/VirtualTryOn.test.tsx` kiểm tra hiển thị danh sách nhiều ảnh Garment và bấm chọn ảnh Garment để thử**
- Khi sản phẩm có nhiều ảnh trong `product.garments` (ví dụ 3 ảnh Garment), hiển thị đủ 3 nút chọn mẫu ảnh Garment ở Cột 1.
- Khi người mua bấm chọn mẫu Garment thứ 2, khung ảnh lớn ở Cột 1 đổi `src` sang URL của mẫu Garment thứ 2.

- [ ] **Step 2: Cập nhật `src/pages/VirtualTryOn.tsx`**
- Tổng hợp danh sách `garmentList` từ `product.garments` (sắp xếp theo `sortOrder`), nếu rỗng thì fallback về `product.garmentUrl`.
- Lưu state `selectedGarmentIndex` (mặc định `0`).
- Ở Cột 1 (`1. Trang phục đang thử`):
  - Hiển thị ảnh Garment đang chọn ở khung lớn.
  - Khi có từ 2 ảnh Garment trở lên (hoặc luôn hiển thị dải thumbnail Garment gọn gàng bên dưới khung ảnh lớn), render dải nút hình ảnh thu nhỏ (`aria-label={'Chọn mẫu trang phục ' + (idx + 1)}`, `aria-pressed={isSelected}`) để người mua nhìn hình và bấm chọn trực tiếp.
  - Khi bấm `"Bắt đầu Thử Đồ Ảo AI"`, đính kèm rõ URL của ảnh Garment đang chọn (`activeGarmentUrl`) vào prompt gửi cho AI agent để tool `tryOn` sử dụng đúng bức ảnh Garment mà người mua đã chọn.

- [ ] **Step 3: Chạy test xác nhận PASS**
Run (in `d:\fashionstore\fashionstore-ui`):
```bash
pnpm test src/pages/VirtualTryOn.test.tsx
```
Expected: PASS.
