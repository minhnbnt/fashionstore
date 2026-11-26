package com.minhnbnt.fashionstore.features.voucher.service;

import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.voucher.AppliedVoucher;
import com.minhnbnt.fashionstore.dto.voucher.CreateShopVoucherRequest;
import com.minhnbnt.fashionstore.dto.voucher.PublicShopVoucherResponse;
import com.minhnbnt.fashionstore.dto.voucher.SellerVoucherDetailResponse;
import com.minhnbnt.fashionstore.dto.voucher.SellerVoucherResponse;
import com.minhnbnt.fashionstore.dto.voucher.UpdateShopVoucherRequest;
import com.minhnbnt.fashionstore.dto.voucher.VoucherLineItem;
import com.minhnbnt.fashionstore.dto.voucher.VoucherProductBrief;
import com.minhnbnt.fashionstore.dto.voucher.VoucherQuoteResult;
import com.minhnbnt.fashionstore.features.catalog.ProductStockService;
import com.minhnbnt.fashionstore.features.shop.ShopAuditService;
import com.minhnbnt.fashionstore.features.voucher.repository.ShopVoucherRepository;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.model.ShopVoucher;
import com.minhnbnt.fashionstore.model.VoucherProductScope;
import com.minhnbnt.fashionstore.model.VoucherStatus;
import com.minhnbnt.fashionstore.model.VoucherVisibility;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private final ShopVoucherRepository vouchers;

    private final ProductStockService productStock;

    private final ShopAccessChecker access;

    private final ShopAuditService audit;

    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<SellerVoucherResponse> listForSeller(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @Nullable String status,
        @Nullable String q,
        int page,
        int size
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.VOUCHER_READ, ShopPermission.VOUCHER_WRITE);

        var now = clock.instant();
        var all = vouchers.findByShopIdOrderByCreatedAtDesc(shopId);
        var voucherIds = all.stream().map(ShopVoucher::getId).toList();
        var productIdsByVoucher = vouchers.findProductIdsByVoucherIds(voucherIds);

        var filtered = new ArrayList<SellerVoucherResponse>();
        for (var voucher : all) {
            var productCount = 0;
            if (voucher.getProductScope() == VoucherProductScope.SPECIFIC_PRODUCTS) {
                var productIds = productIdsByVoucher.get(voucher.getId());
                if (productIds != null) {
                    productCount = productIds.size();
                }
            }
            var response = toSellerResponse(voucher, now, productCount);
            if (!matchesStatusFilter(response.status(), status)) {
                continue;
            }
            if (!matchesSearchQuery(response, q)) {
                continue;
            }
            filtered.add(response);
        }

        var pageSize = Math.clamp(size, 1, 100);
        var pageIndex = Math.max(page, 0);
        var offset = (long) pageIndex * pageSize;
        var items = filtered.stream()
            .skip(offset)
            .limit(pageSize)
            .toList();

        return new PageResponse<>(items, filtered.size(), pageIndex, pageSize);
    }

    @Transactional(readOnly = true)
    public SellerVoucherDetailResponse getForSeller(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID voucherId
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.VOUCHER_READ, ShopPermission.VOUCHER_WRITE);
        var voucher = requireShopVoucher(shopId, voucherId);
        return toDetailResponse(voucher, clock.instant());
    }

    @Transactional
    public SellerVoucherDetailResponse create(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull CreateShopVoucherRequest request
    ) {
        access.requireApprovedShopPermission(userId, shopId, ShopPermission.VOUCHER_WRITE);

        var description = "";
        if (request.description() != null) {
            description = request.description();
        }
        var active = true;
        if (request.active() != null) {
            active = request.active();
        }

        var validatedProductIds = resolveValidatedProductIds(shopId, request.productScope(), request.productIds());
        var now = clock.instant();

        var voucher = ShopVoucher.create(
            shopId,
            request.code(),
            request.name(),
            description,
            request.discountType(),
            request.discountValue(),
            request.maxDiscountAmount(),
            request.minOrderSubtotal(),
            request.productScope(),
            request.visibility(),
            request.usageLimit(),
            request.perBuyerLimit(),
            request.startsAt(),
            request.endsAt(),
            active,
            userId,
            now
        );

        if (vouchers.findByShopIdAndCode(shopId, voucher.getCode()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher code already exists in this shop");
        }

        try {
            vouchers.save(voucher);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher code already exists in this shop");
        }

        vouchers.replaceVoucherProducts(voucher.getId(), validatedProductIds);

        var detail = "Tạo mã giảm giá: " + voucher.getCode() + " (" + voucher.getName() + ")";
        audit.record(shopId, userId, "VOUCHER_CREATE", "VOUCHER", voucher.getId(), detail);

        return toDetailResponse(voucher, now);
    }

    @Transactional
    public SellerVoucherDetailResponse update(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID voucherId,
        @NonNull UpdateShopVoucherRequest request
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.VOUCHER_WRITE);
        var voucher = requireLockedShopVoucher(shopId, voucherId);

        var description = "";
        if (request.description() != null) {
            description = request.description();
        }
        var active = voucher.isActive();
        if (request.active() != null) {
            active = request.active();
        }

        var validatedProductIds = resolveValidatedProductIds(shopId, request.productScope(), request.productIds());
        var now = clock.instant();

        voucher.update(
            request.code(),
            request.name(),
            description,
            request.discountType(),
            request.discountValue(),
            request.maxDiscountAmount(),
            request.minOrderSubtotal(),
            request.productScope(),
            request.visibility(),
            request.usageLimit(),
            request.perBuyerLimit(),
            request.startsAt(),
            request.endsAt(),
            active,
            now
        );

        var existingWithCode = vouchers.findByShopIdAndCode(shopId, voucher.getCode()).orElse(null);
        if (existingWithCode != null && !existingWithCode.getId().equals(voucher.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher code already exists in this shop");
        }

        try {
            vouchers.save(voucher);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher code already exists in this shop");
        }

        vouchers.replaceVoucherProducts(voucher.getId(), validatedProductIds);

        var detail = "Cập nhật mã giảm giá: " + voucher.getCode() + " (" + voucher.getName() + ")";
        audit.record(shopId, userId, "VOUCHER_UPDATE", "VOUCHER", voucher.getId(), detail);

        return toDetailResponse(voucher, now);
    }

    @Transactional
    public SellerVoucherResponse toggleActive(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID voucherId,
        boolean active
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.VOUCHER_WRITE);
        var voucher = requireLockedShopVoucher(shopId, voucherId);
        var now = clock.instant();

        voucher.setActive(active, now);
        vouchers.save(voucher);

        var actionText = "Tạm dừng";
        if (active) {
            actionText = "Kích hoạt";
        }
        var detail = actionText + " mã giảm giá: " + voucher.getCode();
        audit.record(shopId, userId, "VOUCHER_STATUS", "VOUCHER", voucher.getId(), detail);

        var productCount = 0;
        if (voucher.getProductScope() == VoucherProductScope.SPECIFIC_PRODUCTS) {
            productCount = vouchers.findProductIdsByVoucherId(voucher.getId()).size();
        }
        return toSellerResponse(voucher, now, productCount);
    }

    @Transactional
    public void delete(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID voucherId
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.VOUCHER_WRITE);
        var voucher = requireLockedShopVoucher(shopId, voucherId);

        if (voucher.getUsedCount() > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot delete a voucher that has already been used; deactivate it instead"
            );
        }

        vouchers.deleteById(voucher.getId());

        var detail = "Xóa mã giảm giá: " + voucher.getCode() + " (" + voucher.getName() + ")";
        audit.record(shopId, userId, "VOUCHER_DELETE", "VOUCHER", voucher.getId(), detail);
    }

    @Transactional(readOnly = true)
    public List<PublicShopVoucherResponse> listPublicForShop(
        @NonNull UUID shopId,
        @Nullable UUID buyerId
    ) {
        var now = clock.instant();
        var all = vouchers.findByShopIdOrderByCreatedAtDesc(shopId);

        var visible = new ArrayList<ShopVoucher>();
        for (var voucher : all) {
            if (voucher.getVisibility() != VoucherVisibility.PUBLIC) {
                continue;
            }
            var status = voucher.statusAt(now);
            if (status == VoucherStatus.ONGOING || status == VoucherStatus.UPCOMING) {
                visible.add(voucher);
            }
        }

        visible.sort(
            Comparator.comparingInt((ShopVoucher v) -> publicStatusRank(v.statusAt(now)))
                .thenComparing(Comparator.comparingLong(ShopVoucher::getDiscountValue).reversed())
                .thenComparing(Comparator.comparing(ShopVoucher::getCreatedAt).reversed())
        );

        var voucherIds = visible.stream().map(ShopVoucher::getId).toList();
        var productIdsByVoucher = vouchers.findProductIdsByVoucherIds(voucherIds);

        var responses = new ArrayList<PublicShopVoucherResponse>(visible.size());
        for (var voucher : visible) {
            List<UUID> applicableProductIds = List.of();
            if (voucher.getProductScope() == VoucherProductScope.SPECIFIC_PRODUCTS) {
                var productIds = productIdsByVoucher.get(voucher.getId());
                if (productIds != null && !productIds.isEmpty()) {
                    applicableProductIds = List.copyOf(productIds);
                }
            }

            var exhaustedByBuyer = false;
            if (buyerId != null) {
                var usedByBuyer = vouchers.countActiveUsagesByBuyer(voucher.getId(), buyerId);
                exhaustedByBuyer = usedByBuyer >= voucher.getPerBuyerLimit();
            }

            responses.add(new PublicShopVoucherResponse(
                voucher.getId(),
                voucher.getShopId(),
                voucher.getCode(),
                voucher.getName(),
                voucher.getDescription(),
                voucher.getDiscountType(),
                voucher.getDiscountValue(),
                voucher.getMaxDiscountAmount(),
                voucher.getMinOrderSubtotal(),
                voucher.getProductScope(),
                voucher.getUsageLimit(),
                voucher.getUsedCount(),
                voucher.getPerBuyerLimit(),
                voucher.getStartsAt(),
                voucher.getEndsAt(),
                voucher.statusAt(now),
                applicableProductIds,
                exhaustedByBuyer
            ));
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public VoucherQuoteResult quoteForPreview(
        @NonNull UUID shopId,
        @NonNull UUID buyerId,
        @NonNull String rawCode,
        @NonNull List<VoucherLineItem> lines
    ) {
        var normalizedCode = rawCode.trim().toUpperCase(Locale.ROOT);
        var voucher = vouchers.findByShopIdAndCode(shopId, normalizedCode).orElse(null);
        if (voucher == null) {
            return new VoucherQuoteResult(
                null,
                normalizedCode,
                0L,
                "Mã giảm giá không tồn tại ở gian hàng này"
            );
        }

        var now = clock.instant();
        var status = voucher.statusAt(now);
        if (status == VoucherStatus.PAUSED) {
            return new VoucherQuoteResult(
                voucher.getId(),
                voucher.getCode(),
                0L,
                "Mã giảm giá hiện đang tạm dừng"
            );
        }
        if (status == VoucherStatus.EXPIRED) {
            return new VoucherQuoteResult(
                voucher.getId(),
                voucher.getCode(),
                0L,
                "Mã giảm giá đã hết hạn sử dụng"
            );
        }
        if (status == VoucherStatus.DEPLETED) {
            return new VoucherQuoteResult(
                voucher.getId(),
                voucher.getCode(),
                0L,
                "Mã giảm giá đã hết lượt sử dụng"
            );
        }
        if (status == VoucherStatus.UPCOMING) {
            return new VoucherQuoteResult(
                voucher.getId(),
                voucher.getCode(),
                0L,
                "Mã giảm giá chưa đến thời gian áp dụng"
            );
        }

        var usedByBuyer = vouchers.countActiveUsagesByBuyer(voucher.getId(), buyerId);
        if (usedByBuyer >= voucher.getPerBuyerLimit()) {
            return new VoucherQuoteResult(
                voucher.getId(),
                voucher.getCode(),
                0L,
                "Bạn đã hết lượt sử dụng mã giảm giá này"
            );
        }

        var eligibleSubtotal = computeEligibleSubtotal(voucher, lines);
        if (eligibleSubtotal <= 0L) {
            return new VoucherQuoteResult(
                voucher.getId(),
                voucher.getCode(),
                0L,
                "Không có sản phẩm nào trong đơn thuộc phạm vi áp dụng của mã"
            );
        }
        if (eligibleSubtotal < voucher.getMinOrderSubtotal()) {
            return new VoucherQuoteResult(
                voucher.getId(),
                voucher.getCode(),
                0L,
                "Đơn hàng chưa đạt giá trị tối thiểu để áp dụng mã"
            );
        }

        var discountAmount = voucher.calculateDiscount(eligibleSubtotal);
        return new VoucherQuoteResult(voucher.getId(), voucher.getCode(), discountAmount, null);
    }

    @Transactional
    public AppliedVoucher redeemForOrder(
        @NonNull UUID shopId,
        @NonNull UUID buyerId,
        @NonNull String rawCode,
        @NonNull List<VoucherLineItem> lines
    ) {
        var normalizedCode = rawCode.trim().toUpperCase(Locale.ROOT);
        var voucher = vouchers.findLockedByShopIdAndCode(shopId, normalizedCode).orElse(null);
        if (voucher == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher code not found in this shop");
        }

        var now = clock.instant();
        var status = voucher.statusAt(now);
        if (status == VoucherStatus.PAUSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher is currently paused");
        }
        if (status == VoucherStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher has expired");
        }
        if (status == VoucherStatus.DEPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher usage limit has been reached");
        }
        if (status == VoucherStatus.UPCOMING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher is not active yet");
        }

        var usedByBuyer = vouchers.countActiveUsagesByBuyer(voucher.getId(), buyerId);
        if (usedByBuyer >= voucher.getPerBuyerLimit()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Per-buyer usage limit has been reached for this voucher"
            );
        }

        var eligibleSubtotal = computeEligibleSubtotal(voucher, lines);
        if (eligibleSubtotal <= 0L) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "No items in this order are eligible for the voucher"
            );
        }
        if (eligibleSubtotal < voucher.getMinOrderSubtotal()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Order subtotal does not meet voucher minimum requirement"
            );
        }

        var discountAmount = voucher.calculateDiscount(eligibleSubtotal);
        voucher.incrementUsedCount(now);
        vouchers.save(voucher);

        return new AppliedVoucher(voucher.getId(), voucher.getCode(), discountAmount);
    }

    @Transactional
    public void releaseVoucherUsage(@Nullable UUID voucherId) {
        if (voucherId == null) {
            return;
        }
        vouchers.decrementUsedCount(voucherId, clock.instant());
    }

    private long computeEligibleSubtotal(ShopVoucher voucher, List<VoucherLineItem> lines) {
        if (voucher.getProductScope() == VoucherProductScope.ALL_PRODUCTS) {
            var sum = 0L;
            for (var line : lines) {
                sum = sum + line.lineTotal();
            }
            return sum;
        }

        var allowedProducts = new HashSet<>(vouchers.findProductIdsByVoucherId(voucher.getId()));
        var sum = 0L;
        for (var line : lines) {
            if (line.productId() != null && allowedProducts.contains(line.productId())) {
                sum = sum + line.lineTotal();
            }
        }
        return sum;
    }

    private List<UUID> resolveValidatedProductIds(
        UUID shopId,
        VoucherProductScope scope,
        @Nullable List<UUID> productIds
    ) {
        if (scope == VoucherProductScope.ALL_PRODUCTS) {
            return List.of();
        }
        if (productIds == null || productIds.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Specific products voucher requires at least one product"
            );
        }

        var uniqueIds = new LinkedHashSet<UUID>(productIds);
        for (var productId : uniqueIds) {
            var product = productStock.findProductById(productId).orElse(null);
            if (product == null
                || !product.getShopId().equals(shopId)
                || product.getStatus() == ProductStatus.DELETED) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product does not belong to this shop: " + productId
                );
            }
        }
        return List.copyOf(uniqueIds);
    }

    private ShopVoucher requireShopVoucher(UUID shopId, UUID voucherId) {
        var voucher = vouchers.findByShopIdAndId(shopId, voucherId).orElse(null);
        if (voucher == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher not found");
        }
        return voucher;
    }

    private ShopVoucher requireLockedShopVoucher(UUID shopId, UUID voucherId) {
        var voucher = vouchers.findLockedByShopIdAndId(shopId, voucherId).orElse(null);
        if (voucher == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher not found");
        }
        return voucher;
    }

    private SellerVoucherResponse toSellerResponse(ShopVoucher voucher, Instant now, int productCount) {
        return new SellerVoucherResponse(
            voucher.getId(),
            voucher.getShopId(),
            voucher.getCode(),
            voucher.getName(),
            voucher.getDescription(),
            voucher.getDiscountType(),
            voucher.getDiscountValue(),
            voucher.getMaxDiscountAmount(),
            voucher.getMinOrderSubtotal(),
            voucher.getProductScope(),
            voucher.getVisibility(),
            voucher.getUsageLimit(),
            voucher.getUsedCount(),
            voucher.getPerBuyerLimit(),
            voucher.getStartsAt(),
            voucher.getEndsAt(),
            voucher.isActive(),
            voucher.statusAt(now),
            productCount,
            voucher.getCreatedAt(),
            voucher.getUpdatedAt()
        );
    }

    private SellerVoucherDetailResponse toDetailResponse(ShopVoucher voucher, Instant now) {
        List<UUID> productIds = List.of();
        List<VoucherProductBrief> briefs = List.of();

        if (voucher.getProductScope() == VoucherProductScope.SPECIFIC_PRODUCTS) {
            productIds = vouchers.findProductIdsByVoucherId(voucher.getId());
            var briefList = new ArrayList<VoucherProductBrief>(productIds.size());
            for (var productId : productIds) {
                var product = productStock.findProductById(productId).orElse(null);
                if (product != null) {
                    briefList.add(new VoucherProductBrief(
                        product.getId(),
                        product.getName(),
                        product.getStatus().name()
                    ));
                }
            }
            briefs = briefList;
        }

        return new SellerVoucherDetailResponse(
            voucher.getId(),
            voucher.getShopId(),
            voucher.getCode(),
            voucher.getName(),
            voucher.getDescription(),
            voucher.getDiscountType(),
            voucher.getDiscountValue(),
            voucher.getMaxDiscountAmount(),
            voucher.getMinOrderSubtotal(),
            voucher.getProductScope(),
            voucher.getVisibility(),
            voucher.getUsageLimit(),
            voucher.getUsedCount(),
            voucher.getPerBuyerLimit(),
            voucher.getStartsAt(),
            voucher.getEndsAt(),
            voucher.isActive(),
            voucher.statusAt(now),
            productIds,
            briefs,
            voucher.getCreatedAt(),
            voucher.getUpdatedAt()
        );
    }

    private static boolean matchesStatusFilter(VoucherStatus actual, @Nullable String filter) {
        if (filter == null || filter.isBlank() || "ALL".equalsIgnoreCase(filter.trim())) {
            return true;
        }
        return actual.name().equalsIgnoreCase(filter.trim());
    }

    private static boolean matchesSearchQuery(SellerVoucherResponse voucher, @Nullable String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        var needle = q.trim().toLowerCase(Locale.ROOT);
        if (voucher.code().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return voucher.name().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static int publicStatusRank(VoucherStatus status) {
        if (status == VoucherStatus.ONGOING) {
            return 0;
        }
        return 1;
    }
}
