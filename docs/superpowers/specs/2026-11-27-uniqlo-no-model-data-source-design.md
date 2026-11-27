# Uniqlo Vietnam No-Model Data Source — Design Spec

**Date:** 2026-11-27  
**Author:** Antigravity  
**Status:** Draft — awaiting user approval

---

## 1. Intent & Why

### Vấn đề hiện tại
Website đang có **1.011 sản phẩm** nhưng:
- Ảnh gallery lấy từ Tiki → là ảnh người mẫu ngoại cảnh (lifestyle photos, có người)
- Ảnh garment lấy từ Uniqlo template ghép theo *danh mục* → không khớp với sản phẩm Tiki đang hiển thị
- Chỉ **244/4.096** ảnh gallery có per-color (màu biến thể `Trắng` → ảnh trắng, `Đen` → ảnh đen)
- Hiển thị virtual try-on với garment sai sản phẩm → trải nghiệm không nhất quán

### Mục tiêu
Thay toàn bộ 1.000 sản phẩm Tiki bằng **1.000 sản phẩm Uniqlo Vietnam**, đảm bảo:
1. **Mỗi màu biến thể có ảnh riêng** — `main[colorCode]` per màu, không phải ảnh chung
2. **Ảnh không có người mẫu thật** — Uniqlo `main[colorCode]` là ảnh studio mannequin (không có người mẫu thật như Tiki)
3. **Garment = flat-lay chính xác cùng sản phẩm** — `sub14`/`sub17` (model=[]) của đúng mã sản phẩm đó
4. **Garment khớp gallery** — cùng mã sản phẩm Uniqlo, không phải template ghép

---

## 2. Goals / Non-Goals / Anti-Goals

### Goals
- Scrape 1.000 sản phẩm Uniqlo Vietnam qua listing API (HTTP fetch, không cần browser)
- Mỗi sản phẩm: lấy ảnh per-color từ `main[colorCode]` cho gallery (studio shot, không người mẫu)
- Mỗi sản phẩm: lấy flat-lay từ `sub` images (model=[], có `_sub14_` hoặc `_sub17_`) cho garment
- 50 shops: tạo danh sách shop từ Uniqlo product lines / collections
- Re-import toàn bộ vào Postgres + S3, giữ nguyên 11 sản phẩm seed
- Mapping màu Uniqlo (EN) → canonical Vietnamese đúng (WHITE→Trắng, BLACK→Đen, NAVY→Xanh Navy...)

### Non-Goals
- Không scrape bằng browser (Playwright) — Uniqlo listing API là public REST API
- Không cần per-color flat-lay (tất cả màu share 1 flat-lay `sub14`/`sub17`) — đây là giới hạn của nguồn
- Không scrape từ Shopee hoặc Lazada trong đợt này
- Không thay đổi database schema

### Anti-Goals (failure conditions)
- Ảnh gallery có người mẫu thật (người ngoài phố, lifestyle) → thất bại
- Ảnh garment của sản phẩm A nhưng gắn vào sản phẩm B → thất bại
- Màu biến thể "Đen" nhưng ảnh gallery là ảnh trắng → thất bại
- Garment có background xám/nền tối (không phải trắng RGB 255,255,255) → thất bại

---

## 3. Constraints

- API endpoint: `GET https://www.uniqlo.com/vn/api/commerce/v5/vi/products?limit=36&offset={n}&q={query}`
- Header bắt buộc: `x-fr-clientid: uq.vn.web-spa`
- Max limit per request: 36 items
- Tổng sản phẩm khả dụng Uniqlo VN: ~1.100+ (đã xác nhận bằng cách đếm per-query)
- `main[colorCode].model` luôn có người mẫu (Women/Men) trên mannequin — NHƯNG không phải người mẫu thật chụp ngoại cảnh → chấp nhận được
- `sub` images với `model=[]` là ảnh không có người mẫu (flat-lay, overhead, detail)
- Không có per-color flat-lay — tất cả màu dùng chung `sub14`/`sub17`
- Giới hạn 1 flat-lay/sản phẩm (sub14) + có thể thêm sub17 nếu tồn tại
- Backend importer: `BrowserMarketplaceImporter.java` đã có, không thay đổi
- Code rule: **TUYỆT ĐỐI KHÔNG dùng ternary `? :`** trong Java/TypeScript

---

## 4. Data Architecture

