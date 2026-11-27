# Uniqlo No-Model Data Source — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thay thế 1.000 sản phẩm Tiki bằng 1.000 sản phẩm Uniqlo Vietnam, mỗi màu biến thể có ảnh riêng từ `main[colorCode]`, garment = flat-lay `sub14`/`sub17` cùng sản phẩm, không có người mẫu thật.

**Architecture:** HTTP-only scraper (không cần Playwright) fetch Uniqlo listing API, normalize sang NormalizedProduct schema hiện tại, download ảnh + whitening garment, re-import vào Postgres + S3 qua BrowserMarketplaceImporter.

**Tech Stack:** Node.js 20 ESM (scraper), Python 3 + Pillow/numpy (garment), Java 21 + Spring Boot (importer), PostgreSQL 15, MinIO S3

**Spec:** `docs/superpowers/specs/2026-11-27-uniqlo-no-model-data-source-design.md`

## Global Constraints

- **TUYỆT ĐỐI KHÔNG dùng ternary `? :`** trong Java hoặc TypeScript/TSX — luôn dùng `if / else`
- Mọi `Upload` row trong DB phải có bytes thật trên S3
- Uniqlo API header: `x-fr-clientid: uq.vn.web-spa` (bắt buộc)
- Max 36 items/request; dedup bằng `l1Id`
- Không thay đổi `NormalizedProduct` schema hoặc `BrowserMarketplaceImporter.java`
- Giữ nguyên 11 sản phẩm seed (source ≠ 'UNIQLO')
- Code file: `tools/marketplace-browser/lib/extractors/uniqlo.mjs`

## Review Focus

- **`main[colorCode]` missing cho 1 màu:** nếu Uniqlo không trả về `main[displayCode]` cho 1 màu (ảnh chưa upload) → variant vẫn được tạo nhưng `imageFile = null`; importer phải không crash — test: variant với `imageFile: null` vẫn import được
- **Flat-lay không tồn tại (sub14 absent):** sản phẩm không có `sub14`/`sub17` → `garmentImageFiles = []` → `tryOnEnabled = false`; importer xử lý đúng — test: sản phẩm không có garment không bị skip
- **Màu Uniqlo không map được (filterCode lạ):** `filterCode` không trong map → fallback sang parse `name` bằng `STANDARD_COLORS` hiện tại — test: filterCode `PURPLE` → canonical `Tím`
- **Dedup cross-query:** cùng `l1Id` xuất hiện ở nhiều query (ví dụ "ao thun" và "ao") → chỉ scrape 1 lần — test: Set dedup đúng
- **garmentImageFiles whitening fail:** ảnh sub14 không tải được (network) → ghi log, bỏ qua thay vì crash

---

## Task 1: Thêm UNIQLO_FILTER_CODE map vào normalizer + export hashString

**Files:**
- Modify: `tools/marketplace-browser/lib/normalizer.mjs:1-100`

**Interfaces:**
- Produces: `export function mapUniqloFilterCode(filterCode: string): string | null`
- Produces: `export function hashString(input: string): number` ← thêm `export` vào function đã có

**Calls available after task:** `extractColorFromText(text)` (line 150), `extractSizeAndHintFromText(text)` (line 175) — đây là tên export đúng (không phải `normalizeColorName`/`normalizeSizeName`)

- [ ] **Step 1: Thêm `export` vào `hashString`**

  Dòng 86 hiện tại: `function hashString(input) {` → đổi thành `export function hashString(input) {`

