# Deep PDP Marketplace Scraper & Multi-Shop Importer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nâng cấp công cụ thu thập dữ liệu `tools/marketplace-browser` để truy cập sâu trang chi tiết sản phẩm (PDP) lấy dữ liệu thật, tinh chỉnh khớp 100% với schema website (~1.000 sản phẩm & ~50 cửa hàng trên 5 danh mục), và nâng cấp `BrowserMarketplaceImporter.java` để nạp đa gian hàng kèm ảnh S3 thật, trang trí Storefront và đầy đủ biến thể / thông số / ảnh garment Try-On.

**Architecture:**
1. **Tầng Chuẩn hóa & Lưu trữ (`tools/marketplace-browser/lib/`)**: `queries.mjs` định nghĩa 20 truy vấn thời trang phủ đủ 5 danh mục (`ao`, `vay-dam`, `ao-khoac`, `quan`, `phu-kien`). `normalizer.mjs` chuẩn hóa Shop (`normalizeShop`) và Product (`normalizeProduct`) từ dữ liệu PDP thô thành ma trận biến thể `(color, size)` duy nhất, bộ 3–9 ảnh gallery, ảnh theo màu, ảnh garment Try-On, mô tả Markdown sạch và đầy đủ thông số (`material`, `origin`, `careInstructions`, `sizeGuide`, kích thước/cân nặng đóng gói). `storage.mjs` quản lý đọc/ghi lũy tiến (`shops.json`, `products.json`), tải ảnh và tự động sinh ảnh PNG/WebP hợp lệ khi cần bổ sung đủ tối thiểu 3 ảnh hoặc ảnh logo/banner shop.
2. **Tầng Bóc tách PDP & Điều phối (`tools/marketplace-browser/lib/extractors/` & `scrape.mjs`)**: Tách extractor cho `lazada.mjs` (SSR `__moduleData__` + JSON-LD + DOM), `shopee.mjs` (Network response interception `pdp/get_pc` / `item/get` + DOM) và `tiki.mjs` (Listing + PDP `/api/v2/products/{id}`). `scrape.mjs` điều phối quota `--limit 1000`, `--shop-limit 50`, `--max-per-shop 25`, lưu checkpoint ngay sau từng sản phẩm, và tuyệt đối chỉ tạm dừng (`pause`) cho người dùng tự thao tác trên Chrome nếu gặp màn hình đăng nhập/xác thực.
3. **Tầng Nạp dữ liệu Backend (`BrowserMarketplaceImporter.java`)**: Đọc `shops.json` và `products.json`, thực hiện `s3.putObject` đẩy bytes thật cho mọi logo, banner, giấy tờ KYC và ảnh sản phẩm trước khi lưu `Upload`, khởi tạo ~50 cửa hàng `APPROVED` + `VERIFIED` kèm hồ sơ vận hành và bố cục trang trí `ShopDecoration` đã `publish`, đồng thời tạo hoặc `upsert` ~1.000 sản phẩm với đầy đủ variants, gallery images, variant color images, garment images và thông số kỹ thuật.

**Tech Stack:** Node.js 22+ (ESM, `node:test`, Playwright), Spring Boot 4 / Java 25, Spring Data JDBC, AWS SDK v2 S3 (`S3Client`), Jackson (`tools.jackson.databind`), JUnit 5 + AssertJ + Testcontainers.

**Spec:** `d:\fashionstore\fashionstore\docs\superpowers\specs\2026-11-27-marketplace-scraper-and-importer-design.md`

## Global Constraints

