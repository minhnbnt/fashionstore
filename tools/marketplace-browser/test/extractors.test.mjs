import test from 'node:test'
import assert from 'node:assert/strict'

import { parseLazadaPdpPayload } from '../lib/extractors/lazada.mjs'
import { parseShopeePdpPayload } from '../lib/extractors/shopee.mjs'
import { parseTikiPdpPayload } from '../lib/extractors/tiki.mjs'
import { QuotaTracker } from '../scrape.mjs'

test('parseLazadaPdpPayload extracts high-res gallery images, multi-tier SKU variants, specs, and seller', () => {
  const moduleData = {
    data: {
      root: {
        fields: {
          product: {
            title: '[FREESHIP] Áo Sơ Mi Oxford Nam Dài Tay Cao Cấp',
            desc: '<p>Áo sơ mi vải Oxford dày dặn, thoáng mát.</p>',
          },
          seller: {
            sellerId: '880011',
            name: 'Coolmate Flagship Store',
            logo: '//laz-img-cdn.alicdn.com/images/ims-web/TB1logo.png',
            isLazMall: true,
            PositiveSellerRating: '98%',
          },
          specifications: {
            spuSpecs: [
              { name: 'Chất liệu', value: '100% Cotton Oxford' },
              { name: 'Xuất xứ', value: 'Việt Nam' },
            ],
          },
          productOption: {
            skuBase: {
              properties: [
                {
                  pid: '1000',
                  name: 'Nhóm màu',
                  values: [
                    {
                      vid: '1',
                      name: 'Trắng',
                      image: '//vn-live-01.slatic.net/p/white-shirt.jpg_80x80q80.jpg_.webp',
                    },
                    {
                      vid: '2',
                      name: 'Xanh Navy',
                      image: '//vn-live-01.slatic.net/p/navy-shirt.jpg_120x120q80.jpg',
                    },
                  ],
                },
                {
                  pid: '2000',
                  name: 'Kích cỡ',
                  values: [
                    { vid: '10', name: 'M (55-63kg)' },
                    { vid: '20', name: 'L (64-72kg)' },
                  ],
                },
              ],
              skus: [
                { skuId: 'sku_1', propPath: '1000:1;2000:10' },
                { skuId: 'sku_2', propPath: '1000:1;2000:20' },
                { skuId: 'sku_3', propPath: '1000:2;2000:10' },
                { skuId: 'sku_4', propPath: '1000:2;2000:20' },
              ],
            },
          },
          skuInfos: {
            0: {
              images: [
                '//vn-live-01.slatic.net/p/main1.jpg_80x80q80.jpg_.webp',
                '//vn-live-01.slatic.net/p/main2.jpg_720x720q80.jpg',
                '//vn-live-01.slatic.net/p/main3.jpg',
              ],
            },
            sku_1: {
              price: { salePrice: { value: 279000 }, originalPrice: { value: 350000 } },
              stock: 25,
            },
            sku_2: {
              price: { salePrice: { value: 279000 }, originalPrice: { value: 350000 } },
              stock: 18,
            },
            sku_3: {
              price: { salePrice: { value: 289000 }, originalPrice: { value: 350000 } },
              stock: 14,
            },
            sku_4: {
              price: { salePrice: { value: 289000 }, originalPrice: { value: 350000 } },
              stock: 9,
            },
          },
        },
      },
    },
  }

  const { shop, product } = parseLazadaPdpPayload(moduleData, null, {
    sourceId: 'laz101',
    categorySlug: 'ao',
    sourceUrl: 'https://www.lazada.vn/products/ao-so-mi-i101.html',
  })

  assert.equal(shop.shopKey, 'LAZADA-880011')
  assert.equal(shop.sellerType, 'CORPORATE')
  assert.equal(product.shopKey, 'LAZADA-880011')
  assert.equal(product.name, 'Áo Sơ Mi Oxford Nam Dài Tay Cao Cấp')
  assert.equal(product.material, '100% Cotton Oxford')
  assert.equal(product.variants.length, 4)
  assert.equal(product.variants[0].color, 'Trắng')
  assert.equal(product.variants[0].size, 'M')
  assert.equal(product.variants[0].price, 279000)
  assert.equal(product.variants[0].compareAtPrice, 350000)
  assert.equal(
    product.variants[0].imageUrl,
    'https://vn-live-01.slatic.net/p/white-shirt.jpg',
  )
  assert.ok(
    product.imageUrls.includes('https://vn-live-01.slatic.net/p/main1.jpg'),
  )
  assert.ok(product.sizeGuide.includes('55-63kg'))
})