- [ ] **Step 2: Thêm map và function `mapUniqloFilterCode`**

  Thêm sau `COLOR_CODE_MAP` (dòng ~50):
  ```javascript
  const UNIQLO_FILTER_CODE_TO_CANONICAL = {
    WHITE: 'Trắng',
    BLACK: 'Đen',
    GRAY: 'Xám',
    BLUE: 'Xanh Dương',
    NAVY: 'Xanh Navy',
    GREEN: 'Xanh Rêu',
    PINK: 'Hồng',
    RED: 'Đỏ',
    BROWN: 'Nâu',
    BEIGE: 'Be',
    YELLOW: 'Vàng',
    PURPLE: 'Tím',
    ORANGE: 'Cam',
    STRIPE: 'Kẻ sọc',
    PRINT: 'Họa tiết',
  }

  export function mapUniqloFilterCode(filterCode) {
    if (!filterCode) {
      return null
    }
    const upper = String(filterCode).toUpperCase().trim()
    const direct = UNIQLO_FILTER_CODE_TO_CANONICAL[upper]
    if (direct) {
      return direct
    }
    return null
  }
  ```

- [ ] **Step 3: Verify exports**

  ```bash
  cd d:\fashionstore\fashionstore\tools\marketplace-browser
  node --input-type=module <<'EOF'
  import { mapUniqloFilterCode, hashString, extractColorFromText, extractSizeAndHintFromText } from './lib/normalizer.mjs'
  console.log(mapUniqloFilterCode('WHITE'))      // → Trắng
  console.log(mapUniqloFilterCode('NAVY'))       // → Xanh Navy
  console.log(mapUniqloFilterCode('UNKNOWN'))    // → null
  console.log(hashString('475367') % 50)         // → number 0-49
  console.log(extractColorFromText('BLACK'))     // → Đen
  console.log(extractSizeAndHintFromText('XL'))  // → { size: 'XL', hint: ... }
  EOF
  ```

- [ ] **Step 4: Commit**

  ```bash
  cd d:\fashionstore\fashionstore
  git add tools/marketplace-browser/lib/normalizer.mjs
  git commit -m "feat(scraper): add Uniqlo filterCode→canonical color map, export hashString"
  ```

---

## Task 2: Tạo `lib/extractors/uniqlo.mjs`

**Files:**
- Create: `tools/marketplace-browser/lib/extractors/uniqlo.mjs`

**Interfaces:**
- Consumes: `normalizeShop`, `normalizeProduct`, `normalizeUrl`, `safeId`, `mapUniqloFilterCode` từ `normalizer.mjs`; `QuotaTracker` từ `scrape.mjs` (passed in)
- Produces: `export async function scrapeUniqlo(options, storage, quotaTracker)`

**Key logic:**

```javascript
// UNIQLO_QUERIES: array of {query, categorySlug} covering 5 categories
const UNIQLO_QUERIES = [
  { query: 'ao thun', categorySlug: 'ao' },
  { query: 'ao so mi', categorySlug: 'ao' },
  { query: 'ao polo', categorySlug: 'ao' },
  { query: 'ao khoac', categorySlug: 'ao-khoac' },
  { query: 'quan', categorySlug: 'quan' },
  { query: 'quan jeans', categorySlug: 'quan' },
  { query: 'vay', categorySlug: 'vay-dam' },
  { query: 'vay dam', categorySlug: 'vay-dam' },
  { query: 'do bo', categorySlug: 'ao' },
  { query: 'phu kien', categorySlug: 'phu-kien' },
]

// 50 Uniqlo-themed shops:
const UNIQLO_SHOPS = [/* 50 entries với name, address, rating */]

// Per item:
// 1. colors = item.colors (filterCode, displayCode, name)
// 2. sizes = item.sizes (name)  
// 3. noModelSubs = item.images.sub.filter(s => s.image && (!s.model || s.model.length === 0))
// 4. flatLaySubs = noModelSubs.filter(s => /_sub1[47]_/.test(s.image))
// 5. galleryMainImages = colors.map(c => item.images.main?.[c.displayCode]?.image).filter(Boolean)
// 6. imageUrls = [...new Set([...galleryMainImages, ...noModelSubs.map(s=>s.image)])]
// 7. garmentImageFiles = flatLaySubs.map(s => s.image)  ← URL, download qua storage
// 8. variants: colors × sizes, variant.imageUrl = main[color.displayCode]?.image
```

