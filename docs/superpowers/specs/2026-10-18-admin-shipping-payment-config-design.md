# Module Quản trị — Vận chuyển, Phí ship, Cổng thanh toán, Cấu hình chung

## Mục tiêu

Demo được trọn vòng: **admin cấu hình → người mua checkout → phí ship tự tính theo cấu hình → trả tiền COD hoặc VNPay sandbox thật**. Tham chiếu nghiệp vụ: Shopee/Lazada (sàn bật kênh vận chuyển, người mua chọn kênh, phí tính phía server, đơn online "Chờ thanh toán" trước "Chờ xác nhận"), GHN/GHTK (bảng phí theo vùng nội tỉnh / nội miền / liên miền), VNPay PAY API 2.1.0.

Hiện trạng cần sửa:
- `POST /api/orders` tin `shippingFee` client gửi lên; `orders.payment_method` CHECK chỉ cho `COD`.
- Shop và người mua chỉ có địa chỉ text tự do, server không biết tỉnh để tính vùng.
- Frontend chưa có nút mua, trang checkout, trang đơn của người mua.

Bốn chức năng admin:
1. **Đơn vị vận chuyển** — thêm/sửa/bật/tắt hãng (GHN, GHTK, J&T…).
2. **Phí vận chuyển** — bảng giá mỗi hãng theo 3 vùng, tính **theo số món** (shop quần áo, không dùng cân nặng).
3. **Cổng thanh toán** — bật/tắt COD, VNPay; nhập khóa sandbox; xem giao dịch để đối soát.
4. **Cấu hình chung** — thông tin sàn, ngưỡng freeship, số món trong phí cơ bản, thời gian giữ đơn chờ thanh toán, giới hạn COD.

## Ngoài phạm vi / không làm

- Gọi API thật của hãng vận chuyển (tạo vận đơn GHN, tra cứu hành trình). Hãng chỉ là cấu hình + bảng phí nội bộ.
- Tính phí theo cân nặng / kích thước; mã giảm giá vận chuyển; shop tự chọn hãng hỗ trợ (mọi hãng đang bật đều áp dụng cho mọi shop).
- Hoàn tiền tự động qua VNPay Refund API: đơn đã trả bị hủy chỉ chuyển `REFUND_PENDING`, admin hoàn tay ngoài hệ thống.
- Giỏ hàng nhiều shop (nhánh `origin/backend-carts-and-orders` chưa merge) — giữ checkout 1 đơn 1 shop hiện tại.
- Trang sổ địa chỉ của người mua trên UI (backend có sẵn, chỉ bổ sung field tỉnh/xã).
- Mã hóa secret at-rest bằng KMS.

**Anti-goal:** server lại tin số tiền client gửi (phí ship, tổng tiền) hoặc đánh dấu đã thanh toán mà chưa verify chữ ký VNPay + số tiền khớp. Có một trong hai là hỏng, dù UI chạy đẹp.

## 1. Địa giới hành chính (2 cấp sau sáp nhập 7/2025)

- Bảng `province(code TEXT PK, name, region)` — 34 tỉnh/thành theo Nghị quyết 202/2025/QH15, `region IN ('NORTH','CENTRAL','SOUTH')`.
- Bảng `ward(code TEXT PK, province_code FK, name)` — ~3.321 xã/phường/đặc khu, mã theo danh mục đơn vị hành chính mới.
- Seed bằng migration (dữ liệu tham chiếu, cần ở production), không gọi API ngoài lúc chạy. Nguồn: danh mục chính thức 2025 (ví dụ `provinces.open-api.vn/api/v2/?depth=2`); builder tải 1 lần, sinh file SQL, ghi nguồn + ngày tải ở đầu file.
- Phân miền theo vị trí địa lý; Bắc: từ Thanh Hóa trở ra; Trung: Nghệ An → Lâm Đồng (gồm Tây Nguyên); Nam: còn lại. *(Lời Claude — builder giữ quy ước này, liệt kê bảng tỉnh→miền trong migration.)*
- Public API: `GET /api/locations/provinces`, `GET /api/locations/provinces/{code}/wards`.

### Shop

