import { mkdir, readFile, writeFile, stat } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import path from 'node:path'
import { deflateSync } from 'node:zlib'
import { safeId } from './normalizer.mjs'

function sha256Hex(buffer) {
  return createHash('sha256').update(buffer).digest('hex')
}

const CRC_TABLE = new Uint32Array(256)
for (let n = 0; n < 256; n++) {
  let c = n
  for (let k = 0; k < 8; k++) {
    if ((c & 1) !== 0) {
      c = 0xedb88320 ^ (c >>> 1)
    } else {
      c = c >>> 1
    }
  }
  CRC_TABLE[n] = c >>> 0
}

function crc32(buffer) {
  let c = 0xffffffff
  for (let i = 0; i < buffer.length; i++) {
    c = CRC_TABLE[(c ^ buffer[i]) & 0xff] ^ (c >>> 8)
  }
  return (c ^ 0xffffffff) >>> 0
}

function makePngChunk(type, data) {
  const typeBuffer = Buffer.from(type, 'ascii')
  const lengthBuffer = Buffer.alloc(4)
  lengthBuffer.writeUInt32BE(data.length, 0)
  const crcInput = Buffer.concat([typeBuffer, data])
  const crcBuffer = Buffer.alloc(4)
  crcBuffer.writeUInt32BE(crc32(crcInput), 0)
  return Buffer.concat([lengthBuffer, typeBuffer, data, crcBuffer])
}

export function createValidPngBuffer(width = 320, height = 400, rgb = [60, 90, 140]) {
  const rBase = Math.max(0, Math.min(255, Math.round(rgb[0] ?? 60)))
  const gBase = Math.max(0, Math.min(255, Math.round(rgb[1] ?? 90)))
  const bBase = Math.max(0, Math.min(255, Math.round(rgb[2] ?? 140)))

  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0)
  ihdr.writeUInt32BE(height, 4)
  ihdr[8] = 8 // bit depth
  ihdr[9] = 2 // color type: RGB
  ihdr[10] = 0
  ihdr[11] = 0
  ihdr[12] = 0

  const rowStride = width * 3 + 1
  const rawPixels = Buffer.alloc(rowStride * height)

  for (let y = 0; y < height; y++) {
    const rowOffset = y * rowStride
    rawPixels[rowOffset] = 0 // filter type 0
    const yDelta = Math.round(((y / Math.max(1, height)) - 0.5) * 36)
    for (let x = 0; x < width; x++) {
      const pxOffset = rowOffset + 1 + x * 3
      const border = x < 8 || x >= width - 8 || y < 8 || y >= height - 8
      if (border) {
        rawPixels[pxOffset] = Math.min(255, rBase + 35)
        rawPixels[pxOffset + 1] = Math.min(255, gBase + 35)
        rawPixels[pxOffset + 2] = Math.min(255, bBase + 35)
      } else {
        rawPixels[pxOffset] = Math.max(0, Math.min(255, rBase + yDelta))
        rawPixels[pxOffset + 1] = Math.max(0, Math.min(255, gBase + yDelta))
        rawPixels[pxOffset + 2] = Math.max(0, Math.min(255, bBase + yDelta))
      }
    }
  }

  const compressed = deflateSync(rawPixels, { level: 6 })
  return Buffer.concat([
    signature,
    makePngChunk('IHDR', ihdr),
    makePngChunk('IDAT', compressed),
    makePngChunk('IEND', Buffer.alloc(0)),
  ])
}

function paletteFromSeed(seedStr, variantIndex = 0) {
  let hash = 5381
  const input = `${seedStr}:${variantIndex}`
  for (let i = 0; i < input.length; i++) {
    hash = ((hash << 5) + hash + input.charCodeAt(i)) >>> 0
  }
  const r = 55 + (hash & 0x7f)
  const g = 55 + ((hash >>> 8) & 0x7f)
  const b = 55 + ((hash >>> 16) & 0x7f)
  return [r, g, b]
}

async function fileExistsWithContent(fullPath) {
  try {
    const s = await stat(fullPath)
    return s.isFile() && s.size > 40
  } catch {
    return false
  }
}

function extensionFromContentType(contentType, url) {
  const lower = String(contentType ?? '').toLowerCase()
  if (lower.includes('png')) {
    return '.png'
  }
  if (lower.includes('webp')) {
    return '.webp'
  }
  if (lower.includes('jpeg') || lower.includes('jpg')) {
    return '.jpg'
  }
  const urlLower = String(url ?? '').toLowerCase()
  if (urlLower.includes('.png')) {
    return '.png'
  }
  if (urlLower.includes('.webp')) {
    return '.webp'
  }
  return '.jpg'
}

export class MarketplaceStorage {
  constructor({ outputDir, resume = true }) {
    this.outputDir = outputDir
    this.resume = resume
    this.shopsByKey = new Map()
    this.productsByKey = new Map()
  }

