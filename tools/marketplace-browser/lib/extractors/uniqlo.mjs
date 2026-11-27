import { mkdir, writeFile, stat } from 'node:fs/promises'
import path from 'node:path'
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
// 50 inline Uniqlo-themed shops
// ---------------------------------------------------------------------------

export const UNIQLO_SHOPS = [
  { shopKey: 'UNIQLO-VN001', name: 'Uniqlo AIRism - Hà Nội', address: 'Quận Cầu Giấy, Hà Nội', rating: 4.8, ratingCount: 1340 },
  { shopKey: 'UNIQLO-VN002', name: 'Uniqlo AIRism - Hồ Chí Minh', address: 'Quận 1, TP. Hồ Chí Minh', rating: 4.7, ratingCount: 980 },
  { shopKey: 'UNIQLO-VN003', name: 'Uniqlo AIRism - Thanh Xuân', address: 'Quận Thanh Xuân, Hà Nội', rating: 4.8, ratingCount: 760 },
  { shopKey: 'UNIQLO-VN004', name: 'Uniqlo AIRism - Phú Nhuận', address: 'Quận Phú Nhuận, TP. Hồ Chí Minh', rating: 4.7, ratingCount: 820 },
  { shopKey: 'UNIQLO-VN005', name: 'Uniqlo AIRism - Đống Đa', address: 'Quận Đống Đa, Hà Nội', rating: 4.9, ratingCount: 1100 },
  { shopKey: 'UNIQLO-VN006', name: 'Uniqlo HEATTECH - Quận 3', address: 'Quận 3, TP. Hồ Chí Minh', rating: 4.8, ratingCount: 670 },
  { shopKey: 'UNIQLO-VN007', name: 'Uniqlo HEATTECH - Hải Châu', address: 'Quận Hải Châu, Đà Nẵng', rating: 4.7, ratingCount: 530 },
  { shopKey: 'UNIQLO-VN008', name: 'Uniqlo HEATTECH - Bình Thạnh', address: 'Quận Bình Thạnh, TP. Hồ Chí Minh', rating: 4.8, ratingCount: 890 },
  { shopKey: 'UNIQLO-VN009', name: 'Uniqlo HEATTECH - Hoàn Kiếm', address: 'Quận Hoàn Kiếm, Hà Nội', rating: 4.9, ratingCount: 1250 },
  { shopKey: 'UNIQLO-VN010', name: 'Uniqlo HEATTECH - Tân Bình', address: 'Quận Tân Bình, TP. Hồ Chí Minh', rating: 4.7, ratingCount: 740 },
  { shopKey: 'UNIQLO-VN011', name: 'Uniqlo LifeWear - Cầu Giấy', address: 'Quận Cầu Giấy, Hà Nội', rating: 4.8, ratingCount: 920 },
  { shopKey: 'UNIQLO-VN012', name: 'Uniqlo LifeWear - Quận 1', address: 'Quận 1, TP. Hồ Chí Minh', rating: 4.9, ratingCount: 1480 },
  { shopKey: 'UNIQLO-VN013', name: 'Uniqlo LifeWear - Thanh Xuân', address: 'Quận Thanh Xuân, Hà Nội', rating: 4.7, ratingCount: 610 },
  { shopKey: 'UNIQLO-VN014', name: 'Uniqlo LifeWear - Phú Nhuận', address: 'Quận Phú Nhuận, TP. Hồ Chí Minh', rating: 4.8, ratingCount: 770 },
  { shopKey: 'UNIQLO-VN015', name: 'Uniqlo LifeWear - Đống Đa', address: 'Quận Đống Đa, Hà Nội', rating: 4.7, ratingCount: 550 },
  { shopKey: 'UNIQLO-VN016', name: 'Uniqlo Linen - Quận 3', address: 'Quận 3, TP. Hồ Chí Minh', rating: 4.8, ratingCount: 680 },
  { shopKey: 'UNIQLO-VN017', name: 'Uniqlo Linen - Hải Châu', address: 'Quận Hải Châu, Đà Nẵng', rating: 4.7, ratingCount: 490 },
  { shopKey: 'UNIQLO-VN018', name: 'Uniqlo Linen - Bình Thạnh', address: 'Quận Bình Thạnh, TP. Hồ Chí Minh', rating: 4.9, ratingCount: 1030 },
  { shopKey: 'UNIQLO-VN019', name: 'Uniqlo Linen - Hoàn Kiếm', address: 'Quận Hoàn Kiếm, Hà Nội', rating: 4.8, ratingCount: 860 },
  { shopKey: 'UNIQLO-VN020', name: 'Uniqlo Linen - Tân Bình', address: 'Quận Tân Bình, TP. Hồ Chí Minh', rating: 4.7, ratingCount: 720 },
  { shopKey: 'UNIQLO-VN021', name: 'Uniqlo Denim - Cầu Giấy', address: 'Quận Cầu Giấy, Hà Nội', rating: 4.9, ratingCount: 1120 },
  { shopKey: 'UNIQLO-VN022', name: 'Uniqlo Denim - Quận 1', address: 'Quận 1, TP. Hồ Chí Minh', rating: 4.8, ratingCount: 950 },
  { shopKey: 'UNIQLO-VN023', name: 'Uniqlo Denim - Thanh Xuân', address: 'Quận Thanh Xuân, Hà Nội', rating: 4.7, ratingCount: 630 },
  { shopKey: 'UNIQLO-VN024', name: 'Uniqlo Denim - Phú Nhuận', address: 'Quận Phú Nhuận, TP. Hồ Chí Minh', rating: 4.8, ratingCount: 810 },
  { shopKey: 'UNIQLO-VN025', name: 'Uniqlo Denim - Đống Đa', address: 'Quận Đống Đa, Hà Nội', rating: 4.7, ratingCount: 570 },
  { shopKey: 'UNIQLO-VN026', name: 'Uniqlo Flannel - Quận 3', address: 'Quận 3, TP. Hồ Chí Minh', rating: 4.8, ratingCount: 700 },
  { shopKey: 'UNIQLO-VN027', name: 'Uniqlo Flannel - Hải Châu', address: 'Quận Hải Châu, Đà Nẵng', rating: 4.7, ratingCount: 460 },
  { shopKey: 'UNIQLO-VN028', name: 'Uniqlo Flannel - Bình Thạnh', address: 'Quận Bình Thạnh, TP. Hồ Chí Minh', rating: 4.9, ratingCount: 1050 },
  { shopKey: 'UNIQLO-VN029', name: 'Uniqlo Flannel - Hoàn Kiếm', address: 'Quận Hoàn Kiếm, Hà Nội', rating: 4.8, ratingCount: 880 },
  { shopKey: 'UNIQLO-VN030', name: 'Uniqlo Flannel - Tân Bình', address: 'Quận Tân Bình, TP. Hồ Chí Minh', rating: 4.7, ratingCount: 730 },
  { shopKey: 'UNIQLO-VN031', name: 'Uniqlo BLOCKTECH - Cầu Giấy', address: 'Quận Cầu Giấy, Hà Nội', rating: 4.8, ratingCount: 940 },
  { shopKey: 'UNIQLO-VN032', name: 'Uniqlo BLOCKTECH - Quận 1', address: 'Quận 1, TP. Hồ Chí Minh', rating: 4.9, ratingCount: 1300 },
  { shopKey: 'UNIQLO-VN033', name: 'Uniqlo BLOCKTECH - Thanh Xuân', address: 'Quận Thanh Xuân, Hà Nội', rating: 4.7, ratingCount: 590 },
  { shopKey: 'UNIQLO-VN034', name: 'Uniqlo BLOCKTECH - Phú Nhuận', address: 'Quận Phú Nhuận, TP. Hồ Chí Minh', rating: 4.8, ratingCount: 790 },
  { shopKey: 'UNIQLO-VN035', name: 'Uniqlo BLOCKTECH - Đống Đa', address: 'Quận Đống Đa, Hà Nội', rating: 4.7, ratingCount: 540 },
  { shopKey: 'UNIQLO-VN036', name: 'Uniqlo Men Basic - Quận 3', address: 'Quận 3, TP. Hồ Chí Minh', rating: 4.8, ratingCount: 710 },
  { shopKey: 'UNIQLO-VN037', name: 'Uniqlo Men Basic - Hải Châu', address: 'Quận Hải Châu, Đà Nẵng', rating: 4.7, ratingCount: 480 },
  { shopKey: 'UNIQLO-VN038', name: 'Uniqlo Men Basic - Bình Thạnh', address: 'Quận Bình Thạnh, TP. Hồ Chí Minh', rating: 4.9, ratingCount: 1070 },
  { shopKey: 'UNIQLO-VN039', name: 'Uniqlo Men Basic - Hoàn Kiếm', address: 'Quận Hoàn Kiếm, Hà Nội', rating: 4.8, ratingCount: 870 },
  { shopKey: 'UNIQLO-VN040', name: 'Uniqlo Men Basic - Tân Bình', address: 'Quận Tân Bình, TP. Hồ Chí Minh', rating: 4.7, ratingCount: 750 },
  { shopKey: 'UNIQLO-VN041', name: 'Uniqlo Women Basic - Cầu Giấy', address: 'Quận Cầu Giấy, Hà Nội', rating: 4.9, ratingCount: 1180 },
  { shopKey: 'UNIQLO-VN042', name: 'Uniqlo Women Basic - Quận 1', address: 'Quận 1, TP. Hồ Chí Minh', rating: 4.8, ratingCount: 1010 },
  { shopKey: 'UNIQLO-VN043', name: 'Uniqlo Women Basic - Thanh Xuân', address: 'Quận Thanh Xuân, Hà Nội', rating: 4.7, ratingCount: 640 },
  { shopKey: 'UNIQLO-VN044', name: 'Uniqlo Women Basic - Phú Nhuận', address: 'Quận Phú Nhuận, TP. Hồ Chí Minh', rating: 4.8, ratingCount: 830 },
  { shopKey: 'UNIQLO-VN045', name: 'Uniqlo Women Basic - Đống Đa', address: 'Quận Đống Đa, Hà Nội', rating: 4.7, ratingCount: 560 },
  { shopKey: 'UNIQLO-VN046', name: 'Uniqlo Accessories - Quận 3', address: 'Quận 3, TP. Hồ Chí Minh', rating: 4.8, ratingCount: 690 },
  { shopKey: 'UNIQLO-VN047', name: 'Uniqlo Accessories - Hải Châu', address: 'Quận Hải Châu, Đà Nẵng', rating: 4.7, ratingCount: 470 },
  { shopKey: 'UNIQLO-VN048', name: 'Uniqlo Accessories - Bình Thạnh', address: 'Quận Bình Thạnh, TP. Hồ Chí Minh', rating: 4.9, ratingCount: 1090 },
  { shopKey: 'UNIQLO-VN049', name: 'Uniqlo Accessories - Hoàn Kiếm', address: 'Quận Hoàn Kiếm, Hà Nội', rating: 4.8, ratingCount: 850 },
  { shopKey: 'UNIQLO-VN050', name: 'Uniqlo Accessories - Tân Bình', address: 'Quận Tân Bình, TP. Hồ Chí Minh', rating: 4.7, ratingCount: 760 },
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
// File exists helper
// ---------------------------------------------------------------------------

async function fileExistsWithContent(filePath) {
  try {
    const s = await stat(filePath)
    return s.size > 0
  } catch {
    return false
  }
}

function extensionFromContentType(contentType) {
  if (!contentType) {
    return '.jpg'
  }
  const lower = contentType.toLowerCase()
  if (lower.includes('webp')) {
    return '.webp'
  }
  if (lower.includes('png')) {
    return '.png'
  }
  return '.jpg'
}

// ---------------------------------------------------------------------------
// Build variants (colors × sizes)
// ---------------------------------------------------------------------------

function buildVariants(item, colors, sizes) {
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
  // entry.shopKey is e.g. 'UNIQLO-VN001'; normalizeShop prepends 'UNIQLO-' to sourceShopId,
  // so pass only the suffix (e.g. 'VN001') to avoid double-prefixing.
  const sourceShopId = String(entry.shopKey ?? 'VN001').replace(/^UNIQLO-/i, '')
  return normalizeShop({
    source: 'UNIQLO',
    sourceShopId: sourceShopId,
    name: entry.name ?? 'UNIQLO Vietnam',
    logoUrl: null,
    isOfficial: true,
    address: entry.address ?? null,
    rating: entry.rating ?? null,
    ratingCount: entry.ratingCount ?? null,
  })
}

// ---------------------------------------------------------------------------
// Build a single Uniqlo product
// ---------------------------------------------------------------------------

export function buildUniqloProduct(item, categorySlug, shopPool) {
  const shopIndex = hashString(item.l1Id) % shopPool.length
  const shopEntry = shopPool[shopIndex]
  const shop = buildShopFromPoolEntry(shopEntry)

  let colors
  if (Array.isArray(item.colors)) {
    colors = item.colors
  } else {
    colors = [item.colors].filter(Boolean)
  }

  let sizes
  if (Array.isArray(item.sizes)) {
    sizes = item.sizes
  } else {
    sizes = [item.sizes].filter(Boolean)
  }

  let allSubs
  if (Array.isArray(item.images?.sub)) {
    allSubs = item.images.sub
  } else {
    allSubs = []
  }

  const noModelSubs = allSubs.filter(
    (s) => s.image && (!s.model || s.model.length === 0),
  )

  const flatLayUrls = noModelSubs
    .filter((s) => /_sub1[47]_/.test(s.image))
    .map((s) => normalizeUrl(s.image))
    .filter(Boolean)

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

  const variants = buildVariants(item, colors, sizes)

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
      variants: variants,
      sourceUrl: sourceUrl,
    },
    shop.shopKey,
  )

  // Store flat-lay URLs in a side-channel field that survives ensureProductImages.
  // ensureProductImages clobbers product.garmentImageFiles with a gallery fallback,
  // so we keep the original https:// URLs here and read them back in scrapeUniqlo.
  product.garmentImageUrls = flatLayUrls

  return { shop, product }
}

