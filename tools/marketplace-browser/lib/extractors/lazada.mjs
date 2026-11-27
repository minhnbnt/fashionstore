import { FASHION_QUERIES } from '../queries.mjs'
import { normalizeShop, normalizeProduct, normalizeUrl, safeId } from '../normalizer.mjs'

export function cleanLazadaImageUrl(rawUrl) {
  const normalized = normalizeUrl(rawUrl)
  if (!normalized) {
    return null
  }
  return normalized
    .replace(/_\d+x\d+q\d+\.[a-zA-Z0-9_.]+$/i, '')
    .replace(/_\d+x\d+\.[a-zA-Z0-9_.]+$/i, '')
}

function parsePriceNumber(val) {
  if (typeof val === 'number' && Number.isFinite(val)) {
    return Math.max(0, Math.round(val))
  }
  const digits = String(val ?? '').replace(/[^0-9]/g, '')
  if (digits === '') {
    return 0
  }
  return Number.parseInt(digits, 10)
}

export function parseLazadaPdpPayload(moduleData, jsonLd, fallbackListing = {}) {
  const fields = moduleData?.data?.root?.fields ?? moduleData?.fields ?? {}
  const seller = fields?.seller ?? {}
  const productField = fields?.product ?? {}

  let rawShopId = seller.sellerId ?? seller.shopId ?? fallbackListing.shopSourceId ?? '1000'
  rawShopId = String(rawShopId)
  let shopName = seller.name ?? fallbackListing.shopName ?? `Lazada Shop ${rawShopId}`

  const isOfficial = Boolean(
    seller.isLazMall ||
      seller.mall ||
      seller.flagship ||
      /flagship|official|lazmall|mall|chính hãng/i.test(String(shopName)),
  )

  const shop = normalizeShop({
    source: 'LAZADA',
    sourceShopId: rawShopId,
    name: shopName,
    logoUrl: cleanLazadaImageUrl(seller.logo),
    bannerUrl: cleanLazadaImageUrl(seller.banner),
    isOfficial,
    address: seller.location ?? fallbackListing.location ?? null,
  })

  const skuBase = fields?.productOption?.skuBase ?? {}
  let properties = []
  if (Array.isArray(skuBase.properties)) {
    properties = skuBase.properties
  }
  let skus = []
  if (Array.isArray(skuBase.skus)) {
    skus = skuBase.skus
  }
  const skuInfos = fields?.skuInfos ?? {}

  const propValueMap = new Map()
  const colorImages = []

  for (const prop of properties) {
    const pid = String(prop?.pid ?? '')
    let values = []
    if (Array.isArray(prop?.values)) {
      values = prop.values
    }
    for (const val of values) {
      const vid = String(val?.vid ?? '')
      const img = cleanLazadaImageUrl(val?.image)
      if (img) {
        colorImages.push(img)
      }
      propValueMap.set(`${pid}:${vid}`, {
        propName: String(prop?.name ?? ''),
        valueName: String(val?.name ?? ''),
        imageUrl: img,
      })
    }
  }

  const rawVariants = []
  for (const skuEntry of skus) {
    const skuId = String(skuEntry?.skuId ?? '')
    const propPath = String(skuEntry?.propPath ?? '')
    const pairs = propPath.split(';').filter(Boolean)

    const labelParts = []
    let variantImage = null
    for (const pair of pairs) {
      const mapped = propValueMap.get(pair)
      if (mapped) {
        labelParts.push(mapped.valueName)
        if (mapped.imageUrl && !variantImage) {
          variantImage = mapped.imageUrl
        }
      }
    }

    const skuInfo = skuInfos[skuId] ?? skuInfos['0'] ?? {}
    const salePrice = parsePriceNumber(
      skuInfo?.price?.salePrice?.value ??
        skuInfo?.price?.value ??
        fallbackListing.price ??
        199000,
    )
    const origPrice = parsePriceNumber(
      skuInfo?.price?.originalPrice?.value ??
        fallbackListing.originalPrice ??
        salePrice,
    )
    const stock = Number(skuInfo?.stock ?? skuInfo?.quantity ?? 20)

    rawVariants.push({
      name: labelParts.join(' - '),
      price: salePrice,
      originalPrice: origPrice,
      stock,
      imageUrl: variantImage,
    })
  }

  const galleryImages = []
  const rootImages = skuInfos?.['0']?.images ?? fields?.gallery?.images ?? []
  if (Array.isArray(rootImages)) {
    for (const img of rootImages) {
      let rawImg = img
      if (typeof img === 'object' && img !== null) {
        rawImg = img.url ?? img.src
      }
      const cleaned = cleanLazadaImageUrl(rawImg)
      if (cleaned && !galleryImages.includes(cleaned)) {
        galleryImages.push(cleaned)
      }
    }
  }

  if (jsonLd && jsonLd.image) {
    let ldImages = [jsonLd.image]
    if (Array.isArray(jsonLd.image)) {
      ldImages = jsonLd.image
    }
    for (const img of ldImages) {
      const cleaned = cleanLazadaImageUrl(img)
      if (cleaned && !galleryImages.includes(cleaned)) {
        galleryImages.push(cleaned)
      }
    }
  }

  for (const cImg of colorImages) {
    if (!galleryImages.includes(cImg)) {
      galleryImages.push(cImg)
    }
  }

  if (fallbackListing.imageUrl) {
    const cleaned = cleanLazadaImageUrl(fallbackListing.imageUrl)
    if (cleaned && !galleryImages.includes(cleaned)) {
      galleryImages.push(cleaned)
    }
  }

  const attributes = {}
  const spuSpecs = fields?.specifications?.spuSpecs
  if (Array.isArray(spuSpecs)) {
    for (const spec of spuSpecs) {
      if (spec && spec.name && spec.value) {
        attributes[spec.name] = spec.value
      }
    }
  } else if (fields?.specifications && typeof fields.specifications === 'object') {
    for (const [k, v] of Object.entries(fields.specifications)) {
      if (typeof v === 'string') {
        attributes[k] = v
      } else if (v && typeof v === 'object' && Array.isArray(v.features)) {
        for (const f of v.features) {
          if (f && f.name && f.value) {
            attributes[f.name] = f.value
          }
        }
      }
    }
  }

  const firstInfo = skuInfos?.['0'] ?? Object.values(skuInfos)[0] ?? {}
  const basePrice = parsePriceNumber(
    firstInfo?.price?.salePrice?.value ??
      jsonLd?.offers?.price ??
      fallbackListing.price ??
      199000,
  )
  const baseOriginal = parsePriceNumber(
    firstInfo?.price?.originalPrice?.value ??
      fallbackListing.originalPrice ??
      basePrice,
  )

  const sourceId = safeId(
    fields?.primaryKey?.itemId ??
      fallbackListing.sourceId ??
      jsonLd?.sku ??
      '100001',
  )

  const product = normalizeProduct(
    {
      source: 'LAZADA',
      sourceId,
      name:
        productField.title ??
        jsonLd?.name ??
        fallbackListing.name ??
        'Trang phục thời trang Lazada',
      categorySlug: fallbackListing.categorySlug ?? 'ao',
      price: basePrice,
      originalPrice: baseOriginal,
      description: productField.desc ?? jsonLd?.description ?? '',
      attributes,
      imageUrls: galleryImages,
      variants: rawVariants,
      sourceUrl:
        normalizeUrl(fallbackListing.sourceUrl) ??
        `https://www.lazada.vn/products/item-i${sourceId}.html`,
    },
    shop.shopKey,
  )

  return { shop, product }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function scrapeLazada(context, options, storage) {
  const { quotaTracker, pause, noPause, downloadFn } = options
  const page = await context.newPage()

  try {
    await page.goto('https://www.lazada.vn/tag/ao-thun/', {
      waitUntil: 'domcontentloaded',
      timeout: 60_000,
    })
    if (!noPause && pause) {
      await pause(page, 'Lazada')
    }

    for (const query of FASHION_QUERIES) {
      if (quotaTracker.isComplete()) {
        break
      }
      console.log(`[lazada] đang quét danh mục ${query.lazadaTag} (${query.categorySlug})`)

      for (let pageNumber = 1; pageNumber <= 5; pageNumber++) {
        if (quotaTracker.isComplete()) {
          break
        }

        const listingResult = await page.evaluate(
          async ({ pageNumber, tag }) => {
            try {
              const res = await fetch(`/tag/${tag}/?ajax=true&page=${pageNumber}`, {
                credentials: 'include',
                headers: { accept: 'application/json' },
              })
              const text = await res.text()
              return { ok: true, payload: JSON.parse(text) }
            } catch {
              return { ok: false, payload: null }
            }
          },
          { pageNumber, tag: query.lazadaTag },
        )

        if (!listingResult.ok || !listingResult.payload) {
          if (noPause || !pause) {
            console.warn(`[lazada] cần xác thực tại ${query.lazadaTag} nhưng đang bật --no-pause, bỏ qua.`)
            break
          }
          await page.goto(`https://www.lazada.vn/tag/${query.lazadaTag}/?page=${pageNumber}`, {
            waitUntil: 'domcontentloaded',
            timeout: 60_000,
          })
          await pause(page, `Lazada (${query.lazadaTag}, trang ${pageNumber})`)
          continue
        }

        const rows = listingResult.payload?.mods?.listItems ?? []
        if (rows.length === 0) {
          break
        }

        const candidates = []
        for (const row of rows) {
          const id = row.itemId ?? row.nid
          if (!id || !row.name) {
            continue
          }
          const shopSourceId = safeId(row.sellerId ?? row.shopId ?? `lazshop_${id}`)
          const shopKey = `LAZADA-${shopSourceId}`
          if (storage.hasCompleteProduct('LAZADA', id)) {
            continue
          }
          candidates.push({
            source: 'LAZADA',
            sourceId: String(id),
            shopSourceId,
            shopKey,
            shopName: row.sellerName ?? `Gian hàng Lazada ${shopSourceId}`,
            name: row.name,
            price: parsePriceNumber(row.priceShow ?? row.price),
            originalPrice: parsePriceNumber(row.originalPriceShow ?? row.originalPrice),
            imageUrl: cleanLazadaImageUrl(row.image),
            sourceUrl: normalizeUrl(row.productUrl),
            location: row.location ?? null,
            categorySlug: query.categorySlug,
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

          let pdpExtracted = null
          if (candidate.sourceUrl) {
            try {
              await page.goto(candidate.sourceUrl, {
                waitUntil: 'domcontentloaded',
                timeout: 45_000,
              })
              pdpExtracted = await page.evaluate(() => {
                let jsonLd = null
                const ldScript = document.querySelector('script[type="application/ld+json"]')
                if (ldScript) {
                  try {
                    jsonLd = JSON.parse(ldScript.textContent)
                  } catch {
                    jsonLd = null
                  }
                }
                return {
                  moduleData: window.__moduleData__ ?? null,
                  jsonLd,
                }
              })
            } catch {
              pdpExtracted = null
            }
          }

          const { shop, product } = parseLazadaPdpPayload(
            pdpExtracted?.moduleData ?? null,
            pdpExtracted?.jsonLd ?? null,
            candidate,
          )

          await storage.ensureShopImages(shop, downloadFn)
          await storage.saveShop(shop)
          await storage.ensureProductImages(product, downloadFn)
          await storage.saveProduct(product)
          quotaTracker.recordProduct(product, shop)

          console.log(
            `[lazada] [${quotaTracker.getProductCount()}/${quotaTracker.limit}] [shops: ${quotaTracker.getShopCount()}] ${product.name}`,
          )
          await sleep(1200)
        }
      }
    }
  } finally {
    await page.close()
  }
}
