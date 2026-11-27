import path from 'node:path'
import { readFileSync } from 'node:fs'
import {
  normalizeShop,
  normalizeProduct,
  normalizeUrl,
  safeId,
  mapUniqloFilterCode,
  hashString,
  extractColorFromText,
  extractSizeAndHintFromText,
} from '../normalizer.mjs'

// ---------------------------------------------------------------------------
// Queries
// ---------------------------------------------------------------------------

export const UNIQLO_QUERIES = [
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

// ---------------------------------------------------------------------------
// Shop pool: load from existing shops.json (the 50 Tiki shops)
// ---------------------------------------------------------------------------

function loadShopPool() {
  try {
    const shopsPath = path.resolve(
      import.meta.dirname,
      '..', '..', '..', '..', 'data', 'marketplace-import', 'shops.json',
    )
    const raw = readFileSync(shopsPath, 'utf8')
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      return parsed
    }
    if (Array.isArray(parsed.shops)) {
      return parsed.shops
    }
  } catch {
    // Fall through to built-in fallback
  }
  return FALLBACK_SHOP_POOL
}

// Fallback minimal shop pool in case shops.json is not found
const FALLBACK_SHOP_POOL = [
  { shopKey: 'UNIQLO-VN001', source: 'UNIQLO', sourceShopId: 'VN001', name: 'UNIQLO Vietnam Official', isOfficial: true, address: 'Quận 1, TP. Hồ Chí Minh', rating: 4.9, ratingCount: 1200 },
  { shopKey: 'UNIQLO-VN002', source: 'UNIQLO', sourceShopId: 'VN002', name: 'UNIQLO Hà Nội', isOfficial: true, address: 'Quận Cầu Giấy, Hà Nội', rating: 4.8, ratingCount: 950 },
]

// ---------------------------------------------------------------------------
// Fetch helpers
// ---------------------------------------------------------------------------

const UNIQLO_HEADERS = {
  'x-fr-clientid': 'uq.vn.web-spa',
  accept: 'application/json',
  'user-agent': 'Mozilla/5.0 (compatible; FashionStoreCatalogImporter/1.0)',
}

async function fetchUniqloJson(url) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 25_000)
  try {
    const response = await fetch(url, {
      headers: UNIQLO_HEADERS,
      signal: controller.signal,
    })
    if (!response.ok) {
      return null
    }
    return await response.json()
  } catch {
    return null
  } finally {
    clearTimeout(timer)
  }
}

// ---------------------------------------------------------------------------
// Gemini name translation (optional — skips gracefully if no API key)
// ---------------------------------------------------------------------------