- Thêm `shop.province_code`, `shop.ward_code` (nullable ở DB vì shop cũ). `shop.address` giữ nguyên, từ nay nghĩa là **địa chỉ chi tiết** (số nhà, đường).
- Tạo shop (`CreateShopRequest`, form eKYC): `provinceCode`, `wardCode` **bắt buộc**, ward phải thuộc province (`400` nếu sai).
- Cập nhật hồ sơ shop (`UpdateShopProfileRequest`): cho sửa province/ward.
- Shop chưa có tỉnh → tính phí như **liên miền**; Seller Center hiện banner nhắc cập nhật tỉnh lấy hàng.
- DemoSeeder gán sẵn tỉnh/xã cho shop `net-lab`.

### Địa chỉ người mua (`address`)

- Thêm `province_code`, `ward_code`. `house_number`/`street` là chi tiết; `district`/`city` cũ giữ để hiển thị dữ liệu cũ, request mới không cần.

## 2. Đơn vị vận chuyển

Bảng `shipping_carrier`: `id UUID`, `code TEXT UNIQUE` (`^[A-Z0-9_]+$`, không sửa sau khi tạo), `name`, `logo_key` (S3 key, nullable), `tracking_url_template` (nullable, ví dụ `https://donhang.ghn.vn/?order_code={code}`), `active`, `sort_order`, `created_at`, `updated_at`.

- Seed: `GHN` (Giao Hàng Nhanh), `GHTK` (Giao Hàng Tiết Kiệm), `JT` (J&T Express) kèm bảng phí mẫu, đều `active`.
- Không xóa cứng (đơn cũ tham chiếu) — chỉ bật/tắt. Tắt hãng: checkout mới không chọn được, đơn cũ không đổi.
- Chỉ **bật** được khi hãng có đủ bảng phí 3 vùng (`409` nếu thiếu).
- Phải còn ít nhất 1 hãng đang bật: tắt hãng cuối cùng → `409`.

## 3. Phí vận chuyển

Bảng `shipping_rate`: `carrier_id`, `zone IN ('INTRA_PROVINCE','INTRA_REGION','INTER_REGION')`, `base_fee BIGINT`, `extra_item_fee BIGINT`, `eta_min_days`, `eta_max_days`, `UNIQUE(carrier_id, zone)`. Tiền ≥ 0, `1 ≤ eta_min ≤ eta_max ≤ 30`.

**Công thức** (server tính, đơn vị VND):

```
zone =
  shop.province_code null                 → INTER_REGION
  shop.province == buyer.province         → INTRA_PROVINCE
  region(shop) == region(buyer)           → INTRA_REGION
  còn lại                                 → INTER_REGION

items = tổng quantity các dòng
N     = settings.shipping.base_items      (mặc định 2)
fee   = base_fee + max(0, items - N) × extra_item_fee
nếu settings.shipping.free_threshold > 0 và subtotal ≥ free_threshold → fee = 0
```

Bảng phí mẫu seed (VND, theo mặt bằng GHN/GHTK 2025, admin sửa được):

| Hãng | Nội tỉnh | Nội miền | Liên miền | Mỗi món thêm | ETA (ngày) |
|---|---|---|---|---|---|
| GHN | 22.000 | 30.000 | 38.000 | 3.000 / 4.000 / 5.000 | 1–2 / 2–3 / 3–5 |
| GHTK | 20.000 | 28.000 | 35.000 | 2.500 / 3.500 / 4.500 | 1–2 / 2–4 / 4–6 |
| J&T | 21.000 | 29.000 | 36.000 | 3.000 / 4.000 / 5.000 | 1–2 / 2–3 / 3–5 |

- Admin sửa cả 3 vùng một lần (`PUT`), thay thế toàn bộ bảng phí của hãng.
- **Phí được snapshot vào đơn**: đổi bảng phí sau đó không ảnh hưởng đơn cũ. Đơn lưu thêm `carrier_id`, `carrier_name` (snapshot), `shipping_zone`, `shipping_province_code`, `shipping_ward_code`.
- `POST /api/shipping/quote` (đăng nhập) body `{shopId, provinceCode, items[{variantId, quantity}]}` → danh sách hãng đang bật: `carrierId, name, logoKey, zone, fee, originalFee, freeShipApplied, etaMinDays, etaMaxDays`, xếp theo `sort_order`. Dùng chung đúng hàm tính với checkout.

## 4. Cổng thanh toán

### Cấu hình

Bảng `payment_gateway`: `code TEXT PK IN ('COD','VNPAY')`, `name`, `enabled`, `config JSONB`, `updated_at`, `updated_by`. Seed: COD bật; VNPay tắt, `config = {tmnCode: "", hashSecret: "", payUrl: "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", returnUrl: "http://localhost:5173/payment/vnpay-return"}`.

