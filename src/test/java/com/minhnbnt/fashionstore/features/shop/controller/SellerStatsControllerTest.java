package com.minhnbnt.fashionstore.features.shop.controller;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.shop.AlertResponse;
import com.minhnbnt.fashionstore.dto.shop.ProductStatsResponse;
import com.minhnbnt.fashionstore.dto.shop.RevenueDetailResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopDashboardResponse;
import com.minhnbnt.fashionstore.features.shop.ShopStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SellerStatsController.class)
@Import(SecurityConfig.class)
class SellerStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ShopStatsService stats;

    private final UUID shopId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void getDashboardReturns200WithValidShopId() throws Exception {
        when(stats.getDashboard(userId, shopId))
            .thenReturn(new ShopDashboardResponse(1500000L, 5L, 2L, 120L, 1200000L, 4L));

        mockMvc.perform(get("/api/seller/shops/{shopId}/stats/dashboard", shopId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.todayRevenue").value(1500000L))
            .andExpect(jsonPath("$.todayOrders").value(5L))
            .andExpect(jsonPath("$.pendingOrders").value(2L))
            .andExpect(jsonPath("$.shopVisits").value(120L));
    }

    @Test
    void getDashboardReturns401WithoutJwt() throws Exception {
        mockMvc.perform(get("/api/seller/shops/{shopId}/stats/dashboard", shopId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getRevenueDetailAcceptsPeriodParam() throws Exception {
        when(stats.getRevenueDetail(eq(userId), eq(shopId), eq("week"), any(), any()))
            .thenReturn(new RevenueDetailResponse("week", List.of()));

        mockMvc.perform(get("/api/seller/shops/{shopId}/stats/revenue/detail", shopId)
                .param("period", "week")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period").value("week"));

        verify(stats).getRevenueDetail(eq(userId), eq(shopId), eq("week"), any(), any());
    }

    @Test
    void getTopSellingProductsDefaultsLimitTo10() throws Exception {
        when(stats.getTopSellingProducts(userId, shopId, 10))
            .thenReturn(List.of(
                new ProductStatsResponse(UUID.randomUUID(), "Áo Polo", null, 15L, 3000000L, 500L, 0.03, 20)
            ));

        mockMvc.perform(get("/api/seller/shops/{shopId}/stats/products/top-selling", shopId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].productName").value("Áo Polo"));

        verify(stats).getTopSellingProducts(userId, shopId, 10);
    }

    @Test
    void exportRevenueReportReturnsCsvContentType() throws Exception {
        var csvData = "Ngày,Doanh thu (VND),Số đơn hàng\n2026-10-24,100000,1\n".getBytes(StandardCharsets.UTF_8);
        when(stats.exportRevenueReport(eq(userId), eq(shopId), eq("csv"), any(), any()))
            .thenReturn(csvData);

        mockMvc.perform(get("/api/seller/shops/{shopId}/stats/revenue/export", shopId)
                .param("format", "csv")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("bao-cao-doanh-thu-")))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
    }

    @Test
    void exportRevenueReportReturnsXlsxContentTypeByDefault() throws Exception {
        var xlsxData = new byte[]{0x50, 0x4B, 0x03, 0x04}; // PK zip header
        when(stats.exportRevenueReport(eq(userId), eq(shopId), eq("xlsx"), any(), any()))
            .thenReturn(xlsxData);

        mockMvc.perform(get("/api/seller/shops/{shopId}/stats/revenue/export", shopId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("bao-cao-doanh-thu-")))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString(".xlsx")))
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}