- **Cấm tuyệt đối toán tử 3 ngôi (`? :`)** trong mọi file Java (và hạn chế tối đa / dùng `if / else` tường minh trong JS).
- **Tuyệt đối không** viết bất kỳ logic tự động giải hay vượt CAPTCHA / anti-bot; khi sàn yêu cầu đăng nhập hoặc xác thực trên Lazada/Shopee, dùng `pause()` để người dùng tự xử lý trên cửa sổ Chrome hiển thị.
- **Không bao giờ** tạo row `Upload` trong DB mà không `s3.putObject` bytes thực tế lên S3.
- Mỗi sản phẩm sau chuẩn hóa phải có: `name` ≤ 300 ký tự, `description` ≤ 20.000 ký tự, `imageFiles` từ 3 đến 9 phần tử, `variants` có ít nhất 1 phần tử với cặp `(color, size)` không trùng nhau trong cùng sản phẩm, `sku` duy nhất trong shop, `price > 0`, `compareAtPrice > price` (hoặc `null`), và **đúng 1** biến thể có `defaultVariant = true`.
- Tất cả exception/message trong Java bằng tiếng Anh; mặc định non-null (NullAway), chỉ dùng `@Nullable` (JSpecify) khi thực sự có thể null.

## Review Focus

1. **Sản phẩm gốc trên sàn chỉ có 1 chiều phân loại hoặc gộp cả màu và size vào 1 nhãn (ví dụ `"Đen - Size M"`, hoặc chỉ có `"Freesize"`)**: `normalizer.mjs` phải tách được màu và size chuẩn, tự mở rộng hoặc gán màu/size hợp lệ để không bao giờ sinh ra `color = "Theo phân loại"` hoặc trùng cặp `(color, size)` làm gãy `findVariant(variants, size, color)` trên `ProductInfo.tsx`.
2. **Sản phẩm gốc chỉ có 1 hoặc 2 ảnh**: `normalizer.mjs` và `storage.mjs` phải bổ sung từ ảnh phân loại màu hoặc sinh bản biến thể ảnh hợp lệ trên đĩa để mảng `imageFiles` luôn có từ **3 đến 9 file ảnh tồn tại thật** (thỏa `@Size(min = 3, max = 9)` của `CreateProductRequest`).
3. **Giá gốc (`originalPrice` / `compareAtPrice`) bằng 0 hoặc nhỏ hơn/bằng `price`**: Phải chuẩn hóa `compareAtPrice = null`, vì `CatalogService` ném `400 Bad Request` nếu `compareAtPrice != null && compareAtPrice <= price`.
4. **Trùng mã SKU hoặc trùng Slug khi gộp nhiều sản phẩm vào cùng một cửa hàng**: `BrowserMarketplaceImporter` phải đảm bảo SKU của từng variant là duy nhất trong phạm vi cửa hàng và nếu sản phẩm đã tồn tại (từ lần chạy cũ) thì nâng cấp (`upsert`) hoặc bỏ qua an toàn mà không làm dừng tiến trình import.
5. **Logo, banner và giấy tờ KYC của cửa hàng trên S3**: Mọi `Upload` gắn vào `CreateShopRequest` và `ShopDecoration` phải có object thực tế trên S3 (`s3.headObject` thành công) để không xảy ra lỗi 404 trên `PdpShopCard` và `ShopStorefront`.

---

### Task 1: Bộ Truy vấn 5 Danh mục, Bộ Chuẩn hóa Schema Website & Lưu trữ Lũy tiến (`queries.mjs`, `normalizer.mjs`, `storage.mjs`)

**Files:**
- Create: `d:\fashionstore\fashionstore\tools\marketplace-browser\lib\queries.mjs`
- Create: `d:\fashionstore\fashionstore\tools\marketplace-browser\lib\normalizer.mjs`
- Create: `d:\fashionstore\fashionstore\tools\marketplace-browser\lib\storage.mjs`
- Modify: `d:\fashionstore\fashionstore\tools\marketplace-browser\package.json`
- Test: `d:\fashionstore\fashionstore\tools\marketplace-browser\test\normalizer.test.mjs`

