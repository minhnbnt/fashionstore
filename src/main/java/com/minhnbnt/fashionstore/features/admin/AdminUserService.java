package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.dto.admin.AdminUserDetailResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminUserResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.identity.UserResponse;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.infrastructure.GoTrueAdminClient;
import com.minhnbnt.fashionstore.model.AccountStatus;
import com.minhnbnt.fashionstore.model.UserRole;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Điều phối khóa/mở khóa tài khoản: DB (identity + shop) rồi mới tới GoTrue, lỗi GoTrue thì rollback.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String OWNER_LOCKED_PREFIX = "Owner account locked: ";

    private final UserService users;

    private final ShopService shops;

    private final OrderService orders;

    private final GoTrueAdminClient gotrue;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> search(
        @Nullable AccountStatus status,
        @Nullable UserRole role,
        @Nullable String query,
        int page,
        int size
    ) {
        return users.searchForAdmin(status, role, query, page, size);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getDetail(@NonNull UUID userId) {
        var user = users.getForAdmin(userId);

        return new AdminUserDetailResponse(
            user,
            shops.getMine(userId),
            orders.countByBuyer(userId),
            users.historyOf(userId)
        );
    }

    /**
     * Khóa tài khoản: chặn backend ngay, tạm khóa shop họ làm OWNER (shop không có người vận hành
     * thì không được nhận đơn mới), cuối cùng ban ở GoTrue để không đăng nhập/refresh được nữa.
     */
    @Transactional
    public UserResponse lock(@NonNull UUID adminId, @NonNull UUID userId, @NonNull String reason) {
        if (adminId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot lock your own account");
        }

        var locked = users.lock(adminId, userId, reason);
        shops.suspendOwnedShops(userId, adminId, OWNER_LOCKED_PREFIX + reason);
        gotrue.banUser(userId);

        return locked;
    }

    /**
     * Mở khóa không tự mở lại shop: admin xem xét từng shop rồi mở riêng.
     */
    @Transactional
    public UserResponse unlock(@NonNull UUID adminId, @NonNull UUID userId) {
        var unlocked = users.unlock(adminId, userId);
        gotrue.unbanUser(userId);

        return unlocked;
    }
}