- `hashSecret` **write-only**: response trả `hashSecretSet: true/false` + `tmnCode`, không bao giờ trả secret. `PUT` gửi `hashSecret` null/rỗng = giữ secret cũ.
- Bật VNPay khi `tmnCode` hoặc `hashSecret` trống → `409`.
- Phải còn ít nhất 1 cổng bật.
- `GET /api/payment-methods` (public): các cổng đang bật + giới hạn COD từ cấu hình chung, để UI checkout dựng lựa chọn.

### Luồng đơn VNPay

Trạng thái đơn thêm `AWAITING_PAYMENT` ("Chờ thanh toán", trước `PENDING`); `payment_method IN ('COD','VNPAY')`; `payment_status` thêm `PAID`, `REFUND_PENDING`, `REFUNDED`. Đơn thêm `payment_expires_at` (nullable).

```
COD:   checkout ──────────────────────────────▶ PENDING ──▶ (luồng cũ) ──▶ DELIVERED (COLLECTED)
VNPAY: checkout ─▶ AWAITING_PAYMENT ─paid─────▶ PENDING ──▶ (luồng cũ) ──▶ DELIVERED (PAID giữ nguyên)
                        │ hết hạn / người mua hủy
                        └──────────────────────▶ CANCELLED (payment_status FAILED)
```

- Checkout VNPay: tạo đơn `AWAITING_PAYMENT`, `payment_expires_at = now + order.payment_hold_minutes`, tạo `payment_transaction` `PENDING`, trả `paymentUrl` trong response.
- Seller **không thấy** đơn `AWAITING_PAYMENT` (list, đếm, xử lý). Kho vẫn trừ lúc seller confirm như hiện tại — không giữ kho cho đơn chờ thanh toán.
- Người mua thanh toán lại khi còn hạn: `POST /api/orders/{id}/payments/vnpay` → giao dịch mới (`vnp_TxnRef` mới), `paymentUrl` mới. Hết hạn → `409`.
- Người mua hủy được đơn `AWAITING_PAYMENT` (như `PENDING`).
- Job `@Scheduled` mỗi phút: `AWAITING_PAYMENT` quá hạn → `CANCELLED`, `cancel_reason = 'PAYMENT_EXPIRED'`, `payment_status = FAILED`, ghi `order_status_history`.
- Đơn VNPay đã `PAID` bị hủy (người mua hủy lúc `PENDING`, seller hủy) → `payment_status = REFUND_PENDING`.
- `deliver` đơn VNPay không đổi `PAID` sang `COLLECTED`. Doanh thu / đối soát seller tính `PAID` tương đương `COLLECTED`.
- **Đơn chỉ "lộ" cho seller khi đã tới `PENDING`**: thêm `orders.placed_at` (nullable) — COD set ngay lúc checkout, VNPay set lúc thanh toán thành công. Mọi query seller (list, đếm, doanh thu) lọc `placed_at IS NOT NULL`, nên đơn VNPay chưa trả rồi hết hạn/hủy không bao giờ hiện với seller. GMV admin (`OrderAdminQueryRepository.revenueTotals`) cũng lọc `placed_at IS NOT NULL`. *(Lời Claude.)*

### Hoàn tiền (thủ công) và thanh toán trùng

*(Lời Claude — theo cách Shopee/Lazada để bộ phận tài chính xử lý, hệ thống chỉ ghi nhận.)*

- `payment_transaction` thêm `refund_status IN ('NONE','PENDING','REFUNDED')`, `refunded_at`, `refunded_by`, `refund_note`.
- Cần hoàn tiền (`refund_status = PENDING`) khi:
  - đơn đã `PAID` bị hủy → giao dịch thành công của đơn đó;
  - trả muộn sau khi đơn đã `CANCELLED` (hết hạn) → giao dịch đó, đơn `REFUND_PENDING`;
  - **trả trùng**: giao dịch thành công tới khi đơn đã `PAID` bởi giao dịch khác (người mua trả ở 2 tab) → giao dịch tới sau `SUCCESS` + `refund_status = PENDING`, đơn giữ nguyên.
