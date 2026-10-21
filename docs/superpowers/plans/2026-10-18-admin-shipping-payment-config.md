# Vận chuyển, Phí ship, Cổng thanh toán, Cấu hình chung — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admin cấu hình hãng vận chuyển, bảng phí, cổng thanh toán, cấu hình chung; người mua checkout với phí ship do server tính và trả COD hoặc VNPay sandbox; seller xử lý đơn thật trên UI.

**Architecture:** Backend Spring Boot 4 + Data JDBC thêm 4 feature (`location`, `settings`, `shipping`, `payment`) + mở rộng `order`/`shop`; admin controllers mỏng ở `features/admin/`. VNPay ký/verify là hàm thuần (`VnpayClient`); xử lý kết quả dùng chung cho Return + IPN. UI React 19 + orval hooks: 3 trang admin, luồng mua (Mua ngay → checkout → VNPay → kết quả → đơn của tôi), Seller Center tab Đơn hàng + Hồ sơ nối API thật.

**Tech Stack:** Java 21 toolchain, Spring Boot 4.1, Spring Data JDBC, JdbcClient, Flyway, Postgres (Testcontainers image dự án), JUnit 5 + AssertJ + MockMvc; React 19, TanStack Query, orval, Tailwind v4, vitest, pnpm.

**Spec:** `docs/superpowers/specs/2026-10-18-admin-shipping-payment-config-design.md` (đọc trước mỗi task).

**Repos / worktrees:**
- Backend: `F:\TTTN\fashion\.worktrees\fashionstore-shipping-payment` (nhánh `feature/admin-shipping-payment`). Đường dẫn dưới đây tương đối với repo này; `B = src/main/java/com/minhnbnt/fashionstore`, `T = src/test/java/com/minhnbnt/fashionstore`.
- UI: `F:\TTTN\fashion\.worktrees\fashionstore-ui-shipping-payment` (nhánh `feature/admin-shipping-payment`). Chạy `pnpm install` một lần trước Task 12.

## Global Constraints

- Luật repo backend: `AGENTS.md`, `docs/code-guide.md`, `docs/structure.md`, `docs/data.md`. Cấm ternary; entity cấm `@Data/@Setter/@Builder/@EqualsAndHashCode`; NullAway: nullable gắn `@Nullable` (JSpecify); exception/message tiếng Anh; tiền VND `long`; thời gian `Instant`; id sinh trong factory `create(...)`; entity `Persistable`.
- Mỗi endpoint đủ springdoc: `@Tag`, `@Operation(operationId, summary, description)`, `@ApiResponse` từng mã, `@Parameter`, `@Schema` field DTO; endpoint cần auth gắn `@SecurityRequirement(name = "bearerAuth")`. `operationId` đặt tường minh (UI dùng tên hook sinh từ đó).
- `id` user lấy từ JWT `sub`. Admin endpoints dưới `/api/admin/**` (đã `hasRole('ADMIN')`). Public endpoints dưới `/api/public/**` (đã `permitAll`) — **không sửa `SecurityConfig`**.
- Service domain này không gọi repository domain khác; gọi service bên đó.
- Migration bắt đầu **V20** (V11/V12 đã bị nhánh song song dùng). Không sửa migration cũ.
- Mọi chuyển trạng thái tiền/đơn khóa dòng `SELECT ... FOR UPDATE` và ghi history trong cùng transaction.
- **Test class nào sửa cấu hình toàn cục** (settings, carrier, rate, gateway) phải gắn `@TestPropertySource(properties = "test.isolation=<TênClass>")` để có Spring context + database riêng (test class chạy song song, `src/test/resources/junit-platform.properties`).
- UI: `AGENTS.md` + `docs/code-guide.md` của UI. Chỉ `pnpm`; cấm ternary (JSX dùng `&&`); `pnpm format && pnpm lint && pnpm build` xanh trước commit; `generated/` không sửa tay; copy tiếng Việt, giá dùng `formatVnd`.
- Commit message không có dòng `Co-Authored-By`.
- Hằng số dữ liệu test (từ dataset 2025): Hà Nội `01` / Phường Ba Đình `00004`; Bắc Ninh `24` / `07210` (cùng miền Bắc); Đà Nẵng `48` / `20194` (miền Trung); TP.HCM `79` / `25747` (miền Nam).
- Carrier seed id cố định: GHN `00000000-0000-0000-0000-0000000000a1`, GHTK `...00a2`, J&T `...00a3`.

## Review Focus

1. **Giá trị tiền client gửi lên** (`shippingFee` cũ, tổng tiền giả) — server phải bỏ qua hoàn toàn; test ở Task 7 gửi JSON có `shippingFee: 1` vào controller và assert phí = phí bảng giá.
2. **Query VNPay bị sửa 1 ký tự / số tiền bị đổi / gọi lặp Return rồi IPN** — không đổi trạng thái hai lần, không đánh dấu PAID khi lệch tiền; test ở Task 8.
3. **Tên có dấu / ký tự đặc biệt trong `vnp_OrderInfo`** (mã đơn + chữ) — chữ ký phải khớp cách VNPay encode (`US_ASCII`, space → `+`); `vnp_OrderInfo` chỉ dùng ASCII không dấu; test ký ở Task 6 có giá trị chứa space và `:`.
4. **Admin tắt hãng / tắt cổng trong lúc người mua đang ở trang checkout** — checkout trả `409` rõ ràng, UI hiện message thay vì trắng trang; test backend ở Task 7, xử lý lỗi UI ở Task 15.
5. **Shop cũ chưa có tỉnh, người mua chọn tỉnh không có xã / mã xã không thuộc tỉnh** — phí = liên miền; mã sai → `400`; test ở Task 1 và Task 3.

---

### Task 1: Địa giới hành chính (34 tỉnh, 3.321 xã)

**Files:**
- Create: `src/main/resources/db/migration/V20__administrative_units.sql`
- Create: `B/model/Region.java`
- Create: `B/features/location/LocationController.java`, `LocationService.java`, `LocationRepository.java`, `ResolvedLocation.java`
- Create: `B/dto/location/ProvinceResponse.java`, `WardResponse.java`
- Test: `T/features/location/LocationServiceTest.java`, `T/features/location/LocationControllerTest.java`

**Interfaces:**
- Produces: `enum Region { NORTH, CENTRAL, SOUTH }`; `record ResolvedLocation(String provinceCode, String provinceName, Region region, String wardCode, String wardName)`; `LocationService.listProvinces(): List<ProvinceResponse>`, `listWards(String provinceCode): List<WardResponse>` (404 nếu tỉnh không có), `resolve(String provinceCode, String wardCode): ResolvedLocation` (400 `"Ward does not belong to province"` / `"Unknown province"`), `regionOf(String provinceCode): Region` (400 nếu không có); `record ProvinceResponse(String code, String name, String region)`, `record WardResponse(String code, String name)`.

- [ ] **Step 1: Sinh migration.** Tải `https://provinces.open-api.vn/api/v2/?depth=2` vào scratchpad, sinh SQL bằng script node một lần (không commit script). Bảng:

```sql
CREATE TABLE province (code TEXT PRIMARY KEY, name TEXT NOT NULL,
    region TEXT NOT NULL CHECK (region IN ('NORTH', 'CENTRAL', 'SOUTH')));
CREATE TABLE ward (code TEXT PRIMARY KEY, province_code TEXT NOT NULL REFERENCES province (code), name TEXT NOT NULL);
CREATE INDEX idx_ward_province ON ward (province_code, name);
```

Mã tỉnh pad 2 chữ số (`1` → `01`), mã xã pad 5 (`4` → `00004`). Miền theo mã tỉnh: `≤ 38` NORTH (Hà Nội → Thanh Hóa), `40–68` CENTRAL (Nghệ An → Lâm Đồng), `≥ 75` SOUTH. Header file ghi nguồn URL + ngày tải 2026-10-18. Đếm sau khi chạy: 34 province, 3321 ward.

