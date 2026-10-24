# Thống kê & Báo cáo dành cho Shop (2.6) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Shop xem tổng quan hiệu suất kinh doanh, thống kê doanh thu chi tiết, phân tích sản phẩm và khách hàng trên Seller Center dashboard.

**Architecture:** 
- **Backend Domain & Package:** Theo `docs/structure.md`, tính năng thuộc domain `features/shop`:
  - Controller: `features/shop/controller/SellerStatsController.java` (`@RequestMapping("/api/seller/shops/{shopId}/stats")`), bảo vệ bằng `ShopAccessChecker.requireShopRole(userId, shopId, OWNER, STAFF)` và annotation `@SecurityRequirement(name = "bearerAuth")`.
  - Service: `features/shop/ShopStatsService.java` điều phối số liệu tổng hợp hôm nay/kỳ trước, tính toán conversion rate, tổng hợp cảnh báo vận hành và sinh dữ liệu xuất báo cáo CSV/Excel.
  - Repository: `features/shop/ShopStatsRepository.java` dùng `JdbcClient` thực thi native SQL read-only tổng hợp xuyên các bảng (`orders`, `order_item`, `product`, `user_profile`, `shop_follower`, `shop_visit_log`), tuân thủ ngoại lệ join thống kê trong `docs/structure.md`. Repository `ShopVisitLogRepository` quản lý nhật ký truy cập shop.
  - Phân định với domain `order`: `features/order/RevenueService.java` và `SellerRevenueController.java` tiếp tục quản lý số dư ví, giải ngân (settlements) và rút tiền (withdrawals). `ShopStatsService` tập trung toàn bộ báo cáo phân tích hiệu suất kinh doanh (doanh thu đa chiều, sản phẩm, khách hàng, tỷ lệ chuyển đổi).
  - Model & Migration: Migration `V28__seller_statistics.sql` tạo bảng `shop_visit_log`, thêm cột `view_count` cho `product`, tạo index tối ưu `orders(shop_id, status, created_at)` và `shop_visit_log(shop_id, visited_at)`. Entity `ShopVisitLog` đặt tại `model/ShopVisitLog.java`.
  - DTO: Đặt phẳng trong `com.minhnbnt.fashionstore.dto.shop.*` theo quy tắc repo (không tạo thư mục lồng `stats/`). Toàn bộ DTO dùng Java `record`, tiền tệ kiểu `long` (VND), thời gian `Instant`, tỷ lệ `double`, tuân thủ NullAway (không `@Nullable` tùy tiện).
- **Frontend UI & Navigation:** Theo `fashionstore-ui/docs/structure.md`:
  - Quản lý tab: Mở rộng `SellerTabId` trong `src/types/seller/navigation.ts` và `SELLER_TAB_IDS` trong `src/lib/sellerNavigation.ts`.
  - Nâng cấp `OVERVIEW` (`SellerOverviewTab.tsx`): Thay thế hoàn toàn dữ liệu hardcoded bằng API thật qua hooks Orval (`useGetShopDashboard`, `useGetShopPerformance`, `useGetShopAlerts`), thêm mini line chart và gauge tỷ lệ vận hành.
  - Giữ nguyên tab `REVENUE` (`SellerRevenueTab.tsx`) cho quản lý ví và rút tiền, đồng thời thêm nhóm sidebar mới "THỐNG KÊ & BÁO CÁO" trong `SellerLayout.tsx` với 3 tab:
    - `STATS_REVENUE` (`SellerStatsRevenueTab.tsx`): Báo cáo doanh thu chi tiết theo ngày/tuần/tháng/năm, breakdown danh mục, phương thức thanh toán, so sánh kỳ trước, xuất file CSV/Excel.
    - `STATS_PRODUCTS` (`SellerStatsProductsTab.tsx`): Top bán chạy, tỷ lệ chuyển đổi thấp, tồn kho lâu, cảnh báo sắp hết hàng.
    - `STATS_CUSTOMERS` (`SellerStatsCustomersTab.tsx`): Khách mới vs khách quay lại, retention rate, AOV, top khách hàng, xu hướng follower.
  - Biểu đồ (Charts): Bộ component SVG thuần tại `src/components/ui/charts/` (`LineChart.tsx`, `BarChart.tsx`, `DonutChart.tsx`, `CircularProgress.tsx`), zero-dependency ngoài, responsive, tuân thủ `docs/design-system.md` (bo góc `rounded-3xl`, dark mode `dark:bg-[#11182a]`, gradient chàm đêm → xanh hoàng gia → xanh băng).
  - Client API & Rules: Orval sinh hooks trong `generated/endpoints/seller-stats/`; adapter dữ liệu an toàn tại `src/lib/sellerStatsAdapters.ts`; cấm ternary trong backend (dùng `if` + early `return`) và JSX (dùng `&&`).

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JDBC + JdbcClient, Flyway, Postgres (Testcontainers image dự án `ghcr.io/minhnbnt/postgresql:latest`), JUnit 5 + AssertJ + MockMvc; React 19, TanStack Query, orval, Tailwind v4, vitest, pnpm.

