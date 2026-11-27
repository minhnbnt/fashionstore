import { FASHION_QUERIES } from '../queries.mjs'
import { normalizeShop, normalizeProduct, normalizeUrl, safeId } from '../normalizer.mjs'

const TIKI_HEADERS = {
  accept: 'application/json',
  'user-agent': 'Mozilla/5.0 (compatible; FashionStoreCatalogImporter/1.0)',
}

function cleanTikiImageUrl(rawUrl) {
  const url = normalizeUrl(rawUrl)
  if (!url) {
    return null
  }
  return url.replace(/\/cache\/(?:280x280|750x750|200x200|w\d+)\//i, '/cache/w1200/')
}

export function parseTikiPdpPayload(pdpData, sellerData, fallbackListing = {}) {
  const data = pdpData ?? {}
  let firstSeller = null
  if (Array.isArray(data.sellers) && data.sellers.length > 0) {
    firstSeller = data.sellers[0]
  }
  const seller = sellerData ?? data.current_seller ?? firstSeller ?? {}

  const rawShopId = String(
    seller.id ??
      seller.store_id ??
      data.seller_id ??
      fallbackListing.shopSourceId ??
      '3000',
  )
  const shopName =
    seller.name ??
    fallbackListing.shopName ??
    `Tiki Fashion Store ${rawShopId}`

  let hasAuthenticBadge = false
  if (Array.isArray(data.badges_new)) {
    for (const badge of data.badges_new) {
      if (badge && (badge.code === 'is_authentic' || badge.code === 'official_store')) {
        hasAuthenticBadge = true
      }
    }
  }

  const isOfficial = Boolean(
    seller.is_official_store ||
      hasAuthenticBadge ||
      /official|flagship|trading|mall|chính hãng/i.test(String(shopName)),
  )

  const shop = normalizeShop({
    source: 'TIKI',
    sourceShopId: rawShopId,
    name: shopName,
    logoUrl: normalizeUrl(seller.logo),
    isOfficial,
  })

  const galleryImages = []
  if (Array.isArray(data.images)) {
    for (const img of data.images) {
      const candidate = cleanTikiImageUrl(img?.large_url ?? img?.base_url ?? img?.medium_url ?? img?.thumbnail_url)
      if (candidate && !galleryImages.includes(candidate)) {
        galleryImages.push(candidate)
      }
    }
  }
  if (data.thumbnail_url) {
    const thumb = cleanTikiImageUrl(data.thumbnail_url)
    if (thumb && !galleryImages.includes(thumb)) {
      galleryImages.push(thumb)
    }
  }
  if (fallbackListing.imageUrl) {
    const fallbackImg = cleanTikiImageUrl(fallbackListing.imageUrl)
    if (fallbackImg && !galleryImages.includes(fallbackImg)) {
      galleryImages.push(fallbackImg)
    }
  }

  const rawVariants = []
  let configProducts = []
  if (Array.isArray(data.configurable_products)) {
    configProducts = data.configurable_products
  }
  for (const cfg of configProducts) {
    let variantImg = null
    if (Array.isArray(cfg.images) && cfg.images.length > 0) {
      variantImg = cleanTikiImageUrl(
        cfg.images[0]?.large_url ?? cfg.images[0]?.base_url ?? cfg.images[0]?.medium_url,
      )
      if (variantImg && !galleryImages.includes(variantImg)) {
        galleryImages.push(variantImg)
      }
    } else if (cfg.thumbnail_url) {
      variantImg = cleanTikiImageUrl(cfg.thumbnail_url)
    }

    let stock = 20
    if (cfg.inventory_status && cfg.inventory_status !== 'available') {
      stock = 0
    } else if (typeof cfg.qty === 'number') {
      stock = cfg.qty
    }

    rawVariants.push({
      option1: cfg.option1 ?? null,
      option2: cfg.option2 ?? null,
      name: [cfg.option1, cfg.option2].filter(Boolean).join(' - '),
      price: Number(cfg.price ?? data.price ?? fallbackListing.price ?? 199000),
      originalPrice: Number(
        cfg.original_price ??
          data.original_price ??
          data.list_price ??
          fallbackListing.originalPrice ??
          cfg.price ??
          199000,
      ),
      stock,
      imageUrl: variantImg,
    })
  }

  const attributes = {}
  if (Array.isArray(data.specifications)) {
    for (const group of data.specifications) {
      let groupAttrs = []
      if (Array.isArray(group?.attributes)) {
        groupAttrs = group.attributes
      }
      for (const attr of groupAttrs) {
        if (attr && attr.name && attr.value) {
          const cleanVal = String(attr.value).replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
          attributes[attr.name] = cleanVal
          if (attr.code === 'material') {
            attributes.material = cleanVal
          }
          if (attr.code === 'origin') {
            attributes.origin = cleanVal
          }
        }
      }
    }
  }

  const sourceId = safeId(data.id ?? fallbackListing.sourceId ?? '300001')
  const basePrice = Number(data.price ?? fallbackListing.price ?? 199000)
  const baseOriginal = Number(
    data.original_price ?? data.list_price ?? fallbackListing.originalPrice ?? basePrice,
  )

  let sourceUrl = normalizeUrl(fallbackListing.sourceUrl)
  if (!sourceUrl) {
    if (data.url_path) {
      sourceUrl = `https://tiki.vn/${String(data.url_path).replace(/^\/+/, '')}`
    } else {
      sourceUrl = `https://tiki.vn/product-p${sourceId}.html`
    }
  }

  const product = normalizeProduct(
    {
      source: 'TIKI',
      sourceId,
      name: data.name ?? fallbackListing.name ?? 'Trang phục thời trang Tiki',
      categorySlug: fallbackListing.categorySlug ?? 'ao',
      price: basePrice,
      originalPrice: baseOriginal,
      description: data.description ?? data.short_description ?? '',
      attributes,
      imageUrls: galleryImages,
      variants: rawVariants,
      sourceUrl,
    },
    shop.shopKey,
  )

  return { shop, product }
}

async function fetchTikiJson(url) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 20_000)
  try {
    const response = await fetch(url, {
      headers: TIKI_HEADERS,
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

async function fetchTikiListingPage(query, pageNumber) {
  const searchUrl = `https://tiki.vn/api/v2/products?limit=40&include=advertisement&aggregations=2&q=${encodeURIComponent(query.tikiKeyword)}&category=${query.tikiCategoryId}&page=${pageNumber}`
  const searchBody = await fetchTikiJson(searchUrl)
  if (searchBody && Array.isArray(searchBody.data) && searchBody.data.length > 0) {
    return searchBody.data
  }
  const listingUrl = `https://tiki.vn/api/personalish/v1/blocks/listings?limit=40&category=${query.tikiCategoryId}&urlKey=${query.tikiUrlKey}&page=${pageNumber}`
  const listingBody = await fetchTikiJson(listingUrl)
  if (listingBody && Array.isArray(listingBody.data)) {
    return listingBody.data
  }
  return []
}

export async function scrapeTiki(context, options, storage) {
  const { quotaTracker, downloadFn } = options
  const maxPagesPerQuery = 15

  // Interleave queries across the 5 categories in rounds so all 5 categories get balanced coverage
  for (let pageNumber = 1; pageNumber <= maxPagesPerQuery; pageNumber++) {
    if (quotaTracker.isComplete()) {
      break
    }

    for (const query of FASHION_QUERIES) {
      if (quotaTracker.isComplete()) {
        break
      }

      const rows = await fetchTikiListingPage(query, pageNumber)
      if (rows.length === 0) {
        continue
      }

      const candidates = []
      for (const row of rows) {
        if (!row || !row.id || !row.name) {
          continue
        }
        const sourceId = String(row.id)
        if (storage.hasCompleteProduct('TIKI', sourceId)) {
          continue
        }
        const rawSellerId = row.seller_id ?? row.current_seller?.id ?? (1000 + (Number(row.id) % 60))
        const shopSourceId = safeId(rawSellerId)
        const shopKey = `TIKI-${shopSourceId}`
        let sourceUrl = `https://tiki.vn/product-p${sourceId}.html`
        if (row.url_path) {
          sourceUrl = `https://tiki.vn/${String(row.url_path).replace(/^\/+/, '')}`
        }

        candidates.push({
          source: 'TIKI',
          sourceId,
          shopSourceId,
          shopKey,
          shopName: row.seller_name ?? row.brand_name ?? `Tiki Fashion ${shopSourceId}`,
          name: row.name,
          price: Number(row.price ?? row.list_price ?? 199000),
          originalPrice: Number(row.original_price ?? row.list_price ?? row.price ?? 199000),
          imageUrl: cleanTikiImageUrl(row.thumbnail_url),
          sourceUrl,
          categorySlug: query.categorySlug,
        })
      }

      const prioritized = quotaTracker.prioritizeCandidates(candidates)
      const perQueryRoundQuota = Math.max(1, Math.ceil(quotaTracker.limit / FASHION_QUERIES.length / 2))
      const selectedBatch = []
      for (const candidate of prioritized) {
        if (quotaTracker.isComplete()) {
          break
        }
        if (selectedBatch.length >= perQueryRoundQuota) {
          break
        }
        if (quotaTracker.getProductCount() + selectedBatch.length >= quotaTracker.limit) {
          break
        }
        const allowOverflow = pageNumber >= 2
        if (!quotaTracker.canAcceptCandidate(candidate, allowOverflow)) {
          continue
        }
        selectedBatch.push(candidate)
      }

      // Process in batches of 10 concurrent PDP fetches + parallel image downloads
      const concurrency = 10
      for (let i = 0; i < selectedBatch.length; i += concurrency) {
        if (quotaTracker.isComplete()) {
          break
        }
        const slice = selectedBatch.slice(i, i + concurrency)
        const pdpResults = await Promise.all(
          slice.map(async (candidate) => {
            const pdpUrl = `https://tiki.vn/api/v2/products/${candidate.sourceId}`
            const pdpData = await fetchTikiJson(pdpUrl)
            const { shop, product } = parseTikiPdpPayload(pdpData, null, candidate)
            return { candidate, shop, product }
          }),
        )

        const acceptedItems = []
        for (const item of pdpResults) {
          if (quotaTracker.isComplete()) {
            break
          }
          const allowOverflow = pageNumber >= 2
          if (
            !quotaTracker.canAcceptCandidate(
              { source: 'TIKI', sourceId: item.product.sourceId, shopKey: item.shop.shopKey },
              allowOverflow,
            )
          ) {
            continue
          }
          quotaTracker.recordProduct(item.product, item.shop)
          acceptedItems.push(item)
        }

        await Promise.all(
          acceptedItems.map(async ({ shop, product }) => {
            await storage.ensureShopImages(shop, downloadFn)
            await storage.saveShop(shop, false)
            await storage.ensureProductImages(product, downloadFn)
            await storage.saveProduct(product, false)
          }),
        )

        if (acceptedItems.length > 0) {
          await storage.flushShops()
          await storage.flushProducts()
          const lastProduct = acceptedItems[acceptedItems.length - 1].product
          console.log(
            `[tiki] [${quotaTracker.getProductCount()}/${quotaTracker.limit}] [shops: ${quotaTracker.getShopCount()}] ${lastProduct.name}`,
          )
        }
      }
    }
  }
}
