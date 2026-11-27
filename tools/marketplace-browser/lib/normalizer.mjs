function vnBound(patternSource) {
  return new RegExp(`(?:^|[\\s,./|\\-_()\\[\\]:])(?:${patternSource})(?:$|[\\s,./|\\-_()\\[\\]:])`, 'i')
}

const STANDARD_COLORS = [
  { canonical: 'Xanh Navy', patterns: [vnBound('xanh\\s*navy|xanh\\s*than|xanh\\s*đen|navy')] },
  {
    canonical: 'Xanh Dương',
    patterns: [vnBound('xanh\\s*dương|xanh\\s*da\\s*trời|xanh\\s*lam|xanh\\s*biển|xanh\\s*pastel|blue')],
  },
  {
    canonical: 'Xanh Rêu',
    patterns: [vnBound('xanh\\s*rêu|xanh\\s*lá|xanh\\s*lục|xanh\\s*bơ|xanh\\s*mint|xanh\\s*cốm|green|olive')],
  },
  { canonical: 'Kẻ sọc', patterns: [vnBound('kẻ\\s*sọc|sọc|caro|kẻ|stripe|plaid')] },
  {
    canonical: 'Họa tiết',
    patterns: [vnBound('họa\\s*tiết|hoa\\s*nhí|in\\s*hình|phối\\s*màu|pattern|floral')],
  },
  { canonical: 'Đen', patterns: [vnBound('đen|den|black|than\\s*chì')] },
  { canonical: 'Trắng', patterns: [vnBound('trắng\\s*sữa|trắng|trang|white')] },
  { canonical: 'Kem', patterns: [vnBound('kem|cream|trắng\\s*kem|sữa')] },
  { canonical: 'Be', patterns: [vnBound('be|beige|nude|cát')] },
  { canonical: 'Xám', patterns: [vnBound('xám|ghi|gray|grey|bạc')] },
  { canonical: 'Hồng', patterns: [vnBound('hồng|pink|pastel')] },
  { canonical: 'Đỏ', patterns: [vnBound('đỏ\\s*đô|đỏ\\s*rượu|đỏ|red|burgundy|maroon')] },
  { canonical: 'Nâu', patterns: [vnBound('nâu\\s*bò|nâu|brown|cafe|cà\\s*phê|camel|chocolate')] },
  { canonical: 'Vàng', patterns: [vnBound('vàng|yellow|mù\\s*tạt|gold')] },
  { canonical: 'Tím', patterns: [vnBound('tím|purple|violet|lilac|khoai\\s*môn')] },
  { canonical: 'Cam', patterns: [vnBound('cam\\s*đất|cam|orange')] },
]

const COLOR_CODE_MAP = {
  'Đen': 'BLK',
  'Trắng': 'WHT',
  'Be': 'BEI',
  'Kem': 'CRM',
  'Xám': 'GRY',
  'Xanh Navy': 'NVY',
  'Xanh Dương': 'BLU',
  'Xanh Rêu': 'GRN',
  'Hồng': 'PNK',
  'Đỏ': 'RED',
  'Nâu': 'BRW',
  'Vàng': 'YLW',
  'Tím': 'PRP',
  'Cam': 'ORG',
  'Kẻ sọc': 'STR',
  'Họa tiết': 'PTN',
}

const DEFAULT_ADDRESSES = [
  'Quận Cầu Giấy, Hà Nội',
  'Quận 1, TP. Hồ Chí Minh',
  'Quận Thanh Xuân, Hà Nội',
  'Quận Phú Nhuận, TP. Hồ Chí Minh',
  'Quận Đống Đa, Hà Nội',
  'Quận 3, TP. Hồ Chí Minh',
  'Quận Hải Châu, Đà Nẵng',
  'Quận Bình Thạnh, TP. Hồ Chí Minh',
]

export function toUnaccentedSlug(input) {
  const raw = String(input ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'd')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
  if (raw === '') {
    return 'item'
  }
  return raw
}

export function safeId(input) {
  const cleaned = String(input ?? '').replace(/[^a-zA-Z0-9_-]/g, '')
  if (cleaned === '') {
    return '0'
  }
  return cleaned
}