- [ ] **Step 2: Test đỏ** `LocationServiceTest` (`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`):
  - `seeds34ProvincesWithRegions`: `listProvinces()` size 34; `01` → `NORTH`, `48` → `CENTRAL`, `79` → `SOUTH`.
  - `listsWardsOfProvince`: `listWards("01")` chứa `("00004", "Phường Ba Đình")`.
  - `resolveRejectsWardOfOtherProvince`: `resolve("79", "00004")` → `ResponseStatusException` 400.
  - `resolveRejectsUnknownProvince`: `resolve("99", "00004")` → 400.
  - `LocationControllerTest` (`@WebMvcTest` + `@Import(SecurityConfig.class)` + `@MockitoBean JwtDecoder`): `GET /api/public/locations/provinces` không JWT → 200; `GET /api/public/locations/provinces/01/wards` → 200 JSON `$[0].code`.
- [ ] **Step 3: Chạy** `./gradlew test --tests "*Location*"` → FAIL (class chưa có).
- [ ] **Step 4: Implement.** `LocationRepository` dùng `JdbcClient` (read model, không entity). Controller `@RequestMapping("/api/public/locations")`, `operationId` `listProvinces`, `listWards`.
- [ ] **Step 5: Chạy lại** → PASS.
- [ ] **Step 6: Commit** `feat(location): danh mục tỉnh/xã sau sáp nhập 2025`.

---

### Task 2: Cấu hình chung + lịch sử thay đổi cấu hình

**Files:**
- Create: `src/main/resources/db/migration/V21__system_settings.sql`
- Create: `B/features/settings/SystemSettings.java`, `SystemSettingsService.java`, `SystemSettingRepository.java`, `ConfigAuditService.java`, `ConfigAuditRepository.java`, `PublicSettingsController.java`
- Create: `B/features/admin/AdminSettingsController.java`
- Create: `B/dto/settings/SystemSettingsResponse.java`, `UpdateSettingsRequest.java`, `PublicSettingsResponse.java`, `ConfigAuditResponse.java`
- Create: `B/model/ConfigAuditEntity.java` (enum)
- Test: `T/features/settings/SystemSettingsServiceTest.java`, `T/features/admin/AdminSettingsControllerTest.java`

**Interfaces:**
- Produces: `record SystemSettings(String siteName, String hotline, String supportEmail, long freeShippingThreshold, int baseItems, int paymentHoldMinutes, int autoCompleteDays, long codMinTotal, long codMaxTotal)`; `SystemSettingsService.current(): SystemSettings`, `update(UUID adminId, UpdateSettingsRequest): SystemSettingsResponse`, `publicView(): PublicSettingsResponse`; `enum ConfigAuditEntity { CARRIER, SHIPPING_RATE, PAYMENT_GATEWAY, PAYMENT_TRANSACTION, SETTING }`; `ConfigAuditService.record(ConfigAuditEntity entity, String entityKey, String action, @Nullable Object oldValue, @Nullable Object newValue, UUID changedBy)` (serialize JSON bằng Jackson mapper sẵn có của Boot), `page(@Nullable ConfigAuditEntity entity, int page, int size): PageResponse<ConfigAuditResponse>`.
- `UpdateSettingsRequest`: cùng 9 field, tất cả `@Nullable` (null = không đổi), bean validation theo bảng spec §5 (`@Size(min=1,max=100)` siteName, `@Size(max=20)` hotline, `@Email` supportEmail, `@Min(0)` tiền, `@Min(1) @Max(20)` baseItems, `@Min(5) @Max(1440)` paymentHoldMinutes, `@Min(1) @Max(30)` autoCompleteDays).

- [ ] **Step 1: Migration.**

```sql
CREATE TABLE system_setting (key TEXT PRIMARY KEY, value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_by UUID REFERENCES user_profile (id) ON DELETE SET NULL);
CREATE TABLE admin_config_audit (id UUID PRIMARY KEY,
    entity TEXT NOT NULL CHECK (entity IN ('CARRIER','SHIPPING_RATE','PAYMENT_GATEWAY','PAYMENT_TRANSACTION','SETTING')),
    entity_key TEXT NOT NULL, action TEXT NOT NULL, old_value JSONB, new_value JSONB,
    changed_by UUID REFERENCES user_profile (id) ON DELETE SET NULL, changed_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX idx_config_audit ON admin_config_audit (entity, changed_at DESC);
```

Seed 9 key đúng mặc định spec §5 (`site.name`=`nét`, `site.hotline`=`1900 0000`, `site.support_email`=`support@fashionstore.local`, `shipping.free_threshold`=`500000`, `shipping.base_items`=`2`, `order.payment_hold_minutes`=`15`, `order.auto_complete_days`=`7`, `order.cod_min_total`=`0`, `order.cod_max_total`=`10000000`).

- [ ] **Step 2: Test đỏ** `SystemSettingsServiceTest` (isolated context):
  - `currentReturnsSeededDefaults`: `current()` bằng `new SystemSettings("nét", "1900 0000", "support@fashionstore.local", 500000, 2, 15, 7, 0, 10000000)`.
  - `partialUpdateChangesOnlyGivenKeysAndAudits`: update chỉ `freeShippingThreshold=300000` → `current().freeShippingThreshold()==300000`, `siteName` giữ; `page(SETTING,0,20)` có 1 dòng `entityKey="shipping.free_threshold"`, `oldValue` chứa `500000`, `newValue` chứa `300000`.
  - `codMaxBelowMinIsRejected`: update `codMinTotal=200000, codMaxTotal=100000` → 400, không key nào đổi.
  - `codMaxZeroMeansUnlimitedIsAccepted`: `codMinTotal=200000, codMaxTotal=0` → OK.
  - `AdminSettingsControllerTest`: `GET /api/admin/settings` cần ROLE_ADMIN (jwt thường → 403); `PUT` với `baseItems: 0` → 400; `GET /api/public/settings` không JWT → 200 có `siteName`.
- [ ] **Step 3: Chạy** → FAIL.
- [ ] **Step 4: Implement.** `update` validate chéo (`codMaxTotal != 0 && codMaxTotal < codMinTotal` → 400 `"COD max total must be 0 or at least COD min total"`) trước khi ghi; chỉ ghi + audit key có giá trị khác giá trị cũ. Controller admin: `GET/PUT /api/admin/settings` (`getSystemSettings`, `updateSystemSettings`), `GET /api/admin/config-audit?entity=&page=&size=` (`listConfigAudit`). Public: `GET /api/public/settings` (`getPublicSettings`).
- [ ] **Step 5: Chạy lại** → PASS.
- [ ] **Step 6: Commit** `feat(settings): cấu hình chung + lịch sử thay đổi cấu hình`.

---

### Task 3: Hãng vận chuyển, bảng phí, công thức phí

**Files:**
- Create: `src/main/resources/db/migration/V22__shipping.sql`
- Create: `B/model/ShippingCarrier.java`, `ShippingRate.java`, `ShippingZone.java`
- Create: `B/features/shipping/service/ShippingFeeCalculator.java`, `ShippingService.java`; `B/features/shipping/repository/ShippingCarrierRepository.java`
- Create: `B/features/admin/AdminShippingController.java`
- Create: `B/dto/shipping/CarrierResponse.java`, `ShippingRateResponse.java`, `CreateCarrierRequest.java`, `UpdateCarrierRequest.java`, `ShippingRateRequest.java`, `UpdateRatesRequest.java`, `ShippingOptionResponse.java`
- Test: `T/features/shipping/ShippingFeeCalculatorTest.java`, `T/features/shipping/ShippingServiceTest.java`, `T/features/admin/AdminShippingControllerTest.java`