## Global Constraints

- Backend: theo `AGENTS.md`, `docs/code-guide.md`, `docs/structure.md`, `docs/data.md`. Cấm ternary; entity cấm `@Data/@Setter/@EqualsAndHashCode`; NullAway bật, hạn chế `@Nullable`; exception/message tiếng Anh; tiền VND `long`; thời gian `Instant`; DTO dùng `record`; `var` cho local.
- Migration mới bắt đầu `V28`, không sửa migration đã merge. Testcontainers giữ image `ghcr.io/minhnbnt/postgresql:latest`.
- Mỗi endpoint mới đủ springdoc: `@Tag`, `@Operation(operationId, summary, description)`, `@ApiResponse` từng mã, `@Parameter`, `@Schema` field DTO; endpoint cần auth gắn `@SecurityRequirement(name = "bearerAuth")`.
- Service domain này không gọi repository domain khác; gọi service bên đó. Riêng query join bảng cho thống kê dùng `JdbcClient` native SQL (read-only, không qua aggregate root).
- UI: chỉ `pnpm`; cấm ternary (JSX dùng `&&`); `pnpm format && pnpm lint && pnpm build` xanh trước commit; `generated/` không sửa tay; copy tiếng Việt; giá dùng `formatVnd`.
- Design: bo `rounded-3xl` card, `rounded-full` nút/input/badge. Dark mode `dark:bg-[#11182a]` panel, `dark:border-white/10` viền. Gradient chàm đêm → xanh hoàng gia → xanh băng chỉ cho CTA/accent. Inter font. Glass `.glass` class.
- Commit message conventional: `feat(stats): ...`, `test(stats): ...`, `docs(stats): ...`. Không `Co-Authored-By`.

## Review Focus

1. **Hardcode metrics** — `SellerOverviewTab` hiện hardcode doanh thu/đơn/lượt truy cập. Phải thay hoàn toàn bằng API thật. Test: Task 7.
2. **Query performance** — Thống kê chạy trên bảng `orders` + `order_item` lớn. Query phải có index, giới hạn date range hợp lý. Test: Task 2.
3. **Period comparison** — So sánh kỳ trước/sau phải đúng logic (7 ngày so 7 ngày, tháng so tháng). Test: Task 3.
4. **Chart responsive** — SVG chart phải scale đúng trên mobile (1 cột) và desktop. Test: Task 6.
5. **Export file** — Download CSV/Excel phải có content-type đúng, tên file có timestamp, dữ liệu khớp với bảng trên UI. Test: Task 8.

---

### Task 1: Migration V28 — schema hỗ trợ thống kê

**Files:**
- Create: `src/main/resources/db/migration/V28__seller_statistics.sql`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/shop/StatsSchemaMigrationTest.java`

**Interfaces:**
- Produces: bảng `shop_visit_log`; cột `product.view_count`; index thống kê trên `orders`, `shop_visit_log`.

- [ ] **Step 1: Viết migration.**

```sql
-- Lượt truy cập shop
CREATE TABLE shop_visit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id     UUID NOT NULL REFERENCES shop(id) ON DELETE CASCADE,
    visitor_id  UUID,
    visited_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_shop_visit_shop_date ON shop_visit_log (shop_id, visited_at);

-- View count cho product (conversion rate = orders / views)
ALTER TABLE product ADD COLUMN IF NOT EXISTS view_count BIGINT NOT NULL DEFAULT 0;

-- Index phục vụ thống kê doanh thu theo ngày/trạng thái
CREATE INDEX idx_orders_shop_status_created
    ON orders (shop_id, status, created_at);
```

- [ ] **Step 2: Test đỏ** `StatsSchemaMigrationTest` (`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, dùng `JdbcClient`):
  - `shopVisitLogTableExists`: `SELECT count(*) FROM information_schema.tables WHERE table_name = 'shop_visit_log'` = 1.
  - `productHasViewCountColumn`: cột `view_count` tồn tại trong `information_schema.columns`.
  - `ordersShopStatusCreatedIndexExists`: index `idx_orders_shop_status_created` tồn tại.

- [ ] **Step 3: Chạy** `./gradlew test --tests "*StatsSchemaMigrationTest"` → FAIL vì chưa có migration.

- [ ] **Step 4: Implement** migration file SQL ở trên.

- [ ] **Step 5: Chạy lại** → PASS.

- [ ] **Step 6: Commit** `feat(stats): migration V28 schema ho tro thong ke shop`.

---

### Task 2: Backend repository — native queries thống kê

