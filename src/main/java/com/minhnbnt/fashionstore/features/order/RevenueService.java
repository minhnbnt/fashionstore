package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.dto.order.CreateWithdrawalRequest;
import com.minhnbnt.fashionstore.dto.order.DailyRevenueResponse;
import com.minhnbnt.fashionstore.dto.order.RevenueSummaryResponse;
import com.minhnbnt.fashionstore.dto.order.SettlementResponse;
import com.minhnbnt.fashionstore.dto.order.TopProductRevenueResponse;
import com.minhnbnt.fashionstore.dto.order.WithdrawalResponse;
import com.minhnbnt.fashionstore.features.order.repository.OrderItemRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.SellerSettlementRepository;
import com.minhnbnt.fashionstore.features.order.repository.SellerWithdrawalRepository;
import com.minhnbnt.fashionstore.features.shop.ShopAuditService;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.model.SellerSettlement;
import com.minhnbnt.fashionstore.model.SellerWithdrawal;
import com.minhnbnt.fashionstore.model.SettlementStatus;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RevenueService {

    private final SellerSettlementRepository settlements;

    private final SellerWithdrawalRepository withdrawals;

    private final OrderRepository orders;

    private final OrderItemRepository items;

    private final ShopRepository shops;

    private final ShopAccessChecker access;

    private final ShopAuditService audit;

    @Transactional
    public void createSettlementForOrder(UUID shopId, UUID orderId, long grossAmount) {
        var existing = settlements.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            return;
        }
        var platformFee = 0L;
        settlements.save(SellerSettlement.create(shopId, orderId, grossAmount, platformFee));
    }

    @Transactional(readOnly = true)
    public RevenueSummaryResponse getSummary(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVENUE_READ);

        var row = orders.sumDeliveredRevenue(shopId);
        var walletBalance = settlements.sumAvailableBalance(shopId);
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

        var available = settlements.sumAvailableBalance(request.shopId());
        if (request.amount() > available) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient available balance");
        }

        var shop = shops.findById(request.shopId()).orElse(null);
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
}
