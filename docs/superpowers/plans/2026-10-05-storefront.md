# Storefront Public Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Biến `/` thành storefront public cho khách (hero, danh mục, carousel, lưới lọc/sắp xếp, PDP, giỏ localStorage, footer đầy đủ), dời CRUD admin vào `/admin/*`.

**Architecture:** Hai layout song song (`StorefrontLayout` public, `AdminLayout` protected). Data còn thiếu (category, badge) suy ở `lib/catalog.ts` frontend-only. Giỏ hàng là `useCart` + localStorage, không gọi backend. Mỗi page lazy-load như hiện tại.

**Tech Stack:** Vite + React 19 TS + Tailwind v4 + shadcn/ui (thêm `sheet`, `badge`, `accordion`, `select`) + react-router 8 + TanStack Query 5 + sonner. Test: vitest 5 + jsdom + RTL + user-event.

**Spec:** `docs/superpowers/specs/2026-10-05-storefront-design.md`

## Global Constraints

- Chỉ dùng `pnpm`, không dùng `npm` hay ternary (`? :`) — viết `if` + early `return`.
- Một statement một dòng, ưu tiên rõ ràng hơn ngắn gọn.
- Copy UI tiếng Việt.
- Không đổi font/token màu: chỉ dùng utility đã có (`font-display`, `text-muted-foreground`, `bg-card`…).
- Mọi ảnh sản phẩm có `loading="lazy"`.
- Mọi page mới phải `React.lazy` trong `routes.tsx`.
- Test colocated `*.test.tsx`, mock `@/lib/api` theo pattern `ProductList.test.tsx`, render kèm `Toaster` khi assert toast.
- Sau mỗi task: `pnpm test`, `pnpm lint` (0 error), `pnpm build` xanh rồi mới commit trong repo `fashionstore-ui`.

## Review Focus

- Khách thêm hàng, reload trang → giỏ còn nguyên (localStorage round-trip đúng).
- Vào PDP id không tồn tại → thấy trang 404, không crash trắng.
- localStorage chứa JSON hỏng → giỏ bắt đầu rỗng, không crash.
- Chưa login: `/`, PDP, `/cart` xem được; `/admin/*` bị đá về `/login`.
- Lọc giá trần thấp hơn mọi sản phẩm → hiện empty-state, lưới không vỡ.

---

## File map

- Tạo: `src/lib/catalog.ts`, `src/lib/catalog.test.ts`, `src/hooks/useCart.ts`,
  `src/hooks/useCart.test.ts`, `src/components/storefront/PromoBar.tsx`,
  `src/components/storefront/StorefrontHeader.tsx`, `src/components/storefront/StorefrontFooter.tsx`,
  `src/components/storefront/StorefrontLayout.tsx`, `src/components/AdminLayout.tsx`,
  `src/pages/StorefrontHome.tsx`, `src/pages/StorefrontHome.test.tsx`,
  `src/pages/ProductDetail.tsx`, `src/pages/ProductDetail.test.tsx`,
  `src/pages/CartPage.tsx`, `src/pages/CartPage.test.tsx`,
  `src/components/storefront/CartDrawer.tsx`, `src/components/storefront/HomeSections.tsx`
  (Hero, CategoryTiles, BestsellerCarousel, CollectionBanner, Commitments gom một file vì nhỏ).
- Sửa: `src/routes.tsx`, `src/pages/ComingSoon.tsx`, `src/pages/ProductList.tsx`
  (link tạo/sửa sang `/admin/*`), `src/pages/ProductForm.tsx` (navigate sau lưu/huỷ về `/admin`),
  `oxlint.config.ts` (không đổi — chỉ đảm bảo lint sạch).
- Sinh tự động: `src/components/ui/sheet.tsx`, `badge.tsx`, `accordion.tsx`, `select.tsx`
  (qua shadcn CLI, áp override lint hiện có cho `ui/**`).

---

### Task 1: Thêm primitives shadcn

**Files:**
- Create: `src/components/ui/sheet.tsx`, `src/components/ui/badge.tsx`, `src/components/ui/accordion.tsx`, `src/components/ui/select.tsx`
- Modify: none
- Test: none (file sinh tự động, kiểm bằng lint + build)

**Interfaces:**
- Consumes: `oxlint.config.ts` override cho `src/components/ui/**` (đã có).
- Produces: `Sheet`, `Badge`, `Accordion`, `Select` cho Task 4/5/6/7.

- [ ] **Step 1: Cài primitives**

```bash
pnpm dlx shadcn@latest add sheet badge accordion select -y
```

