package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AdminStatsResponse(
    @Schema(description = "Tổng tài khoản")
    long totalUsers,

    @Schema(description = "Tài khoản đang bị khóa")
    long lockedUsers,

    @Schema(description = "Tài khoản role CUSTOMER")
    long customerUsers,

    @Schema(description = "Tài khoản role SHOP (chủ shop đã được duyệt)")
    long sellerUsers,

    @Schema(description = "Tài khoản role ADMIN")
    long adminUsers,

    @Schema(description = "Tổng shop")
    long totalShops,

    @Schema(description = "Shop chờ duyệt")
    long pendingShops,

    @Schema(description = "Shop đang hoạt động")
    long approvedShops,

    @Schema(description = "Shop bị tạm khóa")
    long suspendedShops,

    @Schema(description = "Shop bị từ chối")
    long rejectedShops,

    @Schema(description = "Tổng đơn hàng")
    long totalOrders,

    @Schema(description = "Đơn chờ shop xác nhận")
    long pendingOrders,

    @Schema(description = "Đơn đã xác nhận, chờ giao")
    long confirmedOrders,

    @Schema(description = "Đơn đang giao")
    long shippingOrders,

    @Schema(description = "Đơn đã giao")
    long deliveredOrders,

    @Schema(description = "Đơn đã hủy")
    long cancelledOrders,

    @Schema(description = "Tổng giá trị đơn chưa hủy (VND)")
    long grossMerchandiseValue,

    @Schema(description = "Doanh thu COD đã thu (VND)")
    long collectedRevenue,

    @Schema(description = "Số báo cáo sản phẩm đang chờ xử lý (người mua + hệ thống)")
    long openProductReports,

    @Schema(description = "Tổng sản phẩm (mọi trạng thái)")
    long totalProducts,

    @Schema(description = "Sản phẩm đang bán (ACTIVE)")
    long activeProducts,

    @Schema(description = "Sản phẩm chờ admin duyệt (PENDING)")
    long pendingProducts,

    @Schema(description = "Sản phẩm shop tự ẩn (HIDDEN)")
    long hiddenProducts,

    @Schema(description = "Sản phẩm bị gỡ do vi phạm (BANNED)")
    long bannedProducts,

    @Schema(description = "Tổng đánh giá sản phẩm toàn sàn")
    long totalReviews,

    @Schema(description = "Điểm đánh giá trung bình toàn sàn (1-5), 0 khi chưa có đánh giá")
    double averageRating,

    @Schema(description = "Yêu cầu đổi trả đang chờ shop xử lý (REQUESTED)")
    long openReturnRequests,

    @Schema(description = "Thời gian trung bình từ lúc đặt tới lúc shop xác nhận, 30 ngày gần nhất (giờ)")
    double avgConfirmHours,

    @Schema(description = "Thời gian trung bình từ lúc đặt tới lúc giao thành công, 30 ngày gần nhất (giờ)")
    double avgDeliveryHours,

    @Schema(description = "Số người mua có ít nhất 1 đơn chưa hủy trong 30 ngày gần nhất")
    long activeBuyers,

    @Schema(description = "Số người mua có từ 2 đơn chưa hủy trở lên trong 30 ngày gần nhất")
    long repeatBuyers,

    @Schema(description = "Top 5 shop theo GMV 30 ngày gần nhất, cao trước")
    List<AdminTopShopResponse> topShops,

    @Schema(description = "Top 5 sản phẩm theo số lượng bán 30 ngày gần nhất, cao trước")
    List<AdminTopProductResponse> topProducts,

    @Schema(description = "Top 5 danh mục theo doanh số 30 ngày gần nhất, cao trước")
    List<AdminTopCategoryResponse> topCategories,

    @Schema(description = "Số liệu theo ngày (giờ Việt Nam), 60 ngày gần nhất, cũ trước mới sau, ngày cuối là hôm nay")
    List<AdminDailyStatResponse> dailyTrend
) {
}