**Interfaces:**
- Produces:
  - `FASHION_QUERIES` in `lib/queries.mjs`: mảng 20 cấu hình `{ id, categorySlug, lazadaTag, shopeeKeyword, tikiKeyword, tikiCategoryId, tikiUrlKey }` phủ đủ 5 danh mục (`ao`, `vay-dam`, `ao-khoac`, `quan`, `phu-kien`).
  - `normalizeShop(rawShop)` in `lib/normalizer.mjs`: trả về `{ shopKey, source, sourceShopId, name, slug, description, sellerType, address, phone, logoUrl, bannerUrl, logoFile, bannerFile, rating, ratingCount, followerCount, processingDays, returnPolicy }`.
  - `normalizeProduct(rawProduct, shopKey)` in `lib/normalizer.mjs`: trả về `{ source, sourceId, shopKey, name, slug, categorySlug, price, originalPrice, description, material, origin, careInstructions, sizeGuide, weightGrams, lengthCm, widthCm, heightCm, tryOnEnabled, imageUrls, imageFiles, garmentImageFiles, variants: [{ sku, size, color, price, compareAtPrice, stockQuantity, defaultVariant, imageUrl, imageFile }], sourceUrl }`.
  - `MarketplaceStorage` in `lib/storage.mjs`: quản lý `loadState()`, `saveShop(shop)`, `saveProduct(product)`, `ensureProductImages(product, downloadFn)`, `ensureShopImages(shop, downloadFn)`, `createValidPngBuffer(width, height, rgb)`.

- [ ] **Step 1: Viết unit test trong `tools/marketplace-browser/test/normalizer.test.mjs`**
- Kiểm tra `FASHION_QUERIES` phủ đủ 5 `categorySlug` (`ao`, `vay-dam`, `ao-khoac`, `quan`, `phu-kien`).
- Kiểm tra `normalizeProduct`:
  - Làm sạch tên sản phẩm (xóa `[Mã ...]` đầu dòng, cắt ≤ 300 ký tự).
  - Tách nhãn phân loại gộp `"Màu Đen - Size M (45-55kg)"` thành `color: "Đen"`, `size: "M"`, trích `"45-55kg"` vào `sizeGuide`.
  - Tự mở rộng sản phẩm quần áo chỉ có 1 chiều phân loại (chỉ màu hoặc chỉ size) thành ma trận `(color, size)` không trùng lặp, đúng 1 `defaultVariant: true`, `compareAtPrice: null` khi `originalPrice <= price`.
  - Điền đầy đủ `material`, `origin`, `careInstructions`, `sizeGuide`, `weightGrams`, `lengthCm`, `widthCm`, `heightCm`, `tryOnEnabled`.
- Kiểm tra `normalizeShop`: chuẩn hóa `sellerType` (`CORPORATE` / `INDIVIDUAL`), `slug` hợp lệ `^[a-z0-9-]+$`, `processingDays` trong khoảng `1..3`.
- Kiểm tra `MarketplaceStorage`: đảm bảo một sản phẩm chỉ có 1 ảnh gốc sau khi qua `ensureProductImages` sẽ có tối thiểu 3 file ảnh tồn tại thật trên đĩa và `garmentImageFiles` hợp lệ.

- [ ] **Step 2: Chạy test xác nhận FAIL (RED)**
Run (in `d:\fashionstore\fashionstore\tools\marketplace-browser`):
```bash
node --test test/normalizer.test.mjs
```
Expected: FAIL (`Cannot find module '../lib/queries.mjs'`).

- [ ] **Step 3: Triển khai `lib/queries.mjs`, `lib/normalizer.mjs`, `lib/storage.mjs` và cập nhật script `"test": "node --test"` trong `package.json`**
- Implement đầy đủ các hàm theo đúng signature và quy tắc trong Spec mục 4.2 và 4.4.

- [ ] **Step 4: Chạy test xác nhận PASS (GREEN)**
Run (in `d:\fashionstore\fashionstore\tools\marketplace-browser`):
```bash
node --test test/normalizer.test.mjs
```
Expected: PASS (0 failures).

---

### Task 2: Bộ Bóc tách Trang Chi tiết PDP Đa Nguồn (`lazada.mjs`, `shopee.mjs`, `tiki.mjs`) & Nâng cấp CLI `scrape.mjs`

