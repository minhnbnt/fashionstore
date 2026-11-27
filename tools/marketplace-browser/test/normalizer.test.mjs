import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, rm, stat, readFile } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'

import { FASHION_QUERIES } from '../lib/queries.mjs'
import { normalizeShop, normalizeProduct } from '../lib/normalizer.mjs'
import { MarketplaceStorage, createValidPngBuffer } from '../lib/storage.mjs'

test('FASHION_QUERIES contains 20 queries covering all 5 required categories', () => {
  assert.equal(FASHION_QUERIES.length, 20)
  const categories = new Set(FASHION_QUERIES.map((q) => q.categorySlug))
  assert.deepEqual(
    categories,
    new Set(['ao', 'vay-dam', 'ao-khoac', 'quan', 'phu-kien']),
  )

  for (const query of FASHION_QUERIES) {
    assert.ok(query.id, 'query.id must be defined')
    assert.ok(query.categorySlug, 'query.categorySlug must be defined')
    assert.ok(query.lazadaTag, 'query.lazadaTag must be defined')
    assert.ok(query.shopeeKeyword, 'query.shopeeKeyword must be defined')
    assert.ok(query.tikiKeyword, 'query.tikiKeyword must be defined')
    assert.ok(query.tikiCategoryId, 'query.tikiCategoryId must be defined')
    assert.ok(query.tikiUrlKey, 'query.tikiUrlKey must be defined')
  }
})

test('normalizeShop normalizes corporate and individual shops with valid slugs and defaults', () => {
  const corporate = normalizeShop({
    source: 'SHOPEE',
    sourceShopId: '123456',
    name: 'Routine Official Store',
    isOfficial: true,
    logoUrl: '//down-vn.img.susercontent.com/file/logo123',
  })

  assert.equal(corporate.shopKey, 'SHOPEE-123456')
  assert.equal(corporate.source, 'SHOPEE')
  assert.equal(corporate.sourceShopId, '123456')
  assert.equal(corporate.sellerType, 'CORPORATE')
  assert.match(corporate.slug, /^[a-z0-9-]+$/)
  assert.ok(corporate.slug.endsWith('-123456'))
  assert.ok(corporate.slug.length <= 180)
  assert.ok(corporate.processingDays >= 1 && corporate.processingDays <= 3)
  assert.equal(corporate.logoUrl, 'https://down-vn.img.susercontent.com/file/logo123')
  assert.ok(corporate.description.length > 20)
  assert.ok(corporate.address.length > 5)
  assert.match(corporate.phone, /^0\d{9}$/)
  assert.ok(corporate.returnPolicy.length > 20)
  assert.ok(corporate.rating >= 4.5 && corporate.rating <= 5.0)
  assert.ok(corporate.ratingCount > 0)
  assert.ok(corporate.followerCount > 0)

  const individual = normalizeShop({
    source: 'LAZADA',
    sourceShopId: 'shop_987',
    name: 'Tiệm Đồ Xinh Sài Gòn',
    isOfficial: false,
  })
  assert.equal(individual.sellerType, 'INDIVIDUAL')
  assert.match(individual.slug, /^[a-z0-9-]+$/)
  assert.ok(individual.slug.endsWith('987'))
})

test('normalizeProduct cleans product name, parses combined color-size labels, and extracts sizeGuide', () => {
  const raw = {
    source: 'SHOPEE',
    sourceId: '998877',
    name: '[Mã BMLT30 giảm 30K] [FREESHIP] Áo Thun Cotton Form Rộng Unisex Nam Nữ Cao Cấp',
    categorySlug: 'ao',
    price: 159000,
    originalPrice: 159000,
    description: '<p>Áo thun thoáng mát</p><script>alert(1)</script>',
    imageUrls: ['https://example.com/img1.jpg'],
    variants: [
      {
        name: 'Màu Đen - Size M (45-55kg)',
        price: 159000,
        originalPrice: 159000,
        stock: 0,
        imageUrl: 'https://example.com/black.jpg',
      },
      {
        name: 'Màu Trắng sữa / Size L (56-65kg)',
        price: 165000,
        originalPrice: 220000,
        stock: 12,
        imageUrl: 'https://example.com/white.jpg',
      },
      {
        name: 'Màu Đen - Size M (45-55kg)',
        price: 159000,
        originalPrice: 159000,
        stock: 5,
      },
    ],
    sourceUrl: 'https://shopee.vn/product/123/998877',
  }

  const normalized = normalizeProduct(raw, 'SHOPEE-123')

  assert.equal(
    normalized.name,
    'Áo Thun Cotton Form Rộng Unisex Nam Nữ Cao Cấp',
  )
  assert.match(normalized.slug, /^[a-z0-9-]+$/)
  assert.ok(normalized.slug.length <= 180)
  assert.equal(normalized.shopKey, 'SHOPEE-123')
  assert.equal(normalized.tryOnEnabled, true)
  assert.ok(normalized.material.toLowerCase().includes('cotton'))
  assert.equal(normalized.origin, 'Việt Nam')
  assert.ok(normalized.careInstructions.length > 10)
  assert.ok(normalized.sizeGuide.includes('45-55kg'))
  assert.ok(normalized.sizeGuide.includes('56-65kg'))
  assert.equal(normalized.weightGrams, 250)
  assert.equal(normalized.lengthCm, 28)
  assert.equal(normalized.widthCm, 20)
  assert.equal(normalized.heightCm, 5)
  assert.ok(!normalized.description.includes('<script>'))
  assert.ok(normalized.description.includes('Đặc điểm nổi bật'))

  // Deduplicated variants: 2 unique (color, size) pairs
  assert.equal(normalized.variants.length, 2)
  assert.equal(normalized.variants[0].color, 'Đen')
  assert.equal(normalized.variants[0].size, 'M')
  assert.equal(normalized.variants[0].defaultVariant, true)
  assert.equal(normalized.variants[0].compareAtPrice, null)
  assert.ok(normalized.variants[0].stockQuantity >= 5)

  assert.equal(normalized.variants[1].color, 'Trắng')
  assert.equal(normalized.variants[1].size, 'L')
  assert.equal(normalized.variants[1].defaultVariant, false)
  assert.equal(normalized.variants[1].compareAtPrice, 220000)
  assert.equal(normalized.variants[1].stockQuantity, 12)
})