**Files:**
- Create: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStatsRepository.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/model/ShopVisitLog.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopVisitLogRepository.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/shop/ShopStatsRepositoryTest.java`

**Interfaces:**
- Produces: `ShopStatsRepository` (dùng `JdbcClient` cho native queries phức tạp):
  - `TodayStatsRow todayStats(UUID shopId)`: doanh thu + đơn hàng hôm nay + hôm qua (so sánh).
  - `long countPendingOrders(UUID shopId)`: đơn PENDING.
  - `ShopPerformanceRow shopPerformance(UUID shopId)`: tỷ lệ hủy, giao đúng hạn, điểm đánh giá TB.
  - `List<RevenuePeriodRow> revenueByPeriod(UUID shopId, String period, LocalDate from, LocalDate to)`: doanh thu group by day/week/month/year.
  - `List<RevenueByCategoryRow> revenueByCategory(UUID shopId)`.
  - `List<RevenueByPaymentRow> revenueByPayment(UUID shopId)`.
  - `OrderStatusSummaryRow orderStatusSummary(UUID shopId, LocalDate from, LocalDate to)`: đơn hoàn thành/hủy/đổi trả + giá trị.
  - `List<ProductStatsRow> topSellingProducts(UUID shopId, int limit)`.
  - `List<ProductStatsRow> lowConversionProducts(UUID shopId, int limit)`: views cao, orders thấp.
  - `List<ProductStatsRow> outOfStockProducts(UUID shopId)`: stock = 0.
  - `List<ProductStatsRow> slowMovingProducts(UUID shopId, int limit)`: không bán >= 30 ngày.
  - `CustomerOverviewRow customerOverview(UUID shopId)`: khách mới/quay lại, retention, AOV.
  - `List<TopCustomerRow> topCustomers(UUID shopId, int limit)`.
  - `FollowerTrendRow followerTrend(UUID shopId)`: tổng follower + thay đổi so tháng trước.
- Produces: `ShopVisitLogRepository` (`CrudRepository`): `long countByShopIdAndVisitedAtAfter(UUID shopId, Instant after)`.
- Record rows khai trong `ShopStatsRepository` (pattern giống `OrderRepository.RevenueSummaryRow`):
  - `TodayStatsRow(long todayRevenue, long todayOrders, long yesterdayRevenue, long yesterdayOrders)`
  - `ShopPerformanceRow(double cancelRate, double onTimeDeliveryRate, double avgRating)`
  - `RevenuePeriodRow(String periodLabel, long revenue, long orders)`
  - `RevenueByCategoryRow(UUID categoryId, String categoryName, long revenue, long quantity)`
  - `RevenueByPaymentRow(String paymentMethod, long revenue, long orderCount)`
  - `OrderStatusSummaryRow(long completedOrders, long completedValue, long cancelledOrders, long cancelledValue, long returnedOrders, long returnedValue)`
  - `ProductStatsRow(UUID productId, String productName, String defaultImageKey, long totalSold, long totalRevenue, long viewCount, int currentStock)`
  - `CustomerOverviewRow(long totalCustomers, long newCustomers, long returningCustomers, long avgOrderValue)`
  - `TopCustomerRow(UUID buyerId, String buyerName, long totalSpent, long orderCount, Instant lastOrderAt)`
  - `FollowerTrendRow(long totalFollowers, long previousMonthFollowers)`

- [ ] **Step 1: Test đỏ** `ShopStatsRepositoryTest` (`@SpringBootTest` + Testcontainers):
  - Seed shop APPROVED, 5 orders (3 DELIVERED hôm nay, 1 DELIVERED hôm qua, 1 CANCELLED), 3 products, 2 buyers.
  - `todayStatsReturnsCorrectRevenue`: `todayStats.todayRevenue()` = tổng subtotal 3 đơn hôm nay.
  - `topSellingReturnsOrderedByRevenue`: sản phẩm doanh thu cao nhất đứng đầu.
  - `customerOverviewCountsCorrectly`: 2 buyers, 1 new (1 đơn), 1 returning (2 đơn).
  - `orderStatusSummaryIncludesCancelledValue`: đơn hủy đếm đúng.

- [ ] **Step 2: Chạy** `./gradlew test --tests "*ShopStatsRepositoryTest"` → FAIL.

- [ ] **Step 3: Implement** repository với `JdbcClient` native SQL.

- [ ] **Step 4: Chạy lại** → PASS.

- [ ] **Step 5: Commit** `feat(stats): repository native queries thong ke shop`.

---

### Task 3: Backend service — ShopStatsService

**Files:**
- Create: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStatsService.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/ShopDashboardResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/ShopPerformanceResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/AlertResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/RevenueDetailResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/RevenueByCategoryResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/RevenueByPaymentResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/OrderStatusSummaryResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/PeriodComparisonResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/ProductStatsResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/CustomerStatsResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/TopCustomerResponse.java`
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/shop/FollowerTrendResponse.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/shop/ShopStatsServiceTest.java`

**Interfaces:**
- Consumes: `ShopStatsRepository`, `ShopVisitLogRepository`, `ShopAccessChecker.requireShopRole(userId, shopId, OWNER, STAFF)`.
- Produces:
  - `getDashboard(UUID userId, UUID shopId): ShopDashboardResponse`
  - `getPerformance(UUID userId, UUID shopId): ShopPerformanceResponse`
  - `getAlerts(UUID userId, UUID shopId): List<AlertResponse>`
  - `getRevenueDetail(UUID userId, UUID shopId, String period, LocalDate from, LocalDate to): RevenueDetailResponse`
  - `getRevenueByCategory(UUID userId, UUID shopId): List<RevenueByCategoryResponse>`
  - `getRevenueByPayment(UUID userId, UUID shopId): List<RevenueByPaymentResponse>`
  - `getOrderStatusSummary(UUID userId, UUID shopId, LocalDate from, LocalDate to): OrderStatusSummaryResponse`
  - `getPeriodComparison(UUID userId, UUID shopId, String period): PeriodComparisonResponse`
  - `getTopSellingProducts(UUID userId, UUID shopId, int limit): List<ProductStatsResponse>`
  - `getLowConversionProducts(UUID userId, UUID shopId, int limit): List<ProductStatsResponse>`
  - `getOutOfStockProducts(UUID userId, UUID shopId): List<ProductStatsResponse>`
  - `getSlowMovingProducts(UUID userId, UUID shopId, int limit): List<ProductStatsResponse>`
  - `getCustomerOverview(UUID userId, UUID shopId): CustomerStatsResponse`
  - `getTopCustomers(UUID userId, UUID shopId, int limit): List<TopCustomerResponse>`
  - `getFollowerTrend(UUID userId, UUID shopId): FollowerTrendResponse`
- DTO records: field tiền `long`, tỷ lệ `double` (0.0–1.0), thời gian `Instant`, text `String`. Không `@Nullable` trừ khi nghiệp vụ cho phép thiếu.

```java
public record ShopDashboardResponse(long todayRevenue, long todayOrders,
    long pendingOrders, long shopVisits, long yesterdayRevenue, long yesterdayOrders) {}