- [ ] **Step 1: Viết `scrapeUniqlo` function** trong `tools/marketplace-browser/lib/extractors/uniqlo.mjs`

  Import: `import { normalizeShop, normalizeProduct, normalizeUrl, safeId, mapUniqloFilterCode, hashString, extractColorFromText, extractSizeAndHintFromText } from '../normalizer.mjs'`

  Hàm phải:
  - `const seen = new Set()` dedup l1Id
  - Vòng for `query` trong `UNIQLO_QUERIES`, nested vòng `offset = 0, 36, 72...` đến khi hết hoặc `quotaTracker.isComplete()`
  - Fetch: `https://www.uniqlo.com/vn/api/commerce/v5/vi/products?limit=36&offset=${offset}&q=${encodeURIComponent(query.query)}`
  - Header: `{ 'x-fr-clientid': 'uq.vn.web-spa', accept: 'application/json' }`
  - Mỗi item: gọi `buildUniqloProduct(item, query.categorySlug, shopPool)` → trả về `{ shop, product }`
  - Gọi `storage.ensureShopImages(shop, downloadFn)`, `storage.saveShop(shop)`
  - Gọi `storage.ensureProductImages(product, downloadFn)`, `storage.saveProduct(product)`
  - `storage.flushShops()` và `storage.flushProducts()` mỗi batch
  - Log: `[uniqlo] [n/limit] [shops: k] ProductName`

- [ ] **Step 2: Viết helper `buildUniqloProduct(item, categorySlug, shopPool)`**

  Signature: `(item: object, categorySlug: string, shopPool: object[]) => { shop: NormalizedShop, product: NormalizedProduct }`

  - `shopIndex = hashString(item.l1Id) % shopPool.length` — dùng `hashString` đã export từ Task 1
  - `colors = Array.isArray(item.colors) ? item.colors : [item.colors].filter(Boolean)`
  - `sizes = Array.isArray(item.sizes) ? item.sizes : [item.sizes].filter(Boolean)`
  - `noModelSubs` = `(item.images?.sub ?? []).filter(s => s.image && (!s.model || s.model.length === 0))`
  - `flatLayUrls` = `noModelSubs.filter(s => /_sub1[47]_/.test(s.image)).map(s => s.image)`
  - `mainColorImages` = `colors.map(c => { const img = item.images?.main?.[c.displayCode]?.image; if (img) { return img } return null }).filter(Boolean)`
  - Dedup imageUrls: `[...new Set([...mainColorImages, ...noModelSubs.map(s => s.image)])]` slice(0, 9)
  - `variants` = `buildVariants(item, colors, sizes, categorySlug)` — gắn `imageUrl` per-color vào từng variant
  - Gọi `normalizeProduct({ source: 'UNIQLO', sourceId: item.l1Id, imageUrls, garmentImageFiles: flatLayUrls, variants, ... })`
  - **QUAN TRỌNG:** `imageUrls` (không phải `imageFiles`) là field mà `ensureProductImages` dùng để download — `imageFiles` do storage tự điền sau khi download.

- [ ] **Step 3: Viết `buildVariants(item, colors, sizes, categorySlug)`**

  > **Dùng đúng tên export:** `extractColorFromText(colorName)` (không phải `normalizeColorName`); `extractSizeAndHintFromText(sizeName).size` (không phải `normalizeSizeName`)

  - for color of colors:
    ```javascript
    let canonical = mapUniqloFilterCode(color.filterCode)
    if (!canonical) {
      canonical = extractColorFromText(color.name)
    }
    ```
  - for size of sizes: `const normalizedSize = extractSizeAndHintFromText(size.name).size`
  - `imageUrl = item.images?.main?.[color.displayCode]?.image` (nếu không tồn tại → `null`)
  - Trả về array `{ color: canonical, size: normalizedSize, price, originalPrice, stock, imageUrl, imageFile: null }`
  - **LƯU Ý:** `variant.imageFile` được set `null` ở đây — `storage.ensureProductImages` sẽ null hóa nó dù sao. `imageUrl` trên variant sẽ được `BrowserMarketplaceImporter.java` đọc để tạo per-color `product_image` gắn `variant_id`.

