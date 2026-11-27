# Virtual Try-On (Trang Phòng Thử Đồ Ảo Riêng & Cải Tiến Luồng Try-On) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng trang **Phòng Thử Đồ Ảo AI (`/try-on/:id`)** độc lập cho phép người mua kéo-thả (drag & drop) ảnh bản thân để mặc thử sản phẩm, xem so sánh Trước/Sau, phóng to và tải ảnh kết quả về máy (thay vì phải mở khung chat), đồng thời hoàn thiện validate & lưu lịch sử ảnh Try-On.

**Architecture:**
1. Tạo trang riêng `src/pages/VirtualTryOn.tsx` gắn với route `/try-on/:id` (bọc trong `RequireAuth`). Từ trang Chi tiết sản phẩm (`ProductInfo.tsx`), khi `product.tryOnEnabled === true`, hiển thị nút **"Thử đồ ảo với AI"** điều hướng thẳng sang `/try-on/:id`.
2. Trên trang `VirtualTryOn.tsx`, người dùng chỉ cần kéo-thả hoặc chọn ảnh cá nhân (upload lên S3 qua `uploadImageWithUrl`) rồi bấm **"Bắt đầu Thử Đồ Ảo AI"** — hoàn toàn không cần chọn loại trang phục thủ công. Hệ thống và `ai-backend` tự động nhận diện loại trang phục (`top` / `bottom` / `full`) dựa trên thông tin danh mục, tên/mô tả sản phẩm (`getProductDetail`) và thị giác máy tính (`readImage`), sau đó gọi tool `tryOn` và trả về ảnh kết quả kèm chế độ so sánh Trước/Sau, phóng to (`MediaLightbox`) và nút **"Tải ảnh về máy"**.
3. Hoàn thiện các điểm còn lại từ rà soát: lưu/khôi phục `imageUrl` trong `agentHistory.ts` & `AgentTab.tsx`, bổ sung phóng to & tải ảnh cho `ShowImageBlock`, và bổ sung validate tiếng Việt bắt buộc ảnh Garment trong `SellerEditProductTab.tsx`.

**Tech Stack:** React 19, TypeScript, React Router v7, Tailwind CSS v4, TanStack Query v5, Vercel AI SDK (`@ai-sdk/react`), Vitest + Testing Library (`happy-dom`).

**Spec:** Yêu cầu của người dùng ngày 2026-11-27: *"Tôi muốn thử đồ ở 1 trang riêng được không, có thể kéo ảnh bản thân vào để thử, thay vì mở đoạn chat để thử, và cho phép tải ảnh về"* kết hợp khắc phục 5 điểm rà soát Try-On.

## Global Constraints

- **Tuyệt đối không dùng toán tử 3 ngôi (`? :`)** trong mọi file `.ts` / `.tsx`. Luôn dùng `if / else` hoặc toán tử `&&` trong JSX.
- Sử dụng `pnpm` cho mọi lệnh quản lý package, format (`pnpm format`), lint (`pnpm lint`), build (`pnpm build`) và test (`pnpm test`) tại `d:\fashionstore\fashionstore-ui`.
- Giữ tương thích ngược 100% với các bài test hiện có.

## Review Focus