**Files:**
- Create: `d:\fashionstore\fashionstore\tools\marketplace-browser\lib\extractors\lazada.mjs`
- Create: `d:\fashionstore\fashionstore\tools\marketplace-browser\lib\extractors\shopee.mjs`
- Create: `d:\fashionstore\fashionstore\tools\marketplace-browser\lib\extractors\tiki.mjs`
- Modify: `d:\fashionstore\fashionstore\tools\marketplace-browser\scrape.mjs`
- Test: `d:\fashionstore\fashionstore\tools\marketplace-browser\test\extractors.test.mjs`

**Interfaces:**
- Consumes: `FASHION_QUERIES`, `normalizeShop`, `normalizeProduct`, `MarketplaceStorage` from Task 1.
- Produces:
  - `parseLazadaPdpPayload(moduleData, jsonLd, fallbackListing)` & `scrapeLazada(context, options, storage)` in `lib/extractors/lazada.mjs`
  - `parseShopeePdpPayload(pdpData, shopData, fallbackListing)` & `scrapeShopee(context, options, storage)` in `lib/extractors/shopee.mjs`
  - `parseTikiPdpPayload(pdpData, sellerData, fallbackListing)` & `scrapeTiki(context, options, storage)` in `lib/extractors/tiki.mjs`
  - `QuotaTracker` & CLI orchestrator in `scrape.mjs` supporting `--source <all|lazada|shopee|tiki>`, `--limit <1000>`, `--shop-limit <50>`, `--max-per-shop <25>`, `--resume <true|false>`, `--headless <true|false>`.

- [ ] **Step 1: Viết unit test trong `tools/marketplace-browser/test/extractors.test.mjs`**
- Kiểm tra `parseLazadaPdpPayload` bóc tách đúng gallery images (chuyển thumbnail `_80x80q80` thành URL gốc), variants từ `skuBase.properties` + `skuBase.skus` + `skuInfos`, specifications (`Chất liệu`, `Xuất xứ`), description và thông tin shop (`seller`).
- Kiểm tra `parseShopeePdpPayload` bóc tách đúng gallery images từ mã hash, `tier_variations` + `models` (chia giá cho `100000` khi giá ở đơn vị micro-VND), `attributes`, `description` và `shop_detailed`.
- Kiểm tra `parseTikiPdpPayload` bóc tách đúng `images` (`large_url`), `configurable_options` + `configurable_products`, `specifications`, `description` và `current_seller`.
- Kiểm tra `QuotaTracker` ưu tiên chọn sản phẩm từ shop mới cho đến khi đạt `shopLimit` (50 shop) và giới hạn `maxPerShop` để phân bổ đều ~1.000 sản phẩm trên ~50 cửa hàng.

- [ ] **Step 2: Chạy test xác nhận FAIL (RED)**
Run (in `d:\fashionstore\fashionstore\tools\marketplace-browser`):
```bash
node --test test/extractors.test.mjs
```
Expected: FAIL (`Cannot find module '../lib/extractors/lazada.mjs'`).

- [ ] **Step 3: Triển khai `lib/extractors/lazada.mjs`, `lib/extractors/shopee.mjs`, `lib/extractors/tiki.mjs` và nâng cấp `scrape.mjs`**
- Tuyệt đối không có code giải/vượt CAPTCHA; sử dụng `pause()` khi Lazada/Shopee yêu cầu người dùng xác thực trên giao diện Chrome.
- Lưu lũy tiến mỗi shop và mỗi sản phẩm qua `MarketplaceStorage` ngay khi lấy xong trang chi tiết.

- [ ] **Step 4: Chạy toàn bộ test Node.js xác nhận PASS (GREEN)**
Run (in `d:\fashionstore\fashionstore\tools\marketplace-browser`):
```bash
node --test
```
Expected: PASS (all tests in `normalizer.test.mjs` and `extractors.test.mjs` pass).

---

### Task 3: Nâng cấp `BrowserMarketplaceImporter.java` (Đa Gian hàng, S3 Assets Thật, Trang trí Storefront & Sản phẩm Đầy đủ Tính năng)