// ---------------------------------------------------------------------------
// Download garment flat-lay URLs to local files and return relative paths
// ---------------------------------------------------------------------------

async function downloadGarmentImages(product, urls, outputDir, downloadFn) {
  if (!product.tryOnEnabled) {
    return []
  }

  if (!Array.isArray(urls) || urls.length === 0) {
    return []
  }

  const sourceFolder = 'uniqlo'
  const folderDir = path.join(outputDir, 'images', sourceFolder)
  await mkdir(folderDir, { recursive: true })

  const baseId = safeId(product.sourceId)
  const localPaths = []

  for (let i = 0; i < urls.length; i++) {
    const url = urls[i]
    if (!url || !url.startsWith('http')) {
      continue
    }

    let foundCached = false
    for (const candidateExt of ['.jpg', '.webp', '.png']) {
      const checkRel = `images/${sourceFolder}/${baseId}-garment-${i + 1}${candidateExt}`
      const fullCheck = path.join(outputDir, checkRel)
      const exists = await fileExistsWithContent(fullCheck)
      if (exists) {
        localPaths.push(checkRel)
        foundCached = true
        break
      }
    }

    if (foundCached) {
      continue
    }

    if (!downloadFn) {
      continue
    }

    try {
      const downloaded = await downloadFn(url)
      if (downloaded && downloaded.body && downloaded.body.length > 40) {
        const ext = extensionFromContentType(downloaded.contentType)
        const rel = `images/${sourceFolder}/${baseId}-garment-${i + 1}${ext}`
        await writeFile(path.join(outputDir, rel), downloaded.body)
        localPaths.push(rel)
      }
    } catch {
      // Skip failed download — garment images are optional
    }
  }

  return localPaths
}