1. **Kéo-thả file (Drag & Drop) trên `/try-on/:id`:** Kéo file không phải ảnh (`.txt`, `.pdf`) hoặc ảnh `> 5MB` vào vùng dropzone phải chặn upload và hiển thị thông báo lỗi tiếng Việt rõ ràng (`"Chỉ nhận file ảnh."` / `"Ảnh tối đa 5 MB."`).
2. **Trích xuất ảnh kết quả từ `ai-backend`:** Trang `VirtualTryOn.tsx` phải lấy được URL ảnh kết quả ngay khi `tool-tryOn` trả về `output.image.url` hoặc khi `tool-showImage` trả về `output.url` (kể cả khi LLM dừng ngay sau bước `tryOn`).
3. **Tải ảnh kết quả về máy:** Khi bấm **"Tải ảnh về máy"**, ưu tiên `fetch(resultUrl)` dạng `Blob` để kích hoạt tải file `thu-do-<slug>.png` trực tiếp về máy; nếu CORS chặn `fetch` trực tiếp thì fallback sang mở/tải qua thẻ `<a download target="_blank">`.
4. **Sản phẩm chưa bật `tryOnEnabled`:** Nếu người dùng truy cập trực tiếp `/try-on/:id` của sản phẩm có `tryOnEnabled === false`, hiển thị thông báo sản phẩm này chưa hỗ trợ thử đồ ảo kèm nút quay lại `/product/:id`.
5. **Sửa sản phẩm (`SellerEditProductTab.tsx`):** Khi `tryOnEnabled === true` nhưng thiếu ảnh Garment, chặn lưu ngay trên Frontend bằng thông báo tiếng Việt.

---

### Task 1: Trang Phòng Thử Đồ Ảo Riêng (`VirtualTryOn.tsx`), Route `/try-on/:id` & Nút CTA Trên Trang Sản Phẩm (`ProductInfo.tsx`)

**Files:**
- Create: `d:\fashionstore\fashionstore-ui\src\pages\VirtualTryOn.tsx`
- Modify: `d:\fashionstore\fashionstore-ui\src\pages\index.ts:1-15`
- Modify: `d:\fashionstore\fashionstore-ui\src\App.tsx:13-48,100-185`
- Modify: `d:\fashionstore\fashionstore-ui\src\components\pdp\ProductInfo.tsx:285-315`
- Create: `d:\fashionstore\fashionstore-ui\src\pages\VirtualTryOn.test.tsx`
- Modify: `d:\fashionstore\fashionstore-ui\src\components\pdp\ProductInfo.test.tsx`

**Interfaces:**
- Produces:
  - Route: `/try-on/:id` trong `App.tsx` (bọc trong `<RequireAuth><VirtualTryOn /></RequireAuth>`).
  - Component `VirtualTryOn(props: { config?: AgentConfig; transport?: ChatTransport<AgentUIMessage> })` trong `src/pages/VirtualTryOn.tsx`:
    - Cho phép inject `config` và `transport` để test độc lập giống `AgentTab`.
    - Hỗ trợ:
      1. Hiển thị thông tin sản phẩm đang thử (tên, giá, ảnh bìa, ảnh Garment phẳng, link quay lại `/product/:id`). Hệ thống tự động nhận diện loại trang phục (`top` / `bottom` / `full`) từ dữ liệu sản phẩm và AI mà không bắt người dùng chọn thủ công.
      2. Vùng **Kéo & Thả ảnh cá nhân (Drag & Drop)** (`data-testid="tryon-dropzone"` + `<input type="file" accept="image/*" aria-label="Tải ảnh cá nhân để thử đồ" />`):
         - Hỗ trợ kéo-thả (`onDragOver`, `onDragLeave`, `onDrop`) và click chọn file.
         - Validate định dạng `image/*` và dung lượng `<= 5 MB`, tự động gọi `uploadImageWithUrl(file)`.
         - Hiển thị ảnh xem trước của người dùng, nút `"Đổi ảnh khác"` / `"Gỡ ảnh"`.
      3. Nút **"Bắt đầu Thử Đồ Ảo AI"**:
         - Gửi yêu cầu tới `ai-backend` kèm `context: { route: '/product/' + productId, filters: EMPTY_FILTERS, productId, image: uploadedImage }`.
         - Hiển thị tiến trình đang tạo ảnh (`"AI đang ghép trang phục lên ảnh của bạn, vui lòng đợi 15–30 giây…"`).
      4. Khung **Kết quả Thử Đồ Ảo**:
         - Hiển thị ảnh kết quả (`alt="Ảnh kết quả thử đồ"`).
         - Nút bật/tắt **"So sánh Trước / Sau"** (đặt ảnh gốc của khách và ảnh kết quả cạnh nhau).
         - Bấm vào ảnh kết quả mở `MediaLightbox` phóng to toàn màn hình.
         - Nút **"Tải ảnh về máy"** (`aria-label="Tải ảnh kết quả thử đồ về máy"`) tải file ảnh kết quả về thiết bị.
  - Trong `ProductInfo.tsx`:
    - Khi `p.tryOnEnabled === true && p.id !== undefined`, hiển thị khối CTA nổi bật với nút/link **"Thử đồ ảo với AI"** dẫn tới `/try-on/${p.id}`.