**Interfaces:**
- Consumes: `SystemSettingsService.current()`, `ConfigAuditService.record(...)` (Task 2); `Region` (Task 1).
- Produces:
  - `enum ShippingZone { INTRA_PROVINCE, INTRA_REGION, INTER_REGION }`.
  - `ShippingFeeCalculator` (static, thuần): `zoneOf(@Nullable String shopProvince, @Nullable Region shopRegion, String buyerProvince, Region buyerRegion): ShippingZone`; `fee(long baseFee, long extraItemFee, int itemCount, int baseItems, long subtotal, long freeThreshold): FeeResult` với `record FeeResult(long fee, long originalFee, boolean freeShipApplied)`.
  - `ShippingCarrier` aggregate root (`@MappedCollection(idColumn = "carrier_id", keyColumn = "zone")` `Map<ShippingZone, ShippingRate>` hoặc `Set<ShippingRate>`), behavior `activate()` (409 nếu thiếu vùng), `deactivate()`, `replaceRates(...)`, `update(...)`.
  - `ShippingService.listCarriers(): List<CarrierResponse>`, `createCarrier(UUID adminId, CreateCarrierRequest)`, `updateCarrier(UUID adminId, UUID id, UpdateCarrierRequest)`, `activate(UUID adminId, UUID id)`, `deactivate(UUID adminId, UUID id)`, `replaceRates(UUID adminId, UUID id, UpdateRatesRequest)` — đều trả `CarrierResponse`; `quote(@Nullable String shopProvince, String buyerProvince, int itemCount, long subtotal): List<ShippingOptionResponse>` (chỉ hãng active, theo `sort_order`); `quoteCarrier(UUID carrierId, @Nullable String shopProvince, String buyerProvince, int itemCount, long subtotal): ShippingOptionResponse` (404 không có, 409 `"Carrier is not active"`).
  - `record ShippingOptionResponse(UUID carrierId, String carrierName, @Nullable String logoKey, String zone, long fee, long originalFee, boolean freeShipApplied, int etaMinDays, int etaMaxDays)`.

- [ ] **Step 1: Migration.** `shipping_carrier(id UUID PK, code TEXT UNIQUE CHECK (code ~ '^[A-Z0-9_]+$'), name, logo_key, tracking_url_template, active BOOLEAN, sort_order INT, created_at, updated_at)`; `shipping_rate(carrier_id UUID REFERENCES shipping_carrier ON DELETE CASCADE, zone TEXT CHECK (...3 vùng), base_fee BIGINT CHECK (>=0), extra_item_fee BIGINT CHECK (>=0), eta_min_days INT, eta_max_days INT, CHECK (eta_min_days BETWEEN 1 AND 30 AND eta_max_days BETWEEN eta_min_days AND 30), PRIMARY KEY (carrier_id, zone))`. Seed 3 hãng với id cố định (Global Constraints) + bảng phí đúng bảng spec §3; `tracking_url_template` GHN `https://donhang.ghn.vn/?order_code={code}`, GHTK `https://i.ghtk.vn/{code}`, J&T `https://jtexpress.vn/vi/tracking?type=track&billcode={code}`.
- [ ] **Step 2: Test đỏ** `ShippingFeeCalculatorTest` (unit thuần):
  - zone: shop null → `INTER_REGION`; `("01",NORTH,"01",NORTH)` → `INTRA_PROVINCE`; `("01",NORTH,"24",NORTH)` → `INTRA_REGION`; `("01",NORTH,"79",SOUTH)` → `INTER_REGION`.
  - fee `(22000, 3000, items=2, base=2, subtotal=200000, free=500000)` → `(22000, 22000, false)`; items=5 → `(31000, 31000, false)`; subtotal=500000 → `(0, 22000, true)`; subtotal=499999 → `(22000, 22000, false)`; free=0, subtotal=9_000_000 → `(22000, 22000, false)`.
- [ ] **Step 3: Test đỏ** `ShippingServiceTest` (isolated context):
  - `quoteListsSeededCarriersInOrder`: `quote(null, "01", 2, 200000)` → 3 option `GHN, GHTK, J&T Express`, GHN zone `INTER_REGION` fee `38000`, eta `3–5`.
  - `deactivatedCarrierDisappearsFromQuote` + `quoteCarrier` của hãng đó → 409.
  - `cannotDeactivateLastActiveCarrier` → 409.
  - `newCarrierCannotActivateWithoutAllZones`: tạo hãng `VTP`, `activate` → 409; `replaceRates` đủ 3 vùng rồi `activate` → active.
  - `replaceRatesRejectsMissingOrDuplicateZone` → 400.
  - `createCarrierRejectsDuplicateCode` → 409.
  - mọi thay đổi ghi `ConfigAuditService` (assert `page(CARRIER,…)` / `page(SHIPPING_RATE,…)` tăng).
- [ ] **Step 4: Test đỏ** `AdminShippingControllerTest`: endpoints `GET/POST /api/admin/shipping/carriers` (`listCarriers`, `createCarrier`), `PUT /api/admin/shipping/carriers/{id}` (`updateCarrier`), `PATCH .../{id}/activate` (`activateCarrier`), `PATCH .../{id}/deactivate` (`deactivateCarrier`), `PUT .../{id}/rates` (`replaceCarrierRates`); không admin → 403; `CreateCarrierRequest.code="ghn"` → 400.
- [ ] **Step 5: Chạy** `./gradlew test --tests "*Shipping*"` → FAIL.
- [ ] **Step 6: Implement**; tạo hãng mới mặc định `active=false`. Mọi ghi khóa dòng carrier `FOR UPDATE` (query trong repository).
- [ ] **Step 7: Chạy lại** → PASS.
- [ ] **Step 8: Commit** `feat(shipping): hãng vận chuyển, bảng phí theo vùng + số món`.

---

### Task 4: Tỉnh/xã cho shop và sổ địa chỉ

**Files:**
- Create: `src/main/resources/db/migration/V23__shop_address_location.sql`
- Modify: `B/model/Shop.java`, `B/model/Address.java`, `B/dto/shop/CreateShopRequest.java`, `UpdateShopProfileRequest.java`, `ShopResponse.java`, `B/dto/address/AddressRequest.java`, `AddressResponse.java`, `B/features/shop/ShopService.java`, `B/features/address/AddressService.java`, `B/infrastructure/DemoSeeder.java`
- Test: `T/features/shop/ShopServiceTest.java`, `T/features/shop/ShopControllerTest.java`, `T/features/address/AddressServiceTest.java`

**Interfaces:**
- Consumes: `LocationService.resolve(...)` (Task 1).
- Produces: `Shop.getProvinceCode(): @Nullable String`, `getWardCode()`, `Shop.changePickupLocation(String provinceCode, String wardCode)`; `ShopService.pickupProvinceOf(UUID shopId): @Nullable String` (404 shop không có); `ShopResponse` thêm `provinceCode, provinceName, wardCode, wardName` (nullable); `CreateShopRequest` + `UpdateShopProfileRequest` thêm `provinceCode`, `wardCode` (cuối record).

- [ ] **Step 1: Migration:** `ALTER TABLE shop ADD COLUMN province_code TEXT REFERENCES province (code), ADD COLUMN ward_code TEXT REFERENCES ward (code);` tương tự cho `address`. Comment: `shop.address` từ nay là địa chỉ chi tiết.
- [ ] **Step 2: Test đỏ** trong `ShopServiceTest`: `registerStoresPickupLocation` (province `01`, ward `00004` → response `provinceName="Thành phố Hà Nội"`, `wardName="Phường Ba Đình"`); `registerRejectsWardOutsideProvince` (`79` + `00004` → 400); `updateProfileChangesPickupLocation`; `pickupProvinceOfLegacyShopIsNull` (shop tạo bằng constructor 5 tham số). `ShopControllerTest`: `POST /api/shops` thiếu `provinceCode` → 400 (`@NotBlank` trên DTO, chỉ bắt ở biên API — constructor tiện 5 tham số truyền `null` cho test service). `AddressServiceTest`: tạo địa chỉ có `provinceCode/wardCode` lưu được, sai cặp → 400.
- [ ] **Step 3: Chạy** → FAIL.
- [ ] **Step 4: Implement.** Profile update: cả hai cùng null = không đổi; chỉ một trong hai → 400 `"Province and ward must be given together"`. `DemoSeeder`: shop `net-lab` gán `01`/`00004`.
- [ ] **Step 5: Chạy** `./gradlew test` (toàn bộ, vì đổi DTO dùng chung) → PASS.
- [ ] **Step 6: Commit** `feat(shop): tỉnh/xã lấy hàng cho shop + sổ địa chỉ`.

