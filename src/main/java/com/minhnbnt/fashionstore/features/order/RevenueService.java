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
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.OrderStatus;
import com.minhnbnt.fashionstore.model.SellerSettlement;
import com.minhnbnt.fashionstore.model.SellerWithdrawal;
import com.minhnbnt.fashionstore.model.SettlementStatus;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

        var allOrders = orders.findByShopIdOrderByCreatedAtDesc(shopId);
        var deliveredOrders = allOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
            .toList();

        var totalRevenue = deliveredOrders.stream().mapToLong(o -> o.getSubtotal()).sum();
        var totalCount = (long) deliveredOrders.size();
        var avgValue = 0L;
        if (totalCount > 0) {
            avgValue = totalRevenue / totalCount;
        }

        var walletBalance = settlements.sumAvailableBalance(shopId);
        var holdingBalance = settlements.sumHoldingBalance(shopId);

        return new RevenueSummaryResponse(
            totalRevenue,
            totalCount,
            avgValue,
            walletBalance,
            holdingBalance
        );
    }

    @Transactional(readOnly = true)
    public List<DailyRevenueResponse> getDailyRevenue(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

        var deliveredOrders = orders.findByShopIdOrderByCreatedAtDesc(shopId).stream()
            .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
            .toList();

        var map = new HashMap<LocalDate, long[]>();
        for (var o : deliveredOrders) {
            var date = LocalDate.ofInstant(o.getCreatedAt(), ZoneOffset.UTC);
            var arr = map.computeIfAbsent(date, k -> new long[]{0L, 0L});
            arr[0] += o.getSubtotal();
            arr[1]++;
        }

        var result = new ArrayList<DailyRevenueResponse>();
        for (var entry : map.entrySet()) {
            result.add(new DailyRevenueResponse(
                entry.getKey().toString(),
                entry.getValue()[0],
                (int) entry.getValue()[1]
            ));
        }
        result.sort((a, b) -> b.date().compareTo(a.date()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<TopProductRevenueResponse> getTopProducts(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        int limit
    ) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

        var deliveredOrders = orders.findByShopIdOrderByCreatedAtDesc(shopId).stream()
            .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
            .toList();

        var productStats = new HashMap<UUID, long[]>();
        var productNames = new HashMap<UUID, String>();

        for (var o : deliveredOrders) {
            for (var item : items.findByOrderId(o.getId())) {
                if (item.getProductId() != null) {
                    var stats = productStats.computeIfAbsent(item.getProductId(), k -> new long[]{0L, 0L});
                    stats[0] += item.getQuantity();
                    stats[1] += item.getLineTotal();
                    productNames.putIfAbsent(item.getProductId(), item.getProductName());
                }
            }
        }

        var result = new ArrayList<TopProductRevenueResponse>();
        for (var entry : productStats.entrySet()) {
            result.add(new TopProductRevenueResponse(
                entry.getKey(),
                productNames.getOrDefault(entry.getKey(), "Unknown"),
                (int) entry.getValue()[0],
                entry.getValue()[1]
            ));
        }

        result.sort((a, b) -> Long.compare(b.totalRevenue(), a.totalRevenue()));

        var max = Math.clamp(limit, 1, 50);
        return result.stream().limit(max).toList();
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> listSettlements(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @Nullable String statusFilter
    ) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

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
        access.requireShopRole(userId, request.shopId(), MemberRole.OWNER);

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

        return toWithdrawalResponse(withdrawals.save(withdrawal));
    }

    @Transactional(readOnly = true)
    public List<WithdrawalResponse> listWithdrawals(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

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
