import { createInterface } from 'node:readline/promises'
import { stdin as input, stdout as output } from 'node:process'
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { MarketplaceStorage } from './lib/storage.mjs'
import { scrapeLazada } from './lib/extractors/lazada.mjs'
import { scrapeShopee } from './lib/extractors/shopee.mjs'
import { scrapeTiki } from './lib/extractors/tiki.mjs'
import { scrapeUniqlo } from './lib/extractors/uniqlo.mjs'

export class QuotaTracker {
  constructor({
    limit = 1000,
    shopLimit = 50,
    maxPerShop = 25,
    existingShops = [],
    existingProducts = [],
  } = {}) {
    this.limit = Math.max(1, Number(limit))
    this.shopLimit = Math.max(1, Number(shopLimit))
    this.maxPerShop = Math.max(1, Number(maxPerShop))
    this.shopCounts = new Map()
    this.seenProductKeys = new Set()

    for (const shop of existingShops) {
      if (shop && shop.shopKey && !this.shopCounts.has(shop.shopKey)) {
        this.shopCounts.set(shop.shopKey, 0)
      }
    }
    for (const product of existingProducts) {
      if (product && product.source && product.sourceId) {
        this.recordProduct(product)
      }
    }
  }

  getProductCount() {
    return this.seenProductKeys.size
  }

  getShopCount() {
    return this.shopCounts.size
  }

  isComplete() {
    return this.seenProductKeys.size >= this.limit
  }

  canAcceptCandidate(candidate, allowOverflow = false) {
    if (this.isComplete()) {
      return false
    }
    const productKey = `${candidate.source}-${candidate.sourceId}`
    if (this.seenProductKeys.has(productKey)) {
      return false
    }
    const shopKey = candidate.shopKey
    if (!shopKey) {
      return true
    }

    if (this.shopCounts.has(shopKey)) {
      const currentCount = this.shopCounts.get(shopKey)
      if (currentCount < this.maxPerShop) {
        return true
      }
      return Boolean(allowOverflow)
    }

    if (this.shopCounts.size < this.shopLimit) {
      return true
    }
    return false
  }

  prioritizeCandidates(candidates) {
    const valid = []
    for (const c of candidates) {
      const key = `${c.source}-${c.sourceId}`
      if (!this.seenProductKeys.has(key)) {
        valid.push(c)
      }
    }

    const needMoreShops = this.shopCounts.size < this.shopLimit

    return valid.sort((a, b) => {
      const aKnown = this.shopCounts.has(a.shopKey)
      const bKnown = this.shopCounts.has(b.shopKey)

      if (needMoreShops) {
        if (!aKnown && bKnown) {
          return -1
        }
        if (aKnown && !bKnown) {
          return 1
        }
      } else {
        if (aKnown && !bKnown) {
          return -1
        }
        if (!aKnown && bKnown) {
          return 1
        }
      }

      const aCount = this.shopCounts.get(a.shopKey) ?? 0
      const bCount = this.shopCounts.get(b.shopKey) ?? 0
      return aCount - bCount
    })
  }

  recordProduct(product, shop = null) {
    const productKey = `${product.source}-${product.sourceId}`
    if (this.seenProductKeys.has(productKey)) {
      return
    }
    this.seenProductKeys.add(productKey)
    const shopKey = shop?.shopKey ?? product.shopKey
    if (shopKey) {
      const prev = this.shopCounts.get(shopKey) ?? 0
      this.shopCounts.set(shopKey, prev + 1)
    }
  }
}

function parseCliArgs(argv) {
  function valueOf(flag) {
    const index = argv.indexOf(flag)
    if (index < 0 || index + 1 >= argv.length) {
      return undefined
    }
    return argv[index + 1]
  }

  function parseBoolean(raw, defaultValue) {
    if (raw === undefined) {
      return defaultValue
    }
    const lower = String(raw).toLowerCase()
    if (lower === 'false' || lower === '0' || lower === 'no') {
      return false
    }
    return true
  }

  function parseNumber(raw, defaultValue, min, max) {
    const parsed = Number.parseInt(String(raw ?? defaultValue), 10)
    if (!Number.isFinite(parsed)) {
      return defaultValue
    }
    return Math.max(min, Math.min(parsed, max))
  }

  const source = String(valueOf('--source') ?? 'all').toLowerCase()
  const limit = parseNumber(valueOf('--limit'), 1000, 1, 5000)
  const shopLimit = parseNumber(valueOf('--shop-limit'), 50, 1, 200)
  const maxPerShop = parseNumber(valueOf('--max-per-shop'), 25, 1, 500)
  const resume = parseBoolean(valueOf('--resume'), true)
  const headless = parseBoolean(valueOf('--headless'), false)
  const noPause = argv.includes('--no-pause') || parseBoolean(valueOf('--no-pause'), false)

  return {
    source,
    limit,
    shopLimit,
    maxPerShop,
    resume,
    headless,
    noPause,
  }
}