---

### Task 5: Cấu hình cổng thanh toán

**Files:**
- Create: `src/main/resources/db/migration/V24__payment_gateway.sql`
- Create: `B/model/PaymentMethod.java`, `PaymentGateway.java`
- Create: `B/features/payment/service/PaymentGatewayService.java`, `VnpayConfig.java`; `B/features/payment/repository/PaymentGatewayRepository.java`; `B/features/payment/controller/PublicPaymentController.java`
- Create: `B/features/admin/AdminPaymentController.java`
- Create: `B/dto/payment/PaymentGatewayResponse.java`, `UpdatePaymentGatewayRequest.java`, `PaymentMethodsResponse.java`, `PaymentMethodOption.java`
- Test: `T/features/payment/PaymentGatewayServiceTest.java`, `T/features/admin/AdminPaymentControllerTest.java`

**Interfaces:**
- Consumes: `SystemSettingsService.current()`, `ConfigAuditService.record(...)`.
- Produces: `enum PaymentMethod { COD, VNPAY }`; `record VnpayConfig(String tmnCode, String hashSecret, String payUrl, String returnUrl)`; `PaymentGatewayService.list(): List<PaymentGatewayResponse>`, `update(UUID adminId, PaymentMethod code, UpdatePaymentGatewayRequest): PaymentGatewayResponse`, `publicMethods(): PaymentMethodsResponse`, `requireAvailable(PaymentMethod method, long orderTotal): void` (409 `"Payment method is disabled"` / `"Order total is outside COD limits"`), `vnpayConfig(): VnpayConfig` (409 nếu chưa cấu hình).
  - `record PaymentGatewayResponse(String code, String name, boolean enabled, @Nullable String tmnCode, boolean hashSecretSet, @Nullable String payUrl, @Nullable String returnUrl, Instant updatedAt)` — **không có field secret**.
  - `record UpdatePaymentGatewayRequest(@Nullable Boolean enabled, @Nullable String name, @Nullable String tmnCode, @Nullable String hashSecret, @Nullable String payUrl, @Nullable String returnUrl)`; `hashSecret` null/blank = giữ.
  - `record PaymentMethodsResponse(List<PaymentMethodOption> methods, long codMinTotal, long codMaxTotal)`, `record PaymentMethodOption(String code, String name)`.

- [ ] **Step 1: Migration** đúng spec §4 (`payment_gateway` cột phẳng), seed COD (`Thanh toán khi nhận hàng`, bật) + VNPay (`VNPay`, tắt, `pay_url`/`return_url` sandbox mặc định).
- [ ] **Step 2: Test đỏ** `PaymentGatewayServiceTest` (isolated):
  - `vnpayCannotBeEnabledWithoutCredentials` → 409.
  - `enableVnpayAfterSettingCredentials`: update `tmnCode="DEMO1234", hashSecret="SECRET"` rồi `enabled=true` → `list()` VNPay `enabled`, `hashSecretSet=true`; JSON serialize response không chứa `"SECRET"`.
  - `blankSecretKeepsOldSecret`: update `hashSecret=""` → `vnpayConfig().hashSecret()=="SECRET"`.
  - `cannotDisableLastEnabledGateway` → 409.
  - `codOutsideLimitsIsRejected`: settings `codMaxTotal=1000000` → `requireAvailable(COD, 1000001)` 409, `requireAvailable(COD, 1000000)` OK.
  - audit gateway ghi `hashSecret` = `"****"` (assert `newValue` không chứa `SECRET`).
  - Controller: `GET /api/admin/payment-gateways` (`listPaymentGateways`), `PUT /api/admin/payment-gateways/{code}` (`updatePaymentGateway`), `GET /api/public/payment-methods` (`listPaymentMethods`) không JWT → 200.
- [ ] **Step 3: Chạy** → FAIL. **Step 4: Implement.** **Step 5: Chạy** → PASS.
- [ ] **Step 6: Commit** `feat(payment): cấu hình cổng thanh toán COD/VNPay`.

---

### Task 6: VnpayClient — ký, verify, dựng URL

**Files:**
- Create: `B/features/payment/service/VnpayClient.java`, `VnpayPaymentRequest.java`
- Test: `T/features/payment/VnpayClientTest.java`

**Interfaces:**
- Consumes: `VnpayConfig` (Task 5).
- Produces: `@Component VnpayClient`: `paymentUrl(VnpayConfig config, VnpayPaymentRequest request): String`; `verify(String hashSecret, Map<String, String> params): boolean`; static `sign(String hashSecret, String data): String` (HMAC-SHA512 hex thường); static `canonicalQuery(Map<String, String> params): String`. `record VnpayPaymentRequest(String txnRef, long amountVnd, String orderInfo, String ipAddr, Instant createdAt, Instant expiresAt)`.

- [ ] **Step 1: Test đỏ:**
  - `canonicalQuerySortsAndEncodes`: `{"vnp_OrderInfo":"Thanh toan don ORD-1:A", "vnp_Amount":"10000"}` → `"vnp_Amount=10000&vnp_OrderInfo=Thanh+toan+don+ORD-1%3AA"`.
  - `signMatchesHmacSha512`: expected tính trong test bằng `javax.crypto.Mac.getInstance("HmacSHA512")` với key `"SECRET"`, data trên.
  - `paymentUrlHasRequiredParams`: parse URL trả về có `vnp_Version=2.1.0`, `vnp_Command=pay`, `vnp_Amount=` `amountVnd*100`, `vnp_CurrCode=VND`, `vnp_Locale=vn`, `vnp_OrderType=other`, `vnp_CreateDate`/`vnp_ExpireDate` dạng `yyyyMMddHHmmss` giờ `Asia/Ho_Chi_Minh` (Instant `2026-10-18T03:00:00Z` → `20261018100000`), `vnp_SecureHash` verify được bằng chính `verify`.
  - `verifyRejectsTamperedAmount` / `verifyRejectsTamperedHash` / `verifyIgnoresSecureHashType` (có `vnp_SecureHashType=HmacSHA512` vẫn verify true).
- [ ] **Step 2: Chạy** → FAIL. **Step 3: Implement** — encode `URLEncoder.encode(v, StandardCharsets.US_ASCII)` (space → `+`), bỏ key value rỗng, so sánh hash `MessageDigest.isEqual` không phân biệt hoa thường. **Step 4: Chạy** → PASS.
- [ ] **Step 5: Commit** `feat(payment): ký và verify VNPay 2.1.0`.

---

### Task 7: Checkout mới — phí server tính, chọn hãng, chọn phương thức

**Files:**
- Create: `src/main/resources/db/migration/V25__order_shipping_payment.sql`
- Create: `B/model/PaymentTransaction.java`, `PaymentTransactionStatus.java`, `RefundStatus.java`, `OrderShipping.java`
- Create: `B/features/payment/service/PaymentTransactionService.java`; `B/features/payment/repository/PaymentTransactionRepository.java`
- Create: `B/features/shipping/controller/ShippingQuoteController.java`; `B/dto/shipping/ShippingQuoteRequest.java`
- Modify: `B/model/Order.java`, `OrderStatus.java`, `PaymentStatus.java`, `B/dto/order/CheckoutRequest.java`, `OrderResponse.java`, `B/features/order/OrderService.java`, `B/features/order/controller/OrderController.java`, `B/features/order/repository/OrderRepository.java`, `OrderAdminQueryRepository.java`
- Create (test support): `T/support/Checkouts.java`
- Modify tests: `T/features/order/OrderServiceTest.java`, `T/features/admin/AdminProductServiceTest.java`, `T/features/admin/AdminServicesTest.java`, `T/model/OrderTest.java`, `T/features/order/controller/OrderControllerTest.java`
- Test: `T/features/order/CheckoutShippingPaymentTest.java` (isolated)

