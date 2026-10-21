package com.minhnbnt.fashionstore.infrastructure;

import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CreateWithdrawalRequest;
import com.minhnbnt.fashionstore.dto.shop.ChangeMemberRoleRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRoleRequest;
import com.minhnbnt.fashionstore.dto.shop.InviteStaffRequest;
import com.minhnbnt.fashionstore.dto.shop.UpdateShopProfileRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.RevenueService;
import com.minhnbnt.fashionstore.features.order.repository.SellerSettlementRepository;
import com.minhnbnt.fashionstore.features.order.repository.SellerWithdrawalRepository;
import com.minhnbnt.fashionstore.features.shop.ShopDecorationService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.shop.ShopStaffService;
import com.minhnbnt.fashionstore.model.SellerSettlement;
import com.minhnbnt.fashionstore.model.SettlementStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Seeds shop operations demo data: extended profile, staff roles and members,
 * decoration draft, and wallet history (settlements + withdrawals).
 * Package-private collaborator of {@link DemoSeeder}, not a Spring bean.
 * Dev-only: never enabled outside {@code app.demo-seed=true}.
 */
class DemoShopOpsSeeder {

    private final ShopService shops;

    private final ShopStaffService staff;

    private final ShopDecorationService decoration;

    private final RevenueService revenue;

    private final SellerSettlementRepository settlements;

    private final SellerWithdrawalRepository withdrawals;

    private final UserService users;

    DemoShopOpsSeeder(
        ShopService shops,
        ShopStaffService staff,
        ShopDecorationService decoration,
        RevenueService revenue,
        SellerSettlementRepository settlements,
        SellerWithdrawalRepository withdrawals,
        UserService users
    ) {
        this.shops = shops;
        this.staff = staff;
        this.decoration = decoration;
        this.revenue = revenue;
        this.settlements = settlements;
        this.withdrawals = withdrawals;
        this.users = users;
    }

    void seedProfile(UUID ownerId, UUID shopId) {
        shops.updateProfile(ownerId, shopId, new UpdateShopProfileRequest(
            "0901234567",
            "12 Nguyễn Trãi, Thanh Xuân, Hà Nội",
            "Đổi trả trong 7 ngày nếu sản phẩm có lỗi từ nhà sản xuất.",
            2,
            "Vietcombank",
            "0011002233445",
            "DEMO OWNER",
            "Chào bạn, shop đang bận chút ạ. Bạn để lại tin nhắn, shop sẽ trả lời ngay khi thấy nhé!",
            "{\"mon\":\"08:00-21:30\",\"tue\":\"08:00-21:30\",\"wed\":\"08:00-21:30\","
                + "\"thu\":\"08:00-21:30\",\"fri\":\"08:00-22:00\",\"sat\":\"09:00-22:00\",\"sun\":\"09:00-21:00\"}"
        ));
    }

    void seedStaff(UUID ownerId, UUID shopId) {
        var cskh = staff.createRole(ownerId, shopId, new CreateShopRoleRequest(
            "Chăm sóc khách hàng",
            List.of("ORDER_READ", "REVIEW_READ", "REVIEW_REPLY", "CHAT_ACCESS")
        ));
        var kho = staff.createRole(ownerId, shopId, new CreateShopRoleRequest(
            "Kho vận",
            List.of("ORDER_READ", "ORDER_PROCESS", "INVENTORY_READ", "INVENTORY_WRITE")
        ));

        var hanh = users.upsert(UUID.randomUUID(), new CreateUserRequest(
            "hanh@demo.local",
            "Hạnh, CSKH",
            null
        ));
        var dung = users.upsert(UUID.randomUUID(), new CreateUserRequest(
            "dung@demo.local",
            "Dũng, Kho vận",
            null
        ));

        var hanhInvite = staff.inviteStaff(ownerId, shopId, new InviteStaffRequest("hanh@demo.local", kho.id()));
        staff.acceptInvitation(hanh.id(), hanhInvite.token());
        var dungInvite = staff.inviteStaff(ownerId, shopId, new InviteStaffRequest("dung@demo.local", kho.id()));
        staff.acceptInvitation(dung.id(), dungInvite.token());

        // One role change so the audit log has a natural entry.
        staff.changeMemberRole(ownerId, shopId, hanh.id(), new ChangeMemberRoleRequest(cskh.id()));
    }

    void seedDecoration(UUID ownerId, UUID shopId) {
        var draftJson = """
            [
              {"id":"b-1","type":"BANNER","title":"Banner Khuyến Mãi Đầu Mùa","enabled":true},
              {"id":"b-2","type":"FLASH_SALE","title":"Đồng Hồ Flash Sale Đếm Ngược","enabled":true},
              {"id":"b-3","type":"COLLECTION","title":"Bộ Sưu Tập Mới Nhất 2026","enabled":true},
              {"id":"b-4","type":"VIDEO","title":"Video Trải Nghiệm Sản Phẩm","enabled":false}
            ]
            """;
        decoration.saveDraft(ownerId, shopId, draftJson);
    }

    void seedRevenue(UUID ownerId, UUID shopId, UUID releaseOrderId) {
        // Demo shortcut: no release job exists in the backend yet, so the row is
        // rebuilt through the persistence constructor to backdate and release it.
        var holding = settlements.findByOrderId(releaseOrderId).orElseThrow();
        var releasedAt = Instant.now();
        var released = new SellerSettlement(
            holding.getId(),
            holding.getShopId(),
            holding.getOrderId(),
            holding.getGrossAmount(),
            holding.getPlatformFee(),
            holding.getNetAmount(),
            SettlementStatus.RELEASED,
            releasedAt.minus(2, ChronoUnit.DAYS),
            releasedAt,
            holding.getCreatedAt()
        );
        settlements.save(released);

        // Two withdrawals: one already completed (history), one still REQUESTED.
        var done = revenue.createWithdrawal(ownerId, new CreateWithdrawalRequest(shopId, 100000L, null, null, null));
        revenue.createWithdrawal(ownerId, new CreateWithdrawalRequest(shopId, 150000L, null, null, null));

        // Demo shortcut: no admin processing flow exists yet, complete one row directly.
        var completed = withdrawals.findById(done.id()).orElseThrow();
        completed.complete();
        withdrawals.save(completed);
    }
}