async function createHttpDownloader(context) {
  return async function downloadImage(url) {
    if (context) {
      try {
        const res = await context.request.get(url, { timeout: 20_000 })
        if (res.ok()) {
          const contentType = res.headers()['content-type'] ?? ''
          if (contentType.toLowerCase().startsWith('image/')) {
            return {
              body: await res.body(),
              contentType,
            }
          }
        }
      } catch {
        // Fall through to native fetch
      }
    }

    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 15_000)
    try {
      const response = await fetch(url, {
        headers: {
          'user-agent': 'Mozilla/5.0 (compatible; FashionStoreCatalogImporter/1.0)',
          accept: 'image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8',
        },
        signal: controller.signal,
      })
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      const contentType = response.headers.get('content-type') ?? 'image/jpeg'
      const arrayBuf = await response.arrayBuffer()
      return {
        body: Buffer.from(arrayBuf),
        contentType,
      }
    } finally {
      clearTimeout(timer)
    }
  }
}

export async function runCli(argv = process.argv.slice(2)) {
  const cliOptions = parseCliArgs(argv)
  const root = path.resolve(import.meta.dirname, '..', '..')
  const outputDir = path.join(root, 'data', 'marketplace-import')
  const profileDir = path.join(import.meta.dirname, '.profile')

  const storage = new MarketplaceStorage({
    outputDir,
    resume: cliOptions.resume,
  })
  await storage.init()

  const quotaTracker = new QuotaTracker({
    limit: cliOptions.limit,
    shopLimit: cliOptions.shopLimit,
    maxPerShop: cliOptions.maxPerShop,
    existingShops: storage.getShops(),
    existingProducts: storage.getProducts(),
  })

  console.log(
    `[scraper] khởi động: source=${cliOptions.source}, limit=${cliOptions.limit}, shopLimit=${cliOptions.shopLimit}, maxPerShop=${cliOptions.maxPerShop}, existingProducts=${quotaTracker.getProductCount()}, existingShops=${quotaTracker.getShopCount()}`,
  )

  const needsBrowser =
    (cliOptions.source === 'all' ||
      cliOptions.source === 'lazada' ||
      cliOptions.source === 'shopee') &&
    cliOptions.source !== 'uniqlo'

  let context = null
  let rl = null

  if (needsBrowser) {
    const { chromium } = await import('playwright')
    context = await chromium.launchPersistentContext(profileDir, {
      channel: 'chrome',
      headless: cliOptions.headless,
      viewport: { width: 1400, height: 900 },
      locale: 'vi-VN',
    })
    if (!cliOptions.noPause) {
      rl = createInterface({ input, output })
    }
  }

  async function pause(page, marketplace) {
    if (cliOptions.noPause || !rl) {
      return
    }
    await page.bringToFront()
    await rl.question(
      `\n${marketplace}: hãy đăng nhập/xử lý xác thực trong Chrome, mở trang kết quả sản phẩm, rồi nhấn Enter tại đây...`,
    )
  }

  const downloadFn = await createHttpDownloader(context)
  const scraperOptions = {
    ...cliOptions,
    quotaTracker,
    pause,
    downloadFn,
  }

  try {
    if (cliOptions.source === 'all' || cliOptions.source === 'tiki') {
      await scrapeTiki(context, scraperOptions, storage)
    }
    if (!quotaTracker.isComplete() && (cliOptions.source === 'all' || cliOptions.source === 'lazada')) {
      await scrapeLazada(context, scraperOptions, storage)
    }
    if (!quotaTracker.isComplete() && (cliOptions.source === 'all' || cliOptions.source === 'shopee')) {
      await scrapeShopee(context, scraperOptions, storage)
    }
    if (!quotaTracker.isComplete() && (cliOptions.source === 'all' || cliOptions.source === 'uniqlo')) {
      await scrapeUniqlo(scraperOptions, storage, quotaTracker)
    }

    await storage.flushShops()
    await storage.flushProducts()
    const garmentScript = path.join(import.meta.dirname, 'lib', 'generate-garments.py')
    const pyRes = spawnSync('python', [garmentScript, outputDir], { stdio: 'inherit' })
    if (pyRes.status !== 0) {
      console.warn('[scraper] cảnh báo: bước xử lý garment flat-lay trả về mã', pyRes.status)
    }
    console.log(
      `\nĐã lưu ${storage.getProductCount()} sản phẩm và ${storage.getShopCount()} cửa hàng tại ${outputDir}`,
    )
  } finally {
    if (context) {
      await context.close()
    }
    if (rl) {
      rl.close()
    }
  }
}

const isMainModule =
  Boolean(process.argv[1]) &&
  path.resolve(process.argv[1]) === path.resolve(import.meta.filename)

if (isMainModule) {
  await runCli()
}