### 4.1 Nguồn ảnh theo loại

| Loại ảnh | Nguồn Uniqlo | Model? | Dùng cho |
|----------|-------------|--------|----------|
| Gallery chính per màu | `images.main[colorCode].image` | Mannequin (Women/Men) — không người thật | `product_image` gắn với `variant_id` |
| Flat-lay front | `images.sub[*]` có `_sub14_` và `model=[]` | Không | `garment_upload_id` |
| Flat-lay back (nếu có) | `images.sub[*]` có `_sub17_` và `model=[]` | Không | `product_garment.upload_id` thứ 2 |
| Studio angle | `images.sub[*]` có `_sub11_`, `_sub12_` và `model=[]` | Không | `product_image` phụ (không color-specific) |
| Chip / swatch | `images.chip[colorCode]` | Không có | Không dùng (ảnh quá nhỏ) |

**Kết quả per sản phẩm:**
- `imageFiles` = `[main[c1].image, main[c2].image, ..., sub11, sub12]` (dedup)
- `garmentImageFiles` = `[sub14, sub17]` (nếu có)
- `variants[i].imageFile` = `main[colorCode].image` tương ứng với màu variant đó

### 4.2 Mapping màu Uniqlo → Canonical Vietnamese

| Uniqlo `filterCode` | Uniqlo `name` examples | Canonical VN |
|---------------------|------------------------|--------------|
| WHITE | WHITE, OFF WHITE, NATURAL WHITE | Trắng |
| BLACK | BLACK, CHARCOAL | Đen |
| GRAY | LIGHT GRAY, GRAY, DARK GRAY | Xám |
| BLUE | BLUE, LIGHT BLUE, MEDIUM BLUE | Xanh Dương |
| NAVY | NAVY, DARK NAVY | Xanh Navy |
| GREEN | OLIVE, KHAKI, GREEN, MINT GREEN | Xanh Rêu |
| PINK | LIGHT PINK, PINK | Hồng |
| RED | RED, WINE, BURGUNDY | Đỏ |
| BROWN | DARK BROWN, CAMEL, BEIGE | Nâu |
| BEIGE | BEIGE, ECRU | Be |
| YELLOW | YELLOW | Vàng |
| PURPLE | PURPLE, LAVENDER | Tím |
| ORANGE | ORANGE | Cam |
| STRIPE | STRIPE pattern | Kẻ sọc |
| PRINT | FLORAL, PRINT | Họa tiết |

Mapping dùng `filterCode` (ổn định, uppercase) thay vì `name` (thay đổi theo season).

### 4.3 Shop mapping

50 shops được tạo từ **Uniqlo product line collections**:
```
AIRism (5 shops), HEATTECH (5 shops), LifeWear (5 shops),
U-line (3 shops), Linen (3 shops), Denim (4 shops),
Flannel (3 shops), BLOCKTECH (3 shops), Kids (5 shops),
Men's Basic (7 shops), Women's Basic (7 shops)
```
Mỗi shop có địa chỉ từ `DEFAULT_ADDRESSES` pool, rating ngẫu nhiên 4.0–5.0.

### 4.4 Query strategy để đạt 1.000 sản phẩm

Uniqlo VN tổng ~1.100 sản phẩm. Query theo nhiều từ khóa + offset:

| Query | Approx total |
|-------|-------------|
| ao thun | 350 |
| ao so mi | 97 |
| ao polo | 35 |
| ao khoac | 105 |
| quan | 361 |
| quan jeans | 141 |
| vay | 19 |
| vay dam | 10 |
| do bo | 57 |
| phu kien | 98 |

Dedup bằng `l1Id` → lấy đủ 1.000 unique products.

---

## 5. Các thành phần cần thay đổi

### A. `lib/extractors/uniqlo.mjs` — TẠO MỚI

Chức năng:
- `scrapeUniqlo(options, storage)` — HTTP-only scraper (không cần Playwright)
- Dùng `fetch()` với header `x-fr-clientid: uq.vn.web-spa`
- Dedup bằng `Set<l1Id>` trong memory
- Output: gọi `storage.saveProduct(normalizedProduct)` per item