**Files:**
- Modify: `d:\fashionstore\fashionstore\src\main\java\com\minhnbnt\fashionstore\infrastructure\BrowserMarketplaceImporter.java`
- Create: `d:\fashionstore\fashionstore\src\test\java\com\minhnbnt\fashionstore\infrastructure\BrowserMarketplaceImporterTest.java`

**Interfaces:**
- Consumes: `data/marketplace-import/shops.json` (tùy chọn, tương thích ngược nếu thiếu), `data/marketplace-import/products.json`, `data/marketplace-import/images/**`.
- Produces:
  - `BrowserMarketplaceImporter` hỗ trợ tạo nhiều shop từ `shops.json` (hoặc `shop` nhúng trong từng item `products.json`, fallback về `browser-marketplace-import`), `s3.putObject` bytes thực tế cho mọi logo, banner, giấy tờ KYC, cập nhật hồ sơ `UpdateShopProfileRequest`, cập nhật `rating`/`followerCount`, xuất bản `ShopDecoration` (`BANNER_SLIDER`, `CATEGORY_GRID`, `PRODUCT_COLLECTION`), đảm bảo đủ 5 danh mục (`ao`, `vay-dam`, `ao-khoac`, `quan`, `phu-kien`), và tạo/cập nhật (`upsert`) sản phẩm với đầy đủ 3–9 ảnh gallery, biến thể Màu × Size kèm `imageUploadId`, `garmentUploadIds` + `tryOnEnabled`, `material`, `origin`, `careInstructions`, `sizeGuide`, `weightGrams`, `lengthCm`, `widthCm`, `heightCm`.

- [ ] **Step 1: Viết integration test trong `BrowserMarketplaceImporterTest.java`**
- Kiểm tra import từ manifest mẫu trong thư mục tạm (`@TempDir`) gồm `shops.json` (2 cửa hàng: 1 `CORPORATE`, 1 `INDIVIDUAL`) và `products.json` (sản phẩm có 3 ảnh gallery, 2 ảnh garment, 4 biến thể Màu × Size có `imageFile` riêng theo màu, đầy đủ `material`, `origin`, `careInstructions`, `sizeGuide`, `weightGrams`, `lengthCm`, `widthCm`, `heightCm`, `tryOnEnabled = true`):
  - Cả 2 shop đều ở trạng thái `APPROVED`, `isTrusted() == true`, có `returnPolicy` và `ShopDecoration` đã `publish` (`publishedAt != null`).
  - Các object S3 của logo shop, banner shop, giấy tờ KYC và ảnh sản phẩm đều tồn tại thật trong bucket (`s3.headObject` không ném lỗi).
  - Sản phẩm được tạo với đủ 4 biến thể (có `imageUploadId` riêng cho màu), 3 ảnh gallery, 2 ảnh garment (`garments` trong `CatalogService.getDetail`), `tryOnEnabled == true`, và đầy đủ thông số kỹ thuật.
  - Chạy lại importer lần 2 (idempotent / upsert): không tạo trùng sản phẩm hay cửa hàng, và nếu sản phẩm cũ thiếu thông số/biến thể thì được nâng cấp đầy đủ.

- [ ] **Step 2: Chạy test xác nhận FAIL (RED)**
Run (in `d:\fashionstore\fashionstore`):
```bash
.\gradlew.bat test --tests com.minhnbnt.fashionstore.infrastructure.BrowserMarketplaceImporterTest
```
Expected: FAIL (hiện tại `BrowserMarketplaceImporter` chỉ tạo 1 shop `browser-marketplace-import`, không đọc `shops.json`, không đẩy ảnh logo/banner shop lên S3, và chỉ tạo 1 variant `"Freesize"`).