public record ShopPerformanceResponse(double chatResponseRate, double cancelRate,
    double onTimeDeliveryRate, double avgRating) {}

public record AlertResponse(String type, String message, int count, String severity) {}

public record PeriodComparisonResponse(long currentRevenue, long previousRevenue,
    double changePercent, long currentOrders, long previousOrders) {}

public record ProductStatsResponse(UUID productId, String productName, String imageKey,
    long totalSold, long totalRevenue, long viewCount, double conversionRate, int currentStock) {}

public record CustomerStatsResponse(long totalCustomers, long newCustomers,
    long returningCustomers, double retentionRate, long avgOrderValue,
    long totalFollowers, long followerChange) {}

public record TopCustomerResponse(UUID buyerId, String buyerName,
    long totalSpent, int orderCount, String lastOrderAt) {}
```

- [ ] **Step 1: Test đỏ** `ShopStatsServiceTest` (Mockito, mock `ShopStatsRepository`, `ShopVisitLogRepository`, `ShopAccessChecker`):
  - `getDashboardReturnsTodayStats`: mock `todayStats` trả (100000, 5, 80000, 3) → response `todayRevenue = 100000`, `todayOrders = 5`.
  - `getDashboardChecksShopAccess`: verify `access.requireShopRole` gọi đúng `OWNER, STAFF`.
  - `getPerformanceCalculatesRatesCorrectly`: mock raw data → `cancelRate` tính đúng.
  - `getAlertsIncludesOutOfStockWhenPresent`: mock `outOfStockProducts` trả 3 items → alert type `OUT_OF_STOCK` count 3.
  - `getPeriodComparisonCalculatesChangePercent`: current 120000, previous 100000 → `changePercent` = 0.2.
  - `getTopCustomersRespectsLimit`: limit 5, mock trả 10 → kết quả 5.

- [ ] **Step 2: Chạy** `./gradlew test --tests "*ShopStatsServiceTest"` → FAIL.

- [ ] **Step 3: Implement** service + tất cả DTO records. Service method pattern:
  ```java
  public ShopDashboardResponse getDashboard(@NonNull UUID userId, @NonNull UUID shopId) {
      access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

      var stats = repository.todayStats(shopId);
      var pending = repository.countPendingOrders(shopId);
      var visits = visitLog.countByShopIdAndVisitedAtAfter(
          shopId, Instant.now().truncatedTo(ChronoUnit.DAYS));

      return new ShopDashboardResponse(
          stats.todayRevenue(), stats.todayOrders(),
          pending, visits,
          stats.yesterdayRevenue(), stats.yesterdayOrders()
      );
  }
  ```

- [ ] **Step 4: Chạy lại** → PASS.

- [ ] **Step 5: Commit** `feat(stats): service va DTO thong ke shop`.

---

### Task 4: Backend controller — REST endpoints

**Files:**
- Create: `src/main/java/com/minhnbnt/fashionstore/features/shop/controller/SellerStatsController.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/shop/controller/SellerStatsControllerTest.java`

**Interfaces:**
- Consumes: `ShopStatsService`.
- API endpoints (tất cả `GET`, base `/api/seller/shops/{shopId}/stats`):

| operationId | Path | Params | Response |
|---|---|---|---|
| `getShopDashboard` | `/api/seller/shops/{shopId}/stats/dashboard` | `shopId` | `ShopDashboardResponse` |
| `getShopPerformance` | `/api/seller/shops/{shopId}/stats/performance` | `shopId` | `ShopPerformanceResponse` |
| `getShopAlerts` | `/api/seller/shops/{shopId}/stats/alerts` | `shopId` | `List<AlertResponse>` |
| `getRevenueDetail` | `/api/seller/shops/{shopId}/stats/revenue/detail` | `shopId`, `period`, `from`, `to` | `RevenueDetailResponse` |
| `getRevenueByCategory` | `/api/seller/shops/{shopId}/stats/revenue/by-category` | `shopId` | `List<RevenueByCategoryResponse>` |
| `getRevenueByPayment` | `/api/seller/shops/{shopId}/stats/revenue/by-payment` | `shopId` | `List<RevenueByPaymentResponse>` |
| `getOrderStatusSummary` | `/api/seller/shops/{shopId}/stats/orders/summary` | `shopId`, `from`, `to` | `OrderStatusSummaryResponse` |
| `getPeriodComparison` | `/api/seller/shops/{shopId}/stats/revenue/comparison` | `shopId`, `period` | `PeriodComparisonResponse` |
| `getTopSellingProducts` | `/api/seller/shops/{shopId}/stats/products/top-selling` | `shopId`, `limit` | `List<ProductStatsResponse>` |
| `getLowConversionProducts` | `/api/seller/shops/{shopId}/stats/products/low-conversion` | `shopId`, `limit` | `List<ProductStatsResponse>` |
| `getOutOfStockProducts` | `/api/seller/shops/{shopId}/stats/products/out-of-stock` | `shopId` | `List<ProductStatsResponse>` |
| `getSlowMovingProducts` | `/api/seller/shops/{shopId}/stats/products/slow-moving` | `shopId`, `limit` | `List<ProductStatsResponse>` |
| `getCustomerOverview` | `/api/seller/shops/{shopId}/stats/customers/overview` | `shopId` | `CustomerStatsResponse` |
| `getTopCustomers` | `/api/seller/shops/{shopId}/stats/customers/top` | `shopId`, `limit` | `List<TopCustomerResponse>` |
| `getFollowerTrend` | `/api/seller/shops/{shopId}/stats/customers/followers` | `shopId` | `FollowerTrendResponse` |
| `exportRevenueReport` | `/api/seller/shops/{shopId}/stats/revenue/export` | `shopId`, `format`, `from`, `to` | `byte[]` (file) |

- [ ] **Step 1: Test đỏ** `SellerStatsControllerTest` (`@WebMvcTest` + mock `ShopStatsService`):
  - `getDashboardReturns200WithValidShopId`: mock service trả response → 200 + JSON body đúng.
  - `getDashboardReturns401WithoutJwt`: không gửi JWT → 401.
  - `getRevenueDetailAcceptsPeriodParam`: `period=week` → service nhận đúng `"week"`.
  - `getTopSellingProductsDefaultsLimitTo10`: không truyền `limit` → service nhận `10`.
  - `exportRevenueReportReturnsCsvContentType`: `format=csv` → `Content-Type: text/csv`.

- [ ] **Step 2: Chạy** `./gradlew test --tests "*SellerStatsControllerTest"` → FAIL.

- [ ] **Step 3: Implement** controller. Pattern:
  ```java
  @RestController
  @RequestMapping("/api/seller/shops/{shopId}/stats")
  @RequiredArgsConstructor
  @Tag(name = "Seller - Statistics", description = "Thống kê và Báo cáo dành cho Shop")
  public class SellerStatsController {

      private final ShopStatsService stats;

      @GetMapping("/dashboard")
      @Operation(
          operationId = "getShopDashboard",
          summary = "Dashboard tổng quan shop",
          description = "Doanh thu, đơn hàng, lượt truy cập hôm nay, so sánh hôm qua."
      )
      @ApiResponse(responseCode = "200", description = "Dữ liệu dashboard")
      @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
      @ApiResponse(responseCode = "403", description = "Không phải thành viên shop")
      @SecurityRequirement(name = "bearerAuth")
      public ShopDashboardResponse dashboard(
          @AuthenticationPrincipal Jwt jwt,
          @PathVariable UUID shopId
      ) {
          var subject = Objects.requireNonNull(jwt.getSubject());
          var userId = UUID.fromString(subject);
          return stats.getDashboard(userId, shopId);
      }
  }
  ```

- [ ] **Step 4: Chạy lại** → PASS.

- [ ] **Step 5: Chạy** `./gradlew test` (full suite) → PASS.

- [ ] **Step 6: Commit** `feat(stats): controller REST endpoints thong ke shop`.

---

### Task 5: Orval codegen + frontend types + adapters

**Files:**
- Modify: `docs/openapi.json` (tải lại từ backend)
- Create: `src/types/seller/stats.ts`
- Modify: `src/types/seller/index.ts`
- Create: `src/lib/sellerStatsAdapters.ts`
- Test: `src/lib/sellerStatsAdapters.test.ts`

**Interfaces:**
- Consumes: generated hooks từ `generated/endpoints/seller-statistics/`.
- Produces: UI types + adapter functions theo pattern `sellerAdapters.ts`:
  - `DashboardData`, `PerformanceData`, `AlertData`, `RevenueChartPoint`, `RevenueCategoryData`, `ProductAnalyticsData`, `CustomerAnalyticsData`, `TopCustomerData`.
  - Adapter: `dashboardData(response): DashboardData`, `performanceData(response): PerformanceData`, v.v.

- [ ] **Step 1: Tải openapi.json mới** từ backend đang chạy vào `docs/openapi.json`.

- [ ] **Step 2: Chạy codegen** `pnpm codegen`.

- [ ] **Step 3: Tạo** `src/types/seller/stats.ts` — khai báo UI types.

- [ ] **Step 4: Thêm** `export * from './stats'` vào `src/types/seller/index.ts`.

- [ ] **Step 5: Tạo** `src/lib/sellerStatsAdapters.ts` — adapter functions.

- [ ] **Step 6: Test đỏ** `sellerStatsAdapters.test.ts` (vitest):
  - `dashboardDataCalculatesChangePercent`: yesterday 100000, today 120000 → `revenueChangePercent = 20`.
  - `performanceDataClampsBetweenZeroAndOne`: rate > 1 → clamp 1.0.
  - `alertDataFiltersZeroCount`: count 0 → bỏ khỏi list.

- [ ] **Step 7: Chạy** `pnpm vitest run sellerStatsAdapters` → FAIL.

- [ ] **Step 8: Implement** adapters.

- [ ] **Step 9: Chạy lại** → PASS.

- [ ] **Step 10: Chạy** `pnpm format; pnpm lint; pnpm build` → xanh.

- [ ] **Step 11: Commit** `feat(stats): codegen types va adapters thong ke shop`.

---

### Task 6: SVG chart components

**Files:**
- Create: `src/components/ui/charts/LineChart.tsx`
- Create: `src/components/ui/charts/BarChart.tsx`
- Create: `src/components/ui/charts/DonutChart.tsx`
- Create: `src/components/ui/charts/CircularProgress.tsx`

**Interfaces:**
- Produces: 4 SVG chart components dùng chung, không phụ thuộc thư viện ngoài:
  - `LineChart`: props `{ data: { label: string; value: number }[]; height?: number; color?: string; showDots?: boolean; comparisonData?: { label: string; value: number }[] }`.
  - `BarChart`: props `{ data: { label: string; value: number; color?: string }[]; height?: number; horizontal?: boolean }`.
  - `DonutChart`: props `{ data: { label: string; value: number; color: string }[]; size?: number }`.
  - `CircularProgress`: props `{ value: number; max?: number; size?: number; label: string; color?: string }`.
- Design: `rounded-2xl` wrapper, tooltip on hover (CSS `group-hover:visible`), responsive `width="100%"` + `viewBox`, dark mode colors. Y-axis scale dùng `niceCeiling`.

- [ ] **Step 1: Implement** `LineChart.tsx` — SVG `<polyline>` cho đường chính, dashed cho comparison. Tooltip. Responsive `viewBox`.

- [ ] **Step 2: Implement** `BarChart.tsx` — horizontal bars, gradient fill, label + giá trị.

- [ ] **Step 3: Implement** `DonutChart.tsx` — SVG `<circle>` với `stroke-dasharray` + `stroke-dashoffset`. Legend. Center text tổng.

- [ ] **Step 4: Implement** `CircularProgress.tsx` — arc SVG, gradient stroke, label + value giữa.

- [ ] **Step 5: Chạy** `pnpm format; pnpm lint; pnpm build` → xanh.

- [ ] **Step 6: Commit** `feat(stats): SVG chart components (Line, Bar, Donut, CircularProgress)`.

---

### Task 7: Nâng cấp SellerOverviewTab (2.6.1)

**Files:**
- Modify: `src/components/seller-dashboard-tab/SellerOverviewTab.tsx`
- Modify: `src/pages/ShopDashboard.tsx` (truyền `shopId` prop)

**Interfaces:**
- Consumes: generated hooks `useGetShopDashboard`, `useGetShopPerformance`, `useGetShopAlerts`.
- Consumes: `LineChart`, `CircularProgress` components.
- Produces: SellerOverviewTab hiển thị dữ liệu thật thay vì hardcode. Thêm 3 sections mới: mini line chart, alerts, performance circular gauges.

**Props thay đổi:**
```typescript
interface Props {
  shopId: string                    // MỚI
  onNavigateTab: (tabId: string) => void
  shopName: string
  pendingOrdersCount: number
  confirmedOrdersCount: number
  pendingReturnsCount: number
  lowStockCount: number
  unreadChatCount: number           // MỚI
}
```

- [ ] **Step 1: Thêm** `shopId` và `unreadChatCount` prop, truyền từ `ShopDashboard.tsx`.

- [ ] **Step 2: Gọi API** `useGetShopDashboard({ shopId })`, `useGetShopPerformance({ shopId })`, `useGetShopAlerts({ shopId })`. Thay hardcode `metrics` array bằng dữ liệu thật qua `dashboardData(...)` adapter.

- [ ] **Step 3: Thêm section** "Biểu đồ doanh thu 7 ngày" — `LineChart` component, data từ `useByDay`.

- [ ] **Step 4: Thêm section** "Cảnh báo" — render `AlertData[]`. Card warning với icon `WarningCircle`, severity amber/red.

- [ ] **Step 5: Thêm section** "Hiệu suất Shop" — 4 `CircularProgress` gauges: phản hồi chat, tỷ lệ hủy, giao đúng hạn, đánh giá TB.

- [ ] **Step 6: Thêm** "Trả lời chat" vào todo list, count = `unreadChatCount`.

- [ ] **Step 7: Chạy** `pnpm format; pnpm lint; pnpm build` → xanh.

- [ ] **Step 8: Commit** `feat(stats): nang cap SellerOverviewTab dung API that`.

---

### Task 8: SellerStatsRevenueTab (2.6.2)

**Files:**
- Create: `src/components/seller-dashboard-tab/SellerStatsRevenueTab.tsx`

**Interfaces:**
- Consumes: generated hooks `useGetRevenueDetail`, `useGetRevenueByCategory`, `useGetRevenueByPayment`, `useGetOrderStatusSummary`, `useGetPeriodComparison`, `useExportRevenueReport`.
- Consumes: `LineChart`, `BarChart`, `DonutChart` components.
- Props: `{ shopId: string }`.

**UI Sections:**
1. Header + period selector (ngày/tuần/tháng/năm) `rounded-full` pill buttons + date range inputs + export dropdown (CSV/Excel).
2. KPI cards (`rounded-3xl`) — tổng doanh thu, đơn hoàn thành, đơn hủy, lợi nhuận ước tính — mỗi card hiện `changePercent` so kỳ trước (emerald ↑ / red ↓).
3. `LineChart` doanh thu theo thời gian, overlay đường dashed kỳ trước.
4. `DonutChart` doanh thu theo danh mục (trái) + bảng sản phẩm chi tiết (phải). Grid `lg:grid-cols-3`, chart `lg:col-span-1`, table `lg:col-span-2`.
5. `BarChart` horizontal doanh thu theo phương thức thanh toán.
6. Bảng tổng hợp đơn hàng: hoàn thành/hủy/đổi trả + giá trị. `formatVnd` cho mọi số tiền.
7. Lợi nhuận ước tính: Doanh thu - Phí sàn - Phí ship.

- [ ] **Step 1: Implement** component layout (responsive: 1 cột mobile, grid `lg:` desktop).
- [ ] **Step 2: Period selector** — state `period`, `from`/`to` dates. Buttons `rounded-full`.
- [ ] **Step 3: KPI cards** — query `useGetPeriodComparison`.
- [ ] **Step 4: Charts** — `LineChart` + `DonutChart` + `BarChart`.
- [ ] **Step 5: Export** — button gọi API, trigger download qua `URL.createObjectURL` + `<a>`.
- [ ] **Step 6: Chạy** `pnpm format; pnpm lint; pnpm build` → xanh.
- [ ] **Step 7: Commit** `feat(stats): tab thong ke doanh thu chi tiet`.

---

### Task 9: SellerStatsProductsTab (2.6.3)

**Files:**
- Create: `src/components/seller-dashboard-tab/SellerStatsProductsTab.tsx`

**Interfaces:**
- Consumes: generated hooks `useGetTopSellingProducts`, `useGetLowConversionProducts`, `useGetOutOfStockProducts`, `useGetSlowMovingProducts`.
- Props: `{ shopId: string }`.

**UI Sections:**
1. Top sản phẩm bán chạy — `BarChart` horizontal + table ranked.
2. Sản phẩm xem nhiều mua ít — table: tên, views, orders, conversion %, action link "Tối ưu".
3. Sản phẩm hết hàng thường xuyên — cards cảnh báo amber, link → tab INVENTORY.
4. Sản phẩm tồn kho lâu — table: tên, stock, ngày tồn, action "Giảm giá" / "Ẩn".
5. VTON placeholder — card `rounded-3xl` với icon `HourglassSimple`, text "Sắp ra mắt". Hệ thống chưa có bảng tracking VTON events.

- [ ] **Step 1: Implement** layout + top selling section.
- [ ] **Step 2: Implement** low conversion section (conversion = orders/views × 100).
- [ ] **Step 3: Implement** out of stock + slow moving sections.
- [ ] **Step 4: Placeholder** VTON sections.
- [ ] **Step 5: Chạy** `pnpm format; pnpm lint; pnpm build` → xanh.
- [ ] **Step 6: Commit** `feat(stats): tab phan tich san pham`.

---

### Task 10: SellerStatsCustomersTab (2.6.4)

**Files:**
- Create: `src/components/seller-dashboard-tab/SellerStatsCustomersTab.tsx`

**Interfaces:**
- Consumes: generated hooks `useGetCustomerOverview`, `useGetTopCustomers`, `useGetFollowerTrend`.
- Props: `{ shopId: string }`.

**UI Sections:**
1. KPI cards `rounded-3xl`: khách mới, khách quay lại, retention rate (`CircularProgress`), AOV (`formatVnd`).
2. `DonutChart` khách mới vs quay lại (trái) + `LineChart` follower trend 30 ngày (phải). Grid `lg:grid-cols-2`.
3. Top khách hàng mua nhiều nhất — table `rounded-3xl`: tên, số đơn, tổng chi tiêu, đơn gần nhất.

- [ ] **Step 1: Implement** layout + KPI cards.
- [ ] **Step 2: Implement** charts (DonutChart + LineChart).
- [ ] **Step 3: Implement** top customers table.
- [ ] **Step 4: Chạy** `pnpm format; pnpm lint; pnpm build` → xanh.
- [ ] **Step 5: Commit** `feat(stats): tab phan tich khach hang`.

---

### Task 11: Navigation wiring + documentation

**Files:**
- Modify: `src/types/seller/navigation.ts` — thêm `'STATS_REVENUE' | 'STATS_PRODUCTS' | 'STATS_CUSTOMERS'`.
- Modify: `src/lib/sellerNavigation.ts` — thêm 3 IDs vào `SELLER_TAB_IDS`.
- Modify: `src/components/seller-dashboard-tab/SellerLayout.tsx` — thêm menu group "THỐNG KÊ & BÁO CÁO" vào sidebar + breadcrumb labels cho 3 tab mới.
- Modify: `src/components/seller-dashboard-tab/index.ts` — thêm 3 lazy exports.
- Modify: `src/pages/ShopDashboard.tsx` — import + render 3 tab mới.
- Modify (backend): `docs/api-endpoints.md` — thêm Flow 16 "Seller Center: Thống kê & Báo cáo".

- [ ] **Step 1: Cập nhật** `navigation.ts` — thêm 3 tab IDs vào union type.

- [ ] **Step 2: Cập nhật** `sellerNavigation.ts` — thêm vào mảng `SELLER_TAB_IDS`.

- [ ] **Step 3: Cập nhật** `SellerLayout.tsx` — thêm menu group `THỐNG KÊ & BÁO CÁO` với icon `ChartLineUp`, `ChartBar`, `UsersThree` (từ `@phosphor-icons/react`). Thêm breadcrumb labels.

- [ ] **Step 4: Cập nhật** `index.ts` — 3 lazy exports mới.

- [ ] **Step 5: Cập nhật** `ShopDashboard.tsx` — render 3 tab.

- [ ] **Step 6: Cập nhật** `docs/api-endpoints.md` — Flow 16.

- [ ] **Step 7: Chạy** `pnpm format; pnpm lint; pnpm build` → xanh.

- [ ] **Step 8: Commit** `feat(stats): wiring navigation va documentation`.

---

## Self-Review

- **Spec coverage:** Dashboard tổng quan (Task 7), doanh thu chi tiết + export + so sánh kỳ trước (Task 8), sản phẩm bán chạy/conversion/tồn kho (Task 9), khách hàng mới/quay lại/top/follower (Task 10) (bỏ qua VTON theo yêu cầu). Đủ.
- **Không còn TBD:** mọi endpoint `operationId`, DTO record, tên method, component props đã chốt.
- **Type consistency:** `ShopDashboardResponse`, `ProductStatsResponse`, `CustomerStatsResponse` định nghĩa ở Task 3 và dùng lại ở Task 4–10.
- **AGENTS.md compliance:** cấm ternary (`if` + early `return` backend, `&&` JSX frontend); `record` cho DTO; NullAway default non-null; springdoc đủ annotation; `pnpm` only; conventional commits; TDD (test trước, code sau); Flyway migration V28 kế tiếp V27; Testcontainers image dự án; `JdbcClient` cho native queries; `var` cho local; exception/message tiếng Anh.
- **Review Focus:** 5 rủi ro đều có test/step tương ứng (Task 2, 3, 6, 7, 8).