**Interfaces:**
- Consumes: `LocationService.resolve/regionOf`, `ShopService.pickupProvinceOf`, `ShippingService.quote/quoteCarrier`, `SystemSettingsService.current`, `PaymentGatewayService.requireAvailable/vnpayConfig`, `VnpayClient`.
- Produces:
  - `OrderStatus` thêm `AWAITING_PAYMENT` (đứng đầu enum); `PaymentStatus` thêm `PAID, REFUND_PENDING, REFUNDED`.
  - `record OrderShipping(UUID carrierId, String carrierName, ShippingZone zone, String provinceCode, String wardCode)`.
  - `Order.create(String code, UUID shopId, UUID buyerId, long subtotal, long shippingFee, String shippingName, String shippingPhone, String shippingAddress, OrderShipping shipping, PaymentMethod method, @Nullable Instant paymentExpiresAt)` — COD: `PENDING` + `placedAt=now`; VNPAY: `AWAITING_PAYMENT`, `placedAt=null`. Getters mới: `getCarrierId/getCarrierName/getShippingZone/getShippingProvinceCode/getShippingWardCode/getPaymentMethod(): PaymentMethod/getPaymentExpiresAt/getPlacedAt`.
  - `CheckoutRequest(UUID shopId, List<CheckoutItemRequest> items, String shippingName, String shippingPhone, String shippingAddress, String shippingProvinceCode, String shippingWardCode, UUID carrierId, PaymentMethod paymentMethod)` — **xóa `shippingFee`**.
  - `OrderResponse` thêm (theo thứ tự sau `paymentStatus`): `String paymentMethod, @Nullable UUID carrierId, @Nullable String carrierName, @Nullable String shippingZone, @Nullable Instant paymentExpiresAt, @Nullable String paymentUrl`.
  - `OrderService.quoteShipping(ShippingQuoteRequest): List<ShippingOptionResponse>`; `record ShippingQuoteRequest(UUID shopId, String provinceCode, List<CheckoutItemRequest> items)`; endpoint `POST /api/shipping/quote` (`quoteShipping`, cần đăng nhập).
  - `PaymentTransactionService.startVnpay(UUID orderId, String orderCode, long amount, Instant expiresAt, String ipAddr): String` (tạo giao dịch `PENDING`, `txnRef` = UUID không gạch 32 ký tự, trả `paymentUrl`).
  - `Checkouts.cod(UUID shopId, List<CheckoutItemRequest> items)` / `Checkouts.vnpay(...)`: người nhận `"An"`, `"0901"`, `"So 1 Kim Ma"`, tỉnh `01`, xã `00004`, carrier GHN.
- `OrderController.checkout` nhận thêm `HttpServletRequest` để lấy `getRemoteAddr()` truyền vào `OrderService.checkout(UUID buyerId, CheckoutRequest request, String clientIp)`.

- [ ] **Step 1: Migration** (`V25`): drop + tạo lại CHECK `orders.status` (thêm `AWAITING_PAYMENT`), `payment_method IN ('COD','VNPAY')`, `payment_status IN ('UNPAID','COLLECTED','FAILED','PAID','REFUND_PENDING','REFUNDED')` (tên constraint thật lấy bằng `\d orders` trên DB dev hoặc `pg_constraint`); thêm cột `carrier_id UUID REFERENCES shipping_carrier (id)`, `carrier_name TEXT`, `shipping_zone TEXT CHECK (...)`, `shipping_province_code TEXT REFERENCES province (code)`, `shipping_ward_code TEXT REFERENCES ward (code)`, `payment_expires_at TIMESTAMPTZ`, `placed_at TIMESTAMPTZ`; `UPDATE orders SET placed_at = created_at`; index `idx_orders_awaiting ON orders (payment_expires_at) WHERE status = 'AWAITING_PAYMENT'`. Tạo `payment_transaction` đúng spec §4 + cột hoàn tiền (`refund_status TEXT NOT NULL DEFAULT 'NONE' CHECK (...)`, `refunded_at`, `refunded_by`, `refund_note`, `raw_query TEXT`).
- [ ] **Step 2: Sửa test cũ** sang `Checkouts.cod(...)`; `OrderTest` cập nhật factory mới. Chạy `./gradlew compileTestJava` để chắc chắn đỏ đúng chỗ (chưa có API mới).
- [ ] **Step 3: Test đỏ** `CheckoutShippingPaymentTest` (isolated). Shop test tạo bằng constructor 5 tham số (chưa có tỉnh) trừ khi ghi khác; sản phẩm 100.000đ:
  - `codFeeComesFromRateTable`: qty 2 → `shippingFee=38000`, `total=238000`, `carrierName="Giao Hàng Nhanh"`, `shippingZone="INTER_REGION"`, status `PENDING`, `paymentMethod="COD"`.
  - `extraItemsAddSurcharge`: qty 3 → `43000`.
  - `sameProvinceIsIntraProvince`: shop có tỉnh `01`, người mua `01` → `22000`.
  - `freeShipAtThreshold`: qty 5 (500.000đ) → `shippingFee=0`.
  - `shippingAddressComposedFromLocation`: `shippingAddress == "So 1 Kim Ma, Phường Ba Đình, Thành phố Hà Nội"`.
  - `inactiveCarrierIsConflict`, `disabledPaymentMethodIsConflict`, `codOverLimitIsConflict` (settings `codMaxTotal=100000`), `vnpayWithoutConfigIsConflict` → 409.
  - `vnpayCheckoutAwaitsPayment`: cấu hình VNPay test (`DEMO1234`/`SECRET`) → status `AWAITING_PAYMENT`, `paymentUrl` bắt đầu bằng `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?`, `paymentExpiresAt ≈ now + 15 phút`, seller `listSellerOrdersFiltered(owner, null, null, null, 0, 50)` **không chứa** đơn này.
  - `quoteMatchesCheckout`: `quoteShipping` GHN fee == `checkout(...).shippingFee()`.
  - `OrderControllerTest`: body có `"shippingFee": 1` vẫn 200 (field lạ bị bỏ qua) và service nhận request không có phí; thiếu `carrierId` → 400.
- [ ] **Step 4: Chạy** → FAIL.
- [ ] **Step 5: Implement.** Gom tính tiền hàng + đếm số món vào 1 private method dùng chung cho `checkout` và `quoteShipping`. Thứ tự checkout: shop approved → resolve địa chỉ → tính subtotal → `quoteCarrier` → `requireAvailable(method, total)` → tạo đơn → VNPay: `startVnpay(...)`. Seller queries (`listSellerOrders`, `listSellerOrdersFiltered`, `countByShop`) chỉ lấy `placed_at IS NOT NULL` (thêm method repository `findByShopIdAndPlacedAtIsNotNullOrderByCreatedAtDesc`, `countByShopIdAndPlacedAtIsNotNull`). `OrderAdminQueryRepository.revenueTotals/totalsPerDay` thêm `placed_at IS NOT NULL` vào điều kiện GMV; `collected` = `payment_status IN ('COLLECTED','PAID')`.
- [ ] **Step 6: Chạy** `./gradlew test` → PASS toàn bộ.
- [ ] **Step 7: Commit** `feat(order): checkout tính phí ship phía server, chọn hãng + COD/VNPay`.

---

### Task 8: Kết quả VNPay (Return + IPN), thanh toán lại, hết hạn, hủy đơn đã trả

**Files:**
- Create: `B/features/payment/service/PaymentFlowService.java`, `VnpayResult.java`, `VnpayOutcome.java`
- Create: `B/features/payment/controller/VnpayController.java`
- Create: `B/features/order/PaymentExpiryJob.java`, `B/config/SchedulingConfig.java`
- Create: `B/dto/payment/VnpayReturnResponse.java`, `VnpayIpnResponse.java`, `PaymentUrlResponse.java`
- Modify: `B/model/Order.java`, `B/features/order/OrderService.java`, `B/features/order/controller/OrderController.java`, `B/features/payment/service/PaymentTransactionService.java`, `B/model/PaymentTransaction.java`
- Test: `T/model/OrderTest.java`, `T/features/payment/PaymentFlowServiceTest.java` (isolated), `T/features/payment/VnpayControllerTest.java`