- [ ] **Step 1: Viết các bài test thất bại (RED) trong `ProductInfo.test.tsx` và `VirtualTryOn.test.tsx`**

1. Trong `ProductInfo.test.tsx`:
   - Khi `product.tryOnEnabled === true`, hiển thị link/nút `"Thử đồ ảo với AI"` trỏ tới `/try-on/p-1`.
   - Khi `product.tryOnEnabled` là `false` hoặc `undefined`, không hiển thị `"Thử đồ ảo với AI"`.
2. Trong `VirtualTryOn.test.tsx`:
   - Test 1: Render thông tin sản phẩm (`Áo Sơ Mi Lụa`) và vùng kéo-thả ảnh (`data-testid="tryon-dropzone"`) mà không bắt người dùng chọn loại trang phục thủ công.
   - Test 2: Kéo-thả (`fireEvent.drop`) một file ảnh hợp lệ vào vùng dropzone -> gọi `uploadImageWithUrl`, hiển thị ảnh preview cá nhân và bật nút `"Bắt đầu Thử Đồ Ảo AI"`.
   - Test 3: Kéo-thả file không phải ảnh hoặc ảnh `> 5MB` -> hiển thị thông báo lỗi `"Chỉ nhận file ảnh."` / `"Ảnh tối đa 5 MB."` và không upload.
   - Test 4: Bấm `"Bắt đầu Thử Đồ Ảo AI"` -> hiển thị trạng thái chờ khi `tryOn` đang chạy, sau đó hiển thị `"Ảnh kết quả thử đồ"`, hỗ trợ bật `"So sánh Trước / Sau"`, phóng to ảnh và bấm `"Tải ảnh về máy"`.

- [ ] **Step 2: Chạy test để xác nhận RED**

Run: `npx vp test run src/components/pdp/ProductInfo.test.tsx src/pages/VirtualTryOn.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Triển khai `VirtualTryOn.tsx`, đăng ký route `/try-on/:id` trong `App.tsx` & `pages/index.ts`, và thêm nút CTA trong `ProductInfo.tsx`**

- Tạo `src/pages/VirtualTryOn.tsx` tuân thủ quy tắc không dùng toán tử 3 ngôi (`? :`).
- Đăng ký `VirtualTryOn` trong `src/pages/index.ts` và route `/try-on/:id` trong `src/App.tsx`.
- Bổ sung khối CTA `"Thử đồ ảo với AI"` trong `src/components/pdp/ProductInfo.tsx`.

- [ ] **Step 4: Chạy lại test để xác nhận GREEN**

Run: `npx vp test run src/components/pdp/ProductInfo.test.tsx src/pages/VirtualTryOn.test.tsx`
Expected: PASS toàn bộ.

---

### Task 2: Lưu & Khôi Phục Ảnh Đính Kèm Trong Lịch Sử Chat AI + Phóng To / Tải Ảnh Trong `AgentTab.tsx` (Khắc phục Điểm 3 & Điểm 4)

**Files:**
- Modify: `d:\fashionstore\fashionstore-ui\src\lib\ai\agentHistory.ts:1-86`
- Modify: `d:\fashionstore\fashionstore-ui\src\components\chat\AgentTab.tsx:120-132,521-571,605-646`
- Test: `d:\fashionstore\fashionstore-ui\src\lib\ai\agentHistory.test.ts`
- Test: `d:\fashionstore\fashionstore-ui\src\components\chat\AgentTab.test.tsx`

**Interfaces:**
- Produces in `agentHistory.ts`:
  ```ts
  export function serializeUserMessageMetadata(
    text: string,
    imageUrl?: string,
  ): string

  export function getMessageImageUrl(message: AgentUIMessage): string | undefined
  ```

- [ ] **Step 1: Viết các bài test thất bại (RED) trong `agentHistory.test.ts` và `AgentTab.test.tsx`**

- Trong `agentHistory.test.ts`: Kiểm tra `serializeUserMessageMetadata` và `deserializeAgentMessage` lưu và khôi phục `imageUrl` qua `getMessageImageUrl(restored)`.
- Trong `AgentTab.test.tsx`: Kiểm tra `ShowImageBlock` cho phép phóng to ảnh kết quả thử đồ qua `MediaLightbox` và có liên kết `"Tải ảnh về"` (`download`).

- [ ] **Step 2: Chạy test để xác nhận RED**

Run: `npx vp test run src/lib/ai/agentHistory.test.ts src/components/chat/AgentTab.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Triển khai trong `agentHistory.ts` và `AgentTab.tsx`**

