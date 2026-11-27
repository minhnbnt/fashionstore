package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.order.CreateWithdrawalRequest;
import com.minhnbnt.fashionstore.dto.order.DailyRevenueResponse;
import com.minhnbnt.fashionstore.dto.order.RevenueSummaryResponse;
import com.minhnbnt.fashionstore.dto.order.SettlementResponse;
import com.minhnbnt.fashionstore.dto.order.TopProductRevenueResponse;
import com.minhnbnt.fashionstore.dto.order.WithdrawalResponse;
import com.minhnbnt.fashionstore.dto.payment.AdminWithdrawalResponse;
import com.minhnbnt.fashionstore.features.order.repository.OrderItemRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.SellerSettlementRepository;
import com.minhnbnt.fashionstore.features.order.repository.SellerWithdrawalRepository;
import com.minhnbnt.fashionstore.features.settings.ConfigAuditService;
import com.minhnbnt.fashionstore.features.shop.ShopAuditService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.ConfigAuditEntity;
import com.minhnbnt.fashionstore.model.SellerSettlement;
import com.minhnbnt.fashionstore.model.SellerWithdrawal;
import com.minhnbnt.fashionstore.model.SettlementStatus;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.model.WithdrawalStatus;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RevenueService {

    private final SellerSettlementRepository settlements;

    private final SellerWithdrawalRepository withdrawals;

    private final OrderRepository orders;

    private final OrderItemRepository items;

    private final ShopService shops;

    private final ShopAccessChecker access;

    private final ShopAuditService audit;

    private final ConfigAuditService configAudit;

    @Transactional
    public void createSettlementForOrder(UUID shopId, UUID orderId, long grossAmount) {
        var existing = settlements.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            return;
        }
        var platformFee = SellerSettlement.calculatePlatformFee(grossAmount);
        try {
            settlements.save(SellerSettlement.create(shopId, orderId, grossAmount, platformFee));
        } catch (DataIntegrityViolationException ignored) {
            // Concurrent duplicate settlement creation for the same orderId; uq_seller_settlement_order enforces 1 row.
        }
    }

    @Transactional
    public void releaseSettlementForOrder(UUID orderId) {
        var settlement = findLockedSettlementByOrderId(orderId).orElse(null);
        if (settlement == null || settlement.getStatus() != SettlementStatus.HOLDING) {
            return;
        }
        settlement.release();
        settlements.save(settlement);
    }

    @Transactional
    public void cancelSettlementForOrder(UUID orderId) {
        var settlement = findLockedSettlementByOrderId(orderId).orElse(null);
        if (settlement == null
            || (settlement.getStatus() != SettlementStatus.HOLDING
                && settlement.getStatus() != SettlementStatus.RELEASED)) {
            return;
        }
        settlement.cancel();
        settlements.save(settlement);
    }

    @Transactional
    public void adjustSettlementOnReturn(UUID orderId, long refundAmount) {
        var settlement = findLockedSettlementByOrderId(orderId).orElse(null);
        if (settlement == null
            || (settlement.getStatus() != SettlementStatus.HOLDING
                && settlement.getStatus() != SettlementStatus.RELEASED)) {
            return;
        }
        if (refundAmount >= settlement.getGrossAmount()) {
            settlement.cancel();
        } else if (refundAmount > 0L) {
            settlement.adjustAndRelease(settlement.getGrossAmount() - refundAmount);
        } else {
            settlement.release();
        }
        settlements.save(settlement);
    }

    @Transactional(readOnly = true)
    public RevenueSummaryResponse getSummary(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var row = orders.sumDeliveredRevenue(shopId);
        var walletBalance = settlements.sumAvailableBalance(shopId) - withdrawals.sumActiveWithdrawals(shopId);
        var holdingBalance = settlements.sumHoldingBalance(shopId);

        return new RevenueSummaryResponse(
            row.totalRevenue(),
            row.totalOrders(),
            row.avgOrderValue(),
            walletBalance,
            holdingBalance
        );
    }

    @Transactional(readOnly = true)
    public List<DailyRevenueResponse> getDailyRevenue(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        return orders.sumDailyRevenue(shopId).stream()
            .map(row -> new DailyRevenueResponse(
                row.day().toString(),
                row.revenue(),
                (int) row.orders()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TopProductRevenueResponse> getTopProducts(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        int limit
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var max = Math.clamp(limit, 1, 50);
        return items.findTopProducts(shopId, max).stream()
            .map(row -> new TopProductRevenueResponse(
                row.productId(),
                row.productName(),
                (int) row.totalQuantity(),
                row.totalRevenue()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> listSettlements(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @Nullable String statusFilter
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        List<SellerSettlement> list;
        if (statusFilter != null && !statusFilter.isBlank()) {
            list = settlements.findByShopIdAndStatusOrderByCreatedAtDesc(
                shopId,
                SettlementStatus.valueOf(statusFilter.toUpperCase(Locale.ROOT))
            );
        } else {
            list = settlements.findByShopIdOrderByCreatedAtDesc(shopId);
        }

        return list.stream()
            .map(s -> new SettlementResponse(
                s.getId(),
                s.getShopId(),
                s.getOrderId(),
                s.getGrossAmount(),
                s.getPlatformFee(),
                s.getNetAmount(),
                s.getStatus().name(),
                s.getHoldUntil(),
                s.getReleasedAt(),
                s.getCreatedAt()
            ))
            .toList();
    }

    @Transactional
    public WithdrawalResponse createWithdrawal(
        @NonNull UUID userId,
        @NonNull CreateWithdrawalRequest request
    ) {
        access.requireShopPermission(userId, request.shopId(), ShopPermission.REVENUE_WITHDRAW);

        settlements.lockReleasedByShopId(request.shopId());
        var available = settlements.sumAvailableBalance(request.shopId())
            - withdrawals.sumActiveWithdrawals(request.shopId());
        if (request.amount() <= 0L || request.amount() > available) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient available balance");
        }

        var shop = shops.findEntityById(request.shopId()).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }

        var bankName = request.bankName();
        if (bankName == null || bankName.isBlank()) {
            bankName = shop.getBankName();
        }
        var bankAccount = request.bankAccount();
        if (bankAccount == null || bankAccount.isBlank()) {
            bankAccount = shop.getBankAccount();
        }
        var bankHolder = request.bankHolder();
        if (bankHolder == null || bankHolder.isBlank()) {
            bankHolder = shop.getBankHolder();
        }

        if (bankName == null || bankAccount == null || bankHolder == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Bank information required for withdrawal"
            );
        }

        var withdrawal = SellerWithdrawal.request(
            request.shopId(),
            request.amount(),
            bankName,
            bankAccount,
            bankHolder
        );

        var saved = withdrawals.save(withdrawal);
        var withdrawalDetail = "Yêu cầu rút " + saved.getAmount() + "₫ | Ngân hàng: " + bankName
            + " | Số tài khoản: " + bankAccount + " | Chủ tài khoản: " + bankHolder;
        audit.record(
            saved.getShopId(),
            userId,
            "WITHDRAWAL_CREATE",
            "WITHDRAWAL",
            saved.getId(),
            withdrawalDetail
        );

        return toWithdrawalResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WithdrawalResponse> listWithdrawals(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        return withdrawals.findByShopIdOrderByCreatedAtDesc(shopId).stream()
            .map(RevenueService::toWithdrawalResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminWithdrawalResponse> listAdminWithdrawals(
        @Nullable String status,
        int page,
        int size
    ) {
        var safePage = Math.max(0, page);
        var safeSize = Math.clamp(size, 1, 100);
        var offset = (long) safePage * safeSize;

        List<SellerWithdrawal> list;
        long total;
        if (status != null && !status.isBlank()) {
            WithdrawalStatus parsedStatus;
            try {
                parsedStatus = WithdrawalStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid withdrawal status: " + status
                );
            }
            list = withdrawals.findByStatusPaged(parsedStatus.name(), safeSize, offset);
            total = withdrawals.countByStatus(parsedStatus.name());
        } else {
            list = withdrawals.findAllPaged(safeSize, offset);
            total = withdrawals.count();
        }

        var shopIds = new LinkedHashSet<UUID>();
        for (var w : list) {
            shopIds.add(w.getShopId());
        }
        var summaries = shops.findShopSummariesByIds(shopIds);

        var responses = list.stream()
            .map(w -> {
                var summary = summaries.get(w.getShopId());
                String shopName;
                if (summary != null) {
                    shopName = summary.name();
                } else {
                    shopName = resolveShopName(w.getShopId());
                }
                return toAdminWithdrawalResponse(w, shopName);
            })
            .toList();

        return new PageResponse<>(responses, total, safePage, safeSize);
    }

    @Transactional
    public AdminWithdrawalResponse approveWithdrawal(@NonNull UUID adminId, @NonNull UUID withdrawalId) {
        var withdrawal = requireLockedWithdrawal(withdrawalId);
        if (withdrawal.getStatus() != WithdrawalStatus.REQUESTED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Only requested withdrawals can be approved"
            );
        }

        var oldSnapshot = toWithdrawalAuditSnapshot(withdrawal);
        withdrawal.complete();
        var saved = withdrawals.save(withdrawal);

        configAudit.record(
            ConfigAuditEntity.PAYMENT_TRANSACTION,
            saved.getId().toString(),
            "WITHDRAWAL_APPROVE",
            oldSnapshot,
            toWithdrawalAuditSnapshot(saved),
            adminId
        );

        return toAdminWithdrawalResponse(saved, resolveShopName(saved.getShopId()));
    }

    @Transactional
    public AdminWithdrawalResponse rejectWithdrawal(@NonNull UUID adminId, @NonNull UUID withdrawalId) {
        var withdrawal = requireLockedWithdrawal(withdrawalId);
        if (withdrawal.getStatus() != WithdrawalStatus.REQUESTED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Only requested withdrawals can be rejected"
            );
        }

        var oldSnapshot = toWithdrawalAuditSnapshot(withdrawal);
        withdrawal.reject();
        var saved = withdrawals.save(withdrawal);

        configAudit.record(
            ConfigAuditEntity.PAYMENT_TRANSACTION,
            saved.getId().toString(),
            "WITHDRAWAL_REJECT",
            oldSnapshot,
            toWithdrawalAuditSnapshot(saved),
            adminId
        );

        return toAdminWithdrawalResponse(saved, resolveShopName(saved.getShopId()));
    }

    private Optional<SellerSettlement> findLockedSettlementByOrderId(UUID orderId) {
        var locked = settlements.findLockedByOrderId(orderId);
        if (locked != null && locked.isPresent()) {
            return locked;
        }
        return settlements.findByOrderId(orderId);
    }

    private SellerWithdrawal requireLockedWithdrawal(UUID withdrawalId) {
        var locked = withdrawals.findLockedById(withdrawalId);
        if (locked != null && locked.isPresent()) {
            return locked.get();
        }
        var found = withdrawals.findById(withdrawalId).orElse(null);
        if (found == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Withdrawal not found");
        }
        return found;
    }

    private String resolveShopName(UUID shopId) {
        var name = shops.getShopName(shopId);
        if (name == null || name.isBlank()) {
            return "Cửa hàng";
        }
        return name;
    }

    private static WithdrawalResponse toWithdrawalResponse(SellerWithdrawal w) {
        return new WithdrawalResponse(
            w.getId(),
            w.getShopId(),
            w.getAmount(),
            w.getBankName(),
            w.getBankAccount(),
            w.getBankHolder(),
            w.getStatus().name(),
            w.getCreatedAt(),
            w.getProcessedAt()
        );
    }

    private static AdminWithdrawalResponse toAdminWithdrawalResponse(SellerWithdrawal w, String shopName) {
        return new AdminWithdrawalResponse(
            w.getId(),
            w.getShopId(),
            shopName,
            w.getAmount(),
            w.getBankName(),
            w.getBankAccount(),
            w.getBankHolder(),
            w.getStatus().name(),
            w.getCreatedAt(),
            w.getProcessedAt()
        );
    }

    private static Map<String, @Nullable Object> toWithdrawalAuditSnapshot(SellerWithdrawal w) {
        var map = new LinkedHashMap<String, @Nullable Object>();
        map.put("id", w.getId().toString());
        map.put("shopId", w.getShopId().toString());
        map.put("amount", w.getAmount());
        map.put("bankName", w.getBankName());
        map.put("bankAccount", w.getBankAccount());
        map.put("bankHolder", w.getBankHolder());
        map.put("status", w.getStatus().name());
        map.put("processedAt", w.getProcessedAt());
        return map;
    }
}