- [ ] **Step 2: Chạy lint + build để xác nhận file sinh ra sạch**

```bash
pnpm lint 2>&1 | tail -3
pnpm build 2>&1 | tail -3
```

Expected: 0 error, build xanh. Nếu shadcn rule nào báo trong `ui/**`, áp đúng override pattern đang có, không tắt rule tràn lan.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: them shadcn sheet/badge/accordion/select cho storefront"
```

---

### Task 2: `lib/catalog.ts` — danh mục và badge frontend-only

**Files:**
- Create: `src/lib/catalog.ts`, `src/lib/catalog.test.ts`
- Modify: none
- Test: `src/lib/catalog.test.ts`

**Interfaces:**
- Consumes: `Product` từ `@/lib/api` (`{ id, name, description, price, imageKey, createdAt, updatedAt }`).
- Produces: `CATEGORIES`, `categoryOf(product): string`, `categoryLabel(id): string`,
  `isNew(product, now?: Date): boolean`, `isBestseller(product): boolean` cho Task 5/6.

Thứ tự keyword quan trọng: "Áo khoác trench" chứa cả "áo" lẫn "khoác" nên phải check `khoác` trước `áo`.

- [ ] **Step 1: Viết test đỏ**

```tsx
import { describe, expect, it } from 'vitest'

import type { Product } from '@/lib/api'
import { categoryOf, isBestseller, isNew } from '@/lib/catalog'

function sample(name: string, createdAt: string): Product {
  return {
    id: 1,
    name,
    description: null,
    price: 100000,
    imageKey: null,
    createdAt,
    updatedAt: createdAt,
  }
}

describe('categoryOf', () => {
  it('maps ao khoac before ao', () => {
    expect(categoryOf(sample('Áo khoác trench', '2026-10-04T00:00:00Z'))).toBe('khoac')
  })

  it('maps vay and dam to the same category', () => {
    expect(categoryOf(sample('Váy midi đen', '2026-10-04T00:00:00Z'))).toBe('vay-dam')
    expect(categoryOf(sample('Đầm maxi hoa', '2026-10-04T00:00:00Z'))).toBe('vay-dam')
  })

  it('falls back to khac for unknown names', () => {
    expect(categoryOf(sample('Mũ lưỡi trai', '2026-10-04T00:00:00Z'))).toBe('khac')
  })
})

describe('isNew', () => {
  it('is true within 30 days', () => {
    const product = sample('Áo mới', '2026-10-04T00:00:00Z')
    expect(isNew(product, new Date('2026-10-20T00:00:00Z'))).toBe(true)
  })

  it('is false after 30 days', () => {
    const product = sample('Áo cũ', '2026-08-01T00:00:00Z')
    expect(isNew(product, new Date('2026-10-20T00:00:00Z'))).toBe(false)
  })
})

