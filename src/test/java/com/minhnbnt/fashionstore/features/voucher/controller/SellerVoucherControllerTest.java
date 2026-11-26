package com.minhnbnt.fashionstore.features.voucher.controller;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.voucher.PublicShopVoucherResponse;
import com.minhnbnt.fashionstore.dto.voucher.SellerVoucherDetailResponse;
import com.minhnbnt.fashionstore.dto.voucher.SellerVoucherResponse;
import com.minhnbnt.fashionstore.features.voucher.service.VoucherService;
import com.minhnbnt.fashionstore.model.VoucherDiscountType;
import com.minhnbnt.fashionstore.model.VoucherProductScope;
import com.minhnbnt.fashionstore.model.VoucherStatus;
import com.minhnbnt.fashionstore.model.VoucherVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({SellerVoucherController.class, PublicShopVoucherController.class})
@Import(SecurityConfig.class)
class SellerVoucherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private VoucherService vouchers;

    private static SellerVoucherResponse sampleResponse(UUID shopId, UUID voucherId, boolean active) {
        var now = Instant.parse("2026-11-26T10:00:00Z");
        return new SellerVoucherResponse(
            voucherId,
            shopId,
            "SUMMER15",
            "Chào hè",
            "Giảm 15%",
            VoucherDiscountType.PERCENTAGE,
            15L,
            50_000L,
            200_000L,
            VoucherProductScope.ALL_PRODUCTS,
            VoucherVisibility.PUBLIC,
            100,
            0,
            1,
            now.minusSeconds(60),
            now.plusSeconds(3600),
            active,
            VoucherStatus.ONGOING,
            0,
            now,
            now
        );
    }

    private static SellerVoucherDetailResponse sampleDetailResponse(UUID shopId, UUID voucherId) {
        var now = Instant.parse("2026-11-26T10:00:00Z");
        return new SellerVoucherDetailResponse(
            voucherId,
            shopId,
            "SUMMER15",
            "Chào hè",
            "Giảm 15%",
            VoucherDiscountType.PERCENTAGE,
            15L,
            50_000L,
            200_000L,
            VoucherProductScope.ALL_PRODUCTS,
            VoucherVisibility.PUBLIC,
            100,
            0,
            1,
            now.minusSeconds(60),
            now.plusSeconds(3600),
            true,
            VoucherStatus.ONGOING,
            List.of(),
            List.of(),
            now,
            now
        );
    }

    @Test
    void listSellerVouchersReturnsPage() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var voucherId = UUID.randomUUID();
        var page = new PageResponse<>(List.of(sampleResponse(shopId, voucherId, true)), 1, 0, 20);

        when(vouchers.listForSeller(userId, shopId, "ONGOING", "SUM", 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/seller/shops/{shopId}/vouchers", shopId)
                .param("status", "ONGOING")
                .param("q", "SUM")
                .param("page", "0")
                .param("size", "20")
                .with(jwt().jwt(b -> b.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].code").value("SUMMER15"));
    }

    @Test
    void getSellerVoucherReturnsDetail() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var voucherId = UUID.randomUUID();

        when(vouchers.getForSeller(userId, shopId, voucherId))
            .thenReturn(sampleDetailResponse(shopId, voucherId));

        mockMvc.perform(get("/api/seller/shops/{shopId}/vouchers/{voucherId}", shopId, voucherId)
                .with(jwt().jwt(b -> b.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(voucherId.toString()))
            .andExpect(jsonPath("$.code").value("SUMMER15"));
    }

    @Test
    void createSellerVoucherReturns201() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var voucherId = UUID.randomUUID();

        when(vouchers.create(eq(userId), eq(shopId), any()))
            .thenReturn(sampleDetailResponse(shopId, voucherId));

        mockMvc.perform(post("/api/seller/shops/{shopId}/vouchers", shopId)
                .with(jwt().jwt(b -> b.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "SUMMER15",
                      "name": "Chào hè",
                      "description": "Giảm 15%",
                      "discountType": "PERCENTAGE",
                      "discountValue": 15,
                      "maxDiscountAmount": 50000,
                      "minOrderSubtotal": 200000,
                      "productScope": "ALL_PRODUCTS",
                      "visibility": "PUBLIC",
                      "usageLimit": 100,
                      "perBuyerLimit": 1,
                      "startsAt": "2026-11-26T09:00:00Z",
                      "endsAt": "2026-11-26T11:00:00Z",
                      "active": true
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUMMER15"));
    }

    @Test
    void updateSellerVoucherReturns200() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var voucherId = UUID.randomUUID();

        when(vouchers.update(eq(userId), eq(shopId), eq(voucherId), any()))
            .thenReturn(sampleDetailResponse(shopId, voucherId));

        mockMvc.perform(put("/api/seller/shops/{shopId}/vouchers/{voucherId}", shopId, voucherId)
                .with(jwt().jwt(b -> b.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "SUMMER15",
                      "name": "Chào hè cập nhật",
                      "description": "Giảm 15%",
                      "discountType": "PERCENTAGE",
                      "discountValue": 15,
                      "maxDiscountAmount": 50000,
                      "minOrderSubtotal": 200000,
                      "productScope": "ALL_PRODUCTS",
                      "visibility": "PUBLIC",
                      "usageLimit": 100,
                      "perBuyerLimit": 1,
                      "startsAt": "2026-11-26T09:00:00Z",
                      "endsAt": "2026-11-26T11:00:00Z",
                      "active": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUMMER15"));
    }

    @Test
    void toggleSellerVoucherActiveReturns200() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var voucherId = UUID.randomUUID();

        when(vouchers.toggleActive(userId, shopId, voucherId, false))
            .thenReturn(sampleResponse(shopId, voucherId, false));

        mockMvc.perform(patch("/api/seller/shops/{shopId}/vouchers/{voucherId}/active", shopId, voucherId)
                .with(jwt().jwt(b -> b.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deleteSellerVoucherReturns204() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var voucherId = UUID.randomUUID();

        mockMvc.perform(delete("/api/seller/shops/{shopId}/vouchers/{voucherId}", shopId, voucherId)
                .with(jwt().jwt(b -> b.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(vouchers).delete(userId, shopId, voucherId);
    }

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        var shopId = UUID.randomUUID();
        var voucherId = UUID.randomUUID();

        mockMvc.perform(get("/api/seller/shops/{shopId}/vouchers", shopId))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/seller/shops/{shopId}/vouchers/{voucherId}", shopId, voucherId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listPublicShopVouchersWorksAnonymouslyAndAuthenticated() throws Exception {
        var shopId = UUID.randomUUID();
        var voucherId = UUID.randomUUID();
        var buyerId = UUID.randomUUID();
        var now = Instant.parse("2026-11-26T10:00:00Z");
        var pub = new PublicShopVoucherResponse(
            voucherId,
            shopId,
            "SUMMER15",
            "Chào hè",
            "Giảm 15%",
            VoucherDiscountType.PERCENTAGE,
            15L,
            50_000L,
            200_000L,
            VoucherProductScope.ALL_PRODUCTS,
            100,
            5,
            1,
            now.minusSeconds(60),
            now.plusSeconds(3600),
            VoucherStatus.ONGOING,
            List.of(),
            false
        );

        when(vouchers.listPublicForShop(shopId, null)).thenReturn(List.of(pub));
        when(vouchers.listPublicForShop(shopId, buyerId)).thenReturn(List.of(pub));

        mockMvc.perform(get("/api/shops/{shopId}/vouchers", shopId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("SUMMER15"));

        mockMvc.perform(get("/api/shops/{shopId}/vouchers", shopId)
                .with(jwt().jwt(b -> b.subject(buyerId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("SUMMER15"));
    }
}