function hashString(input) {
  const str = String(input ?? '')
  let hash = 2166136261
  for (let i = 0; i < str.length; i++) {
    hash ^= str.charCodeAt(i)
    hash = Math.imul(hash, 16777619)
  }
  return Math.abs(hash >>> 0)
}

export function normalizeUrl(url) {
  if (!url || typeof url !== 'string') {
    return null
  }
  const trimmed = url.trim()
  if (trimmed === '') {
    return null
  }
  if (trimmed.startsWith('//')) {
    return `https:${trimmed}`
  }
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
    return trimmed
  }
  return null
}

function cleanProductName(rawName) {
  let name = String(rawName ?? '')
    .replace(/^\s*(?:\[[^\]]*\]|【[^】]*】|\([^)]*giảm[^)]*\))\s*/gi, '')
    .replace(/^\s*(?:\[[^\]]*\]|【[^】]*】)\s*/gi, '')
    .replace(/\s+/g, ' ')
    .trim()

  if (name === '') {
    name = 'Sản phẩm thời trang cao cấp'
  }
  if (name.length > 300) {
    name = name.slice(0, 300).trim()
  }
  return name
}

function inferCategorySlug(rawCategory, productName) {
  const valid = new Set(['ao', 'vay-dam', 'ao-khoac', 'quan', 'phu-kien'])
  if (valid.has(rawCategory)) {
    return rawCategory
  }
  const lower = String(productName ?? '').toLowerCase()
  if (/váy|đầm|chân váy|skirt|dress/i.test(lower)) {
    return 'vay-dam'
  }
  if (/khoác|blazer|bomber|hoodie|cardigan|sweater|jacket|coat/i.test(lower)) {
    return 'ao-khoac'
  }
  if (/quần|jean|short|jogger|kaki|culottes|pants|trousers/i.test(lower)) {
    return 'quan'
  }
  if (/túi|ví|balo|mũ|nón|thắt lưng|dây nịt|khăn|kính|vớ|tất|phụ kiện/i.test(lower)) {
    return 'phu-kien'
  }
  return 'ao'
}

export function extractColorFromText(text) {
  if (!text) {
    return null
  }
  const raw = String(text)
    .replace(/^màu\s*/i, '')
    .trim()

  // Note: check 'Trắng sữa' specifically before 'Kem' so 'Trắng sữa' maps to 'Trắng'
  if (/trắng/i.test(raw)) {
    return 'Trắng'
  }
  for (const entry of STANDARD_COLORS) {
    for (const pattern of entry.patterns) {
      if (pattern.test(raw)) {
        return entry.canonical
      }
    }
  }
  if (/xanh/i.test(raw)) {
    return 'Xanh Dương'
  }
  return null
}

export function extractSizeAndHintFromText(text) {
  if (!text) {
    return { size: null, hint: null }
  }
  const str = String(text).trim()
  let hint = null
  const parenMatch = str.match(/\(([^)]*(?:kg|cm)[^)]*)\)/i)
  if (parenMatch) {
    hint = parenMatch[1].trim()
  } else {
    const inlineWeight = str.match(/(\d{2}\s*-\s*\d{2}\s*kg)/i)
    if (inlineWeight) {
      hint = inlineWeight[1].trim()
    }
  }

  const withoutHint = str.replace(/\([^)]*\)/g, ' ').trim()

  if (/free\s*size|one\s*size|\bfs\b/i.test(withoutHint)) {
    return { size: 'Freesize', hint }
  }
  if (/\b(?:3xl|xxxl)\b/i.test(withoutHint)) {
    return { size: '3XL', hint }
  }
  if (/\b(?:2xl|xxl)\b/i.test(withoutHint)) {
    return { size: '2XL', hint }
  }
  if (/\bxl\b/i.test(withoutHint)) {
    return { size: 'XL', hint }
  }
  if (/\bxs\b/i.test(withoutHint)) {
    return { size: 'XS', hint }
  }

  const explicitSizeMatch = withoutHint.match(/(?:size|cỡ|sz)\s*[:\-]?\s*(xs|s|m|l|xl|2xl|3xl|xxl|xxxl|\d{2})\b/i)
  if (explicitSizeMatch) {
    const token = explicitSizeMatch[1].toUpperCase()
    if (token === 'XXL') {
      return { size: '2XL', hint }
    }
    if (token === 'XXXL') {
      return { size: '3XL', hint }
    }
    return { size: token, hint }
  }

  const numericMatch = withoutHint.match(/\b(2[7-9]|3[0-4])\b/)
  if (numericMatch) {
    return { size: numericMatch[1], hint }
  }

  const standaloneLetter = withoutHint.match(/(?:^|[\s,/|\-_])(S|M|L)(?:$|[\s,/|\-_])/i)
  if (standaloneLetter) {
    return { size: standaloneLetter[1].toUpperCase(), hint }
  }

  return { size: null, hint }
}

