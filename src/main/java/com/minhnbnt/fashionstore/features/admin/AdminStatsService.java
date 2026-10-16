package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.dto.admin.AdminStatsResponse;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.AccountStatus;
import com.minhnbnt.fashionstore.model.ShopStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final ShopService shops;

    private final UserService users;

    @Transactional(readOnly = true)
    public AdminStatsResponse get() {
        var shopCounts = shops.countByStatus();
        var userCounts = users.countByStatus();

        long totalShops = 0;
        for (var count : shopCounts.values()) {
            totalShops += count;
        }

        long totalUsers = 0;
        for (var count : userCounts.values()) {
            totalUsers += count;
        }

        return new AdminStatsResponse(
            totalUsers,
            countOf(userCounts, AccountStatus.LOCKED),
            totalShops,
            countOf(shopCounts, ShopStatus.PENDING),
            countOf(shopCounts, ShopStatus.APPROVED),
            countOf(shopCounts, ShopStatus.SUSPENDED),
            countOf(shopCounts, ShopStatus.REJECTED)
        );
    }

    private static <K> long countOf(Map<K, Long> counts, K key) {
        return Objects.requireNonNullElse(counts.get(key), 0L);
    }
}
