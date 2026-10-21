import { chromium } from 'playwright'
import { createInterface } from 'node:readline/promises'
import { stdin as input, stdout as output } from 'node:process'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const args = process.argv.slice(2)
const source = valueOf('--source') ?? 'all'
const requested = Number.parseInt(valueOf('--limit') ?? '100', 10)
let requestedLimit = requested
if (!Number.isFinite(requestedLimit)) requestedLimit = 100
const limit = Math.max(1, Math.min(requestedLimit, 500))
const root = path.resolve(import.meta.dirname, '..', '..')
const outputDir = path.join(root, 'data', 'marketplace-import')
const profileDir = path.join(import.meta.dirname, '.profile')
const rl = createInterface({ input, output })
const fashionQueries = [
  { lazadaTag: 'ao-thun', shopeeKeyword: 'áo thun', categorySlug: 'ao' },
  { lazadaTag: 'ao-so-mi', shopeeKeyword: 'áo sơ mi', categorySlug: 'ao' },
  { lazadaTag: 'ao-polo', shopeeKeyword: 'áo polo', categorySlug: 'ao' },
  { lazadaTag: 'ao-khoac', shopeeKeyword: 'áo khoác', categorySlug: 'ao-khoac' },
  { lazadaTag: 'ao-hoodie', shopeeKeyword: 'áo hoodie', categorySlug: 'ao-khoac' },
  { lazadaTag: 'quan-jean', shopeeKeyword: 'quần jean', categorySlug: 'quan' },
  { lazadaTag: 'quan-dai', shopeeKeyword: 'quần dài', categorySlug: 'quan' },
  { lazadaTag: 'quan-short', shopeeKeyword: 'quần short', categorySlug: 'quan' },
  { lazadaTag: 'vay-dam', shopeeKeyword: 'váy đầm', categorySlug: 'vay-dam' },
  { lazadaTag: 'chan-vay', shopeeKeyword: 'chân váy', categorySlug: 'vay-dam' },
]

function valueOf(flag) {
  const index = args.indexOf(flag)
  if (index < 0) return undefined
  return args[index + 1]
}

function money(value) {
  if (typeof value === 'number') return Math.max(0, Math.round(value))
  const digits = String(value ?? '').replace(/[^0-9]/g, '')
  if (digits === '') return 0
  return Number.parseInt(digits, 10)
}

function safeId(value) {
  return String(value).replace(/[^a-zA-Z0-9_-]/g, '')
}

function sleep(milliseconds) {
  return new Promise(function (resolve) {
    setTimeout(resolve, milliseconds)
  })
}

async function pause(page, marketplace) {
  await page.bringToFront()
  await rl.question(
    `\n${marketplace}: hãy đăng nhập/xử lý CAPTCHA trong Chrome, mở trang kết quả sản phẩm, rồi nhấn Enter tại đây...`,
  )
}

async function downloadImages(context, items, marketplace) {
  const imageDir = path.join(outputDir, 'images', marketplace)
  await mkdir(imageDir, { recursive: true })
  const complete = []
  for (const item of items) {
    try {
      const response = await context.request.get(item.imageUrl, { timeout: 30_000 })
      if (!response.ok()) throw new Error(`HTTP ${response.status()}`)
      const contentType = response.headers()['content-type'] ?? ''
      if (!contentType.toLowerCase().startsWith('image/')) throw new Error('response is not image/*')
      let extension = '.jpg'
      if (contentType.includes('png')) extension = '.png'
      if (contentType.includes('webp')) extension = '.webp'
      const relative = path.join('images', marketplace, safeId(item.sourceId) + extension).replaceAll('\\', '/')
      await writeFile(path.join(outputDir, relative), await response.body())
      complete.push({ ...item, imageFile: relative })
      console.log(`[${marketplace}] ${complete.length}/${items.length}: ${item.name}`)
    } catch (error) {
      console.warn(`[${marketplace}] bỏ qua ảnh ${item.sourceId}: ${error.message}`)
    }
  }
  return complete
}