function parseRawVariant(rawVariant) {
  let color = null
  let size = null
  const hints = []

  if (rawVariant.color) {
    color = extractColorFromText(rawVariant.color)
  }
  if (rawVariant.size) {
    const parsedSize = extractSizeAndHintFromText(rawVariant.size)
    size = parsedSize.size
    if (parsedSize.hint) {
      hints.push({ size: parsedSize.size, hint: parsedSize.hint })
    }
    if (!color) {
      color = extractColorFromText(rawVariant.size)
    }
  }

  const combinedLabel = [rawVariant.name, rawVariant.title, rawVariant.option1, rawVariant.option2]
    .filter(Boolean)
    .join(' - ')

  if (combinedLabel) {
    if (!color) {
      color = extractColorFromText(combinedLabel)
    }
    if (!size) {
      const parsed = extractSizeAndHintFromText(combinedLabel)
      size = parsed.size
      if (parsed.hint) {
        hints.push({ size: parsed.size, hint: parsed.hint })
      }
    } else {
      const parsed = extractSizeAndHintFromText(combinedLabel)
      if (parsed.hint) {
        hints.push({ size, hint: parsed.hint })
      }
    }
  }

  return {
    color,
    size,
    hints,
    price: Number(rawVariant.price ?? 0),
    originalPrice: Number(rawVariant.originalPrice ?? rawVariant.compareAtPrice ?? 0),
    stock: Number(rawVariant.stock ?? rawVariant.stockQuantity ?? 0),
    imageUrl: normalizeUrl(rawVariant.imageUrl ?? rawVariant.image),
    imageFile: rawVariant.imageFile ?? null,
  }
}

function inferColorsFromProduct(productName, categorySlug) {
  const detected = []
  for (const entry of STANDARD_COLORS) {
    for (const pattern of entry.patterns) {
      if (pattern.test(productName)) {
        if (!detected.includes(entry.canonical)) {
          detected.push(entry.canonical)
        }
      }
    }
  }
  if (detected.length >= 2) {
    return detected.slice(0, 3)
  }
  if (detected.length === 1) {
    if (detected[0] === 'Đen') {
      return ['Đen', 'Trắng']
    }
    return [detected[0], 'Đen']
  }
  if (categorySlug === 'vay-dam') {
    return ['Trắng', 'Đen', 'Be']
  }
  if (categorySlug === 'quan') {
    return ['Đen', 'Xanh Navy']
  }
  if (categorySlug === 'phu-kien') {
    return ['Đen', 'Nâu']
  }
  return ['Đen', 'Trắng', 'Xám']
}

function defaultSizesForCategory(categorySlug, productName) {
  if (categorySlug === 'phu-kien') {
    return ['Freesize']
  }
  if (categorySlug === 'quan' && /jean|bò|tây|âu|kaki/i.test(productName)) {
    return ['29', '30', '31', '32']
  }
  return ['S', 'M', 'L', 'XL']
}

function normalizePrice(rawPrice, fallbackPrice) {
  const num = Math.round(Number(rawPrice ?? 0))
  if (Number.isFinite(num) && num >= 1000) {
    return num
  }
  const fallback = Math.round(Number(fallbackPrice ?? 0))
  if (Number.isFinite(fallback) && fallback >= 1000) {
    return fallback
  }
  return 159000
}

function normalizeCompareAtPrice(rawOriginal, finalPrice) {
  const num = Math.round(Number(rawOriginal ?? 0))
  if (Number.isFinite(num) && num > finalPrice) {
    return num
  }
  return null
}

