# Thiết kế Nâng cấp Công cụ Thu thập Dữ liệu Chuyên sâu (Deep PDP Scraper) & Bộ Nhập liệu Đa Gian hàng (~1.000 Sản phẩm & ~50 Cửa hàng)

## 1. Mục đích & Động lực (Intent & Why)

Công cụ lấy dữ liệu hiện tại ([scrape.mjs](file:///d:/fashionstore/fashionstore/tools/marketplace-browser/scrape.mjs)) và bộ nạp dữ liệu vào hệ thống ([BrowserMarketplaceImporter.java](file:///d:/fashionstore/fashionstore/src/main/java/com/minhnbnt/fashionstore/infrastructure/BrowserMarketplaceImporter.java)) được viết từ giai đoạn đầu của dự án khi mô hình dữ liệu còn sơ khai. Qua rà soát toàn bộ source code Backend (`fashionstore`) và Frontend (`fashionstore-ui`), có sự lệch pha lớn giữa dữ liệu công cụ lấy về và những gì website thực sự cần:

1. **Dữ liệu sản phẩm quá nghèo nàn so với tính năng của website:**
   - [scrape.mjs](file:///d:/fashionstore/fashionstore/tools/marketplace-browser/scrape.mjs) chỉ lấy dữ liệu bề mặt ở trang danh sách (1 ảnh thumbnail, tên, giá, link) và thiếu hẳn danh mục `phu-kien` (Phụ kiện).
   - [BrowserMarketplaceImporter.java](file:///d:/fashionstore/fashionstore/src/main/java/com/minhnbnt/fashionstore/infrastructure/BrowserMarketplaceImporter.java) gán cứng mọi sản phẩm thành 1 biến thể duy nhất (`size = "Freesize"`, `color = "Theo phân loại"`, `stockQuantity = 20`), mô tả chỉ ghi `"Nguồn LAZADA, mã..."`, bỏ trống hoàn toàn `material`, `origin`, `careInstructions`, `sizeGuide`, kích thước/cân nặng đóng gói, ảnh theo phân loại màu, và không bật được **Thử đồ ảo AI** (`tryOnEnabled`, `garmentUploadIds`).
   - Hệ quả: Bộ lọc tìm kiếm theo Màu sắc & Kích cỡ (`/api/search/facets` trên [Search.tsx](file:///d:/fashionstore/fashionstore-ui/src/pages/Search.tsx)), bộ chọn phân loại trên trang chi tiết ([ProductInfo.tsx](file:///d:/fashionstore/fashionstore-ui/src/components/pdp/ProductInfo.tsx)), gallery nhiều ảnh ([ProductGallery.tsx](file:///d:/fashionstore/fashionstore-ui/src/components/pdp/ProductGallery.tsx)), bảng thông số ([ProductTabs.tsx](file:///d:/fashionstore/fashionstore-ui/src/components/pdp/ProductTabs.tsx)) và phòng Thử đồ ảo ([VirtualTryOn.tsx](file:///d:/fashionstore/fashionstore-ui/src/pages/VirtualTryOn.tsx)) không thể phát huy tác dụng trên dữ liệu import.
2. **Dồn toàn bộ sản phẩm vào 1 shop giả & lỗi 404 ảnh S3 của shop:**
   - Toàn bộ sản phẩm bị gom vào 1 shop duy nhất (`browser-marketplace-import`) thay vì tái hiện hệ sinh thái nhiều cửa hàng thời trang trên sàn.
   - Các file media của shop (`seeds/market-logo.webp`, `seeds/market-banner.webp`, giấy tờ KYC) chỉ được tạo bản ghi trong bảng `upload` nhưng **không đẩy bytes thực tế lên S3**, gây lỗi 404 trên `PdpShopCard` và `ShopStorefront`.
3. **Cơ chế cào dễ đứt gãy & dễ mất dữ liệu:**
   - Gọi `fetch('/api/v4/search/search_items')` trần từ `page.evaluate()` trên Shopee thường xuyên bị chặn bởi cơ chế kiểm tra chữ ký điều hướng.
   - Chỉ tải ảnh và lưu `products.json` ở cuối phiên chạy; nếu gián đoạn ở giữa hoặc chạy riêng từng `--source` sẽ mất toàn bộ dữ liệu trước đó.

Thiết kế này nâng cấp toàn diện **công cụ lấy dữ liệu trang chi tiết sản phẩm (Deep PDP Scraper)** và **luồng nạp dữ liệu Backend (`BrowserMarketplaceImporter`)** để thu thập và chuẩn hóa khoảng **1.000 sản phẩm** phân bổ trên khoảng **50 cửa hàng thời trang** với dữ liệu thật, giàu chi tiết và khớp 100% với mọi tính năng của website.

---

## 2. Mục tiêu, Ngoài phạm vi & Điều cấm kỵ (Goals, Non-Goals, Anti-Goals)

### Mục tiêu (Goals)
- **Quy mô mục tiêu:** Hỗ trợ lấy và nạp khoảng **1.000 sản phẩm** (`--limit`, mặc định tổng `1000`) và khoảng **50 cửa hàng** (`--shop-limit`, mặc định `50`) phủ đều 5 danh mục của sàn: `ao` (Áo), `vay-dam` (Váy & Đầm), `ao-khoac` (Áo khoác), `quan` (Quần), `phu-kien` (Phụ kiện).
- **Truy cập sâu trang chi tiết sản phẩm (Deep PDP Scraping):**
  - Hỗ trợ đa nguồn thời trang tại Việt Nam (`lazada`, `shopee`, và bổ sung `tiki` vào cùng [scrape.mjs](file:///d:/fashionstore/fashionstore/tools/marketplace-browser/scrape.mjs) để đảm bảo luôn đạt mốc ~1.000 sản phẩm & ~50 cửa hàng nhanh, ổn định).
  - Bắt dữ liệu lai (Network Response Interception + SSR state `__moduleData__` / `__NEXT_DATA__` + DOM fallback) khi điều hướng trang danh sách và trang chi tiết sản phẩm thật.
  - Thu thập dữ liệu thật của **Cửa hàng** (tên shop, mã shop gốc, logo, banner, mô tả, địa chỉ/khu vực, loại hình Mall/Official → `CORPORATE` hay shop thường → `INDIVIDUAL`) và **Sản phẩm** (bộ 3–9 ảnh gallery, ảnh theo phân loại màu, cây biến thể Màu × Size kèm giá bán/giá gốc/tồn kho, mô tả chi tiết, chất liệu, xuất xứ, hướng dẫn bảo quản, bảng chọn size, khối lượng/kích thước đóng gói).
- **Bộ chuẩn hóa dữ liệu khớp tuyệt đối với Website (`normalizer.mjs`):**
  - Chuẩn hóa biến thể về ma trận cặp `(color, size)` duy nhất trong mỗi sản phẩm (đáp ứng thuật toán `findVariant(variants, size, color)` của [ProductInfo.tsx](file:///d:/fashionstore/fashionstore-ui/src/components/pdp/ProductInfo.tsx) và bộ lọc `/api/search/facets`).
  - Làm sạch tên sản phẩm (≤ 300 ký tự, bỏ tag rác đầu dòng) và chuyển mô tả sang Markdown có cấu trúc đẹp (≤ 20.000 ký tự, lọc bỏ link ngoài/quảng cáo rác và từ khóa cấm).
  - Đảm bảo mỗi sản phẩm có từ **3 đến 9 ảnh gallery** hợp lệ (đáp ứng `@Size(min = 3, max = 9)` của [CreateProductRequest.java](file:///d:/fashionstore/fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/catalog/CreateProductRequest.java)), gắn `garmentImages` (1–4 ảnh) và bật `tryOnEnabled = true` cho các sản phẩm trang phục để dùng ngay tính năng **Thử đồ ảo AI**.
- **Lưu trữ lũy tiến (Incremental Checkpoint & Resume):**
  - Lưu `data/marketplace-import/shops.json`, `data/marketplace-import/products.json` và tải ảnh ngay sau từng sản phẩm.
  - Hỗ trợ chạy tiếp (`--resume`, mặc định bật) và gộp dữ liệu từ nhiều lần chạy `--source` khác nhau mà không ghi đè mất dữ liệu cũ.
- **Nâng cấp [BrowserMarketplaceImporter.java](file:///d:/fashionstore/fashionstore/src/main/java/com/minhnbnt/fashionstore/infrastructure/BrowserMarketplaceImporter.java):**
  - Đọc `shops.json` và `products.json`, khởi tạo đủ ~50 cửa hàng `APPROVED` + `VERIFIED` (có ảnh logo, banner và giấy tờ KYC đẩy bytes thật lên S3, cập nhật hồ sơ `UpdateShopProfileRequest`, và xuất bản sẵn bố cục trang trí `ShopDecoration` gồm `CATEGORY_GRID` + `PRODUCT_COLLECTION` để trang `/shops/:slug` hiển thị đẹp ở cả 3 tab).
  - Nạp toàn bộ ~1.000 sản phẩm với đầy đủ variants, ảnh gallery, ảnh riêng của từng màu variant (`imageUploadId`), ảnh garment (`garmentUploadIds`), thông số kỹ thuật và kích thước đóng gói; hỗ trợ cập nhật (`upsert`) các sản phẩm import cũ chỉ có 1 biến thể.

### Ngoài phạm vi (Non-Goals)
- Không thay đổi schema cơ sở dữ liệu hiện có của `product`, `product_variant`, `product_image`, `product_garment`, `shop` (vì schema hiện tại đã có đủ mọi cột cần thiết).
- Không cào bình luận/đánh giá của người mua từ sàn ngoài trong tool này.

### Điều cấm kỵ (Anti-Goals)
- **Tuyệt đối không** viết code tự động giải, vượt hoặc can thiệp CAPTCHA / màn hình xác thực chống bot. Khi sàn yêu cầu đăng nhập hoặc xác thực trên giao diện Chrome (`headless: false`), tool phải tạm dừng (`pause`) và để người dùng trực tiếp thao tác trên cửa sổ trình duyệt hiển thị.
- **Không bao giờ** tạo bản ghi `Upload` trong DB mà không `putObject` bytes thực tế lên S3 (tránh lỗi 404 ảnh shop/sản phẩm và lỗi worker `product_embedding`).
- **Không bao giờ** tạo các biến thể trùng cặp `(color, size)` trong cùng một sản phẩm hoặc có giá trị rác như `color = "Theo phân loại"`, và không dùng toán tử 3 ngôi (`? :`) trong code Java/TypeScript theo luật `AGENTS.md`.

---

## 3. Các phương án kiến trúc đã cân nhắc (Approaches)

### Phương án 1 (Đã chọn): Điều hướng trang chi tiết thật + Bắt dữ liệu lai (Network Interception & SSR/DOM) + Bộ chuẩn hóa Schema Website
- **Mô tả:** Kết hợp điều hướng trình duyệt thật qua các trang danh mục/tìm kiếm và trang chi tiết sản phẩm (PDP), lắng nghe gói tin JSON do chính trang web tải về (`page.on('response')`) kết hợp đọc state SSR (`window.__moduleData__`, `__NEXT_DATA__`, JSON-LD) và DOM thực tế. Sau khi bóc tách dữ liệu thật của sản phẩm và cửa hàng, đưa qua tầng `normalizer.mjs` để chuẩn hóa phân loại `(color, size)`, bộ ảnh 3–9 tấm, ảnh garment, mô tả Markdown và thông số kỹ thuật trước khi lưu xuống `shops.json` + `products.json` cho [BrowserMarketplaceImporter.java](file:///d:/fashionstore/fashionstore/src/main/java/com/minhnbnt/fashionstore/infrastructure/BrowserMarketplaceImporter.java) nạp vào DB & S3.
- **Ưu điểm:** Lấy được dữ liệu thật từ trang chi tiết, tỷ lệ bị chặn thấp nhất vì tận dụng luồng tải trang tự nhiên của trình duyệt, lưu checkpoint liên tục để đạt quy mô ~1.000 sản phẩm & ~50 cửa hàng an toàn, dữ liệu sau tinh chỉnh khớp 100% với giao diện và bộ lọc của website.
- **Đánh đổi:** Thời gian cào lâu hơn chỉ lấy trang danh sách (khoảng 2–4 giây/sản phẩm), được giải quyết bằng cơ chế lưu lũy tiến từng sản phẩm và hỗ trợ chạy đa nguồn (`lazada`, `shopee`, `tiki`).

### Phương án 2 (Loại bỏ): Chỉ đọc DOM và mô phỏng click từng nút phân loại trên giao diện
- **Mô tả:** Dùng CSS selector đọc text và tự động click từng tổ hợp nút Màu × Size trên màn hình để lấy giá và tồn kho.
- **Lý do loại bỏ:** Quá chậm khi cào 1.000 sản phẩm (mỗi sản phẩm 10–20 biến thể phải click 10–20 lần) và dễ gãy khi giao diện thay đổi class CSS.

### Phương án 3 (Loại bỏ): Gọi trực tiếp `fetch()` vào Private API chi tiết sản phẩm mà không điều hướng trang
- **Mô tả:** Gọi `fetch('/api/v4/pdp/get_pc...')` hàng loạt trong `page.evaluate`.
- **Lý do loại bỏ:** Shopee khóa chặt các request `fetch` ngầm thiếu chữ ký điều hướng trang (`af-ac-enc-dat`), dẫn đến bị chặn liên tục không thể lấy đủ 1.000 sản phẩm.

---

## 4. Thiết kế chi tiết (Detailed Design)

### 4.1. Kiến trúc Module của Công cụ Scraper (`tools/marketplace-browser/`)

Tách [scrape.mjs](file:///d:/fashionstore/fashionstore/tools/marketplace-browser/scrape.mjs) thành cấu trúc rõ ràng, dễ bảo trì và có unit test cho tầng chuẩn hóa dữ liệu:

```
tools/marketplace-browser/
├── package.json
├── scrape.mjs                 # CLI entrypoint, quản lý Playwright context, checkpoint, điều phối quota 1000 SP / 50 Shop
├── lib/
│   ├── queries.mjs            # Bộ ~20 truy vấn thời trang phủ đủ 5 danh mục (ao, vay-dam, ao-khoac, quan, phu-kien)
│   ├── extractors/
│   │   ├── lazada.mjs         # Bóc tách danh sách + trang chi tiết PDP & Shop từ Lazada (SSR __moduleData__ + JSON-LD + DOM)
│   │   ├── shopee.mjs         # Bóc tách danh sách + trang chi tiết PDP & Shop từ Shopee (Network response intercept + SSR/DOM)
│   │   └── tiki.mjs           # Bóc tách danh sách + trang chi tiết PDP & Seller từ Tiki (API/PDP + DOM)
│   ├── normalizer.mjs         # Tinh chỉnh & chuẩn hóa Shop + Product + Variants (color, size) + Attributes + Markdown
│   └── storage.mjs            # Quản lý đọc/ghi lũy tiến shops.json, products.json, tải ảnh & tạo ảnh fallback hợp lệ
└── test/
    └── normalizer.test.mjs    # Unit test (node:test) kiểm증 chuẩn hóa biến thể, thông số, ảnh, mô tả, shop
```

#### Tham số CLI hỗ trợ:
- `--source <all|lazada|shopee|tiki>` (mặc định: `all`)
- `--limit <number>` (tổng số sản phẩm mục tiêu, mặc định: `1000`, cho phép tới `5000`)
- `--shop-limit <number>` (tổng số cửa hàng mục tiêu, mặc định: `50`, cho phép tới `200`)
- `--max-per-shop <number>` (số sản phẩm tối đa lấy trên mỗi cửa hàng để phân bổ đều, mặc định: `25`)
- `--resume <true|false>` (mặc định: `true` — đọc `shops.json` và `products.json` hiện có, bỏ qua sản phẩm đã lấy đủ dữ liệu chi tiết).

---

### 4.2. Chiến lược Lấy ~1.000 Sản phẩm & ~50 Cửa hàng trên 5 Danh mục (`queries.mjs`)

1. **Phủ đủ 5 danh mục chuẩn của website:**
   - `ao`: áo thun, áo sơ mi, áo polo, áo kiểu nữ, áo len/cardigan
   - `vay-dam`: váy đầm dự tiệc, đầm maxi/hoa nhí, chân váy chữ A, chân váy xếp ly
   - `ao-khoac`: áo khoác blazer, áo khoác bomber/kaki, áo hoodie/sweater, áo khoác jean
   - `quan`: quần jean ống suông, quần tây/âu, quần ống rộng, quần short, quần jogger/kaki
   - `phu-kien`: túi xách/túi tote, mũ nón thời trang, thắt lưng da, khăn choàng cổ
2. **Thuật toán cân bằng Cửa hàng & Sản phẩm:**
   - Khi duyệt trang danh sách của từng truy vấn, scraper thu thập danh sách ứng viên `{ productUrl, sourceId, shopSourceId, shopName, thumbnail, basicPrice }`.
   - Ưu tiên chọn sản phẩm từ các `shopSourceId` mới cho đến khi đạt `shopLimit` (~50 cửa hàng), sau đó ưu tiên các sản phẩm thuộc 50 cửa hàng đã chọn (mỗi cửa hàng tối đa `maxPerShop` ~20–25 sản phẩm) cho tới khi đạt tổng `limit` (~1.000 sản phẩm). Nếu các cửa hàng đã có không còn đủ sản phẩm trong danh mục đang quét, cho phép mở rộng thêm cửa hàng hoặc lấy tiếp sản phẩm trong danh mục để đảm bảo đạt đủ ~1.000 sản phẩm.

---

### 4.3. Bóc tách Chuyên sâu Trang Chi tiết Sản phẩm (Deep PDP Extraction)

Với mỗi sản phẩm được chọn, scraper mở trang chi tiết (hoặc đọc payload PDP bắt được từ network interceptor khi trang tải):

1. **Lazada (`lib/extractors/lazada.mjs`):**
   - Mở URL sản phẩm (`https://www.lazada.vn/products/...`).
   - Đọc `window.__moduleData__` (chứa `data.root.fields.skuInfos`, `productOption.skuBase.properties`, `productOption.skuBase.skus`, `specifications`, `product.desc`, `seller`) kết hợp JSON-LD (`script[type="application/ld+json"]`) và DOM gallery.
   - Bóc tách:
     - **Shop:** `seller.sellerId`, `seller.name`, `seller.shopUrl`, `seller.logo`, `seller.PositiveSellerRating`, cờ LazMall (`sellerType = CORPORATE`).
     - **Ảnh Gallery:** Tất cả ảnh trong `skuInfos["0"].images` / DOM gallery (chuyển URL thumbnail `_80x80q80` / `_120x120` sang ảnh gốc độ phân giải cao).
     - **Phân loại (Variants):** Ánh xạ `properties` (Màu sắc / Kích cỡ + ảnh của từng màu) với `skus` (`propPath` → `skuId`) và `skuInfos[skuId]` (`price.salePrice.value`, `price.originalPrice.value`, `stock`).
     - **Thông số & Mô tả:** Bóc `specifications` + HTML trong `product.desc`.

2. **Shopee (`lib/extractors/shopee.mjs`):**
   - Trên trang tìm kiếm và trang chi tiết, đăng ký `page.on('response')` bắt các response JSON của `/api/v4/search/search_items`, `/api/v4/pdp/get_pc`, `/api/v4/item/get`, `/api/v4/pdp/get_rw` cùng lúc với việc render DOM.
   - Khi vào trang chi tiết sản phẩm (`https://shopee.vn/product/{shopid}/{itemid}`):
     - Lấy từ payload PDP bắt được hoặc từ DOM/state:
     - **Shop:** `shop_detailed` / `shop_info` (`shopid`, `name`, `account.portrait`, `cover`, `description`, `shop_location`, `is_official_shop` → `CORPORATE`, `rating_star`, `follower_count`).
     - **Ảnh Gallery:** Danh sách `images` (tối đa 9 mã hash → `https://down-vn.img.susercontent.com/file/{hash}`).
     - **Phân loại (Variants):** Kết hợp `tier_variations` (tên nhóm phân loại, danh sách `options` và `images` theo từng màu) với mảng `models` (`name` như `"Đen,M"`, `price / 100000`, `price_before_discount / 100000`, `stock`). Nếu chỉ bắt được từ DOM, đọc các nhóm phân loại trên UI, ảnh thumbnail và giá hiển thị.
     - **Thông số & Mô tả:** Mảng `attributes` (`Chất liệu`, `Xuất xứ`, `Kiểu dáng`...) và `description`.

3. **Tiki (`lib/extractors/tiki.mjs`):**
   - Từ trang danh mục/tìm kiếm thời trang trên Tiki, lấy danh sách `id` sản phẩm và truy cập trang chi tiết / payload `/api/v2/products/{id}` ngay trong context trình duyệt:
     - **Shop:** `current_seller` (`id`, `name`, `logo`, `link`) + thông tin cửa hàng chính hãng (`CORPORATE` nếu Tiki Trading / Official Store).
     - **Ảnh Gallery:** Mảng `images` (`large_url` / `base_url`, lấy 3–9 ảnh).
     - **Phân loại (Variants):** Mảng `configurable_options` (Màu sắc, Kích cỡ) + `configurable_products` (giá, giá gốc, SKU, ảnh riêng, tồn kho).
     - **Thông số & Mô tả:** Mảng `specifications` (`attributes`: Chất liệu, Xuất xứ, Hướng dẫn bảo quản...) + `description` HTML.

---

### 4.4. Bộ Tinh chỉnh & Chuẩn hóa Dữ liệu Khớp 100% với Website (`lib/normalizer.mjs`)

Mọi dữ liệu thô lấy từ trang chi tiết đều đi qua `normalizeProduct()` và `normalizeShop()` để đảm bảo tuân thủ toàn bộ ràng buộc của [CatalogService.java](file:///d:/fashionstore/fashionstore/src/main/java/com/minhnbnt/fashionstore/features/catalog/CatalogService.java), [ShopService.java](file:///d:/fashionstore/fashionstore/src/main/java/com/minhnbnt/fashionstore/features/shop/ShopService.java) và giao diện React:

1. **Chuẩn hóa Tên & Mô tả (`name`, `description`):**
   - `name`: Xóa các tiền tố quảng cáo rối mắt kiểu `[Mã ... giảm ...]`, `[FREESHIP]`, `[HCM]`, khoảng trắng thừa; giới hạn ≤ 300 ký tự.
   - `description`: Chuyển HTML (`<p>`, `<br>`, `<li>`, `<table>`, `<strong>`) sang Markdown sạch; loại bỏ thẻ script/style, URL ngoài sàn, số điện thoại rác; nếu bài mô tả gốc quá ngắn (< 80 ký tự), tự động bổ sung khung mô tả thời trang chuẩn gồm điểm nổi bật, thông số chất liệu và hướng dẫn sử dụng dựa trên thuộc tính thật của sản phẩm. Giới hạn ≤ 20.000 ký tự.

2. **Chuẩn hóa Ma trận Biến thể `(color, size)` (`variants`):**
   - Website yêu cầu mỗi biến thể có `color` (Màu sắc) và `size` (Kích cỡ) rõ ràng, không trùng cặp `(color, size)` trong cùng sản phẩm, để:
     - `/api/search/facets` hiển thị danh sách chip lọc Màu sắc và Kích cỡ chuẩn xác.
     - [ProductInfo.tsx](file:///d:/fashionstore/fashionstore-ui/src/components/pdp/ProductInfo.tsx) cho phép người mua chọn Màu + Size và khớp đúng 1 biến thể qua `findVariant(variants, size, color)`.
   - **Quy tắc tách & chuẩn hóa:**
     - Chuẩn hóa tên màu về từ điển màu thời trang tiếng Việt đẹp (`Đen`, `Trắng`, `Be`, `Kem`, `Xám`, `Xanh Navy`, `Xanh Dương`, `Xanh Rêu`, `Hồng`, `Đỏ`, `Nâu`, `Vàng`, `Tím`, `Cam`, `Kẻ sọc`, `Họa tiết`...). Ví dụ: `"Màu đen tuyền"` → `"Đen"`, `"Trắng sữa"` → `"Trắng"`, `"Xanh than"` → `"Xanh Navy"`.
     - Chuẩn hóa tên size về tập chuẩn (`XS`, `S`, `M`, `L`, `XL`, `2XL`, `3XL`, `Freesize`, hoặc size quần `27`–`34`). Ví dụ: `"Size M (45-55kg)"` → `"M"` (và trích phần `"Size M: 45-55kg"` đưa vào `sizeGuide`!).
     - Nếu sản phẩm gốc chỉ có 1 chiều phân loại (ví dụ chỉ có màu `Đen`, `Trắng` mà không chia size): với `phu-kien` gán `size = "Freesize"`; với quần áo (`ao`, `vay-dam`, `ao-khoac`, `quan`) tự động mở rộng mỗi màu qua bộ size phổ biến (`S`, `M`, `L`, `XL`) với cùng mức giá để người mua có trải nghiệm chọn size đầy đủ.
     - Nếu sản phẩm gốc chỉ có size (`S`, `M`, `L`) mà không khai báo màu: suy luận màu chính từ tên/mô tả sản phẩm (hoặc gán màu phù hợp với danh mục như `Đen` / `Trắng`).
     - Khử trùng lặp theo khóa `${color}__${size}`; đảm bảo mỗi variant có `price >= 1000`, `compareAtPrice > price` (hoặc `null` nếu không giảm giá), `stockQuantity` từ `5` đến `100` (nếu sản phẩm gốc báo `stock = 0` trên mọi biến thể thì đặt tồn kho mặc định `15` để sản phẩm có thể mua được trên sàn), `sku` duy nhất, và **đúng 1 biến thể đầu tiên** có `defaultVariant = true`.

3. **Chuẩn hóa Bộ ảnh Gallery (3–9 ảnh), Ảnh theo Màu & Ảnh Garment Try-On:**
   - Tải tối đa 9 ảnh gallery của sản phẩm về `data/marketplace-import/images/<source>/`.
   - Nếu sản phẩm gốc trên sàn chỉ có 1 hoặc 2 ảnh (trong khi `CreateProductRequest` và chuẩn giao diện yêu cầu 3–9 ảnh): tự động bổ sung từ ảnh phân loại màu của chính sản phẩm đó, hoặc sinh thêm các bản crop chi tiết chất vải / góc cận cảnh từ ảnh gốc để luôn có tối thiểu **3 ảnh hợp lệ** (`imageFiles.length >= 3 && imageFiles.length <= 9`).
   - Lưu ánh xạ `colorImageFile` trên từng biến thể nếu màu đó có ảnh riêng, để khi nạp vào DB sẽ gắn `imageUploadId` vào đúng biến thể màu.
   - Với các danh mục trang phục (`ao`, `vay-dam`, `ao-khoac`, `quan`), chọn từ 1 đến 4 ảnh rõ nét nhất (ưu tiên ảnh chính + ảnh các phân loại màu) làm `garmentImageFiles` và đặt `tryOnEnabled = true`.

4. **Chuẩn hóa Thông số Kỹ thuật & Đóng gói (`material`, `origin`, `careInstructions`, `sizeGuide`, `weightGrams`, `lengthCm`, `widthCm`, `heightCm`):**
   - Ưu tiên lấy giá trị thật từ bảng thông số (`specifications` / `attributes`) của trang chi tiết sản phẩm.
   - Nếu người bán trên sàn bỏ trống một trường, tự động suy luận từ tên + mô tả sản phẩm hoặc điền giá trị chuẩn theo danh mục:
     - `material`: suy luận từ tên/mô tả (`Cotton 100%`, `Denim cao cấp`, `Kaki co giãn`, `Lụa tổng hợp`, `Nỉ bông`, `Da PU cao cấp`, `Vải bố Canvas`...).
     - `origin`: giá trị thật từ PDP hoặc mặc định `"Việt Nam"`.
     - `careInstructions`: giá trị thật hoặc hướng dẫn bảo quản phù hợp với chất liệu (ví dụ: `"Giặt nhẹ ở nhiệt độ thường, lộn trái khi phơi, không dùng thuốc tẩy mạnh."`).
     - `sizeGuide`: tổng hợp từ ghi chú cân nặng/chiều cao trong tên size của PDP hoặc bảng quy đổi size chuẩn theo danh mục (`S: 45-52kg | M: 53-60kg | L: 61-68kg | XL: 69-76kg`).
     - Đóng gói: `weightGrams` (`ao`: 250g, `vay-dam`: 350g, `quan`: 450g, `ao-khoac`: 600g, `phu-kien`: 300g), `lengthCm = 28`, `widthCm = 20`, `heightCm = 5`.

5. **Chuẩn hóa Dữ liệu Cửa hàng (`shops.json`):**
   - Mỗi cửa hàng có cấu trúc chuẩn trong `data/marketplace-import/shops.json`:
     ```json
     {
       "shopKey": "SHOPEE-123456",
       "source": "SHOPEE",
       "sourceShopId": "123456",
       "name": "Routine Official Store",
       "slug": "routine-official-store-123456",
       "description": "Gian hàng thời trang chính hãng...",
       "sellerType": "CORPORATE",
       "address": "Quận Cầu Giấy, Hà Nội",
       "phone": "0901234567",
       "logoFile": "images/shops/shopee-123456-logo.webp",
       "bannerFile": "images/shops/shopee-123456-banner.webp",
       "rating": 4.9,
       "ratingCount": 128,
       "followerCount": 5420,
       "processingDays": 1,
       "returnPolicy": "Hỗ trợ đổi trả miễn phí trong 15 ngày nếu sản phẩm lỗi từ nhà sản xuất hoặc không vừa kích cỡ."
     }
     ```
   - Nếu cửa hàng trên sàn không có ảnh logo hoặc ảnh banner riêng (hoặc tải lỗi), `storage.mjs` tự động dùng ảnh sản phẩm nổi bật của shop hoặc tạo file ảnh `.webp`/`.png` hợp lệ trên đĩa để **100% cửa hàng đều có file ảnh thật**.

---

### 4.5. Nâng cấp Bộ Nạp Dữ liệu Backend ([BrowserMarketplaceImporter.java](file:///d:/fashionstore/fashionstore/src/main/java/com/minhnbnt/fashionstore/infrastructure/BrowserMarketplaceImporter.java))

1. **Đọc Manifest Đa Cửa hàng & Tương thích Ngược:**
   - Đọc `shops.json` cùng thư mục với `products.json` (`data/marketplace-import/shops.json`).
   - Tương thích ngược hoàn toàn: nếu `shops.json` không tồn tại hoặc một item trong `products.json` không có `shopKey`, tự động dùng shop mặc định `browser-marketplace-import` như cũ.

2. **Đẩy File Ảnh Thật lên S3 cho Toàn bộ Media của Shop & KYC:**
   - Khắc phục triệt để lỗi 404 S3: tạo hàm `putAndSeedUpload(String s3Key, byte[] bytes, String contentType)` thực hiện `s3.putObject(...)` trước rồi mới lưu `Upload.create(s3Key, contentType)`.
   - Với giấy tờ KYC mẫu của các shop import (`seeds/kyc/id-front.png`, `id-back.png`, `id-selfie.png`), sinh mảng byte ảnh PNG hợp lệ và `putObject` lên S3 một lần để mọi shop import đều có object thật trên S3.
   - Với `logoFile` và `bannerFile` của từng shop trong `shops.json`, đọc bytes từ đĩa và `putObject` lên `imports/shops/{slug}-logo.{ext}` và `imports/shops/{slug}-banner.{ext}`.

3. **Khởi tạo ~50 Cửa hàng Hoàn chỉnh (Hồ sơ + Trang trí Storefront):**
   - Với mỗi shop trong `shops.json`:
     - Tìm theo `slug`; nếu chưa tồn tại thì tạo user owner (`shop-{slug}@import.local`), gọi `shopService.register(...)`, `shopService.approve(...)`, `shopService.setTrusted(..., true)`, và gọi `shopService.updateProfile(...)` để cập nhật `returnPolicy`, `processingDays`, `workingHours`, `autoReplyText`.
     - Cập nhật `rating`, `ratingCount`, `followerCount` cho shop nếu manifest có thông số này.
     - Tạo và `publish` bố cục `ShopDecoration` mặc định gồm 4 khối (`BANNER_SLIDER` sử dụng URL banner của shop, `CATEGORY_GRID`, `PRODUCT_COLLECTION` cho `NEW_ARRIVALS` và `SALE_OFF`) để trang `/shops/:slug` hiển thị sống động ngay khi truy cập.

4. **Khởi tạo / Cập nhật ~1.000 Sản phẩm Đầy đủ Tính năng:**
   - Đảm bảo đủ 5 danh mục (`ao`, `vay-dam`, `ao-khoac`, `quan`, `phu-kien`).
   - Hỗ trợ đọc cả định dạng mới (`imageFiles: [...]`, `garmentImageFiles: [...]`, `variants: [...]`, `material`, `origin`, `careInstructions`, `sizeGuide`, `weightGrams`, `lengthCm`, `widthCm`, `heightCm`, `tryOnEnabled`) lẫn định dạng cũ (`imageFile` đơn lẻ).
   - Cache `Map<String, UUID> uploadIdByS3Key` trong phiên import để nếu ảnh gallery, ảnh phân loại màu và ảnh garment dùng chung một file ảnh thì chỉ upload S3 và tạo row `Upload` đúng 1 lần.
   - Truyền danh sách `ImageItemRequest` (3–9 ảnh), danh sách `CreateVariantRequest` (kèm `imageUploadId` của từng màu), `garmentUploadIds` + `tryOnEnabled` và đầy đủ các trường thông số vào `catalogService.createProduct(...)`.
   - Nếu sản phẩm đã tồn tại trong shop (`findByShopIdAndSlug`) và cờ `app.browser-import.upsert=true` (mặc định `true`), gọi `catalogService.updateProduct(...)` để nâng cấp các sản phẩm cũ từng bị import thiếu ảnh/biến thể/thông số lên dữ liệu đầy đủ mới.

---

## 5. Phần để lại cho Người Triển khai (What's Left to the Builder)

- Chi tiết danh sách selector fallback và regex trích xuất thông số cụ thể trong từng extractor (`lazada.mjs`, `shopee.mjs`, `tiki.mjs`).
- Bảng từ điển ánh xạ màu sắc và kích cỡ chi tiết trong `normalizer.mjs`.
- Cấu trúc chi tiết các unit test trong `normalizer.test.mjs` và `BrowserMarketplaceImporterTest.java`.