// ---------------------------------------------------------------------------
// Main scrapeUniqlo function
// ---------------------------------------------------------------------------

export async function scrapeUniqlo(options, storage, quotaTracker) {
  const { downloadFn } = options
  const outputDir = storage.outputDir
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

        quotaTracker.recordProduct(product, shop)

        await storage.ensureShopImages(shop, downloadFn)
        await storage.saveShop(shop, false)

        // Save the original flat-lay URLs BEFORE ensureProductImages runs,
        // because ensureProductImages clobbers product.garmentImageFiles with
        // [imageFiles[0]] (gallery fallback) since the https:// URLs fail the
        // fileExistsWithContent check.
        let flatLayUrls
        if (Array.isArray(product.garmentImageUrls)) {
          flatLayUrls = product.garmentImageUrls
        } else {
          flatLayUrls = []
        }

        // ensureProductImages downloads gallery imageUrls → sets product.imageFiles
        await storage.ensureProductImages(product, downloadFn)

        // Now download the actual garment flat-lay URLs using the saved https:// list
        if (outputDir) {
          const garmentLocalPaths = await downloadGarmentImages(product, flatLayUrls, outputDir, downloadFn)
          if (garmentLocalPaths.length > 0) {
            product.garmentImageFiles = garmentLocalPaths
          } else if (product.tryOnEnabled) {
            product.garmentImageFiles = [product.imageFiles[0]]
          }
        }

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