function normalizeStock(rawStock, seedNumber) {
  const num = Math.round(Number(rawStock ?? 0))
  if (Number.isFinite(num) && num >= 5) {
    return Math.min(num, 500)
  }
  return 15 + (seedNumber % 20)
}

function buildSku(source, sourceId, color, size) {
  const colorCode = COLOR_CODE_MAP[color] ?? toUnaccentedSlug(color).toUpperCase().slice(0, 4)
  const sizeCode = toUnaccentedSlug(size).toUpperCase()
  return `${source}-${safeId(sourceId)}-${colorCode}-${sizeCode}`
}

function inferMaterial(rawAttributes, productName, description, categorySlug) {
  const attrMaterial = rawAttributes?.material ?? rawAttributes?.['Chất liệu'] ?? rawAttributes?.['chất liệu']
  if (attrMaterial && String(attrMaterial).trim().length >= 2) {
    return String(attrMaterial).trim().slice(0, 120)
  }
  const text = `${productName} ${description}`
  if (/cotton/i.test(text)) {
    return 'Cotton 100% co giãn 4 chiều'
  }
  if (/jean|denim/i.test(text)) {
    return 'Denim cao cấp dày dặn'
  }
  if (/kaki/i.test(text)) {
    return 'Vải Kaki cotton thoáng khí'
  }
  if (/lụa|silk|voan|chiffon/i.test(text)) {
    return 'Lụa tổng hợp mềm mịn'
  }
  if (/len|wool|dệt kim/i.test(text)) {
    return 'Len dệt kim mềm mại'
  }
  if (/nỉ|fleece|hoodie|sweater/i.test(text)) {
    return 'Vải nỉ bông cotton dày dặn'
  }
  if (/da\s*pu|da\s*bò|da\s*thật|leather/i.test(text)) {
    return 'Da PU cao cấp chống thấm nhẹ'
  }
  if (/canvas|vải\s*bố/i.test(text)) {
    return 'Vải bố Canvas bền chắc'
  }
  if (categorySlug === 'vay-dam') {
    return 'Vải Tuyết mưa cao cấp mềm rủ'
  }
  if (categorySlug === 'ao-khoac') {
    return 'Vải Kaki phối lớp lót thoáng khí'
  }
  if (categorySlug === 'quan') {
    return 'Vải Tuyết mưa co giãn nhẹ giữ form'
  }
  if (categorySlug === 'phu-kien') {
    return 'Da tổng hợp cao cấp phối phụ kiện kim loại'
  }
  return 'Cotton pha Spandex thoáng mát'
}

function inferOrigin(rawAttributes) {
  const attrOrigin = rawAttributes?.origin ?? rawAttributes?.['Xuất xứ'] ?? rawAttributes?.['xuất xứ']
  if (attrOrigin && String(attrOrigin).trim().length >= 2) {
    const cleaned = String(attrOrigin).trim()
    if (!/không xác định|other|khác/i.test(cleaned)) {
      return cleaned.slice(0, 80)
    }
  }
  return 'Việt Nam'
}

function inferCareInstructions(rawAttributes, material, categorySlug) {
  const attrCare =
    rawAttributes?.careInstructions ??
    rawAttributes?.['Hướng dẫn bảo quản'] ??
    rawAttributes?.['hướng dẫn bảo quản']
  if (attrCare && String(attrCare).trim().length >= 10) {
    return String(attrCare).trim().slice(0, 500)
  }
  if (categorySlug === 'phu-kien') {
    return 'Lau nhẹ bằng khăn ẩm mềm, bảo quản nơi khô ráo thoáng mát, tránh tiếp xúc trực tiếp với hóa chất tẩy rửa.'
  }
  return `Giặt nhẹ ở nhiệt độ thường với sản phẩm cùng màu (${material}), lộn trái khi phơi nơi thoáng mát, không sử dụng chất tẩy mạnh.`
}