describe('isBestseller', () => {
  it('flags the curated ids only', () => {
    const hot = sample('Váy midi đen', '2026-10-04T00:00:00Z')
    hot.id = 2
    expect(isBestseller(hot)).toBe(true)
    const cold = sample('Quần âu vải lụa', '2026-10-04T00:00:00Z')
    cold.id = 4
    expect(isBestseller(cold)).toBe(false)
  })
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
pnpm vitest run src/lib/catalog.test.ts 2>&1 | tail -5
```

Expected: FAIL vì module chưa tồn tại.

- [ ] **Step 3: Viết implementation tối thiểu**

```ts
import type { Product } from '@/lib/api'

export interface Category {
  id: string
  label: string
}

export const CATEGORIES: Category[] = [
  { id: 'ao', label: 'Áo' },
  { id: 'vay-dam', label: 'Váy & Đầm' },
  { id: 'khoac', label: 'Áo khoác' },
  { id: 'quan', label: 'Quần' },
  { id: 'phu-kien', label: 'Phụ kiện' },
]

// TẠM: backend chưa có field category nên suy từ tên.
// Khi API có category thật, thay đúng hàm này, giữ nguyên chữ ký.
export function categoryOf(product: Product): string {
  const name = product.name.toLowerCase()
  if (name.includes('khoác')) {
    return 'khoac'
  }
  if (name.includes('váy') || name.includes('đầm')) {
    return 'vay-dam'
  }
  if (name.includes('quần')) {
    return 'quan'
  }
  if (name.includes('túi')) {
    return 'phu-kien'
  }
  if (name.includes('áo')) {
    return 'ao'
  }
  return 'khac'
}

export function categoryLabel(id: string): string {
  const found = CATEGORIES.find((category) => category.id === id)
  if (found === undefined) {
    return 'Khác'
  }
  return found.label
}

const NEW_DAYS = 30

export function isNew(product: Product, now: Date = new Date()): boolean {
  const created = new Date(product.createdAt).getTime()
  if (Number.isNaN(created)) {
    return false
  }
  const ageMs = now.getTime() - created
  if (ageMs < 0) {
    return false
  }
  return ageMs < NEW_DAYS * 24 * 60 * 60 * 1000
}

// TẠM: curated cứng theo mock ban đầu, backend có số liệu bán thì thay.
const BESTSELLER_IDS = [1, 2, 3]

export function isBestseller(product: Product): boolean {
  return BESTSELLER_IDS.includes(product.id)
}
```

- [ ] **Step 4: Chạy test, xác nhận xanh + lint**

```bash
pnpm vitest run src/lib/catalog.test.ts 2>&1 | tail -4
pnpm lint 2>&1 | tail -2
```

Expected: test pass, 0 error.

- [ ] **Step 5: Commit**

```bash
git add src/lib/catalog.ts src/lib/catalog.test.ts
git commit -m "feat: catalog suy category/badge frontend-only (tam, cho backend co field that)"
```

---

### Task 3: `useCart` — giỏ hàng localStorage

**Files:**
- Create: `src/hooks/useCart.ts`, `src/hooks/useCart.test.ts`
- Modify: none
- Test: `src/hooks/useCart.test.ts`

**Interfaces:**
- Consumes: `Product` từ `@/lib/api`.
- Produces: `useCart()` trả `{ lines, count, total, add(product, qty), remove(id), setQty(id, qty), clear() }`
  với `CartLine = { id, name, price, imageKey, qty }`. Task 4/6/7 dùng đúng tên này.

Quy tắc: `add` cộng dồn qty, bỏ qua qty <= 0. `setQty` với qty <= 0 thì xoá dòng.
JSON hỏng trong localStorage → bắt đầu rỗng, không throw.

- [ ] **Step 1: Viết test đỏ**

```tsx
import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'

import type { Product } from '@/lib/api'
import { useCart } from '@/hooks/useCart'

const SHIRT: Product = {
  id: 1,
  name: 'Áo sơ mi lụa trắng',
  description: null,
  price: 890000,
  imageKey: 'mock/ao.jpg',
  createdAt: '2026-10-04T00:00:00Z',
  updatedAt: '2026-10-04T00:00:00Z',
}

beforeEach(() => {
  window.localStorage.clear()
})

describe('useCart', () => {
  it('starts empty and adds a line', () => {
    const { result } = renderHook(() => useCart())

    expect(result.current.count).toBe(0)
    expect(result.current.total).toBe(0)

    act(() => {
      result.current.add(SHIRT, 2)
    })

    expect(result.current.count).toBe(2)
    expect(result.current.total).toBe(1780000)
  })

  it('accumulates quantity and drops lines at zero', () => {
    const { result } = renderHook(() => useCart())

    act(() => {
      result.current.add(SHIRT, 1)
    })
    act(() => {
      result.current.add(SHIRT, 1)
    })
    expect(result.current.count).toBe(2)

    act(() => {
      result.current.setQty(1, 0)
    })
    expect(result.current.lines).toHaveLength(0)
  })

  it('persists across mounts', () => {
    const first = renderHook(() => useCart())
    act(() => {
      first.result.current.add(SHIRT, 1)
    })
    first.unmount()

    const second = renderHook(() => useCart())
    expect(second.result.current.count).toBe(1)
  })

  it('recovers from corrupt storage', () => {
    window.localStorage.setItem('fashionstore:cart', 'khong-phai-json')

    const { result } = renderHook(() => useCart())

    expect(result.current.lines).toHaveLength(0)
  })
})
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
pnpm vitest run src/hooks/useCart.test.ts 2>&1 | tail -5
```

Expected: FAIL vì hook chưa tồn tại.

- [ ] **Step 3: Viết implementation tối thiểu**

```ts
import { useCallback, useEffect, useState } from 'react'

import type { Product } from '@/lib/api'

export interface CartLine {
  id: number
  name: string
  price: number
  imageKey: string | null
  qty: number
}

const STORAGE_KEY = 'fashionstore:cart'

function loadCart(): CartLine[] {
  const raw = window.localStorage.getItem(STORAGE_KEY)
  if (raw === null) {
    return []
  }
  try {
    const parsed: unknown = JSON.parse(raw)
    if (Array.isArray(parsed) === false) {
      return []
    }
    return (parsed as CartLine[]).filter((line) => line.qty > 0)
  } catch {
    return []
  }
}

export function useCart() {
  const [lines, setLines] = useState<CartLine[]>(() => loadCart())

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(lines))
  }, [lines])

  const add = useCallback((product: Product, qty: number) => {
    if (qty <= 0) {
      return
    }
    setLines((current) => {
      const found = current.find((line) => line.id === product.id)
      if (found === undefined) {
        return [
          ...current,
          {
            id: product.id,
            name: product.name,
            price: product.price,
            imageKey: product.imageKey,
            qty,
          },
        ]
      }
      return current.map((line) => {
        if (line.id === product.id) {
          return { ...line, qty: line.qty + qty }
        }
        return line
      })
    })
  }, [])

  const remove = useCallback((id: number) => {
    setLines((current) => current.filter((line) => line.id !== id))
  }, [])

  const setQty = useCallback((id: number, qty: number) => {
    if (qty <= 0) {
      setLines((current) => current.filter((line) => line.id !== id))
      return
    }
    setLines((current) =>
      current.map((line) => {
        if (line.id === id) {
          return { ...line, qty }
        }
        return line
      }),
    )
  }, [])

  const clear = useCallback(() => {
    setLines([])
  }, [])

  let count = 0
  let total = 0
  for (const line of lines) {
    count += line.qty
    total += line.price * line.qty
  }

  return { lines, count, total, add, remove, setQty, clear }
}
```

- [ ] **Step 4: Chạy test + lint**

```bash
pnpm vitest run src/hooks/useCart.test.ts 2>&1 | tail -4
pnpm lint 2>&1 | tail -2
```

Expected: pass, 0 error.

- [ ] **Step 5: Commit**

```bash
git add src/hooks/useCart.ts src/hooks/useCart.test.ts
git commit -m "feat: useCart gio hang localStorage (guest duoc, chua tru ton kho)"
```

---

### Task 4: Routes + layouts storefront/admin

**Files:**
- Create: `src/components/storefront/PromoBar.tsx`, `src/components/storefront/StorefrontHeader.tsx`,
  `src/components/storefront/StorefrontFooter.tsx`, `src/components/storefront/StorefrontLayout.tsx`,
  `src/components/AdminLayout.tsx`
- Modify: `src/routes.tsx`, `src/pages/ComingSoon.tsx`
- Test: assert qua `src/pages/StorefrontHome.test.tsx` (Task 5) render trong layout thật;
  riêng redirect admin test ở đây bằng test tạm? Không — viết test route trong Task 4 luôn:
  tạo `src/routes.test.tsx`, xong giữ lại.

**Interfaces:**
- Consumes: `RequireAuth`, `useCart` (`{ count }`), `useSession` + `signOut`.
- Produces: `StorefrontLayout` (Outlet + PromoBar + Header + Footer + CartDrawer ở Task 7
  — Task 4 để chỗ cắm `<Outlet />`, drawer cắm sau), route `/admin/*` protected.

Header hiện badge số lượng từ `useCart().count`, nút giỏ link tới `/cart`.
Nav anchor: Áo (`/?cat=ao`), Váy & Đầm, Áo khoác, Phụ kiện — Home đọc `useSearchParams` (Task 5).

- [ ] **Step 1: Viết test route đỏ**

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import AppRoutes from '@/routes'

vi.mock('@/lib/auth', () => ({
  useSession: vi.fn(),
  signOut: vi.fn(),
}))

vi.mock('@/lib/api', () => ({
  listProducts: vi.fn().mockResolvedValue([]),
  getProduct: vi.fn(),
}))

import { useSession } from '@/lib/auth'

const sessionMock = vi.mocked(useSession)

function renderAt(path: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('routes', () => {
  it('shows the storefront to guests', async () => {
    sessionMock.mockReturnValue({ session: null, loading: false })
    renderAt('/')

    expect(await screen.findByText('Miễn phí vận chuyển đơn từ 500K')).toBeInTheDocument()
  })

  it('redirects guests away from /admin to /login', async () => {
    sessionMock.mockReturnValue({ session: null, loading: false })
    renderAt('/admin')

    expect(await screen.findByText('Đăng nhập')).toBeInTheDocument()
  })
})
```

Lưu ý: text nút login trong `Login.tsx` phải khớp "Đăng nhập" — kiểm tra file trước khi chạy,
sửa expectation theo thực tế, không sửa component cho vừa test.

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
pnpm vitest run src/routes.test.tsx 2>&1 | tail -5
```

Expected: FAIL (route/layout chưa tồn tại).

- [ ] **Step 3: Viết routes + layouts**

`src/routes.tsx` mới:

```tsx
import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router'

import AdminLayout from '@/components/AdminLayout'
import RequireAuth from '@/components/RequireAuth'
import StorefrontLayout from '@/components/storefront/StorefrontLayout'

const Login = lazy(() => import('@/pages/Login'))
const StorefrontHome = lazy(() => import('@/pages/StorefrontHome'))
const ProductDetail = lazy(() => import('@/pages/ProductDetail'))
const CartPage = lazy(() => import('@/pages/CartPage'))
const ProductList = lazy(() => import('@/pages/ProductList'))
const ProductForm = lazy(() => import('@/pages/ProductForm'))
const ComingSoon = lazy(() => import('@/pages/ComingSoon'))
const NotFound = lazy(() => import('@/pages/NotFound'))

function PageLoading() {
  return (
    <div className="flex min-h-svh items-center justify-center text-sm text-muted-foreground">
      Đang tải…
    </div>
  )
}

export default function AppRoutes() {
  return (
    <Suspense fallback={<PageLoading />}>
      <Routes>
        <Route path="/login" element={<Login />} />

        <Route element={<StorefrontLayout />}>
          <Route path="/" element={<StorefrontHome />} />
          <Route path="/products/:id" element={<ProductDetail />} />
          <Route path="/cart" element={<CartPage />} />
          <Route path="/orders" element={<ComingSoon title="Đơn hàng" />} />
          <Route path="/account" element={<ComingSoon title="Tài khoản" />} />
        </Route>

        <Route
          element={
            <RequireAuth>
              <AdminLayout />
            </RequireAuth>
          }
        >
          <Route path="/admin" element={<ProductList />} />
          <Route path="/admin/products/new" element={<ProductForm />} />
          <Route path="/admin/products/:id/edit" element={<ProductForm />} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  )
}
```

`AdminLayout`: tách từ `Layout.tsx` hiện tại (giữ nguyên header + nút Đăng xuất), đổi link logo về `/admin`.
`StorefrontLayout`: `PromoBar` + `StorefrontHeader` + `<main><Outlet /></main>` + `StorefrontFooter`.
`ComingSoon`: link về `/` với text "Về trang chủ".

- [ ] **Step 4: Chạy test + lint + build**

```bash
pnpm vitest run src/routes.test.tsx 2>&1 | tail -4
pnpm lint 2>&1 | tail -2
pnpm build 2>&1 | tail -3
```

Expected: pass, 0 error, build xanh.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: tach StorefrontLayout public va AdminLayout protected (/admin/*)"
```

---

### Task 5: StorefrontHome — hero, tiles, carousel, lưới lọc/sắp xếp

**Files:**
- Create: `src/components/storefront/HomeSections.tsx`, `src/pages/StorefrontHome.tsx`,
  `src/pages/StorefrontHome.test.tsx`
- Modify: none (đọc `?cat=` qua `useSearchParams`)
- Test: `src/pages/StorefrontHome.test.tsx`

**Interfaces:**
- Consumes: `useProducts`, `categoryOf`/`CATEGORIES`/`isNew`/`isBestseller`, `formatVnd`,
  `resolveImageUrl`, `Button`/`Input`/`Select`/`Badge` shadcn.
- Produces: page `/` hoàn chỉnh. Wishlist localStorage key `fashionstore:wishlist` (mảng id).

Toolbar: search input (lọc tên), pills danh mục (Tất cả + CATEGORIES), select sắp xếp
(`moi-nhat` | `gia-tang` | `gia-giam`), input giá trần (number, rỗng = không lọc).
Đếm "N sản phẩm" kiểu uniqlo. Rỗng → empty-state "Không tìm thấy sản phẩm phù hợp".

- [ ] **Step 1: Viết test đỏ (render + lọc + sắp xếp)**

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import { listProducts } from '@/lib/api'
import type { Product } from '@/lib/api'
import StorefrontHome from '@/pages/StorefrontHome'

vi.mock('@/lib/api', () => ({
  listProducts: vi.fn(),
}))

const listMock = vi.mocked(listProducts)

const SAMPLE: Product[] = [
  {
    id: 1,
    name: 'Áo sơ mi lụa trắng',
    description: null,
    price: 890000,
    imageKey: 'mock/ao.jpg',
    createdAt: '2026-10-04T00:00:00Z',
    updatedAt: '2026-10-04T00:00:00Z',
  },
  {
    id: 2,
    name: 'Váy midi đen',
    description: null,
    price: 1250000,
    imageKey: 'mock/vay.jpg',
    createdAt: '2026-10-04T00:00:00Z',
    updatedAt: '2026-10-04T00:00:00Z',
  },
]

function renderHome() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/']}>
        <StorefrontHome />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('StorefrontHome', () => {
  it('renders hero, product grid and footer commitments', async () => {
    listMock.mockResolvedValue(SAMPLE)
    renderHome()

    expect(await screen.findByText('Áo sơ mi lụa trắng')).toBeInTheDocument()
    expect(screen.getByText('2 sản phẩm')).toBeInTheDocument()
  })

  it('filters by search text', async () => {
    const user = userEvent.setup()
    listMock.mockResolvedValue(SAMPLE)
    renderHome()

    await user.type(await screen.findByPlaceholderText('Tìm sản phẩm…'), 'váy')

    expect(screen.queryByText('Áo sơ mi lụa trắng')).not.toBeInTheDocument()
    expect(screen.getByText('Váy midi đen')).toBeInTheDocument()
  })

  it('links each card to its PDP', async () => {
    listMock.mockResolvedValue(SAMPLE)
    renderHome()

    const card = await screen.findByRole('link', { name: /Áo sơ mi lụa trắng/ })
    expect(card).toHaveAttribute('href', '/products/1')
  })
})
```

Card là link bọc ảnh + tên (giống PLP adidas) nên role link chứa tên — đặt `aria-label` tên SP
cho link để test ổn định, wishlist là button riêng (stopPropagation không cần vì button nằm ngoài link).

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
pnpm vitest run src/pages/StorefrontHome.test.tsx 2>&1 | tail -5
```

Expected: FAIL.

- [ ] **Step 3: Viết implementation** (HomeSections + StorefrontHome theo spec mục 4.2;
  carousel scroll-snap `overflow-x-auto` + 2 nút scrollBy; badge "Mới"/"Bán chạy" từ catalog;
  ảnh `loading="lazy"`, `width={800} height={800}`).

- [ ] **Step 4: Chạy test + lint + build**

```bash
pnpm test 2>&1 | tail -3
pnpm lint 2>&1 | tail -2
pnpm build 2>&1 | tail -3
```

Expected: toàn suite xanh (không vỡ test admin cũ — nếu vỡ vì đổi link `/products/new`
sang `/admin/products/new` thì sửa test admin ở Task 8, ghi rõ).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: StorefrontHome hero + tiles + carousel + luoi loc/sap xep"
```

---

### Task 6: ProductDetail (PDP)

**Files:**
- Create: `src/pages/ProductDetail.tsx`, `src/pages/ProductDetail.test.tsx`
- Modify: none
- Test: `src/pages/ProductDetail.test.tsx`

**Interfaces:**
- Consumes: `useProduct(id)`, `useProducts` (related), `useCart` (`{ add }`),
  `categoryOf`, `formatVnd`, `resolveImageUrl`, shadcn `Accordion`/`Button`, `NotFound`, `toast`.
- Produces: page `/products/:id`. Thêm giỏ → `toast.success('Đã thêm vào giỏ')`.

404: `query.error instanceof ApiError && query.error.status === 404` → render `<NotFound />`.
Lỗi khác → error state + nút "Về trang chủ". Related: cùng category, trừ self, lấy 4.

- [ ] **Step 1: Viết test đỏ**

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { Toaster } from 'sonner'
import { describe, expect, it, vi } from 'vitest'

import { ApiError, getProduct, listProducts } from '@/lib/api'
import ProductDetail from '@/pages/ProductDetail'

vi.mock('@/lib/api', () => ({
  ApiError: class ApiError extends Error {
    readonly status: number
    constructor(status: number, message: string) {
      super(message)
      this.status = status
    }
  },
  getProduct: vi.fn(),
  listProducts: vi.fn(),
}))

const getMock = vi.mocked(getProduct)
const listMock = vi.mocked(listProducts)

const SHIRT = {
  id: 1,
  name: 'Áo sơ mi lụa trắng',
  description: 'Lụa tơ tằm.',
  price: 890000,
  imageKey: 'mock/ao.jpg',
  createdAt: '2026-10-04T00:00:00Z',
  updatedAt: '2026-10-04T00:00:00Z',
}

function renderDetail(id: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/products/${id}`]}>
        <Toaster />
        <Routes>
          <Route path="/products/:id" element={<ProductDetail />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ProductDetail', () => {
  it('renders buy box and adds to cart with a toast', async () => {
    const user = userEvent.setup()
    getMock.mockResolvedValue(SHIRT)
    listMock.mockResolvedValue([])
    renderDetail('1')

    expect(await screen.findByText('890.000 ₫')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Thêm vào giỏ' }))

    expect(await screen.findByText('Đã thêm vào giỏ')).toBeInTheDocument()
  })

  it('shows not-found for unknown ids', async () => {
    getMock.mockRejectedValue(new ApiError(404, 'Không tìm thấy sản phẩm'))
    listMock.mockResolvedValue([])
    renderDetail('999')

    expect(await screen.findByText('Không tìm thấy trang')).toBeInTheDocument()
  })
})
```

Text 404 phải khớp `NotFound.tsx` thực tế — đọc file trước khi chạy, sửa expectation theo.

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
pnpm vitest run src/pages/ProductDetail.test.tsx 2>&1 | tail -5
```

Expected: FAIL.

- [ ] **Step 3: Viết implementation** (theo spec mục 4.3: breadcrumb, ảnh trái, buy-box phải,
  số lượng +/-, accordion, related carousel tái dùng card link như Home).

- [ ] **Step 4: Chạy test + lint + build**

```bash
pnpm test 2>&1 | tail -3
pnpm lint 2>&1 | tail -2
pnpm build 2>&1 | tail -3
```

Expected: xanh toàn suite.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: PDP buy-box + so luong + them gio + related"
```

---

### Task 7: CartDrawer + CartPage

**Files:**
- Create: `src/components/storefront/CartDrawer.tsx`, `src/pages/CartPage.tsx`, `src/pages/CartPage.test.tsx`
- Modify: `src/components/storefront/StorefrontLayout.tsx` (cắm drawer), `src/components/storefront/StorefrontHeader.tsx` (nút giỏ mở drawer thay vì chỉ link)
- Test: `src/pages/CartPage.test.tsx`

**Interfaces:**
- Consumes: `useCart` (`{ lines, total, remove, setQty, clear }`), `formatVnd`,
  `resolveImageUrl`, shadcn `Sheet`, `Button`, `toast`.
- Produces: drawer + trang `/cart`. Nút "Đặt hàng" → toast "Thanh toán ra mắt ở iteration sau".

Drawer state: `useState` trong `StorefrontLayout`, truyền `open`/`onOpenChange` xuống drawer
và callback mở cho header. Không dùng context hay event bus.

- [ ] **Step 1: Viết test đỏ (CartPage)**

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { Toaster } from 'sonner'
import { describe, expect, it } from 'vitest'

import CartPage from '@/pages/CartPage'

function seedCart() {
  window.localStorage.setItem(
    'fashionstore:cart',
    JSON.stringify([
      { id: 1, name: 'Áo sơ mi lụa trắng', price: 890000, imageKey: 'mock/ao.jpg', qty: 2 },
    ]),
  )
}

describe('CartPage', () => {
  it('renders lines, lets quantity change and shows the total', async () => {
    const user = userEvent.setup()
    seedCart()
    render(
      <MemoryRouter>
        <CartPage />
      </MemoryRouter>,
    )

    expect(await screen.findByText('Áo sơ mi lụa trắng')).toBeInTheDocument()
    expect(screen.getByText('1.780.000 ₫')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Tăng số lượng' }))

    expect(await screen.findByText('2.670.000 ₫')).toBeInTheDocument()
  })

  it('shows an empty state linking home', async () => {
    render(
      <MemoryRouter>
        <CartPage />
      </MemoryRouter>,
    )

    const link = await screen.findByRole('link', { name: 'Tiếp tục mua sắm' })
    expect(link).toHaveAttribute('href', '/')
  })

  it('toasts that checkout comes later', async () => {
    const user = userEvent.setup()
    seedCart()
    render(
      <MemoryRouter>
        <Toaster />
        <CartPage />
      </MemoryRouter>,
    )

    await user.click(await screen.findByRole('button', { name: 'Đặt hàng' }))

    expect(await screen.findByText('Thanh toán ra mắt ở iteration sau')).toBeInTheDocument()
  })
})
```

Nhớ `window.localStorage.clear()` trong `beforeEach`/`afterEach` để test không rò rỉ giỏ.

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
pnpm vitest run src/pages/CartPage.test.tsx 2>&1 | tail -5
```

Expected: FAIL.

- [ ] **Step 3: Viết implementation** (CartPage + CartDrawer dùng chung row component nội bộ mỗi file;
  nút +/- có `aria-label` "Tăng số lượng"/"Giảm số lượng"; xoá có `aria-label` "Xoá …").

- [ ] **Step 4: Chạy test + lint + build**

```bash
pnpm test 2>&1 | tail -3
pnpm lint 2>&1 | tail -2
pnpm build 2>&1 | tail -3
```

Expected: xanh.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: CartDrawer + CartPage (localStorage, checkout de sau)"
```

---

### Task 8: Sửa test admin sau dời route + verify tổng + soi trình duyệt

**Files:**
- Modify: `src/pages/ProductList.test.tsx` (link `/products/new` → `/admin/products/new`,
  `/products/:id/edit` → `/admin/products/:id/edit`), `src/pages/ProductForm.test.tsx` tương tự,
  `src/pages/ProductForm.tsx` + `src/pages/ProductList.tsx` nếu còn link/navigate cũ,
  `src/pages/Login.tsx` nếu redirect mặc định về `/` (giờ là storefront — giữ nguyên, khách login
  xong ra home shop là hợp lý; chỉ sửa nếu test ghi khác).
- Test: toàn suite.

**Interfaces:**
- Consumes: toàn bộ task trước.
- Produces: suite xanh, lint sạch, build xanh, screenshots 4 breakpoint.

- [ ] **Step 1: Sửa link/navigate admin còn sót**

```bash
grep -rn "products/new\|products/.*edit\|navigate('/')" src --include="*.tsx" | grep -v test
```

Sửa từng chỗ sang `/admin/*` (pages admin), giữ `/` cho link về shop.

- [ ] **Step 2: Sửa test admin theo route mới, chạy toàn suite**

```bash
pnpm test 2>&1 | tail -3
pnpm lint 2>&1 | tail -2
pnpm build 2>&1 | tail -3
```

Expected: test pass hết, lint 0 error, build xanh.

- [ ] **Step 3: Soi bằng trình duyệt của user (chỉ localhost:3000, không đi đâu khác)**

Dùng chromium-devtools: resize 375/768/1024/1440, chụp `/`, `/products/1`, `/cart`
(mock mode, không cần login cho 3 trang này). Fix lỗi tràn/tràn chữ giống Task 9
(line-clamp, whitespace-nowrap). Login/logout luồng admin test tay một lần
(signup GoTrue :9999 → vào `/admin` được, guest vào `/admin` bị đá về login).

- [ ] **Step 4: Commit + ghi ledger**

```bash
git add -A && git commit -m "feat: storefront public hoan chinh (home/PDP/cart) + admin doi /admin/*"
```

Ghi một dòng vào `.superpowers/sdd/2026-10-04-frontend-iteration1-plan/progress.md`
(repo chính): kết quả test/lint/build + breakpoints đã soi.

## Self-Review

**1. Spec coverage:** Route (Task 4) ✓. Header/footer/promo (Task 4) ✓. Home 6 khối (Task 5) ✓.
PDP buy-box/accordion/related (Task 6) ✓. Drawer + CartPage + toast checkout (Task 7) ✓.
useCart localStorage (Task 3) ✓. catalog tạm + comment thay thế (Task 2) ✓.
Test/lint/build + 4 breakpoint (Task 8) ✓. Wishlist localStorage — nằm trong Task 5 (tim trên card).
Rating số — spec đã chốt KHÔNG hiện, chỉ khung accordion text. Không sót.

**2. Placeholder scan:** Mọi step có lệnh/code cụ thể. Không có TBD/TODO/"tương tự".
Chỗ duy nhất mở: text `NotFound`/`Login` — đã dặn đọc file trước khi chạy, sửa expectation
theo thực tế (đúng chiều, không sửa component cho vừa test).

**3. Type consistency:** `CartLine { id, name, price, imageKey, qty }` dùng xuyên suốt
Task 3/6/7. `useCart()` trả `{ lines, count, total, add, remove, setQty, clear }` — trùng tên
mọi task. `categoryOf/isNew/isBestseller/CATEGORIES/categoryLabel` trùng Task 2/5/6.
`listProducts` mock resolve `Product[]` — khớp `useProducts`.

**4. Review Focus:** Cả 5 dòng đã có test pin: round-trip localStorage (Task 3 step 1, test 3);
PDP 404 (Task 6 step 1, test 2); JSON hỏng (Task 3 step 1, test 4); public vs `/admin`
redirect (Task 4 step 1); lọc rỗng (Task 5 empty-state — test hiện có cover lọc search rỗng,
giá trần rỗng suy cùng code path `filtered.length === 0`).
