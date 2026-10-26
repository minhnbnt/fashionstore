package com.minhnbnt.fashionstore.dto.shop;

import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.ShopStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Thông tin gian hàng và vai trò thành viên của người dùng hiện tại")
public record SellerShopMembershipResponse(
    @Schema(description = "Id gian hàng", example = "e3a6a4f4-aca9-4612-88f7-097896db6aef")
    UUID shopId,

    @Schema(description = "Tên gian hàng", example = "Nét Boutique")
    String shopName,

    @Schema(description = "Slug gian hàng", example = "net-boutique")
    String shopSlug,

    @Schema(description = "S3 key logo gian hàng")
    @Nullable String logoKey,

    @Schema(description = "S3 key ảnh bìa gian hàng")
    @Nullable String bannerKey,

    @Schema(description = "Trạng thái phê duyệt gian hàng", example = "APPROVED")
    ShopStatus shopStatus,

    @Schema(description = "Vai trò trong shop: OWNER hoặc STAFF", example = "STAFF")
    MemberRole role,

    @Schema(description = "Id vai trò tùy chỉnh (nếu là STAFF)")
    @Nullable UUID roleId,

    @Schema(description = "Tên vai trò hiển thị", example = "Kho vận (Warehouse)")
    String roleName,

    @Schema(description = "Trạng thái thành viên: ACTIVE hoặc DEACTIVATED", example = "ACTIVE")
    String memberStatus,

    @Schema(description = "Thời điểm gia nhập shop")
    Instant joinedAt,

    @Schema(description = "Danh sách quyền thao tác trong shop")
    List<String> permissions
) {
}