- Khi thanh toán lại, các giao dịch `PENDING` cũ của đơn **không** bị vô hiệu (URL cũ vẫn trả được ở VNPay tới hạn); trường hợp trả trùng xử lý như trên.
- `payment_status` thêm `REFUNDED`. Admin: tab *Giao dịch* có bộ lọc **Cần hoàn tiền**, nút **Đánh dấu đã hoàn tiền** (ghi chú bắt buộc 5–500 ký tự) → `PATCH /api/admin/payments/transactions/{id}/refunded`: giao dịch `REFUNDED`; nếu đơn đang `REFUND_PENDING` và không còn giao dịch nào chờ hoàn → đơn `REFUNDED`. Ghi `admin_config_audit` (entity `PAYMENT_TRANSACTION`). `409` nếu giao dịch không ở `PENDING` hoàn.
- Bổ sung test: trả trùng, đánh dấu hoàn tiền, đánh dấu lặp → `409`.

### Tích hợp VNPay (PAY API 2.1.0)

- Tham số: `vnp_Version=2.1.0`, `vnp_Command=pay`, `vnp_TmnCode`, `vnp_Amount = total × 100`, `vnp_CurrCode=VND`, `vnp_TxnRef` (unique mỗi giao dịch), `vnp_OrderInfo` (không dấu, chứa mã đơn), `vnp_OrderType=other`, `vnp_Locale=vn`, `vnp_ReturnUrl`, `vnp_IpAddr`, `vnp_CreateDate`/`vnp_ExpireDate` (`yyyyMMddHHmmss`, giờ `Asia/Ho_Chi_Minh`, expire = `payment_expires_at`).
- Ký: sort key tăng dần, URL-encode giá trị (`US_ASCII`, space → `+`), nối `k=v&...`, `vnp_SecureHash = HMAC-SHA512(hashSecret, data)` hex. Verify: bỏ `vnp_SecureHash`/`vnp_SecureHashType` rồi ký lại, so sánh constant-time.
- Bảng `payment_transaction`: `id`, `order_id`, `provider='VNPAY'`, `txn_ref UNIQUE`, `amount`, `status IN ('PENDING','SUCCESS','FAILED')`, `response_code`, `transaction_status`, `transaction_no`, `bank_code`, `card_type`, `pay_date`, `raw JSONB` (toàn bộ query VNPay trả), `created_at`, `updated_at`.
- **Một hàm xử lý kết quả dùng chung** cho Return và IPN, idempotent, khóa `SELECT ... FOR UPDATE` giao dịch + đơn:
  1. Chữ ký sai → không đổi gì (IPN `97`).
  2. Không có `txn_ref` → IPN `01`. Số tiền lệch → IPN `04`, không đổi gì.
  3. Giao dịch đã xử lý → IPN `02`, trả lại kết quả cũ.
  4. `vnp_ResponseCode=00` và `vnp_TransactionStatus=00` → giao dịch `SUCCESS`; đơn đang `AWAITING_PAYMENT` → `PAID` + `PENDING` + `placed_at` + history. Đơn đã `CANCELLED` (trả muộn sau hết hạn) → `REFUND_PENDING`, đơn giữ `CANCELLED`. Đơn đã `PAID` bởi giao dịch khác → trả trùng (xem *Hoàn tiền*).
  5. Khác → giao dịch `FAILED`, đơn giữ `AWAITING_PAYMENT` (thanh toán lại được).
  6. IPN trả `{"RspCode":"00","Message":"Confirm Success"}` khi xử lý xong.
- `GET /api/payments/vnpay/ipn` (public): VNPay gọi server-to-server — chỉ chạy khi deploy có URL public, đăng ký IPN URL trong cổng merchant VNPay.
- `GET /api/payments/vnpay/return` (public): UI trang `/payment/vnpay-return` chuyển nguyên query string sang đây → backend verify + chạy hàm xử lý trên → trả `{orderId, orderCode, success, message}`. **Ở localhost đây là đường xác nhận chính** vì IPN không gọi về `localhost` được.
- Admin đối soát: `GET /api/admin/payments/transactions?status=&q=&page=&size=` (`q` khớp mã đơn, `txn_ref`, `transaction_no`), mới trước.

## 5. Cấu hình chung

Bảng `system_setting(key TEXT PK, value TEXT, updated_at, updated_by)`, Java đọc/ghi qua record có kiểu `SystemSettings` (validate từng field), không rải key string khắp code.