function buildSizeGuide(rawSizeGuide, extractedHints, categorySlug) {
  const hintParts = []
  const seenSizes = new Set()
  for (const item of extractedHints) {
    const label = item.size ?? 'Chuẩn'
    const key = `${label}:${item.hint}`
    if (!seenSizes.has(key)) {
      seenSizes.add(key)
      hintParts.push(`Size ${label}: ${item.hint}`)
    }
  }
  if (rawSizeGuide && String(rawSizeGuide).trim().length >= 10) {
    if (hintParts.length > 0) {
      return `${String(rawSizeGuide).trim()} | ${hintParts.join(' | ')}`
    }
    return String(rawSizeGuide).trim()
  }
  if (hintParts.length > 0) {
    return hintParts.join(' | ')
  }
  if (categorySlug === 'phu-kien') {
    return 'Freesize: Thiết kế tiêu chuẩn phù hợp với mọi vóc dáng và nhu cầu sử dụng hằng ngày.'
  }
  if (categorySlug === 'quan') {
    return 'Size S / 28: 45-52kg | Size M / 29-30: 53-60kg | Size L / 31: 61-68kg | Size XL / 32: 69-76kg'
  }
  return 'Size S: 45-52kg (1m50-1m62) | Size M: 53-60kg (1m55-1m68) | Size L: 61-68kg (1m60-1m74) | Size XL: 69-78kg (1m65-1m80)'
}

function defaultPackagingForCategory(categorySlug) {
  if (categorySlug === 'vay-dam') {
    return { weightGrams: 350, lengthCm: 28, widthCm: 20, heightCm: 5 }
  }
  if (categorySlug === 'ao-khoac') {
    return { weightGrams: 600, lengthCm: 32, widthCm: 24, heightCm: 7 }
  }
  if (categorySlug === 'quan') {
    return { weightGrams: 450, lengthCm: 30, widthCm: 22, heightCm: 5 }
  }
  if (categorySlug === 'phu-kien') {
    return { weightGrams: 300, lengthCm: 25, widthCm: 18, heightCm: 8 }
  }
  return { weightGrams: 250, lengthCm: 28, widthCm: 20, heightCm: 5 }
}

function htmlToCleanMarkdown(rawHtml, productName, material, origin, careInstructions, sizeGuide) {
  let text = String(rawHtml ?? '')
  text = text
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?<\/style>/gi, '')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/p>/gi, '\n\n')
    .replace(/<\/div>/gi, '\n')
    .replace(/<\/h[1-6]>/gi, '\n\n')
    .replace(/<li[^>]*>/gi, '- ')
    .replace(/<\/li>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/https?:\/\/[^\s)]+/gi, '')
    .replace(/\b0\d{9,10}\b/g, '')
    .replace(/Giá sản phẩm trên Tiki đã bao gồm thuế theo luật hiện hành[\s\S]*$/gi, '')
    .replace(/\r\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()

  const structuredAppendix = [
    '### Đặc điểm nổi bật',
    `- **${productName}** sở hữu phom dáng hiện đại, đường may tỉ mỉ, dễ dàng phối cùng nhiều trang phục khác nhau.`,
    `- Chất liệu **${material}** tạo cảm giác thoải mái, giữ phom tốt trong suốt quá trình sử dụng.`,
    '',
    '### Thông số chất liệu & Xuất xứ',
    `- **Chất liệu:** ${material}`,
    `- **Xuất xứ:** ${origin}`,
    `- **Bảng kích cỡ tham khảo:** ${sizeGuide}`,
    '',
    '### Hướng dẫn bảo quản',
    `- ${careInstructions}`,
  ].join('\n')

  let combined = structuredAppendix
  if (text.length >= 20) {
    combined = `${text}\n\n${structuredAppendix}`
  }

  if (combined.length > 20000) {
    return combined.slice(0, 20000).trim()
  }
  return combined
}

