package com.minhnbnt.fashionstore.features.admin.controller;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.payment.AdminWithdrawalResponse;
import com.minhnbnt.fashionstore.dto.payment.PaymentGatewayResponse;
import com.minhnbnt.fashionstore.dto.payment.PaymentMethodOption;
import com.minhnbnt.fashionstore.dto.payment.PaymentMethodsResponse;
import com.minhnbnt.fashionstore.dto.payment.UpdatePaymentGatewayRequest;
import com.minhnbnt.fashionstore.features.order.RevenueService;
import com.minhnbnt.fashionstore.features.payment.PaymentGatewayService;
import com.minhnbnt.fashionstore.features.payment.controller.PublicPaymentController;
import com.minhnbnt.fashionstore.model.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminPaymentController.class, PublicPaymentController.class})
@Import(SecurityConfig.class)
class AdminPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private PaymentGatewayService gateways;

    @MockitoBean
    private RevenueService revenue;

    private static RequestPostProcessor admin(UUID adminId) {
        return jwt()
            .jwt(builder -> builder.subject(adminId.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void listPaymentGatewaysRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/payment-gateways").with(jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void listPaymentGatewaysReturnsGatewaysForAdmin() throws Exception {
        var adminId = UUID.randomUUID();
        when(gateways.list()).thenReturn(List.of(
            new PaymentGatewayResponse(
                "COD",
                "Thanh toán khi nhận hàng (COD)",
                true,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                Instant.parse("2026-11-26T00:00:00Z")
            )
        ));

        mockMvc.perform(get("/api/admin/payment-gateways").with(admin(adminId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("COD"))
            .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    void updatePaymentGatewayDelegatesToService() throws Exception {
        var adminId = UUID.randomUUID();
        when(gateways.update(eq(adminId), eq(PaymentMethod.VIETQR), any(UpdatePaymentGatewayRequest.class)))
            .thenReturn(new PaymentGatewayResponse(
                "VIETQR",
                "VietQR NAPAS",
                true,
                "970436",
                "VCB",
                "Ngân hàng TMCP Ngoại Thương Việt Nam",
                "0011223344",
                "CONG TY DHMQ",
                true,
                false,
                Instant.parse("2026-11-26T00:00:00Z")
            ));

        mockMvc.perform(put("/api/admin/payment-gateways/VIETQR")
                .with(admin(adminId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "enabled": true,
                      "name": "VietQR NAPAS",
                      "bankBin": "970436",
                      "accountNumber": "0011223344",
                      "accountName": "CONG TY DHMQ",
                      "sandboxMode": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("VIETQR"))
            .andExpect(jsonPath("$.bankCode").value("VCB"))
            .andExpect(jsonPath("$.webhookSecretSet").value(true));
    }

    @Test
    void publicPaymentMethodsNeedsNoJwt() throws Exception {
        when(gateways.publicMethods()).thenReturn(new PaymentMethodsResponse(
            List.of(
                new PaymentMethodOption("COD", "Thanh toán khi nhận hàng (COD)", null, null, false),
                new PaymentMethodOption("VIETQR", "Chuyển khoản ngân hàng (VietQR)", "MB", "MBBank", true)
            ),
            0L,
            10_000_000L,
            15
        ));

        mockMvc.perform(get("/api/public/payment-methods"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.methods[0].code").value("COD"))
            .andExpect(jsonPath("$.methods[1].code").value("VIETQR"))
            .andExpect(jsonPath("$.codMaxTotal").value(10000000));
    }

    @Test
    void listApproveAndRejectWithdrawalsDelegateToRevenueService() throws Exception {
        var adminId = UUID.randomUUID();
        var withdrawalId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var now = Instant.parse("2026-11-26T00:00:00Z");
        var requested = new AdminWithdrawalResponse(
            withdrawalId,
            shopId,
            "Shop Alpha",
            100_000L,
            "Vietcombank",
            "999888777",
            "NGUYEN VAN A",
            "REQUESTED",
            now,
            null
        );
        var completed = new AdminWithdrawalResponse(
            withdrawalId,
            shopId,
            "Shop Alpha",
            100_000L,
            "Vietcombank",
            "999888777",
            "NGUYEN VAN A",
            "COMPLETED",
            now,
            now
        );
        var rejected = new AdminWithdrawalResponse(
            withdrawalId,
            shopId,
            "Shop Alpha",
            100_000L,
            "Vietcombank",
            "999888777",
            "NGUYEN VAN A",
            "REJECTED",
            now,
            now
        );

        when(revenue.listAdminWithdrawals("REQUESTED", 0, 20))
            .thenReturn(new PageResponse<>(List.of(requested), 1L, 0, 20));
        when(revenue.approveWithdrawal(adminId, withdrawalId)).thenReturn(completed);
        when(revenue.rejectWithdrawal(adminId, withdrawalId)).thenReturn(rejected);

        mockMvc.perform(get("/api/admin/payments/withdrawals")
                .param("status", "REQUESTED")
                .param("page", "0")
                .param("size", "20")
                .with(admin(adminId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(withdrawalId.toString()))
            .andExpect(jsonPath("$.items[0].shopName").value("Shop Alpha"))
            .andExpect(jsonPath("$.items[0].status").value("REQUESTED"));

        mockMvc.perform(patch("/api/admin/payments/withdrawals/{id}/approve", withdrawalId)
                .with(admin(adminId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(patch("/api/admin/payments/withdrawals/{id}/reject", withdrawalId)
                .with(admin(adminId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