- [ ] **Step 4: Smoke test trên 10 sản phẩm**

  ```bash
  cd d:\fashionstore\fashionstore\tools\marketplace-browser
  node -e "
  import('./lib/extractors/uniqlo.mjs').then(async m => {
    const { MarketplaceStorage } = await import('./lib/storage.mjs')
    // dry-run: check 3 items only
    const headers = { 'x-fr-clientid': 'uq.vn.web-spa' }
    const r = await fetch('https://www.uniqlo.com/vn/api/commerce/v5/vi/products?limit=3&offset=0&q=ao+thun', { headers })
    const data = await r.json()
    for (const item of data.result.items) {
      const result = m.buildUniqloProduct(item, 'ao', m.UNIQLO_SHOPS)
      console.log(JSON.stringify({ name: result.product.name, variants: result.product.variants?.length, garments: result.product.garmentImageFiles?.length, images: result.product.imageUrls?.length }, null, 2))
    }
  })
  "
  ```
  Expected: 3 objects, mỗi object có `variants >= 1`, `images >= 1`

- [ ] **Step 5: Commit**

  ```bash
  git add tools/marketplace-browser/lib/extractors/uniqlo.mjs
  git commit -m "feat(scraper): add Uniqlo Vietnam HTTP-only extractor"
  ```

---

## Task 3: Tích hợp vào `scrape.mjs`

**Files:**
- Modify: `tools/marketplace-browser/scrape.mjs` (thêm import + case uniqlo)

**Interfaces:**
- Consumes: `scrapeUniqlo` từ `./lib/extractors/uniqlo.mjs`

- [ ] **Step 1: Thêm import**

  Thêm vào đầu file sau các import hiện có:
  ```javascript
  import { scrapeUniqlo } from './lib/extractors/uniqlo.mjs'
  ```

- [ ] **Step 2: Thêm case uniqlo vào main switch/if**

  Tìm nơi `scrapeTiki`, `scrapeLazada`, `scrapeShopee` được gọi. Thêm:
  ```javascript
  if (cliOptions.source === 'all' || cliOptions.source === 'uniqlo') {
    await scrapeUniqlo(cliOptions, storage, quotaTracker)
  }
  ```
  Với note: Uniqlo không cần browser (`needsBrowser = false` cho case này).

- [ ] **Step 3: Verify help text / dry-run**

  ```bash
  cd d:\fashionstore\fashionstore\tools\marketplace-browser
  node scrape.mjs --help
  ```
  Expected: không crash, in ra usage

- [ ] **Step 4: Commit**

  ```bash
  git add tools/marketplace-browser/scrape.mjs
  git commit -m "feat(scraper): integrate Uniqlo source into scrape.mjs"
  ```

---

## Task 4: Chạy scraper Uniqlo + download ảnh

**Files:**
- Output: `data/marketplace-import/products.json` (1.000 Uniqlo products)
- Output: `data/marketplace-import/shops.json` (50 Uniqlo shops)
- Output: `data/marketplace-import/images/` (ảnh tải về)
- Output: `data/marketplace-import/images/garments/` (flat-lay trắng sau whitening)

- [ ] **Step 1: Backup dữ liệu Tiki hiện tại**

  ```bash
  cd d:\fashionstore\fashionstore\data\marketplace-import
  copy products.json products.tiki.backup.json
  copy shops.json shops.tiki.backup.json
  ```

- [ ] **Step 2: Chạy scraper (source=uniqlo, limit=1000)**

  ```bash
  cd d:\fashionstore\fashionstore\tools\marketplace-browser
  node scrape.mjs --source uniqlo --limit 1000 --shop-limit 50 --no-pause
  ```
  
  Expected khi hoàn tất:
  - `products.json`: 1.000 items source=UNIQLO
  - `shops.json`: 50 shops
  - Log: `[uniqlo] [1000/1000]`