  async init() {
    await mkdir(this.outputDir, { recursive: true })
    await mkdir(path.join(this.outputDir, 'images', 'shops'), { recursive: true })
    await mkdir(path.join(this.outputDir, 'images', 'lazada'), { recursive: true })
    await mkdir(path.join(this.outputDir, 'images', 'shopee'), { recursive: true })
    await mkdir(path.join(this.outputDir, 'images', 'tiki'), { recursive: true })

    if (!this.resume) {
      return
    }

    const shopsPath = path.join(this.outputDir, 'shops.json')
    if (await fileExistsWithContent(shopsPath)) {
      try {
        const parsed = JSON.parse(await readFile(shopsPath, 'utf8'))
        if (Array.isArray(parsed)) {
          for (const shop of parsed) {
            if (shop && shop.shopKey) {
              this.shopsByKey.set(shop.shopKey, shop)
            }
          }
        }
      } catch {
        // Ignore malformed checkpoint and rebuild
      }
    }

    const productsPath = path.join(this.outputDir, 'products.json')
    if (await fileExistsWithContent(productsPath)) {
      try {
        const parsed = JSON.parse(await readFile(productsPath, 'utf8'))
        if (Array.isArray(parsed)) {
          for (const product of parsed) {
            if (product && product.source && product.sourceId) {
              const key = `${product.source}-${product.sourceId}`
              this.productsByKey.set(key, product)
            }
          }
        }
      } catch {
        // Ignore malformed checkpoint and rebuild
      }
    }
  }

  hasShop(shopKey) {
    return this.shopsByKey.has(shopKey)
  }

  getShop(shopKey) {
    return this.shopsByKey.get(shopKey) ?? null
  }

  hasCompleteProduct(source, sourceId) {
    const key = `${String(source).toUpperCase()}-${safeId(sourceId)}`
    const existing = this.productsByKey.get(key)
    if (!existing) {
      return false
    }
    const hasImages = Array.isArray(existing.imageFiles) && existing.imageFiles.length >= 1
    const hasVariants = Array.isArray(existing.variants) && existing.variants.length >= 1
    return hasImages && hasVariants
  }

  getShops() {
    return Array.from(this.shopsByKey.values())
  }

  getProducts() {
    return Array.from(this.productsByKey.values())
  }

  getShopCount() {
    return this.shopsByKey.size
  }

  getProductCount() {
    return this.productsByKey.size
  }

  async ensureShopImages(shop, downloadFn) {
    const safeKey = safeId(shop.shopKey).toLowerCase()

    // Logo
    let logoRel = shop.logoFile
    if (!logoRel || !(await fileExistsWithContent(path.join(this.outputDir, logoRel)))) {
      logoRel = null
      if (shop.logoUrl && downloadFn) {
        try {
          const downloaded = await downloadFn(shop.logoUrl)
          if (downloaded && downloaded.body && downloaded.body.length > 40) {
            const ext = extensionFromContentType(downloaded.contentType, shop.logoUrl)
            const rel = path.join('images', 'shops', `${safeKey}-logo${ext}`).replaceAll('\\', '/')
            await writeFile(path.join(this.outputDir, rel), downloaded.body)
            logoRel = rel
          }
        } catch {
          logoRel = null
        }
      }
      if (!logoRel) {
        const rel = path.join('images', 'shops', `${safeKey}-logo.png`).replaceAll('\\', '/')
        const fullPath = path.join(this.outputDir, rel)
        if (!(await fileExistsWithContent(fullPath))) {
          const buf = createValidPngBuffer(200, 200, paletteFromSeed(shop.shopKey, 1))
          await writeFile(fullPath, buf)
        }
        logoRel = rel
      }
    }
    shop.logoFile = logoRel

    // Banner
    let bannerRel = shop.bannerFile
    if (!bannerRel || !(await fileExistsWithContent(path.join(this.outputDir, bannerRel)))) {
      bannerRel = null
      if (shop.bannerUrl && downloadFn) {
        try {
          const downloaded = await downloadFn(shop.bannerUrl)
          if (downloaded && downloaded.body && downloaded.body.length > 40) {
            const ext = extensionFromContentType(downloaded.contentType, shop.bannerUrl)
            const rel = path.join('images', 'shops', `${safeKey}-banner${ext}`).replaceAll('\\', '/')
            await writeFile(path.join(this.outputDir, rel), downloaded.body)
            bannerRel = rel
          }
        } catch {
          bannerRel = null
        }
      }
      if (!bannerRel) {
        const rel = path.join('images', 'shops', `${safeKey}-banner.png`).replaceAll('\\', '/')
        const fullPath = path.join(this.outputDir, rel)
        if (!(await fileExistsWithContent(fullPath))) {
          const buf = createValidPngBuffer(600, 200, paletteFromSeed(shop.shopKey, 2))
          await writeFile(fullPath, buf)
        }
        bannerRel = rel
      }
    }
    shop.bannerFile = bannerRel
    return shop
  }