**Interfaces:**
- Consumes: `VnpayClient.verify`, `PaymentGatewayService.vnpayConfig`, `PaymentTransactionService`, `OrderService`.
- Produces:
  - `Order.markPaid()` (AWAITING_PAYMENT → PENDING, `PAID`, `placedAt`; khác → 409), `Order.expirePayment()` (→ CANCELLED, `FAILED`, `cancelReason="PAYMENT_EXPIRED"`), `Order.flagLatePayment()` (CANCELLED → `REFUND_PENDING`), `Order.settleRefund()` (`REFUND_PENDING` → `REFUNDED`); `cancel()` cho phép `PENDING` + `AWAITING_PAYMENT` (AWAITING → `FAILED`; PAID → `REFUND_PENDING`); `cancelBySeller` PAID → `REFUND_PENDING`; `deliver()` chỉ đặt `COLLECTED` khi COD.
  - `enum PaymentApplyOutcome { PLACED, LATE_AFTER_CANCEL, DUPLICATE }`; `OrderService.applyPaymentSuccess(UUID orderId): PaymentApplyOutcome` (khóa đơn `FOR UPDATE`, ghi history); `OrderService.expireAwaitingPayments(Instant now): int`; `OrderService.retryVnpay(UUID buyerId, UUID orderId, String clientIp): PaymentUrlResponse` (409 nếu không AWAITING hoặc hết hạn); `OrderService.settleRefund(UUID orderId)`.
  - `enum VnpayOutcome { OK, INVALID_SIGNATURE, NOT_FOUND, AMOUNT_MISMATCH, ALREADY_PROCESSED }`; `record VnpayResult(VnpayOutcome outcome, boolean success, @Nullable UUID orderId, @Nullable String orderCode)`; `PaymentFlowService.handleVnpay(Map<String, String> params): VnpayResult`.
  - `PaymentTransactionService`: `lockByTxnRef(String txnRef): Optional<PaymentTransaction>`, `markSuccess(...)`, `markFailed(...)`, `flagRefund(UUID transactionId)`, `flagRefundForOrder(UUID orderId)` (gọi bởi `OrderService` khi hủy đơn PAID).
  - Endpoints: `GET /api/public/payments/vnpay/return` (`verifyVnpayReturn`) → `VnpayReturnResponse(UUID orderId, String orderCode, boolean success, String message)` (400 nếu chữ ký sai / không thấy); `GET /api/public/payments/vnpay/ipn` (`vnpayIpn`) → `VnpayIpnResponse(String RspCode, String Message)` (JSON key đúng `RspCode`, `Message`: `@JsonProperty`); `POST /api/orders/{id}/payments/vnpay` (`retryVnpayPayment`).
  - IPN map: `OK`→`00 Confirm Success`, `ALREADY_PROCESSED`→`02 Order already confirmed`, `NOT_FOUND`→`01 Order not found`, `AMOUNT_MISMATCH`→`04 Invalid amount`, `INVALID_SIGNATURE`→`97 Invalid signature`, exception → `99 Unknown error`.

- [ ] **Step 1: Test đỏ `OrderTest`** cho từng method entity ở trên (bảng chuyển trạng thái + 409).
- [ ] **Step 2: Test đỏ `PaymentFlowServiceTest`** — dựng params VNPay trả về bằng `VnpayClient.sign` với secret test, `vnp_TxnRef` lấy từ giao dịch của đơn (đọc qua `PaymentTransactionService`):
  - `successPlacesOrder`: `vnp_ResponseCode=00`, `vnp_TransactionStatus=00`, `vnp_Amount=total*100` → `OK, success=true`; đơn `PENDING`/`PAID`; seller thấy đơn.
  - `failureKeepsAwaiting`: `vnp_ResponseCode=24` → `OK, success=false`; đơn `AWAITING_PAYMENT`; giao dịch `FAILED`.
  - `tamperedSignature` → `INVALID_SIGNATURE`, đơn không đổi.
  - `amountMismatch` → `AMOUNT_MISMATCH`, đơn không đổi.
  - `secondCallIsAlreadyProcessed`: gọi `handleVnpay` 2 lần cùng params → lần 2 `ALREADY_PROCESSED`, history `AWAITING_PAYMENT→PENDING` chỉ 1 dòng.
  - `lateSuccessAfterExpiryNeedsRefund`: `expireAwaitingPayments(now + 16 phút)` = 1 → đơn `CANCELLED`; rồi success → đơn `REFUND_PENDING`, giao dịch `refundStatus=PENDING`.
  - `duplicatePaymentFlagsSecondTransaction`: `retryVnpay` tạo giao dịch 2; success giao dịch 1 rồi giao dịch 2 → đơn `PAID`, giao dịch 2 `SUCCESS` + `refundStatus=PENDING`.
  - `buyerCancelsAwaitingOrder` → `CANCELLED`/`FAILED`; `sellerCancelsPaidOrder` → `REFUND_PENDING` + giao dịch `refundStatus=PENDING`.
  - `retryAfterExpiryIsConflict` → 409.
  - `deliverKeepsPaid`: đơn VNPay đã trả, confirm → ship → deliver → `paymentStatus=PAID`.
- [ ] **Step 3: Test đỏ `VnpayControllerTest`**: IPN không JWT trả `{"RspCode":"97","Message":"Invalid signature"}` khi service trả `INVALID_SIGNATURE`; return không JWT → 200.
- [ ] **Step 4: Chạy** → FAIL.
- [ ] **Step 5: Implement.** `handleVnpay`: verify chữ ký với `vnpayConfig().hashSecret()` → khóa giao dịch theo `vnp_TxnRef` → so tiền (`vnp_Amount / 100 == amount`) → giao dịch không còn `PENDING` → `ALREADY_PROCESSED` → success: `markSuccess` + `applyPaymentSuccess`; `LATE_AFTER_CANCEL` / `DUPLICATE` → `flagRefund(tx)`. Lưu `raw_query` mọi lần. `PaymentExpiryJob`: `@Scheduled(fixedDelay = 60_000)` gọi `expireAwaitingPayments(clock.instant())`; `SchedulingConfig` = `@Configuration @EnableScheduling` (file riêng, không sửa `FashionStoreApplication` để tránh conflict nhánh song song).
- [ ] **Step 6: Chạy** `./gradlew test` → PASS.
- [ ] **Step 7: Commit** `feat(payment): xử lý kết quả VNPay, thanh toán lại, hết hạn giữ đơn`.

---

### Task 9: Đối soát giao dịch + đánh dấu đã hoàn tiền

**Files:**
- Create: `B/features/payment/repository/PaymentTransactionQueryRepository.java`; `B/dto/payment/PaymentTransactionResponse.java`, `MarkRefundedRequest.java`
- Modify: `B/features/admin/AdminPaymentController.java`, `B/features/payment/service/PaymentFlowService.java`, `PaymentTransactionService.java`
- Test: `T/features/payment/PaymentFlowServiceTest.java`, `T/features/admin/AdminPaymentControllerTest.java`

**Interfaces:**
- Produces: `PaymentTransactionService.search(@Nullable PaymentTransactionStatus status, @Nullable RefundStatus refundStatus, @Nullable String q, int page, int size): PageResponse<PaymentTransactionResponse>` (`q` khớp `orders.code`, `txn_ref`, `transaction_no`, mới trước); `PaymentFlowService.markRefunded(UUID adminId, UUID transactionId, String note): PaymentTransactionResponse`.
  - `record PaymentTransactionResponse(UUID id, UUID orderId, String orderCode, String provider, String txnRef, long amount, String status, @Nullable String responseCode, @Nullable String transactionNo, @Nullable String bankCode, @Nullable String cardType, @Nullable Instant payDate, String refundStatus, @Nullable Instant refundedAt, @Nullable String refundNote, Instant createdAt)`; `record MarkRefundedRequest(@NotBlank @Size(min = 5, max = 500) String note)`.
  - Endpoints: `GET /api/admin/payments/transactions?status=&refundStatus=&q=&page=&size=` (`listPaymentTransactions`), `PATCH /api/admin/payments/transactions/{id}/refunded` (`markTransactionRefunded`).