| Key | Kiểu | Mặc định | Ràng buộc |
|---|---|---|---|
| `site.name` | text | `nét` (tên sàn nhánh site-content đang dùng) | 1–100 ký tự |
| `site.hotline` | text | `1900 0000` | ≤ 20 |
| `site.support_email` | email | `support@fashionstore.local` | email hợp lệ |
| `shipping.free_threshold` | VND | `500000` | ≥ 0, `0` = tắt freeship |
| `shipping.base_items` | int | `2` | 1–20 |
| `order.payment_hold_minutes` | int | `15` | 5–1440 |
| `order.auto_complete_days` | int | `7` | 1–30 |
| `order.cod_min_total` | VND | `0` | ≥ 0 |
| `order.cod_max_total` | VND | `10000000` | ≥ cod_min, `0` = không giới hạn |

- COD ngoài `[cod_min, cod_max]` → checkout `409`, `GET /api/payment-methods` trả kèm giới hạn để UI disable COD kèm lý do.
- `order.auto_complete_days`: lưu + hiển thị; **hệ thống chưa có trạng thái "Hoàn thành / đã nhận hàng"** nên chưa có job dùng tới. *(Lời Claude — làm luồng hoàn thành đơn thuộc nhánh khác.)*
- `GET /api/admin/settings`, `PUT /api/admin/settings` (partial: chỉ key gửi lên, validate tất cả trước khi ghi, 1 transaction), `GET /api/settings/public` (tên sàn, hotline, email, ngưỡng freeship) cho footer + checkout.

## 6. Lịch sử thay đổi cấu hình

Một bảng chung `admin_config_audit(id, entity IN ('CARRIER','SHIPPING_RATE','PAYMENT_GATEWAY','SETTING'), entity_key, action, old_value JSONB, new_value JSONB, changed_by, changed_at)` ghi trong cùng transaction với thay đổi. `hashSecret` luôn ghi dạng `"****"`. `GET /api/admin/config-audit?entity=&page=&size=`.

## 7. Checkout mới

`CheckoutRequest` (`POST /api/orders`):
- **Bỏ** `shippingFee` (field client gửi lên bị bỏ qua hẳn; xóa khỏi DTO).
- Thêm `carrierId` (bắt buộc), `paymentMethod` (`COD` | `VNPAY`, bắt buộc), `shippingProvinceCode`, `shippingWardCode` (bắt buộc, ward thuộc province).
- `shippingAddress` nghĩa là địa chỉ chi tiết; server ghép `"{chi tiết}, {xã}, {tỉnh}"` lưu vào `orders.shipping_address` (giữ cột cũ để mọi màn hình đang đọc vẫn đúng).
- Lỗi: hãng tắt / không tồn tại → `409` / `404`; cổng tắt → `409`; COD vượt giới hạn → `409`.
- `OrderResponse` thêm `carrierName`, `shippingZone`, `paymentMethod`, `paymentStatus`, `paymentExpiresAt`, `paymentUrl` (chỉ có ngay sau checkout / thanh toán lại VNPay).

## 8. UI (`fashionstore-ui`)

**Admin** — thanh điều hướng thêm 3 mục:
- `/admin/shipping`: tab *Đơn vị vận chuyển* (danh sách, thêm/sửa, bật/tắt) và *Bảng phí* (chọn hãng → bảng 3 vùng sửa tại chỗ, có ô "Thử tính phí": chọn vùng + số món → hiện phí).
- `/admin/payments`: tab *Cổng thanh toán* (thẻ COD / VNPay, form TMN + Hash Secret che, nút bật/tắt) và *Giao dịch* (bảng đối soát, lọc trạng thái, tìm kiếm).
- `/admin/settings`: form nhóm *Thông tin sàn / Vận chuyển / Đơn hàng*, lưu một lần, khối *Lịch sử thay đổi* bên dưới.

**Người mua** — tối thiểu để demo được:
- PDP: chọn biến thể + số lượng → nút **Mua ngay** → `/checkout` với món đó.
- `/checkout`: người nhận, SĐT, tỉnh → xã (select phụ thuộc), địa chỉ chi tiết; danh sách hãng kèm phí + ETA (gọi quote mỗi khi đổi tỉnh/số lượng); chọn COD/VNPay (COD disable kèm lý do nếu vượt giới hạn); tóm tắt tiền hàng + phí + tổng; đặt hàng. VNPay → `window.location` sang `paymentUrl`.
- `/payment/vnpay-return`: gọi backend verify → hiện thành công / thất bại, nút xem đơn / thanh toán lại.
- `/orders` + `/orders/:id`: đơn của tôi, trạng thái, hãng, phí ship, thanh toán, nút **Thanh toán lại** (còn hạn) và **Hủy**.