test('parseShopeePdpPayload converts micro-VND prices, image hashes, tier_variations, and shop_detailed', () => {
  const pdpData = {
    data: {
      item: {
        itemid: 445566,
        shopid: 7788,
        title: '[Mã 20K] Quần Jean Nữ Ống Suông Lưng Cao Hack Dáng',
        price_min: 24500000000,
        price_min_before_discount: 32000000000,
        images: ['hash_img_1', 'hash_img_2', 'hash_img_3'],
        description: 'Quần jean nữ chấtデニム bền đẹp, lên chuẩn form.',
        attributes: [
          { name: 'Chất liệu', value: 'Denim Cotton' },
          { name: 'Xuất xứ', value: 'Việt Nam' },
        ],
        tier_variations: [
          {
            name: 'Màu sắc',
            options: ['Xanh Dương', 'Đen'],
            images: ['hash_blue', 'hash_black'],
          },
          {
            name: 'Size',
            options: ['S (42-48kg)', 'M (49-54kg)'],
          },
        ],
        models: [
          {
            name: 'Xanh Dương,S (42-48kg)',
            tier_index: [0, 0],
            price: 24500000000,
            price_before_discount: 32000000000,
            normal_stock: 30,
          },
          {
            name: 'Xanh Dương,M (49-54kg)',
            tier_index: [0, 1],
            price: 24500000000,
            price_before_discount: 32000000000,
            normal_stock: 22,
          },
          {
            name: 'Đen,S (42-48kg)',
            tier_index: [1, 0],
            price: 25500000000,
            price_before_discount: 32000000000,
            normal_stock: 15,
          },
          {
            name: 'Đen,M (49-54kg)',
            tier_index: [1, 1],
            price: 25500000000,
            price_before_discount: 32000000000,
            normal_stock: 19,
          },
        ],
      },
      shop_detailed: {
        shopid: 7788,
        name: 'GUMAC Official Store',
        is_official_shop: true,
        shop_location: 'TP. Hồ Chí Minh',
        rating_star: 4.92,
        follower_count: 185000,
        account: { portrait: 'portrait_hash_1' },
      },
    },
  }

  const { shop, product } = parseShopeePdpPayload(pdpData, null, {
    categorySlug: 'quan',
  })

  assert.equal(shop.shopKey, 'SHOPEE-7788')
  assert.equal(shop.sellerType, 'CORPORATE')
  assert.equal(
    shop.logoUrl,
    'https://down-vn.img.susercontent.com/file/portrait_hash_1',
  )
  assert.equal(product.name, 'Quần Jean Nữ Ống Suông Lưng Cao Hack Dáng')
  assert.equal(product.price, 245000)
  assert.equal(product.originalPrice, 320000)
  assert.equal(product.material, 'Denim Cotton')
  assert.equal(product.variants.length, 4)
  assert.equal(product.variants[0].color, 'Xanh Dương')
  assert.equal(product.variants[0].size, 'S')
  assert.equal(product.variants[0].price, 245000)
  assert.equal(product.variants[0].compareAtPrice, 320000)
  assert.equal(
    product.variants[0].imageUrl,
    'https://down-vn.img.susercontent.com/file/hash_blue',
  )
  assert.equal(product.variants[2].color, 'Đen')
  assert.equal(product.variants[2].price, 255000)
})