- [ ] **Step 3: Nâng cấp `BrowserMarketplaceImporter.java` (tuân thủ nghiêm ngặt `AGENTS.md`: cấm toán tử 3 ngôi `? :`, NullAway clean)**
- Inject thêm `ShopDecorationService` (hoặc `ShopDecorationRepository`) để lưu và `publish` bố cục trang trí mặc định cho từng shop import.
- Tạo ảnh PNG hợp lệ bằng `BufferedImage` / `ImageIO` cho các giấy tờ KYC seed (`seeds/kyc/id-front.png`, `id-back.png`, `id-selfie.png`) và fallback logo/banner nếu shop thiếu file, gọi `s3.putObject` trước khi tạo row `Upload`.
- Đọc `shops.json` + `products.json`, ánh xạ `shopKey` → `ImportContext(ownerId, shopId, shopSlug)`.
- Đọc danh sách `imageFiles` (fallback `imageFile`), `garmentImageFiles`, `variants` (fallback 1 variant nếu đọc manifest cũ), và các trường chi tiết (`description`, `material`, `origin`, `careInstructions`, `sizeGuide`, `weightGrams`, `lengthCm`, `widthCm`, `heightCm`, `tryOnEnabled`).
- Hỗ trợ `upsert`: nếu sản phẩm đã tồn tại theo `(shopId, slug)` nhưng chỉ có ≤ 1 biến thể hoặc ≤ 1 ảnh (dữ liệu cũ), gọi `catalogService.updateProduct` để nâng cấp lên dữ liệu đầy đủ.

- [ ] **Step 4: Chạy test `BrowserMarketplaceImporterTest` xác nhận PASS (GREEN)**
Run (in `d:\fashionstore\fashionstore`):
```bash
.\gradlew.bat test --tests com.minhnbnt.fashionstore.infrastructure.BrowserMarketplaceImporterTest
```
Expected: PASS.

---

### Task 4: Thu thập & Chuẩn hóa Bộ Dữ liệu ~1.000 Sản phẩm & ~50 Cửa hàng Thực tế và Kiểm증 Toàn bộ Hệ thống

**Files:**
- Generate/Update: `d:\fashionstore\fashionstore\data\marketplace-import\shops.json`
- Generate/Update: `d:\fashionstore\fashionstore\data\marketplace-import\products.json`
- Generate/Update: `d:\fashionstore\fashionstore\data\marketplace-import\images\**`
- Modify (if needed for config): `d:\fashionstore\fashionstore\config\springboot.yml`

**Interfaces:**
- Consumes: `scrape.mjs`, `lib/extractors/*`, `lib/normalizer.mjs`, `lib/storage.mjs`, `BrowserMarketplaceImporter.java`.
- Produces:
  - Bộ dữ liệu thực tế tại `d:\fashionstore\fashionstore\data\marketplace-import\` gồm **~50 cửa hàng** trong `shops.json`, **~1.000 sản phẩm** trong `products.json` (phủ đủ 5 danh mục `ao`, `vay-dam`, `ao-khoac`, `quan`, `phu-kien`) cùng toàn bộ file ảnh thật trong `data/marketplace-import/images/`.
  - Cấu hình `app.browser-import.enabled: true` trong `config/springboot.yml` để khi khởi động backend dữ liệu tự động được nạp vào hệ thống.

- [ ] **Step 1: Chạy công cụ thu thập & chuẩn hóa dữ liệu để tạo bộ dữ liệu ~1.000 sản phẩm và ~50 cửa hàng tại `data/marketplace-import/`**
Run (in `d:\fashionstore\fashionstore\tools\marketplace-browser`):
```bash
node scrape.mjs --source tiki --limit 1000 --shop-limit 50 --max-per-shop 25 --no-pause
```
(Kết hợp thu thập từ nguồn công khai không chặn CAPTCHA và kiểm tra số lượng sản phẩm, cửa hàng, file ảnh trong `data/marketplace-import/`).
Expected: `shops.json` có ~50 cửa hàng, `products.json` có ~1.000 sản phẩm, mỗi sản phẩm có 3–9 `imageFiles` tồn tại thật trên đĩa, đầy đủ `variants` `(color, size)` không trùng lặp, `garmentImageFiles`, `material`, `origin`, `careInstructions`, `sizeGuide`.

- [ ] **Step 2: Chạy toàn bộ test suite của Node.js tool và Backend Spring Boot**
Run:
```bash
node --test
.\gradlew.bat test
```
Expected: 100% PASS.