async function translateName(englishName) {
  const apiKey = process.env.GEMINI_API_KEY
  if (!apiKey) {
    return null
  }
  const prompt = `Dịch tên sản phẩm thời trang sau sang tiếng Việt tự nhiên, ngắn gọn, không giải thích: "${englishName}"`
  const body = JSON.stringify({
    contents: [{ parts: [{ text: prompt }] }],
    generationConfig: { maxOutputTokens: 60, temperature: 0 },
  })
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 10_000)
  try {
    const res = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${apiKey}`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body,
        signal: controller.signal,
      },
    )
    if (!res.ok) {
      return null
    }
    const data = await res.json()
    const translated = data?.candidates?.[0]?.content?.parts?.[0]?.text
    if (translated && String(translated).trim().length > 1) {
      return String(translated).trim()
    }
    return null
  } catch {
    return null
  } finally {
    clearTimeout(timer)
  }
}

// ---------------------------------------------------------------------------
// Build variants (colors × sizes)
// ---------------------------------------------------------------------------

function buildVariants(item, colors, sizes, categorySlug) {
  const basePrice = Number(item.prices?.base?.value ?? item.prices?.min?.value ?? 299000)
  const promoPrice = Number(item.prices?.promo?.value ?? basePrice)
  const finalPrice = Math.min(basePrice, promoPrice)
  const originalPrice = Math.max(basePrice, promoPrice)

  const variants = []
  for (const color of colors) {
    let canonical = mapUniqloFilterCode(color.filterCode)
    if (!canonical) {
      canonical = extractColorFromText(color.name)
    }
    if (!canonical) {
      canonical = 'Đen'
    }

    const imageUrl = normalizeUrl(item.images?.main?.[color.displayCode]?.image ?? null)

    for (const sizeObj of sizes) {
      const rawSizeName = sizeObj.name ?? sizeObj.displayCode ?? sizeObj.code ?? ''
      let normalizedSize = extractSizeAndHintFromText(rawSizeName).size
      if (!normalizedSize) {
        normalizedSize = rawSizeName.trim()
      }
      if (!normalizedSize) {
        normalizedSize = 'M'
      }

      variants.push({
        color: canonical,
        size: normalizedSize,
        price: finalPrice,
        originalPrice: originalPrice,
        stock: 20,
        imageUrl: imageUrl,
        imageFile: null,
      })
    }
  }
  return variants
}

// ---------------------------------------------------------------------------
// Build shop from shop pool entry
// ---------------------------------------------------------------------------

function buildShopFromPoolEntry(entry) {
  return normalizeShop({
    source: 'UNIQLO',
    sourceShopId: entry.sourceShopId ?? safeId(entry.shopKey ?? 'VN001'),
    name: entry.name ?? 'UNIQLO Vietnam',
    logoUrl: entry.logoUrl ?? null,
    isOfficial: entry.sellerType === 'CORPORATE' || entry.isOfficial === true || (entry.name ?? '').toLowerCase().includes('official'),
    address: entry.address ?? null,
    rating: entry.rating ?? null,
    ratingCount: entry.ratingCount ?? null,
    description: entry.description ?? null,
    returnPolicy: entry.returnPolicy ?? null,
    phone: entry.phone ?? null,
  })
}

// ---------------------------------------------------------------------------
// Build a single Uniqlo product
// ---------------------------------------------------------------------------

export function buildUniqloProduct(item, categorySlug, shopPool) {
  const shopIndex = hashString(item.l1Id) % shopPool.length
  const shopEntry = shopPool[shopIndex]
  const shop = buildShopFromPoolEntry(shopEntry)

  const colors = Array.isArray(item.colors)
    ? item.colors
    : [item.colors].filter(Boolean)

  const sizes = Array.isArray(item.sizes)
    ? item.sizes
    : [item.sizes].filter(Boolean)

  const allSubs = Array.isArray(item.images?.sub) ? item.images.sub : []
  const noModelSubs = allSubs.filter(
    (s) => s.image && (!s.model || s.model.length === 0),
  )
  const flatLayUrls = noModelSubs
    .filter((s) => /_sub1[47]_/.test(s.image))
    .map((s) => s.image)

  const mainColorImages = []
  for (const color of colors) {
    const img = item.images?.main?.[color.displayCode]?.image
    if (img) {
      mainColorImages.push(img)
    }
  }

  const noModelSubUrls = noModelSubs.map((s) => s.image)
  const rawImageUrls = [...mainColorImages, ...noModelSubUrls]
  const imageUrlSet = new Set()
  for (const u of rawImageUrls) {
    const norm = normalizeUrl(u)
    if (norm) {
      imageUrlSet.add(norm)
    }
  }
  const imageUrls = Array.from(imageUrlSet).slice(0, 9)

  const variants = buildVariants(item, colors, sizes, categorySlug)

  const basePrice = Number(item.prices?.base?.value ?? item.prices?.min?.value ?? 299000)
  const promoPrice = Number(item.prices?.promo?.value ?? basePrice)
  const finalPrice = Math.min(basePrice, promoPrice)
  const originalPrice = Math.max(basePrice, promoPrice)

  const sourceId = safeId(item.l1Id)
  const sourceUrl = `https://www.uniqlo.com/vn/vi/products/${item.l1Id}.html`

  const product = normalizeProduct(
    {
      source: 'UNIQLO',
      sourceId: sourceId,
      name: item.name ?? 'Trang phục UNIQLO',
      categorySlug: categorySlug,
      price: finalPrice,
      originalPrice: originalPrice,
      description: item.longDescription ?? item.shortDescription ?? '',
      imageUrls: imageUrls,
      garmentImageFiles: flatLayUrls,
      variants: variants,
      sourceUrl: sourceUrl,
    },
    shop.shopKey,
  )

  return { shop, product }
}

// ---------------------------------------------------------------------------
// Export the shop pool so callers can pass it in (used by smoke test)
// ---------------------------------------------------------------------------

export const UNIQLO_SHOPS = loadShopPool()

// ---------------------------------------------------------------------------
// Main scrapeUniqlo function
// ---------------------------------------------------------------------------

export async function scrapeUniqlo(options, storage, quotaTracker) {
  const { downloadFn } = options
  const shopPool = UNIQLO_SHOPS
  const seen = new Set()

  for (const queryDef of UNIQLO_QUERIES) {
    if (quotaTracker.isComplete()) {
      break
    }

    let offset = 0
    while (true) {
      if (quotaTracker.isComplete()) {
        break
      }

      const url = `https://www.uniqlo.com/vn/api/commerce/v5/vi/products?limit=36&offset=${offset}&q=${encodeURIComponent(queryDef.query)}`
      const data = await fetchUniqloJson(url)

      if (!data || !Array.isArray(data.result?.items) || data.result.items.length === 0) {
        break
      }

      const items = data.result.items
      const batch = []

      for (const item of items) {
        if (!item || !item.l1Id) {
          continue
        }
        if (seen.has(item.l1Id)) {
          continue
        }
        seen.add(item.l1Id)
        if (storage.hasCompleteProduct('UNIQLO', safeId(item.l1Id))) {
          continue
        }
        batch.push(item)
      }

      for (const item of batch) {
        if (quotaTracker.isComplete()) {
          break
        }

        const { shop, product } = buildUniqloProduct(item, queryDef.categorySlug, shopPool)

        if (!quotaTracker.canAcceptCandidate({ source: 'UNIQLO', sourceId: product.sourceId, shopKey: shop.shopKey })) {
          continue
        }

        // Translate name if GEMINI_API_KEY is set
        const translated = await translateName(product.name)
        if (translated) {
          product.name = translated
        }

        quotaTracker.recordProduct(product, shop)

        await storage.ensureShopImages(shop, downloadFn)
        await storage.saveShop(shop, false)
        await storage.ensureProductImages(product, downloadFn)
        await storage.saveProduct(product, false)

        console.log(
          `[uniqlo] [${quotaTracker.getProductCount()}/${quotaTracker.limit}] [shops: ${quotaTracker.getShopCount()}] ${product.name}`,
        )
      }

      await storage.flushShops()
      await storage.flushProducts()

      // If fewer than 36 items returned, we've exhausted this query
      if (items.length < 36) {
        break
      }

      offset += 36
    }
  }
}