async function scrapeLazada(context) {
  const page = await context.newPage()
  await page.goto('https://www.lazada.vn/tag/ao-thun/', { waitUntil: 'domcontentloaded', timeout: 60_000 })
  await pause(page, 'Lazada')
  const items = []
  const seen = new Set()
  for (const query of fashionQueries) {
    let queryCount = 0
    let pageNumber = 1
    console.log(`[lazada] đang lấy ${query.lazadaTag}`)
    while (queryCount < limit) {
      const pageLimit = Math.min(40, limit - queryCount)
      const result = await page.evaluate(async ({ pageNumber, tag }) => {
        const response = await fetch(`/tag/${tag}/?ajax=true&page=${pageNumber}`, {
          credentials: 'include',
          headers: { accept: 'application/json' },
        })
        const text = await response.text()
        try {
          return { captcha: false, payload: JSON.parse(text) }
        } catch {
          return { captcha: true, payload: null }
        }
      }, { pageNumber, tag: query.lazadaTag })
      if (result.captcha) {
        console.warn(`[lazada] CAPTCHA tại ${query.lazadaTag}, trang ${pageNumber}`)
        await page.goto(`https://www.lazada.vn/tag/${query.lazadaTag}/?page=${pageNumber}`, {
          waitUntil: 'domcontentloaded',
          timeout: 60_000,
        })
        await pause(page, `Lazada (${query.lazadaTag}, trang ${pageNumber})`)
        continue
      }
      const payload = result.payload
      const rows = payload?.mods?.listItems ?? []
      if (rows.length === 0) break
      for (const row of rows.slice(0, pageLimit)) {
        const id = row.itemId ?? row.nid
        let imageUrl = row.image
        if (String(row.image ?? '').startsWith('//')) imageUrl = `https:${row.image}`
        let sourceUrl = row.productUrl
        if (row.productUrl?.startsWith('//')) sourceUrl = `https:${row.productUrl}`
        if (!id || !row.name || !imageUrl) continue
        queryCount++
        if (seen.has(String(id))) continue
        seen.add(String(id))
        items.push({
          source: 'LAZADA',
          sourceId: String(id),
          name: row.name,
          price: money(row.priceShow ?? row.price),
          originalPrice: money(row.originalPriceShow ?? row.originalPrice),
          imageUrl,
          sourceUrl,
          categorySlug: query.categorySlug,
        })
      }
      pageNumber++
      await sleep(2_000)
    }
  }
  await page.close()
  return downloadImages(context, items, 'lazada')
}

async function scrapeShopee(context) {
  const page = await context.newPage()
  await page.goto('https://shopee.vn/search?keyword=%C3%A1o%20thun', {
    waitUntil: 'domcontentloaded',
    timeout: 60_000,
  })
  await pause(page, 'Shopee')
  const items = []
  const seen = new Set()
  for (const queryConfig of fashionQueries) {
    let queryCount = 0
    let offset = 0
    console.log(`[shopee] đang lấy ${queryConfig.shopeeKeyword}`)
    while (queryCount < limit) {
      const result = await page.evaluate(async ({ offset, keyword }) => {
        const query = new URLSearchParams({
          by: 'relevancy', keyword, limit: '60', newest: String(offset), order: 'desc',
          page_type: 'search', scenario: 'PAGE_GLOBAL_SEARCH', version: '2',
        })
        const response = await fetch(`/api/v4/search/search_items?${query}`, { credentials: 'include' })
        const body = await response.json()
        if (!response.ok || body.error) {
          return { error: String(body.error ?? response.status), payload: null }
        }
        return { error: null, payload: body }
      }, { offset, keyword: queryConfig.shopeeKeyword })
      if (result.error !== null) {
        console.warn(`[shopee] bị từ chối tại ${queryConfig.shopeeKeyword}: ${result.error}`)
        const keyword = encodeURIComponent(queryConfig.shopeeKeyword)
        await page.goto(`https://shopee.vn/search?keyword=${keyword}`, {
          waitUntil: 'domcontentloaded',
          timeout: 60_000,
        })
        await pause(page, `Shopee (${queryConfig.shopeeKeyword})`)
        continue
      }
      const payload = result.payload
      const rows = payload?.items ?? []
      if (rows.length === 0) break
      for (const wrapper of rows) {
        const row = wrapper.item_basic ?? wrapper
        if (!row.itemid || !row.name || !row.image) continue
        queryCount++
        const sourceId = `${row.shopid}-${row.itemid}`
        if (seen.has(sourceId)) continue
        seen.add(sourceId)
        const price = Math.round(Number(row.price_min ?? row.price ?? 0) / 100000)
        const originalPrice = Math.round(Number(row.price_min_before_discount ?? price * 100000) / 100000)
        items.push({
          source: 'SHOPEE',
          sourceId,
          name: row.name,
          price,
          originalPrice,
          imageUrl: `https://down-vn.img.susercontent.com/file/${row.image}`,
          sourceUrl: `https://shopee.vn/product/${row.shopid}/${row.itemid}`,
          categorySlug: queryConfig.categorySlug,
        })
        if (queryCount >= limit) break
      }
      offset += 60
      await sleep(2_000)
    }
  }
  await page.close()
  return downloadImages(context, items, 'shopee')
}

await mkdir(outputDir, { recursive: true })
const context = await chromium.launchPersistentContext(profileDir, {
  channel: 'chrome',
  headless: false,
  viewport: { width: 1400, height: 900 },
  locale: 'vi-VN',
})

try {
  const manifest = []
  if (source === 'all' || source === 'lazada') manifest.push(...await scrapeLazada(context))
  if (source === 'all' || source === 'shopee') manifest.push(...await scrapeShopee(context))
  await writeFile(path.join(outputDir, 'products.json'), JSON.stringify(manifest, null, 2), 'utf8')
  console.log(`\nĐã lưu ${manifest.length} sản phẩm và file ảnh tại ${outputDir}`)
} finally {
  await context.close()
  rl.close()
}
