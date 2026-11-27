import { FASHION_QUERIES } from '../queries.mjs'
import { normalizeShop, normalizeProduct, normalizeUrl, safeId } from '../normalizer.mjs'

export function shopeeImageUrl(hashOrUrl) {
  if (!hashOrUrl || typeof hashOrUrl !== 'string') {
    return null
  }
  const trimmed = hashOrUrl.trim()
  if (trimmed === '') {
    return null
  }
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://') || trimmed.startsWith('//')) {
    return normalizeUrl(trimmed)
  }
  return `https://down-vn.img.susercontent.com/file/${trimmed}`
}

export function toShopeeVnd(rawPrice) {
  const num = Number(rawPrice ?? 0)
  if (!Number.isFinite(num) || num <= 0) {
    return 0
  }
  if (num > 10_000_000) {
    return Math.round(num / 100_000)
  }
  return Math.round(num)
}

export function parseShopeePdpPayload(pdpData, shopData, fallbackListing = {}) {
  const item = pdpData?.data?.item ?? pdpData?.item ?? pdpData ?? {}
  const shopDetailed =
    shopData?.data ??
    shopData ??
    pdpData?.data?.shop_detailed ??
    pdpData?.shop_detailed ??
    {}

  const shopId = String(
    item.shopid ??
      shopDetailed.shopid ??
      fallbackListing.shopSourceId ??
      '2000',
  )
  const itemId = String(item.itemid ?? fallbackListing.itemId ?? fallbackListing.sourceId ?? '10000')
  const sourceId = `${shopId}-${itemId}`.replace(/^(\d+)-\1-/, '$1-')

  let shopName =
    shopDetailed.name ??
    item.shop_name ??
    fallbackListing.shopName ??
    `Shopee Fashion ${shopId}`

  const isOfficial = Boolean(
    shopDetailed.is_official_shop ||
      item.is_official_shop ||
      shopDetailed.show_official_shop_label ||
      /official|mall|flagship|chính hãng/i.test(String(shopName)),
  )

  const portraitHash = shopDetailed?.account?.portrait ?? shopDetailed?.portrait ?? null
  const coverHash = shopDetailed?.cover ?? null

  const shop = normalizeShop({
    source: 'SHOPEE',
    sourceShopId: shopId,
    name: shopName,
    description: shopDetailed.description ?? null,
    address: shopDetailed.shop_location ?? item.shop_location ?? fallbackListing.location ?? null,
    logoUrl: shopeeImageUrl(portraitHash),
    bannerUrl: shopeeImageUrl(coverHash),
    isOfficial,
    rating: Number(shopDetailed.rating_star ?? 4.8),
    ratingCount: Number(shopDetailed.rating_good ?? 120),
    followerCount: Number(shopDetailed.follower_count ?? 1500),
  })

  const galleryImages = []
  let rawImages = []
  if (Array.isArray(item.images)) {
    rawImages = item.images
  } else if (item.image) {
    rawImages = [item.image]
  }
  for (const hash of rawImages) {
    const url = shopeeImageUrl(hash)
    if (url && !galleryImages.includes(url)) {
      galleryImages.push(url)
    }
  }
  if (fallbackListing.imageUrl) {
    const url = shopeeImageUrl(fallbackListing.imageUrl)
    if (url && !galleryImages.includes(url)) {
      galleryImages.push(url)
    }
  }

  let tierVariations = []
  if (Array.isArray(item.tier_variations)) {
    tierVariations = item.tier_variations
  }

  const tier0 = tierVariations[0] ?? null
  const tier1 = tierVariations[1] ?? null
  let tier0Images = []
  if (Array.isArray(tier0?.images)) {
    tier0Images = tier0.images
  }

  for (const hash of tier0Images) {
    const url = shopeeImageUrl(hash)
    if (url && !galleryImages.includes(url)) {
      galleryImages.push(url)
    }
  }

  const rawVariants = []
  let models = []
  if (Array.isArray(item.models)) {
    models = item.models
  }
  for (const model of models) {
    let tierIndex = []
    if (Array.isArray(model.tier_index)) {
      tierIndex = model.tier_index
    }
    let option1 = null
    let option2 = null
    let variantImg = null

    if (tier0 && Array.isArray(tier0.options) && tierIndex.length > 0) {
      option1 = tier0.options[tierIndex[0]] ?? null
      const imgHash = tier0Images[tierIndex[0]]
      if (imgHash) {
        variantImg = shopeeImageUrl(imgHash)
      }
    }
    if (tier1 && Array.isArray(tier1.options) && tierIndex.length > 1) {
      option2 = tier1.options[tierIndex[1]] ?? null
    }

    rawVariants.push({
      name: model.name ?? '',
      option1,
      option2,
      price: toShopeeVnd(model.price ?? item.price_min ?? item.price),
      originalPrice: toShopeeVnd(
        model.price_before_discount ??
          item.price_min_before_discount ??
          model.price,
      ),
      stock: Number(model.normal_stock ?? model.stock ?? 20),
      imageUrl: variantImg,
    })
  }

  const attributes = {}
  if (Array.isArray(item.attributes)) {
    for (const attr of item.attributes) {
      if (attr && attr.name && attr.value) {
        attributes[attr.name] = attr.value
      }
    }
  }

  const basePrice = toShopeeVnd(
    item.price_min ?? item.price ?? fallbackListing.price ?? 189000,
  )
  const baseOriginal = toShopeeVnd(
    item.price_min_before_discount ??
      item.price_before_discount ??
      fallbackListing.originalPrice ??
      basePrice,
  )

  const product = normalizeProduct(
    {
      source: 'SHOPEE',
      sourceId: safeId(sourceId),
      name: item.title ?? item.name ?? fallbackListing.name ?? 'Sản phẩm thời trang Shopee',
      categorySlug: fallbackListing.categorySlug ?? 'ao',
      price: basePrice,
      originalPrice: baseOriginal,
      description: item.description ?? '',
      attributes,
      imageUrls: galleryImages,
      variants: rawVariants,
      sourceUrl:
        normalizeUrl(fallbackListing.sourceUrl) ??
        `https://shopee.vn/product/${shopId}/${itemId}`,
    },
    shop.shopKey,
  )

  return { shop, product }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function scrapeShopee(context, options, storage) {
  const { quotaTracker, pause, noPause, downloadFn } = options
  const page = await context.newPage()

  let latestSearchPayload = null
  let latestPdpPayload = null
  let latestShopPayload = null

  page.on('response', async (response) => {
    const url = response.url()
    try {
      if (url.includes('/api/v4/search/search_items')) {
        const body = await response.json()
        if (body && Array.isArray(body.items)) {
          latestSearchPayload = body
        }
      } else if (url.includes('/api/v4/pdp/get_pc') || url.includes('/api/v4/item/get')) {
        const body = await response.json()
        if (body && (body.data || body.item)) {
          latestPdpPayload = body
        }
      } else if (url.includes('/api/v4/pdp/get_shop') || url.includes('/api/v4/shop/get_shop_detail')) {
        const body = await response.json()
        if (body && body.data) {
          latestShopPayload = body
        }
      }
    } catch {
      // Ignore non-JSON responses
    }
  })

  try {
    await page.goto('https://shopee.vn/search?keyword=%C3%A1o%20thun', {
      waitUntil: 'domcontentloaded',
      timeout: 60_000,
    })
    if (!noPause && pause) {
      await pause(page, 'Shopee')
    }

    for (const queryConfig of FASHION_QUERIES) {
      if (quotaTracker.isComplete()) {
        break
      }

      for (let pageIdx = 0; pageIdx < 3; pageIdx++) {
        if (quotaTracker.isComplete()) {
          break
        }

        latestSearchPayload = null
        const searchUrl = `https://shopee.vn/search?keyword=${encodeURIComponent(queryConfig.shopeeKeyword)}&page=${pageIdx}`
        await page.goto(searchUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 })
        await sleep(2500)

        if (!latestSearchPayload) {
          if (noPause || !pause) {
            console.warn(`[shopee] không bắt được gói tin tìm kiếm cho "${queryConfig.shopeeKeyword}" ở chế độ --no-pause, bỏ qua.`)
            break
          }
          await pause(page, `Shopee (${queryConfig.shopeeKeyword}, trang ${pageIdx + 1})`)
        }

        const rows = latestSearchPayload?.items ?? []
        if (rows.length === 0) {
          break
        }

        const candidates = []
        for (const wrapper of rows) {
          const row = wrapper.item_basic ?? wrapper
          if (!row.itemid || !row.shopid || !row.name) {
            continue
          }
          const sourceId = `${row.shopid}-${row.itemid}`
          const shopKey = `SHOPEE-${row.shopid}`
          if (storage.hasCompleteProduct('SHOPEE', sourceId)) {
            continue
          }
          candidates.push({
            source: 'SHOPEE',
            sourceId,
            itemId: String(row.itemid),
            shopSourceId: String(row.shopid),
            shopKey,
            shopName: row.shop_name ?? `Shopee Fashion ${row.shopid}`,
            name: row.name,
            price: toShopeeVnd(row.price_min ?? row.price),
            originalPrice: toShopeeVnd(row.price_min_before_discount ?? row.price_before_discount),
            imageUrl: shopeeImageUrl(row.image),
            sourceUrl: `https://shopee.vn/product/${row.shopid}/${row.itemid}`,
            location: row.shop_location ?? null,
            categorySlug: queryConfig.categorySlug,
          })
        }

        const prioritized = quotaTracker.prioritizeCandidates(candidates)
        for (const candidate of prioritized) {
          if (quotaTracker.isComplete()) {
            break
          }
          if (!quotaTracker.canAcceptCandidate(candidate)) {
            continue
          }

          latestPdpPayload = null
          latestShopPayload = null
          try {
            await page.goto(candidate.sourceUrl, {
              waitUntil: 'domcontentloaded',
              timeout: 45_000,
            })
            await sleep(2000)
          } catch {
            // Fall back to listing candidate metadata
          }

          const { shop, product } = parseShopeePdpPayload(
            latestPdpPayload,
            latestShopPayload,
            candidate,
          )

          await storage.ensureShopImages(shop, downloadFn)
          await storage.saveShop(shop)
          await storage.ensureProductImages(product, downloadFn)
          await storage.saveProduct(product)
          quotaTracker.recordProduct(product, shop)

          console.log(
            `[shopee] [${quotaTracker.getProductCount()}/${quotaTracker.limit}] [shops: ${quotaTracker.getShopCount()}] ${product.name}`,
          )
        }
      }
    }
  } finally {
    await page.close()
  }
}