- Cập nhật `agentHistory.ts` để lưu/khôi phục `imageUrl` trong `metadata`.
- Cập nhật `AgentTab.tsx` để dùng `serializeUserMessageMetadata`, dùng tiêu đề `'Thử đồ ảo với AI'` khi tin nhắn đầu chỉ có ảnh, fallback sang `getMessageImageUrl(message)` khi render tin nhắn `user`, và tích hợp `MediaLightbox` + nút `"Tải ảnh về"` trong `ShowImageBlock`.

- [ ] **Step 4: Chạy lại test để xác nhận GREEN**

Run: `npx vp test run src/lib/ai/agentHistory.test.ts src/components/chat/AgentTab.test.tsx`
Expected: PASS.

---

### Task 3: Validate Tiếng Việt Bắt Buộc Ảnh Garment Khi Sửa Sản Phẩm (`SellerEditProductTab.tsx`) (Khắc phục Điểm 5)

**Files:**
- Modify: `d:\fashionstore\fashionstore-ui\src\components\seller-dashboard-tab\products\SellerEditProductTab.tsx:480-495`
- Create: `d:\fashionstore\fashionstore-ui\src\components\seller-dashboard-tab\products\SellerEditProductTab.tryOn.test.tsx`

- [ ] **Step 1: Viết bài test thất bại (RED) trong `SellerEditProductTab.tryOn.test.tsx`**

- Kiểm tra khi `tryOnEnabled === true` nhưng người bán xóa ảnh Garment rồi bấm `"Lưu thay đổi"`, hệ thống báo lỗi `'Ảnh garment (trải phẳng trên nền trắng) là bắt buộc khi bật tính năng thử đồ ảo.'` và không gọi `updateMutation.mutate`.

- [ ] **Step 2: Chạy test để xác nhận RED**

Run: `npx vp test run src/components/seller-dashboard-tab/products/SellerEditProductTab.tryOn.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Bổ sung validate trong `SellerEditProductTab.tsx`**

- Trong `handleSave()` của `SellerEditProductTab.tsx`, kiểm tra `if (tryOnEnabled && (garment === null || garment.uploadId === null || garment.uploadId === ''))` trước `setIsSaving(true)`.

- [ ] **Step 4: Chạy kiểm chứng toàn bộ (`format`, `lint`, `build`, `test`)**

Run: `pnpm format && pnpm lint && pnpm build && npx vp test run src/pages/VirtualTryOn.test.tsx src/components/pdp/ProductInfo.test.tsx src/lib/ai/agentHistory.test.ts src/components/chat/AgentTab.test.tsx src/components/seller-dashboard-tab/products/SellerEditProductTab.tryOn.test.tsx`
Expected: 0 warnings, 0 errors, build thành công, toàn bộ test PASS.