- [ ] **Step 1: Test đỏ**: `markRefundedSettlesOrderWhenNoPendingLeft` (đơn hủy sau khi PAID → mark → giao dịch `REFUNDED`, đơn `REFUNDED`, audit `PAYMENT_TRANSACTION`); `markRefundedTwiceIsConflict` → 409; `duplicateRefundKeepsOrderPaid` (giao dịch trùng mark → đơn vẫn `PAID`); `searchFiltersByRefundStatusAndCode`; controller: note 3 ký tự → 400, không admin → 403.
- [ ] **Step 2: Chạy** → FAIL. **Step 3: Implement** (khóa giao dịch `FOR UPDATE`; đơn chỉ `settleRefund` khi đang `REFUND_PENDING` và không còn giao dịch nào `refund_status=PENDING`). **Step 4: Chạy** `./gradlew test` → PASS.
- [ ] **Step 5: Commit** `feat(payment): đối soát giao dịch, đánh dấu đã hoàn tiền`.

---

### Task 10: Docs backend + xuất OpenAPI cho UI

**Files:**
- Modify: `docs/api-endpoints.md` (Flow mới "Vận chuyển & thanh toán" + "Quản trị cấu hình", checkbox `[x]`, sửa dòng #17 checkout), `docs/data.md` (V20–V25), `AGENTS.md` nếu cần nhắc feature mới.
- Create: `T/OpenApiDumpTest.java` (giống nhánh order-monitoring: GET `/v3/api-docs` → `build/openapi.json`).
- Copy: `build/openapi.json` → UI `docs/openapi.json`.

- [ ] **Step 1:** Viết docs. **Step 2:** `./gradlew test --tests "*OpenApiDumpTest"` → có `build/openapi.json`; kiểm tra có `operationId` `quoteShipping`, `retryVnpayPayment`, `listPaymentTransactions`, `getPublicSettings`.
- [ ] **Step 3:** Full `./gradlew test` → xanh. **Step 4: Commit** backend `docs: endpoint vận chuyển, thanh toán, cấu hình chung`.
- [ ] **Step 5:** UI: copy spec, `pnpm install`, `pnpm codegen`, `pnpm build` (lỗi type ở chỗ cũ dùng `CheckoutRequest`/`OrderResponse` thì sửa luôn). Commit UI `chore(api): regenerate openapi spec cho vận chuyển + thanh toán`.

---

### Task 11: UI nền — helper + chọn tỉnh/xã

**Files (UI):**
- Create: `src/lib/shipping.ts` (+ `shipping.test.ts`), `src/lib/payment.ts` (+ `payment.test.ts`), `src/lib/orderStatus.ts` (+ `orderStatus.test.ts`)
- Create: `src/components/LocationSelect.tsx`

**Interfaces:**
- Produces: `zoneLabel(zone: string): string` (`INTRA_PROVINCE`→`Nội tỉnh`, `INTRA_REGION`→`Nội miền`, `INTER_REGION`→`Liên miền`); `etaLabel(min: number, max: number): string` (`1–2 ngày`, min==max → `2 ngày`); `paymentMethodLabel(code)` (`COD`→`Thanh toán khi nhận hàng`, `VNPAY`→`VNPay`); `paymentStatusLabel(code)` (`UNPAID` Chưa thanh toán, `PAID` Đã thanh toán, `COLLECTED` Đã thu COD, `FAILED` Thanh toán thất bại, `REFUND_PENDING` Chờ hoàn tiền, `REFUNDED` Đã hoàn tiền); `orderStatusLabel(code)` (`AWAITING_PAYMENT` Chờ thanh toán, `PENDING` Chờ xác nhận, `CONFIRMED` Đã xác nhận, `SHIPPING` Đang giao, `DELIVERED` Đã giao, `CANCELLED` Đã hủy); `codUnavailableReason(total: number, min: number, max: number): string | null` (`max === 0` = không giới hạn; trả `Đơn trên {formatVnd(max)} không hỗ trợ COD` / `Đơn dưới {formatVnd(min)} không hỗ trợ COD`); `minutesLeft(expiresAt: string, now: Date): number`.
- `LocationSelect` props `{ provinceCode: string; wardCode: string; onChange: (next: { provinceCode: string; wardCode: string }) => void; disabled?: boolean; idPrefix: string }` — 2 `<select>` có `<label htmlFor>`; đổi tỉnh thì reset xã; dùng hook `useListProvinces`, `useListWards` (enabled khi có tỉnh).

- [ ] **Step 1:** test vitest cho mọi hàm trên (giá trị đúng như liệt kê). **Step 2:** `pnpm test` → FAIL. **Step 3:** implement. **Step 4:** `pnpm test` → PASS; `pnpm format && pnpm lint && pnpm build` xanh. **Step 5: Commit** `feat(ui): helper vận chuyển/thanh toán + chọn tỉnh/xã`.

---

### Task 12: Trang admin Vận chuyển

**Files (UI):** Create `src/pages/AdminShipping.tsx`, `src/components/admin/CarrierFormDialog.tsx`, `src/components/admin/RateTableEditor.tsx`; Modify `src/components/admin/AdminNav.tsx`, `src/App.tsx`, `docs/structure.md`.

- Nav thêm (theo thứ tự): `Vận chuyển` (`/admin/shipping`, icon `Truck`), `Thanh toán` (`/admin/payments`, `CreditCard`), `Cấu hình chung` (`/admin/settings`, `GearSix`); route bọc như `/admin/products`.
- Tab *Đơn vị vận chuyển*: bảng code / tên / trạng thái / thứ tự; nút Thêm, Sửa (code read-only khi sửa), Bật/Tắt (lỗi 409 hiện message backend qua `hookErrorMessage`).
- Tab *Bảng phí*: chọn hãng → `RateTableEditor` 3 hàng vùng × (phí cơ bản, mỗi món thêm, ETA min, ETA max), nút Lưu (`replaceCarrierRates`); khối "Thử tính phí": chọn vùng + số món + tiền hàng → hiện phí bằng cùng công thức (hàm `previewFee` trong `src/lib/shipping.ts`, test cùng giá trị Task 3 Step 2), lấy `baseItems`/`freeShippingThreshold` từ `getSystemSettings`.
- [ ] **Step 1:** test `previewFee` (thêm vào `shipping.test.ts`) → đỏ → implement → xanh. **Step 2:** dựng trang. **Step 3:** `pnpm format && pnpm lint && pnpm build` xanh; chạy backend + `pnpm dev`, kiểm tay: tắt GHN → biến khỏi quote; sửa phí → lưu → reload giữ. **Step 4: Commit** `feat(admin): trang quản lý vận chuyển và bảng phí`.

---

### Task 13: Trang admin Thanh toán

**Files (UI):** Create `src/pages/AdminPayments.tsx`, `src/components/admin/GatewayCard.tsx`, `src/components/admin/MarkRefundedDialog.tsx`; Modify `src/App.tsx`.

- Tab *Cổng thanh toán*: thẻ COD (bật/tắt) + thẻ VNPay (form TMN Code, Hash Secret `type="password"` placeholder `Đã thiết lập — để trống nếu giữ nguyên` khi `hashSecretSet`, Pay URL, Return URL, bật/tắt); hướng dẫn ngắn đăng ký sandbox `https://sandbox.vnpayment.vn/devreg/` + thẻ test NCB.
- Tab *Giao dịch*: bộ lọc trạng thái (Tất cả / Thành công / Thất bại / Đang chờ) + chip **Cần hoàn tiền** (`refundStatus=PENDING`), ô tìm mã đơn/mã GD, bảng phân trang (`Pagination` có sẵn), hành động **Đánh dấu đã hoàn tiền** mở dialog ghi chú 5–500 ký tự.
- [ ] **Step 1:** dựng. **Step 2:** lint/build xanh; kiểm tay lưu secret → reload không lộ secret. **Step 3: Commit** `feat(admin): trang cổng thanh toán và đối soát giao dịch`.

---

### Task 14: Trang admin Cấu hình chung

**Files (UI):** Create `src/pages/AdminSettings.tsx`; Modify `src/App.tsx`.

- Form 3 nhóm *Thông tin sàn* (tên, hotline, email), *Vận chuyển* (ngưỡng freeship — gợi ý `0 = tắt`, số món trong phí cơ bản), *Đơn hàng* (giữ đơn chờ thanh toán (phút), tự xác nhận nhận hàng (ngày) + chú thích "chưa áp dụng tự động", COD tối thiểu/tối đa — `0 = không giới hạn`). Chỉ gửi field đã đổi; nút Lưu disable khi không đổi; lỗi 400 hiện cạnh form.
- Khối *Lịch sử thay đổi* (`listConfigAudit`, lọc entity) bên dưới: thời gian, entity, key, cũ → mới.
- [ ] **Step 1:** dựng. **Step 2:** lint/build xanh; kiểm tay đổi ngưỡng freeship → audit hiện dòng mới. **Step 3: Commit** `feat(admin): trang cấu hình chung`.

---

### Task 15: Người mua — Mua ngay + Checkout

**Files (UI):** Create `src/pages/Checkout.tsx`, `src/lib/checkout.ts` (+ `checkout.test.ts`); Modify `src/pages/ProductDetail.tsx` (hoặc `src/components/pdp/*` nơi nút "Mua ngay" placeholder đang nằm), `src/App.tsx` (route `/checkout` bọc `RequireAuth`).

**Interfaces:** `buildCheckoutUrl(variantId: string, quantity: number): string` → `/checkout?variantId=…&qty=…`; `parseCheckoutParams(search: string): { variantId: string; quantity: number } | null` (qty nguyên 1–99, sai → null).

- PDP: nút **Mua ngay** dùng biến thể + số lượng đang chọn → `navigate(buildCheckoutUrl(...))`; chưa chọn biến thể → disable.
- `/checkout`: lấy sản phẩm qua API chi tiết sản phẩm (tìm variant theo id); form người nhận, SĐT, `LocationSelect`, địa chỉ chi tiết; khi đủ tỉnh → `quoteShipping` (debounce đổi số lượng) → radio danh sách hãng (tên, `zoneLabel`, `etaLabel`, phí, gạch phí gốc khi freeship); `listPaymentMethods` → radio COD/VNPay, COD disable kèm `codUnavailableReason`; tóm tắt tiền hàng / phí ship / tổng; **Đặt hàng** → COD: `navigate('/orders/{id}')`; VNPay: `window.location.assign(paymentUrl)`. Lỗi 409 (hãng/cổng vừa tắt) → hiện message + refetch quote/methods.
- [ ] **Step 1:** test `checkout.ts` → đỏ → implement → xanh. **Step 2:** dựng trang. **Step 3:** lint/build xanh; kiểm tay đặt COD thành công. **Step 4: Commit** `feat(store): mua ngay và trang thanh toán`.

---

### Task 16: Người mua — Kết quả VNPay + Đơn của tôi

**Files (UI):** Create `src/pages/VnpayReturn.tsx`, `src/pages/MyOrders.tsx`, `src/pages/MyOrderDetail.tsx`, `src/lib/vnpayReturn.ts`; Modify `src/App.tsx`, `src/components/Header.tsx` (link "Đơn mua" trong menu tài khoản).

**Interfaces:** `verifyVnpayReturn(search: string): Promise<VnpayReturnResponse>` gọi `customFetch('/api/public/payments/vnpay/return' + search)` (query VNPay động nên không dùng hook sinh), mở envelope bằng `takeData`.

- `/payment/vnpay-return`: gọi 1 lần khi mount (React Query `useQuery` key theo `search`, `retry: false`) → thành công: "Thanh toán thành công", mã đơn, nút Xem đơn; thất bại: lý do + nút **Thanh toán lại** (`retryVnpayPayment` → `window.location.assign`).
- `/orders`: danh sách `getMine` (mã, shop, trạng thái `orderStatusLabel`, thanh toán `paymentStatusLabel`, tổng). `/orders/:id`: chi tiết, hãng + phí ship, địa chỉ, thanh toán; `AWAITING_PAYMENT` hiện đếm `minutesLeft` + nút Thanh toán lại; `PENDING`/`AWAITING_PAYMENT` có nút Hủy.
- [ ] **Step 1:** dựng. **Step 2:** lint/build xanh. **Step 3:** kiểm tay với sandbox thật nếu đã có TMN/secret (thẻ NCB `9704198526191432198`, `NGUYEN VAN A`, `07/15`, OTP `123456`) → về trang kết quả thành công, đơn `Chờ xác nhận` / `Đã thanh toán`; nếu chưa có mã thì ghi rõ trong báo cáo là chưa kiểm sandbox. **Step 4: Commit** `feat(store): kết quả thanh toán VNPay và đơn mua`.

---

### Task 17: Seller Center — eKYC tỉnh/xã, tab Đơn hàng + Hồ sơ nối API thật

**Files (UI):** Modify `src/components/ShopKycRegistrationModal.tsx`, `src/pages/ShopDashboard.tsx`, `src/components/seller-dashboard-tab/SellerOrdersTab.tsx`, `SellerProfileTab.tsx`, `sellerData.ts` (bỏ `INITIAL_ORDERS` / `INITIAL_SHOP_PROFILE` nếu không còn dùng); Create `src/lib/sellerOrders.ts` (+ `sellerOrders.test.ts`).

**Interfaces:** `toSellerOrderItem(order: OrderResponse): OrderItem` (map sang type `OrderItem` hiện có của tab, thêm field `carrierName`, `paymentMethod`, `paymentStatus` vào type `OrderItem`); test map đủ field + đơn không có hãng (đơn cũ) → `carrierName = 'Chưa chọn hãng'`.

- eKYC: thêm `LocationSelect` bắt buộc (validate cùng chỗ kiểm `address` ở dòng ~166), nhãn ô cũ đổi thành "Địa chỉ chi tiết (số nhà, đường)", gửi `provinceCode`, `wardCode`.
- `ShopDashboard`: shop hiện tại từ `GET /api/shops/mine` (shop đầu tiên `APPROVED`); orders từ `listSellerOrdersFiltered`/hook tương ứng theo `shopId`; confirm/ship/deliver/cancel gọi mutation thật rồi invalidate; lỗi 409 (hết hàng) hiện toast message backend. Thẻ đơn hiện hãng, phí ship, `paymentMethodLabel` + `paymentStatusLabel`.
- Tab Hồ sơ: đọc shop thật, form gửi `PATCH /api/shops/{id}/profile` gồm field hiện có + `LocationSelect`; banner vàng "Cập nhật tỉnh/thành lấy hàng để phí ship tính đúng" khi `provinceCode == null`.
- Các tab khác giữ mock (không đụng).
- [ ] **Step 1:** test `sellerOrders.ts` → đỏ → implement → xanh. **Step 2:** sửa các component. **Step 3:** `pnpm format && pnpm lint && pnpm build && pnpm test` xanh. **Step 4:** kiểm tay trọn vòng: admin cấu hình → người mua đặt COD + VNPay → seller thấy đơn (đơn VNPay chỉ hiện sau khi trả) → xác nhận → giao → đã giao. **Step 5: Commit** `feat(seller): eKYC tỉnh/xã, đơn hàng và hồ sơ shop dùng API thật`.

---

### Task 18: Review cuối nhánh

- [ ] Backend `./gradlew test` xanh; UI `pnpm format:check && pnpm lint && pnpm build && pnpm test` xanh.
- [ ] Đối chiếu spec từng mục §1–§9 + Review Focus; cập nhật spec nếu có quyết định thay đổi trong lúc làm.
- [ ] Không push, không merge — báo lại cho người dùng.