**Seller** — hiện Seller Center (`ShopDashboard.tsx`) chạy hoàn toàn trên dữ liệu mẫu `sellerData.ts`, chỉ form eKYC gọi API thật. Làm cả hai:
- Form eKYC thêm tỉnh → xã (bắt buộc), ô địa chỉ cũ thành "Địa chỉ chi tiết".
- **Nối tab Đơn hàng vào API thật** (`GET /api/seller/orders`, confirm / ship / deliver / cancel): seller xử lý được đơn người mua vừa đặt; đơn hiện hãng vận chuyển, phí ship, phương thức + trạng thái thanh toán. Đơn VNPay chưa thanh toán không hiện (backend đã lọc).
- **Nối tab Hồ sơ shop vào API thật** (đọc shop + `UpdateShopProfileRequest`): sửa tỉnh → xã + địa chỉ chi tiết cùng các field hồ sơ đang có; banner nhắc khi shop chưa có tỉnh.
- Các tab khác của Seller Center giữ dữ liệu mẫu như hiện tại. Nếu tab Đơn hàng / Hồ sơ cần field mà API chưa trả, bổ sung API cho đủ hiển thị — không giữ field mẫu giả.

## 9. Code (backend)

- Migration (số **từ V20**: V11/V12 đã bị các nhánh song song `feature/admin-order-monitoring` và `feature/admin-site-content` dùng; `out-of-order` đã bật): địa giới + seed, cột shop/address, carrier/rate/gateway/setting/audit + seed, sửa CHECK + cột mới của `orders`, `payment_transaction`.
- `features/location/` (phẳng): `LocationController`, `LocationService`, repository.
- `features/shipping/` (3 lớp, có luật): carrier, rate, `ShippingFeeCalculator` (hàm thuần, unit test kỹ), quote.
- `features/payment/` (3 lớp): gateway config, `VnpayClient` (ký / verify / dựng URL, hàm thuần), `PaymentService` (xử lý kết quả), `VnpayController` (return + IPN), job hết hạn.
- `features/settings/` (phẳng): `SystemSettingsService`, audit.
- `features/admin/`: `AdminShippingController`, `AdminPaymentController`, `AdminSettingsController` điều phối, giống `AdminProductController`.
- `OrderService.checkout` gọi `ShippingService` / `PaymentService` / `SystemSettingsService` qua service (luật `docs/structure.md`).
- `SecurityConfig`: `/api/admin/**` sẵn `hasRole('ADMIN')`; mở public `/api/locations/**`, `/api/payment-methods`, `/api/settings/public`, `/api/payments/vnpay/return`, `/api/payments/vnpay/ipn`.
- Cập nhật `docs/api-endpoints.md` (flow mới + checkbox), `docs/data.md` (schema mới).

## Kiểm thử

- TDD theo `docs/code-guide.md`. Bắt buộc có test:
  - `ShippingFeeCalculator`: 3 vùng, shop chưa có tỉnh, `items ≤ N`, `items > N`, freeship đúng ngưỡng / dưới ngưỡng, freeship tắt.
  - `VnpayClient`: ký ra đúng hash với bộ dữ liệu cố định (tự tính bằng HMAC-SHA512 trong test), verify từ chối khi sửa 1 tham số / sửa số tiền.
  - Xử lý kết quả: thành công, thất bại, chữ ký sai, lệch tiền, gọi lặp (idempotent), trả muộn sau khi đơn hết hạn → `REFUND_PENDING`.
  - Checkout: phí server tính bỏ qua client, hãng tắt, cổng tắt, COD vượt giới hạn, VNPay chưa cấu hình.
  - Job hết hạn hủy đúng đơn.
- `./gradlew test` xanh; UI `pnpm build` (chạy orval + tsc) + `pnpm test` xanh.
- Kiểm tay: có `tmnCode`/`hashSecret` sandbox thật → trả thẻ test NCB `9704198526191432198`, `NGUYEN VAN A`, `07/15`, OTP `123456` → đơn chuyển `PENDING` / `PAID`.

## Để builder quyết

- Bố cục, màu, component cụ thể của các trang (theo style admin hiện có: `glass`, indigo, Phosphor icons).
- Tên class/method, chia nhỏ migration thành mấy file.
- Cách sinh SQL seed xã/phường (script một lần, không commit script vào runtime).
