# Unified Product Detail Card, Sequential Product Tabs & Balanced Virtual Try-On Studio Layout Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tái thiết kế Trang Chi Tiết Sản Phẩm (`/product/:id`) theo Phương án 1 (Unified Hero Card 50/50 cân đối chiều cao, khung ảnh vuông cố định, thanh Thử đồ ảo AI 1 dòng tinh gọn, bảng Thông số dạng lưới thẻ hiện đại) và đồng bộ Trang Phòng Thử Đồ Ảo AI (`/try-on/:id`) thành 3 cột bằng chiều cao (`items-stretch`) cân đối, thẩm mỹ trên một màn hình.

**Architecture:**
1. **`ProductDetail.tsx` + `ProductGallery.tsx` + `ProductInfo.tsx`**: Gộp cột ảnh (`lg:col-span-6`) và cột thông tin (`lg:col-span-6`) vào chung 1 khung Card lớn (`rounded-3xl border bg-white/90 p-5 sm:p-6 lg:p-7 shadow-xs`) trên container `max-w-6xl`. Khung ảnh chính có chiều cao cố định `h-[340px] sm:h-[380px]` hiển thị `object-contain` để không bao giờ bị co lùn khi gặp ảnh ngang. Thanh Thử đồ ảo AI thu gọn thành 1 dòng duy nhất (`whitespace-nowrap`), không có đoạn mô tả phụ làm rớt dòng.
2. **`ProductTabs.tsx`**: Giữ 1 tab hiển thị lần lượt `Mô tả` → `Thông số` → `Đánh giá`, thiết kế lại phần `Thông số` thành lưới thẻ 3 cột (`grid-cols-1 sm:grid-cols-3`) gọn đẹp thay vì bảng trải dài trống trải.
3. **`VirtualTryOn.tsx`**: Sử dụng lưới 3 cột bằng chiều cao (`grid-cols-1 items-stretch gap-5 lg:grid-cols-3`) trên khung `max-w-6xl`. Ở cột 1, khung ảnh sản phẩm cao đồng nhất (`h-52`) và ảnh Garment chuẩn AI thu gọn thành thanh ngang có thumbnail `h-12 w-12` thay vì một hộp ảnh dọc thứ hai làm lệch chiều cao cột.

**Tech Stack:** React 19, TypeScript, Tailwind CSS v4, React Router v7, TanStack Query v5, Vitest + Testing Library.

## Global Constraints

- **Cấm tuyệt đối toán tử 3 ngôi (`? :`)** trong mọi file TypeScript/TSX.
- Luôn dùng `pnpm` trong `d:\fashionstore\fashionstore-ui`.
- Giữ nguyên toàn bộ selector/text mà các unit tests đang kiểm tra (`Hỗ trợ Thử đồ ảo AI`, `Thử đồ ảo với AI`, `Kích cỡ có sẵn`, `Chi tiết sản phẩm`, `data-testid="tryon-dropzone"`, v.v.).

---

### Task 1: Unified Hero Card 50/50, Khung Ảnh Cố Định & Thanh Try-On 1 Dòng (`ProductDetail.tsx`, `ProductGallery.tsx`, `ProductInfo.tsx`)

**Files:**
- Modify: `d:\fashionstore\fashionstore-ui\src\pages\ProductDetail.tsx`
- Modify: `d:\fashionstore\fashionstore-ui\src\components\pdp\ProductGallery.tsx`
- Modify: `d:\fashionstore\fashionstore-ui\src\components\pdp\ProductInfo.tsx`
- Modify: `d:\fashionstore\fashionstore-ui\src\components\pdp\ProductInfo.test.tsx`

- [ ] **Step 1: Cập nhật `ProductDetail.tsx` gộp Gallery và ProductInfo vào 1 Unified Card cân đối (`max-w-6xl`, `6/6`)**
- Container `<main className="mx-auto max-w-6xl px-4 pt-20 pb-16 md:pb-12">`.
- Bọc `ProductGallery` và `ProductInfo` trong `<section className="rounded-3xl border border-zinc-200/80 bg-white/90 p-5 shadow-xs sm:p-6 lg:p-7 dark:border-white/10 dark:bg-[#11182a]">` với `<div className="grid grid-cols-1 items-stretch gap-6 lg:grid-cols-12 lg:gap-8">` (`lg:col-span-6` cho Gallery, `lg:col-span-6 flex` cho Info).

- [ ] **Step 2: Cập nhật `ProductGallery.tsx` với khung ảnh chính chiều cao cố định `h-[340px] sm:h-[380px]`**
- Khung chính luôn cao `h-[340px] sm:h-[380px]` với nền `bg-zinc-50/90 dark:bg-zinc-900/50`, ảnh bên trong `h-full w-full object-contain p-3` để dù ảnh ngang hay dọc thì khung vẫn vuông vức, bằng chiều cao với cột thông tin bên phải.
- Hàng thumbnail bên dưới dùng `h-16 w-16 rounded-xl` đều đặn.