- [ ] **Step 3: Verify output**

  ```bash
  node -e "
  const p = JSON.parse(require('fs').readFileSync('../../data/marketplace-import/products.json'))
  console.log('total:', p.length)
  const withGarment = p.filter(x => x.garmentImageFiles?.length > 0)
  console.log('with garment:', withGarment.length)
  const withVariantImg = p.filter(x => x.variants?.some(v => v.imageUrl))
  console.log('with per-color img:', withVariantImg.length)
  "
  ```
  Expected: total=1000, withGarment >= 800, withVariantImg >= 800

- [ ] **Step 4: Chạy generate-garments.py để whitening flat-lay**

  ```bash
  cd d:\fashionstore\fashionstore\tools\marketplace-browser
  python lib\generate-garments.py d:\fashionstore\fashionstore\data\marketplace-import
  ```
  
  Expected: in ra số ảnh garment đã xử lý

---

## Task 5: Clear DB và re-import

> **CẢNH BÁO:** Task này xóa 1.000 sản phẩm Tiki khỏi DB. 11 sản phẩm seed được giữ lại.

**Files:**
- Không thay đổi code

- [ ] **Step 1: Clear products (giữ seed)**

  ```bash
  docker exec fashionstore-postgres-1 psql -U postgres -d fashionstore -c "
  DELETE FROM product WHERE source_id LIKE 'UNIQLO-%' OR source_id LIKE 'TIKI-%';
  "
  ```

  Verify:
  ```bash
  docker exec fashionstore-postgres-1 psql -U postgres -d fashionstore -c "SELECT COUNT(*) FROM product;"
  ```
  Expected: 11 (chỉ còn seed)

- [ ] **Step 2: Chạy importer**

  ```bash
  cd d:\fashionstore\fashionstore
  .\gradlew.bat bootRun --args="--app.browser-import.enabled=true --server.port=0"
  ```
  
  Chờ đến khi log in ra `Import completed` hoặc process exit.

- [ ] **Step 3: Verify DB sau import**

  ```bash
  docker exec fashionstore-postgres-1 psql -U postgres -d fashionstore -c "
  SELECT 
    COUNT(DISTINCT p.id) as products,
    COUNT(DISTINCT pi.id) as gallery_imgs,
    COUNT(DISTINCT CASE WHEN pi.variant_id IS NOT NULL THEN pi.id END) as color_imgs,
    COUNT(DISTINCT CASE WHEN p.garment_upload_id IS NOT NULL THEN p.id END) as has_garment
  FROM product p
  LEFT JOIN product_image pi ON pi.product_id = p.id
  WHERE p.source_id LIKE 'UNIQLO-%';"
  ```
  
  Expected:
  - products = 1000
  - gallery_imgs >= 2000 (nhiều hơn trước vì có per-color)
  - color_imgs >= 2000 (per-color variant images)
  - has_garment >= 800

- [ ] **Step 4: Visual spot check qua browser**

  Mở `http://localhost:8080` → vào trang chi tiết 3 sản phẩm ngẫu nhiên → xác nhận:
  - Gallery không có người mẫu thật
  - Mỗi màu variant hiển thị ảnh màu tương ứng
  - Try-on garment khớp với ảnh sản phẩm

---

## Task 6: Commit và push

- [ ] **Step 1: Commit data (nếu cần — optional)**

  Data files (`products.json`, `shops.json`) không nhất thiết phải commit vì nặng. Chỉ commit code.

- [ ] **Step 2: Push code**

  ```bash
  cd d:\fashionstore\fashionstore
  git push origin feature/virtual-tryon-page
  ```

---

## Thứ tự thực thi song song

Các task độc lập có thể chạy song song:
- Task 1 và Task 2 (normalizer + extractor): **có thể song song** (Task 2 đọc Task 1 nhưng có thể mock)
- Task 3 phụ thuộc Task 2
- Task 4 phụ thuộc Task 3
- Task 5 phụ thuộc Task 4
- Task 6 phụ thuộc Task 5

**Khuyến nghị:** Dùng **subagent-driven** vì Task 4 (scrape 1.000 sản phẩm) chạy lâu (~10-20 phút) và cần review độc lập trước khi xóa DB ở Task 5.