test('parseTikiPdpPayload parses configurable_options, configurable_products, specifications, and current_seller', () => {
  const pdpData = {
    id: 27451122,
    name: 'Đầm Xòe Công Sở Cổ V Thanh Lịch',
    price: 349000,
    original_price: 490000,
    url_path: 'dam-xoe-cong-so-p27451122.html',
    description: '<p>Thiết kế thanh lịch phù hợp đi làm và dự tiệc.</p>',
    images: [
      { large_url: 'https://salt.tikicdn.com/cache/w1200/ts/product/a1.jpg' },
      { large_url: 'https://salt.tikicdn.com/cache/w1200/ts/product/a2.jpg' },
      { large_url: 'https://salt.tikicdn.com/cache/w1200/ts/product/a3.jpg' },
    ],
    current_seller: {
      id: 9012,
      name: 'Elise Fashion Boutique',
      logo: 'https://vcdn.tikicdn.com/ts/seller/logo.jpg',
      is_official_store: false,
    },
    specifications: [
      {
        name: 'Thông tin chung',
        attributes: [
          { code: 'material', name: 'Chất liệu', value: 'Lụa Hàn Châu cao cấp' },
          { code: 'origin', name: 'Xuất xứ', value: 'Việt Nam' },
        ],
      },
    ],
    configurable_options: [
      { code: 'option1', name: 'Màu sắc', values: [{ label: 'Be' }, { label: 'Đỏ đô' }] },
      { code: 'option2', name: 'Kích cỡ', values: [{ label: 'S' }, { label: 'M' }] },
    ],
    configurable_products: [
      {
        option1: 'Be',
        option2: 'S',
        price: 349000,
        original_price: 490000,
        inventory_status: 'available',
        images: [{ large_url: 'https://salt.tikicdn.com/cache/w1200/ts/product/be.jpg' }],
      },
      {
        option1: 'Đỏ đô',
        option2: 'M',
        price: 359000,
        original_price: 490000,
        inventory_status: 'available',
        images: [{ large_url: 'https://salt.tikicdn.com/cache/w1200/ts/product/red.jpg' }],
      },
    ],
  }

  const { shop, product } = parseTikiPdpPayload(pdpData, null, {
    categorySlug: 'vay-dam',
  })

  assert.equal(shop.shopKey, 'TIKI-9012')
  assert.equal(product.shopKey, 'TIKI-9012')
  assert.equal(product.categorySlug, 'vay-dam')
  assert.equal(product.material, 'Lụa Hàn Châu cao cấp')
  assert.equal(product.variants.length, 2)
  assert.equal(product.variants[0].color, 'Be')
  assert.equal(product.variants[0].size, 'S')
  assert.equal(product.variants[1].color, 'Đỏ')
  assert.equal(product.variants[1].size, 'M')
  assert.equal(product.variants[1].price, 359000)
})

test('QuotaTracker prioritizes discovering new shops up to shopLimit and balances products per shop', () => {
  const tracker = new QuotaTracker({
    limit: 6,
    shopLimit: 3,
    maxPerShop: 2,
  })

  const candidates = [
    { source: 'TIKI', sourceId: 'p1', shopKey: 'TIKI-S1' },
    { source: 'TIKI', sourceId: 'p2', shopKey: 'TIKI-S1' },
    { source: 'TIKI', sourceId: 'p3', shopKey: 'TIKI-S1' },
    { source: 'TIKI', sourceId: 'p4', shopKey: 'TIKI-S2' },
    { source: 'TIKI', sourceId: 'p5', shopKey: 'TIKI-S3' },
    { source: 'TIKI', sourceId: 'p6', shopKey: 'TIKI-S4' },
  ]

  // Record 1st product from S1
  tracker.recordProduct({ source: 'TIKI', sourceId: 'p1', shopKey: 'TIKI-S1' })
  assert.equal(tracker.getShopCount(), 1)
  assert.equal(tracker.getProductCount(), 1)

  // Next prioritization should rank S2 and S3 (new shops) ahead of another S1 product
  const prioritized = tracker.prioritizeCandidates(candidates)
  assert.equal(prioritized[0].shopKey, 'TIKI-S2')
  assert.equal(prioritized[1].shopKey, 'TIKI-S3')

  tracker.recordProduct({ source: 'TIKI', sourceId: 'p4', shopKey: 'TIKI-S2' })
  tracker.recordProduct({ source: 'TIKI', sourceId: 'p5', shopKey: 'TIKI-S3' })
  assert.equal(tracker.getShopCount(), 3)

  // Now shopLimit (3) is reached: S1 has 1 product, S2 has 1, S3 has 1.
  // S1's p2 is accepted (1 < maxPerShop 2), while S1's p3 will exceed maxPerShop once p2 is added
  assert.equal(
    tracker.canAcceptCandidate({ source: 'TIKI', sourceId: 'p2', shopKey: 'TIKI-S1' }),
    true,
  )
  tracker.recordProduct({ source: 'TIKI', sourceId: 'p2', shopKey: 'TIKI-S1' })

  // S1 now has 2 products (== maxPerShop), so p3 is rejected in strict mode
  assert.equal(
    tracker.canAcceptCandidate({ source: 'TIKI', sourceId: 'p3', shopKey: 'TIKI-S1' }),
    false,
  )
})
