package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Thông tin khách hàng mua nhiều nhất")
public record TopCustomerResponse(
    @Schema(description = "ID người mua")
    UUID buyerId,

    @Schema(description = "Tên hiển thị người mua", example = "Nguyễn Văn A")
    String buyerName,

    @Schema(description = "Tổng số tiền đã chi (VND)", example = "3500000")
    long totalSpent,

    @Schema(description = "Số lượng đơn hàng đã đặt", example = "8")
    int orderCount,

    @Schema(description = "Thời điểm đơn hàng gần nhất (ISO-8601)", example = "2026-10-24T14:30:00Z")
    String lastOrderAt
) {}