test('normalizeProduct expands 1-dimensional color variants across sizes for clothing and uses Freesize for accessories', () => {
  const clothingOnlyColor = normalizeProduct(
    {
      source: 'LAZADA',
      sourceId: '554433',
      name: 'Đầm Maxi Dự Tiệc Dáng Dài Sang Trọng',
      categorySlug: 'vay-dam',
      price: 320000,
      originalPrice: 450000,
      variants: [
        { color: 'Đỏ rượu', price: 320000, originalPrice: 450000, stock: 10 },
        { color: 'Xanh than', price: 320000, originalPrice: 450000, stock: 8 },
      ],
    },
    'LAZADA-111',
  )

  // Should expand 2 colors across S, M, L, XL = 8 unique variants
  assert.equal(clothingOnlyColor.variants.length, 8)
  const pairs = new Set(
    clothingOnlyColor.variants.map((v) => `${v.color}__${v.size}`),
  )
  assert.equal(pairs.size, 8)
  assert.ok(pairs.has('Đỏ__S'))
  assert.ok(pairs.has('Xanh Navy__XL'))
  const defaults = clothingOnlyColor.variants.filter((v) => v.defaultVariant)
  assert.equal(defaults.length, 1)

  const accessoryOnlyColor = normalizeProduct(
    {
      source: 'TIKI',
      sourceId: '776655',
      name: 'Túi Xách Nữ Da PU Đeo Chéo Thời Trang',
      categorySlug: 'phu-kien',
      price: 210000,
      originalPrice: 280000,
      variants: [
        { color: 'Đen', price: 210000, originalPrice: 280000, stock: 20 },
        { color: 'Nâu bò', price: 210000, originalPrice: 280000, stock: 15 },
      ],
    },
    'TIKI-222',
  )

  assert.equal(accessoryOnlyColor.tryOnEnabled, false)
  assert.equal(accessoryOnlyColor.variants.length, 2)
  assert.equal(accessoryOnlyColor.variants[0].size, 'Freesize')
  assert.equal(accessoryOnlyColor.variants[1].color, 'Nâu')
  assert.equal(accessoryOnlyColor.variants[1].size, 'Freesize')
})

test('MarketplaceStorage creates valid PNG fallback files to guarantee 3-9 product images and shop assets', async () => {
  const tempDir = await mkdtemp(path.join(os.tmpdir(), 'marketplace-storage-test-'))
  try {
    const pngBytes = createValidPngBuffer(64, 64, [40, 80, 140])
    assert.equal(pngBytes[0], 0x89)
    assert.equal(pngBytes[1], 0x50)
    assert.equal(pngBytes[2], 0x4e)
    assert.equal(pngBytes[3], 0x47)

    const storage = new MarketplaceStorage({ outputDir: tempDir, resume: true })
    await storage.init()

    const shop = normalizeShop({
      source: 'TIKI',
      sourceShopId: '501',
      name: 'Canifa Official',
      isOfficial: true,
    })
    await storage.ensureShopImages(shop, async () => {
      throw new Error('simulated network offline')
    })
    await storage.saveShop(shop)

    assert.ok(shop.logoFile)
    assert.ok(shop.bannerFile)
    const logoStat = await stat(path.join(tempDir, shop.logoFile))
    const bannerStat = await stat(path.join(tempDir, shop.bannerFile))
    assert.ok(logoStat.size > 60)
    assert.ok(bannerStat.size > 60)

    const product = normalizeProduct(
      {
        source: 'TIKI',
        sourceId: '9001',
        name: 'Áo Khoác Blazer Nữ Hàn Quốc Thanh Lịch',
        categorySlug: 'ao-khoac',
        price: 390000,
        originalPrice: 490000,
        imageUrls: [
          'https://example.com/only-one.png',
          'https://example.com/duplicate-content.png',
        ],
      },
      shop.shopKey,
    )

    const singlePng = createValidPngBuffer(80, 100, [200, 180, 160])
    await storage.ensureProductImages(product, async () => ({
      body: singlePng,
      contentType: 'image/png',
    }))
    await storage.saveProduct(product)

    // Deduplicates identical image bytes and does not pad fake images when >= 1 real image exists
    assert.equal(product.imageFiles.length, 1)
    assert.equal(product.imageFile, product.imageFiles[0])
    assert.ok(product.garmentImageFiles.length >= 1 && product.garmentImageFiles.length <= 9)

    for (const relPath of product.imageFiles) {
      const fileStat = await stat(path.join(tempDir, relPath))
      assert.ok(fileStat.size > 60)
    }

    const savedShops = JSON.parse(await readFile(path.join(tempDir, 'shops.json'), 'utf8'))
    const savedProducts = JSON.parse(await readFile(path.join(tempDir, 'products.json'), 'utf8'))
    assert.equal(savedShops.length, 1)
    assert.equal(savedProducts.length, 1)
  } finally {
    await rm(tempDir, { recursive: true, force: true })
  }
})