  async ensureProductImages(product, downloadFn) {
    const sourceFolder = String(product.source ?? 'tiki').toLowerCase()
    const folderDir = path.join(this.outputDir, 'images', sourceFolder)
    await mkdir(folderDir, { recursive: true })

    const baseId = safeId(product.sourceId)
    const savedFiles = []
    const seenContentHashes = new Set()
    const urlToFile = new Map()

    // Check existing imageFiles on disk first, skipping synthetic -view- files and duplicate content hashes
    if (Array.isArray(product.imageFiles)) {
      for (const existingRel of product.imageFiles) {
        const normalizedRel = String(existingRel).replaceAll('\\', '/')
        if (normalizedRel.includes('-view-')) {
          continue
        }
        const fullExisting = path.join(this.outputDir, normalizedRel)
        if (await fileExistsWithContent(fullExisting)) {
          try {
            const buf = await readFile(fullExisting)
            const hash = sha256Hex(buf)
            if (!seenContentHashes.has(hash) && !savedFiles.includes(normalizedRel)) {
              seenContentHashes.add(hash)
              savedFiles.push(normalizedRel)
            }
          } catch {
            // Skip unreadable file
          }
        }
      }
    }

    let candidateUrls = []
    if (Array.isArray(product.imageUrls)) {
      candidateUrls = product.imageUrls.filter(Boolean).slice(0, 9)
    }

    const downloadTasks = candidateUrls.map(async (url, i) => {
      let indexSuffix = `-${i + 1}`
      if (i === 0) {
        indexSuffix = ''
      }

      for (const candidateExt of ['.jpg', '.webp', '.png']) {
        const checkRel = path
          .join('images', sourceFolder, `${baseId}${indexSuffix}${candidateExt}`)
          .replaceAll('\\', '/')
        const fullCheck = path.join(this.outputDir, checkRel)
        if (await fileExistsWithContent(fullCheck)) {
          try {
            const buf = await readFile(fullCheck)
            return { url, rel: checkRel, hash: sha256Hex(buf), body: null }
          } catch {
            // Fall through to re-download
          }
        }
      }

      if (downloadFn) {
        try {
          const downloaded = await downloadFn(url)
          if (downloaded && downloaded.body && downloaded.body.length > 40) {
            const ext = extensionFromContentType(downloaded.contentType, url)
            const rel = path
              .join('images', sourceFolder, `${baseId}${indexSuffix}${ext}`)
              .replaceAll('\\', '/')
            return { url, rel, hash: sha256Hex(downloaded.body), body: downloaded.body }
          }
        } catch {
          return { url, rel: null, hash: null, body: null }
        }
      }
      return { url, rel: null, hash: null, body: null }
    })

    const downloadedResults = await Promise.all(downloadTasks)
    for (const { url, rel, hash, body } of downloadedResults) {
      if (!rel || !hash) {
        continue
      }
      if (seenContentHashes.has(hash)) {
        continue
      }
      seenContentHashes.add(hash)
      if (body) {
        await writeFile(path.join(this.outputDir, rel), body)
      }
      urlToFile.set(url, rel)
      if (!savedFiles.includes(rel) && savedFiles.length < 9) {
        savedFiles.push(rel)
      }
    }

    // Only generate 1 fallback image if 0 real images could be downloaded
    if (savedFiles.length === 0) {
      const rel = path
        .join('images', sourceFolder, `${baseId}-view-1.png`)
        .replaceAll('\\', '/')
      const fullPath = path.join(this.outputDir, rel)
      if (!(await fileExistsWithContent(fullPath))) {
        const rgb = paletteFromSeed(`${product.source}-${product.sourceId}`, 11)
        const buf = createValidPngBuffer(320, 400, rgb)
        await writeFile(fullPath, buf)
      }
      savedFiles.push(rel)
    }

    product.imageFiles = savedFiles.slice(0, 9)
    product.imageFile = product.imageFiles[0]

    // Do NOT duplicate gallery images onto every variant (which causes duplicate product_image rows in DB)
    if (Array.isArray(product.variants)) {
      for (const variant of product.variants) {
        variant.imageFile = null
      }
    }

    if (product.tryOnEnabled) {
      const existingGarments = []
      if (Array.isArray(product.garmentImageFiles)) {
        for (const gRel of product.garmentImageFiles) {
          const normG = String(gRel).replaceAll('\\', '/')
          if (await fileExistsWithContent(path.join(this.outputDir, normG))) {
            if (!existingGarments.includes(normG)) {
              existingGarments.push(normG)
            }
          }
        }
      }
      if (existingGarments.length > 0) {
        product.garmentImageFiles = existingGarments.slice(0, 9)
      } else {
        product.garmentImageFiles = [product.imageFiles[0]]
      }
    } else {
      product.garmentImageFiles = []
    }

    return product
  }

  async saveShop(shop, flush = true) {
    this.shopsByKey.set(shop.shopKey, shop)
    if (flush) {
      await this.flushShops()
    }
  }

  async saveProduct(product, flush = true) {
    const key = `${product.source}-${product.sourceId}`
    this.productsByKey.set(key, product)
    if (flush) {
      await this.flushProducts()
    }
  }

  async flushShops() {
    const shops = this.getShops()
    await writeFile(
      path.join(this.outputDir, 'shops.json'),
      JSON.stringify(shops, null, 2),
      'utf8',
    )
  }

  async flushProducts() {
    const products = this.getProducts()
    await writeFile(
      path.join(this.outputDir, 'products.json'),
      JSON.stringify(products, null, 2),
      'utf8',
    )
  }
}