export function normalizeShop(rawShop) {
  const source = String(rawShop?.source ?? 'TIKI').toUpperCase()
  const rawShopId = String(rawShop?.sourceShopId ?? rawShop?.id ?? '1000').trim()
  const cleanShopId = safeId(rawShopId)
  const shopKey = `${source}-${cleanShopId}`
  let name = String(rawShop?.name ?? `Gian hàng thời trang ${cleanShopId}`).trim()
  if (name === '') {
    name = `Gian hàng thời trang ${cleanShopId}`
  }

  const hash = hashString(shopKey)
  const slugSuffix = toUnaccentedSlug(rawShopId)
  const baseSlug = toUnaccentedSlug(name).slice(0, 140)
  let slug = `${baseSlug}-${slugSuffix}`
  if ( baseSlug.endsWith(`-${slugSuffix}`) || baseSlug === slugSuffix ) {
    slug = baseSlug
  }
  slug = slug.replace(/-+/g, '-').replace(/^-+|-+$/g, '').slice(0, 180)

  let sellerType = 'INDIVIDUAL'
  if (
    rawShop?.sellerType === 'CORPORATE' ||
    rawShop?.isOfficial === true ||
    rawShop?.isMall === true ||
    /official|mall|flagship|trading|chính hãng|chinh hang|authentic|brand/i.test(name)
  ) {
    sellerType = 'CORPORATE'
  }

  let description = String(rawShop?.description ?? '').trim()
  if (description.length < 20) {
    description = `${name} chuyên cung cấp các dòng sản phẩm thời trang và phụ kiện chất lượng cao, cam kết hình ảnh thực tế, kiểm tra kỹ lưỡng trước khi đóng gói giao hàng.`
  }

  let address = String(rawShop?.address ?? rawShop?.location ?? '').trim()
  if (address.length < 5) {
    address = DEFAULT_ADDRESSES[hash % DEFAULT_ADDRESSES.length]
  }

  let phone = String(rawShop?.phone ?? '').replace(/[^0-9]/g, '')
  if (!/^0\d{9}$/.test(phone)) {
    const suffix = String(10000000 + (hash % 89999999)).slice(0, 8)
    phone = `09${suffix}`
  }

  let rating = Number(rawShop?.rating ?? 0)
  if (!Number.isFinite(rating) || rating < 4.5 || rating > 5.0) {
    rating = Number((4.6 + ((hash % 5) * 0.1)).toFixed(1))
  } else {
    rating = Number(rating.toFixed(1))
  }

  let ratingCount = Math.round(Number(rawShop?.ratingCount ?? 0))
  if (!Number.isFinite(ratingCount) || ratingCount <= 0) {
    ratingCount = 45 + (hash % 850)
  }

  let followerCount = Math.round(Number(rawShop?.followerCount ?? 0))
  if (!Number.isFinite(followerCount) || followerCount <= 0) {
    followerCount = 320 + (hash % 15000)
  }

  let processingDays = Math.round(Number(rawShop?.processingDays ?? 0))
  if (!Number.isFinite(processingDays) || processingDays < 1 || processingDays > 3) {
    processingDays = 1 + (hash % 2)
  }

  let returnPolicy = String(rawShop?.returnPolicy ?? '').trim()
  if (returnPolicy.length < 20) {
    returnPolicy =
      'Hỗ trợ đổi trả miễn phí trong vòng 15 ngày nếu sản phẩm có lỗi từ nhà sản xuất, giao sai mẫu hoặc không vừa kích cỡ (sản phẩm còn nguyên tem mác).'
  }

  return {
    shopKey,
    source,
    sourceShopId: cleanShopId,
    name,
    slug,
    description,
    sellerType,
    address,
    phone,
    logoUrl: normalizeUrl(rawShop?.logoUrl ?? rawShop?.logo),
    bannerUrl: normalizeUrl(rawShop?.bannerUrl ?? rawShop?.banner),
    logoFile: rawShop?.logoFile ?? null,
    bannerFile: rawShop?.bannerFile ?? null,
    rating,
    ratingCount,
    followerCount,
    processingDays,
    returnPolicy,
  }
}