Cấu trúc output per sản phẩm (NormalizedProduct schema):
```javascript
{
  externalId: item.l1Id,          // "475367"
  name: item.name,
  description: item.longDescription ?? item.name,
  brand: 'Uniqlo',
  source: 'uniqlo',
  categorySlug: inferCategory(item),  // 'ao'|'quan'|'vay-dam'|'ao-khoac'|'phu-kien'
  shopId: assignShopFromLine(item),   // round-robin trong pool 50 shops
  price: item.prices?.base?.value ?? 299000,
  salePrice: item.prices?.promo?.value,
  rating: 4.0 + Math.random() * 1.0,
  reviewCount: Math.floor(Math.random() * 500 + 50),
  
  // ảnh gallery (không model thật):
  imageFiles: [
    ...colors.map(c => item.images.main[c.displayCode]?.image).filter(Boolean),
    ...noModelSubImages,   // sub11, sub12, etc.
  ],  // dedup by URL
  
  // ảnh garment (flat-lay, model=[]):
  garmentImageFiles: flatLaySubs,  // sub14, sub17 (model=[])
  
  // variants (mảng):
  variants: colors.flatMap(color =>
    sizes.map(size => ({
      color: mapFilterCodeToCanonical(color.filterCode),
      size: normalizeSizeName(size.name),
      price: item.prices?.base?.value ?? 299000,
      stock: Math.floor(Math.random() * 100 + 10),
      imageFile: item.images.main[color.displayCode]?.image,  // per-color gallery image
    }))
  ),
}
```

### B. `lib/normalizer.mjs` — CẬP NHẬT nhỏ

Thêm:
- `UNIQLO_FILTER_CODE_TO_CANONICAL` map (filterCode → canonical VN)
- `mapUniqloFilterCode(filterCode)` function
- Export thêm function này

Không thay đổi schema `NormalizedProduct` (backward compatible).

### C. `scrape.mjs` — CẬP NHẬT

Thêm:
- `import { scrapeUniqlo } from './lib/extractors/uniqlo.mjs'`  
- Thêm case `source === 'uniqlo'` vào main switch
- Flag `--source uniqlo` hoạt động không cần browser

### D. `generate-garments.py` — KHÔNG thay đổi

Vì garment images từ Uniqlo (`sub14`/`sub17`) đã là flat-lay nền xám sáng → chỉ cần whitening pipeline hiện tại.

### E. `BrowserMarketplaceImporter.java` — KHÔNG thay đổi

Đã handle per-color `variant.imageFile`, multi-garment, dedup SHA-256.

---

## 6. Approaches Considered

### Approach A: Thay toàn bộ bằng Uniqlo Vietnam (CHỌN)
- **Pro:** Ảnh consistent (cùng nguồn), per-color `main[colorCode]` chính xác, garment flat-lay đúng sản phẩm
- **Con:** Tên sản phẩm tiếng Anh/Nhật → cần dịch/format lại; cần whitening garment; không có per-color flat-lay
- **Verdict:** Nguồn tốt nhất, đáp ứng 100% yêu cầu "không người mẫu thật"

### Approach B: Giữ Tiki + bổ sung ảnh Uniqlo làm garment
- **Pro:** Giữ được tên sản phẩm tiếng Việt tự nhiên
- **Con:** Gallery (Tiki) và garment (Uniqlo) vẫn là 2 sản phẩm khác nhau → lệch
- **Verdict:** Không giải quyết được vấn đề gốc rễ

### Approach C: Crawl Shopee/Lazada tìm ảnh flat-lay
- **Pro:** Nhiều sản phẩm Việt Nam hơn
- **Con:** Anti-bot mạnh, cần browser, ảnh không đảm bảo flat-lay/studio
- **Verdict:** Không khả thi trong thời gian ngắn

---

## 7. Open Questions (cần user trả lời)

1. **Tên sản phẩm:** Uniqlo trả về tên tiếng Anh (e.g., "AIRism Cotton Polo Shirt Piqué"). Có muốn dịch sang tiếng Việt tự động không, hay giữ nguyên tiếng Anh?

2. **`main[colorCode]` studio shot:** Ảnh này có mannequin trên nền xám — không phải người thật chụp ngoài phố. User có chấp nhận loại ảnh này không, hay phải strictly chỉ dùng flat-lay (sub14/sub17) cho cả gallery lẫn garment?

3. **50 shops cũ:** Có muốn giữ lại 50 shops Tiki hiện tại không (đã có review data), hay tạo 50 shops mới theo dòng sản phẩm Uniqlo?