- [ ] **Step 3: Cập nhật `ProductInfo.tsx` bỏ border lồng nhau, thu gọn thanh Thử đồ ảo AI về 1 dòng duy nhất và căn đều bố cục**
- Root `<div className="flex w-full flex-col justify-between">`.
- Khối giá bán nổi bật trên nền nhẹ `rounded-2xl bg-zinc-50/90 px-4 py-3 dark:bg-white/5`.
- Thanh `Hỗ trợ Thử đồ ảo AI` nằm gọn trên **1 dòng duy nhất** (`flex items-center justify-between gap-3 rounded-2xl border border-blue-200/80 bg-blue-50/60 px-3.5 py-2 dark:border-sky-400/25 dark:bg-sky-500/10`), bỏ câu phụ dài dòng để nút `Thử đồ ảo với AI` (`shrink-0 whitespace-nowrap`) luôn nằm ngang bên phải trên cùng 1 hàng.
- Cụm nút `Thêm giỏ` / `Mua ngay` / `WishlistButton` căn sát đáy cột phải (`mt-6 pt-2`), ngang bằng với đáy hàng thumbnail bên trái.

---

### Task 2: Trình bày tinh gọn phần `ProductTabs.tsx` (Mô tả → Thông số dạng lưới thẻ → Đánh giá)

**Files:**
- Modify: `d:\fashionstore\fashionstore-ui\src\components\pdp\ProductTabs.tsx`
- Modify: `d:\fashionstore\fashionstore-ui\src\components\pdp\ProductTabs.test.tsx`

- [ ] **Step 1: Cập nhật `ProductTabs.tsx` làm sạch thanh tiêu đề và chuyển phần `Thông số` sang lưới thẻ cân đối**
- Thanh tab đầu card hiển thị gọn gàng, bên dưới là 3 mục nối tiếp rõ ràng (`Mô tả`, `Thông số`, `Đánh giá (N)`).
- Trong phần `Thông số`, thay bảng trải rộng trống trải bằng bố cục lưới 2–3 cột (`grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3`) cho `Kích cỡ có sẵn` và `Chi tiết sản phẩm` (`Màu sắc`, `Tình trạng`, `Chất liệu`, `Xuất xứ`, `Hướng dẫn bảo quản`, `Bảng chọn size`), vừa dễ đọc vừa không bị khoảng trống lớn ở giữa.

---

### Task 3: Đồng bộ giao diện 3 cột bằng chiều cao cho Trang Phòng Thử Đồ Ảo AI (`VirtualTryOn.tsx`)

**Files:**
- Modify: `d:\fashionstore\fashionstore-ui\src\pages\VirtualTryOn.tsx`
- Modify: `d:\fashionstore\fashionstore-ui\src\pages\VirtualTryOn.test.tsx`

- [ ] **Step 1: Cập nhật `VirtualTryOn.tsx` và `VirtualTryOn.test.tsx` theo bố cục Studio 3 cột bằng chiều cao (`items-stretch`, `max-w-6xl`)**
- Container `<main className="mx-auto max-w-6xl px-4 pt-20 pb-12">`.
- Lưới 3 cột `<div className="grid grid-cols-1 items-stretch gap-5 lg:grid-cols-3">`, mỗi cột là một card `flex h-full flex-col justify-between gap-3.5 p-5`.
- **Cột 1 (`1. Trang phục đang thử`)**:
  - Khung ảnh sản phẩm cao cố định `h-52` (`flex h-52 items-center justify-center rounded-2xl border border-zinc-200/80 bg-zinc-50/80 p-3`) với `<img className="h-44 w-full object-contain p-2" />`.
  - Khi có `garmentUrl`, hiển thị dưới dạng một thẻ ngang gọn ở đáy cột 1 (`flex items-center gap-3 rounded-2xl border border-zinc-200/70 bg-zinc-50/70 p-2.5`): thumbnail `h-12 w-12 rounded-xl bg-white object-contain p-1` bên trái + nhãn `"Ảnh trang phục chuẩn AI (Garment)"` bên phải (thay vì một hộp ảnh cao `h-20`/`h-32` chiếm chiều dọc).
- **Cột 2 (`2. Ảnh cá nhân của bạn`)**:
  - Vùng kéo-thả (`data-testid="tryon-dropzone"`) dùng `flex min-h-56 flex-1 flex-col items-center justify-center rounded-2xl border-2 border-dashed p-4 text-center transition` để tự động giãn đầy chiều cao cột, giữ nút `"Bắt đầu Thử Đồ Ảo AI"` luôn nằm thẳng hàng ở đáy cột 2.
- **Cột 3 (`3. Kết quả mặc thử AI`)**:
  - Khung kết quả dùng `flex min-h-56 flex-1 flex-col items-center justify-center rounded-2xl border border-zinc-200/80 bg-zinc-50/60 p-4`, có biểu tượng minh họa nhẹ nhàng khi chưa tạo ảnh và giữ chiều cao bằng khít với cột 1 và cột 2.