export function normalizeProduct(rawProduct, shopKey) {
  const source = String(rawProduct?.source ?? 'TIKI').toUpperCase()
  const sourceId = safeId(rawProduct?.sourceId ?? rawProduct?.id ?? '10000')
  const name = cleanProductName(rawProduct?.name)
  const categorySlug = inferCategorySlug(rawProduct?.categorySlug, name)
  const slugSuffix = toUnaccentedSlug(sourceId)
  const baseSlug = toUnaccentedSlug(name).slice(0, 140)
  const slug = `${baseSlug}-${slugSuffix}`.replace(/-+/g, '-').slice(0, 180)
  const seed = hashString(`${source}-${sourceId}`)

  const basePrice = normalizePrice(rawProduct?.price, 159000)
  const baseOriginalPrice = normalizePrice(rawProduct?.originalPrice, basePrice)

  const parsedVariants = []
  const extractedHints = []
  let rawVariants = []
  if (Array.isArray(rawProduct?.variants)) {
    rawVariants = rawProduct.variants
  }

  for (const rawVar of rawVariants) {
    const parsed = parseRawVariant(rawVar)
    parsedVariants.push(parsed)
    for (const h of parsed.hints) {
      extractedHints.push(h)
    }
  }

  const defaultSizes = defaultSizesForCategory(categorySlug, name)
  const inferredColors = inferColorsFromProduct(name, categorySlug)
  const expandedCandidates = []

  if (parsedVariants.length === 0) {
    for (const color of inferredColors.slice(0, 2)) {
      for (const size of defaultSizes) {
        expandedCandidates.push({
          color,
          size,
          price: basePrice,
          originalPrice: baseOriginalPrice,
          stock: 20,
          imageUrl: null,
          imageFile: null,
        })
      }
    }
  } else {
    for (const item of parsedVariants) {
      if (item.color && item.size) {
        let finalSize = item.size
        if (categorySlug === 'phu-kien') {
          finalSize = 'Freesize'
        }
        expandedCandidates.push({
          color: item.color,
          size: finalSize,
          price: item.price,
          originalPrice: item.originalPrice,
          stock: item.stock,
          imageUrl: item.imageUrl,
          imageFile: item.imageFile,
        })
      } else if (item.color && !item.size) {
        for (const size of defaultSizes) {
          expandedCandidates.push({
            color: item.color,
            size,
            price: item.price,
            originalPrice: item.originalPrice,
            stock: item.stock,
            imageUrl: item.imageUrl,
            imageFile: item.imageFile,
          })
        }
      } else if (!item.color && item.size) {
        let finalSize = item.size
        if (categorySlug === 'phu-kien') {
          finalSize = 'Freesize'
        }
        for (const color of inferredColors.slice(0, 2)) {
          expandedCandidates.push({
            color,
            size: finalSize,
            price: item.price,
            originalPrice: item.originalPrice,
            stock: item.stock,
            imageUrl: item.imageUrl,
            imageFile: item.imageFile,
          })
        }
      } else {
        for (const color of inferredColors.slice(0, 2)) {
          for (const size of defaultSizes) {
            expandedCandidates.push({
              color,
              size,
              price: item.price,
              originalPrice: item.originalPrice,
              stock: item.stock,
              imageUrl: item.imageUrl,
              imageFile: item.imageFile,
            })
          }
        }
      }
    }
  }

  // When a clothing product on PDP has only 1 remaining SKU in stock, expand across standard sizes for that color
  if (categorySlug !== 'phu-kien' && expandedCandidates.length === 1) {
    const only = expandedCandidates[0]
    for (const defSize of defaultSizes) {
      if (defSize !== only.size) {
        expandedCandidates.push({
          color: only.color,
          size: defSize,
          price: only.price,
          originalPrice: only.originalPrice,
          stock: only.stock,
          imageUrl: only.imageUrl,
          imageFile: only.imageFile,
        })
      }
    }
  }

  const seenPairs = new Set()
  const finalVariants = []

  for (let i = 0; i < expandedCandidates.length; i++) {
    const candidate = expandedCandidates[i]
    const pairKey = `${candidate.color}__${candidate.size}`
    if (seenPairs.has(pairKey)) {
      continue
    }
    seenPairs.add(pairKey)

    const price = normalizePrice(candidate.price, basePrice)
    const compareAtPrice = normalizeCompareAtPrice(candidate.originalPrice, price)
    const stockQuantity = normalizeStock(candidate.stock, seed + i)
    const isDefault = finalVariants.length === 0

    finalVariants.push({
      sku: buildSku(source, sourceId, candidate.color, candidate.size),
      color: candidate.color,
      size: candidate.size,
      price,
      compareAtPrice,
      stockQuantity,
      defaultVariant: isDefault,
      imageUrl: candidate.imageUrl,
      imageFile: candidate.imageFile,
    })
  }

  const attributes = rawProduct?.attributes ?? {}
  const material = inferMaterial(
    { ...attributes, material: rawProduct?.material ?? attributes.material },
    name,
    rawProduct?.description ?? '',
    categorySlug,
  )
  const origin = inferOrigin({ ...attributes, origin: rawProduct?.origin ?? attributes.origin })
  const careInstructions = inferCareInstructions(
    {
      ...attributes,
      careInstructions: rawProduct?.careInstructions ?? attributes.careInstructions,
    },
    material,
    categorySlug,
  )
  const sizeGuide = buildSizeGuide(rawProduct?.sizeGuide, extractedHints, categorySlug)
  const description = htmlToCleanMarkdown(
    rawProduct?.description,
    name,
    material,
    origin,
    careInstructions,
    sizeGuide,
  )

  const packaging = defaultPackagingForCategory(categorySlug)
  let weightGrams = Math.round(Number(rawProduct?.weightGrams ?? 0))
  if (!Number.isFinite(weightGrams) || weightGrams <= 0) {
    weightGrams = packaging.weightGrams
  }
  let lengthCm = Math.round(Number(rawProduct?.lengthCm ?? 0))
  if (!Number.isFinite(lengthCm) || lengthCm <= 0) {
    lengthCm = packaging.lengthCm
  }
  let widthCm = Math.round(Number(rawProduct?.widthCm ?? 0))
  if (!Number.isFinite(widthCm) || widthCm <= 0) {
    widthCm = packaging.widthCm
  }
  let heightCm = Math.round(Number(rawProduct?.heightCm ?? 0))
  if (!Number.isFinite(heightCm) || heightCm <= 0) {
    heightCm = packaging.heightCm
  }

  const tryOnEnabled = categorySlug !== 'phu-kien'

  const imageUrlSet = new Set()
  let rawImageUrls = []
  if (Array.isArray(rawProduct?.imageUrls)) {
    rawImageUrls = [...rawProduct.imageUrls]
  }
  if (rawProduct?.imageUrl) {
    rawImageUrls.unshift(rawProduct.imageUrl)
  }
  for (const rawUrl of rawImageUrls) {
    const normalized = normalizeUrl(rawUrl)
    if (normalized) {
      imageUrlSet.add(normalized)
    }
  }
  for (const v of finalVariants) {
    if (v.imageUrl) {
      imageUrlSet.add(v.imageUrl)
    }
  }

  const imageUrls = Array.from(imageUrlSet).slice(0, 9)
  let imageFiles = []
  if (Array.isArray(rawProduct?.imageFiles)) {
    imageFiles = [...rawProduct.imageFiles]
  }
  let imageFile = rawProduct?.imageFile ?? null
  if (!imageFile && imageFiles.length > 0) {
    imageFile = imageFiles[0]
  }
  let garmentImageFiles = []
  if (Array.isArray(rawProduct?.garmentImageFiles)) {
    garmentImageFiles = [...rawProduct.garmentImageFiles]
  }

  const firstVariantPrice = finalVariants[0]?.price ?? basePrice
  const firstVariantCompare = finalVariants[0]?.compareAtPrice ?? null
  let finalOriginalPrice = firstVariantPrice
  if (firstVariantCompare !== null && firstVariantCompare > firstVariantPrice) {
    finalOriginalPrice = firstVariantCompare
  } else if (baseOriginalPrice > firstVariantPrice) {
    finalOriginalPrice = baseOriginalPrice
  }

  let resolvedShopKey = shopKey
  if (!resolvedShopKey) {
    resolvedShopKey = rawProduct?.shopKey ?? `${source}-DEFAULT`
  }

  return {
    source,
    sourceId,
    shopKey: resolvedShopKey,
    name,
    slug,
    categorySlug,
    price: firstVariantPrice,
    originalPrice: finalOriginalPrice,
    description,
    material,
    origin,
    careInstructions,
    sizeGuide,
    weightGrams,
    lengthCm,
    widthCm,
    heightCm,
    tryOnEnabled,
    imageUrls,
    imageFile,
    imageFiles,
    garmentImageFiles,
    variants: finalVariants,
    sourceUrl: normalizeUrl(rawProduct?.sourceUrl) ?? `https://tiki.vn/product-p${sourceId}.html`,
  }
}
