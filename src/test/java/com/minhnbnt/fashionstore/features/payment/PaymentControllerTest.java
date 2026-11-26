package com.minhnbnt.fashionstore.features.payment;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.payment.VietQrOrderBrief;
import com.minhnbnt.fashionstore.dto.payment.VietQrSessionResponse;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.order.controller.OrderController;
import com.minhnbnt.fashionstore.features.payment.controller.PaymentController;
import com.minhnbnt.fashionstore.features.payment.controller.PublicPaymentController;
import com.minhnbnt.fashionstore.model.PaymentGateway;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PaymentController.class, PublicPaymentController.class, OrderController.class})
@Import(SecurityConfig.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PublicPaymentController publicPaymentController;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private PaymentFlowService paymentFlow;

    @MockitoBean
    private PaymentGatewayService gateways;

    @MockitoBean
    private OrderService orders;

    private static VietQrSessionResponse sampleSession(String txnRef, String status, long amount, long receivedAmount) {
        return new VietQrSessionResponse(
            UUID.randomUUID(),
            txnRef,
            status,
            amount,
            receivedAmount,
            "970422",
            "MB",
            "Ngân hàng TMCP Quân đội (MB Bank)",
            "0900123456789",
            "SAN TMDT FASHIONSTORE",
            "https://img.vietqr.io/image/970422-0900123456789-compact2.png?amount=" + amount + "&addInfo=" + txnRef,
            Instant.parse("2026-11-26T10:30:00Z"),
            null,
            true,
            "NONE",
            0L,
            null,
            List.of(new VietQrOrderBrief(
                UUID.randomUUID(),
                "ORD-TEST01",
                UUID.randomUUID(),
                "Shop Alpha",
                amount,
                "AWAITING_PAYMENT",
                "UNPAID"
            ))
        );
    }

    private static PaymentGateway activeVietQrGateway() {
        return new PaymentGateway(
            "VIETQR",
            "Chuyển khoản ngân hàng (VietQR)",
            true,
            "970422",
            "MB",
            "Ngân hàng TMCP Quân đội (MB Bank)",
            "0900123456789",
            "SAN TMDT FASHIONSTORE",
            "demo-vietqr-secret-key",
            true,
            Instant.parse("2026-11-26T10:00:00Z"),
            null,
            false
        );
    }

    @Test
    void getVietQrSessionRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/payments/vietqr/DHMQ1234567890"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getVietQrSessionReturnsSessionForBuyer() throws Exception {
        var buyerId = UUID.randomUUID();
        var txnRef = "DHMQ1234567890";
        when(paymentFlow.getSessionForViewer(buyerId, false, txnRef))
            .thenReturn(sampleSession(txnRef, "PENDING", 360_000L, 0L));

        mockMvc.perform(get("/api/payments/vietqr/{txnRef}", txnRef)
                .with(jwt().jwt(builder -> builder.subject(buyerId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.txnRef").value(txnRef))
            .andExpect(jsonPath("$.amount").value(360000))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void simulateVietQrTransferDelegatesToPaymentFlowService() throws Exception {
        var buyerId = UUID.randomUUID();
        var txnRef = "DHMQ1234567890";
        when(paymentFlow.simulateTransfer(buyerId, false, txnRef, 180_000L))
            .thenReturn(sampleSession(txnRef, "UNDERPAID", 360_000L, 180_000L));

        mockMvc.perform(post("/api/payments/vietqr/{txnRef}/simulate", txnRef)
                .with(jwt().jwt(builder -> builder.subject(buyerId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":180000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UNDERPAID"))
            .andExpect(jsonPath("$.receivedAmount").value(180000));
    }

    @Test
    void retryVietQrPaymentEndpointCreatesOrReturnsSession() throws Exception {
        var buyerId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var txnRef = "DHMQ9988776655";
        when(paymentFlow.retryForOrder(buyerId, orderId))
            .thenReturn(sampleSession(txnRef, "PENDING", 230_000L, 0L));

        mockMvc.perform(post("/api/orders/{id}/payments/vietqr", orderId)
                .with(jwt().jwt(builder -> builder.subject(buyerId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.txnRef").value(txnRef))
            .andExpect(jsonPath("$.amount").value(230000));
    }

    @Test
    void vietQrWebhookRejectsMissingOrInvalidAuthorizationHeader() throws Exception {
        when(gateways.requireActiveVietQrGateway()).thenReturn(activeVietQrGateway());

        mockMvc.perform(post("/api/public/payments/vietqr/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"CK DHMQ1234567890\",\"transferAmount\":360000,\"transferType\":\"in\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/public/payments/vietqr/webhook")
                .header("Authorization", "Apikey wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"CK DHMQ1234567890\",\"transferAmount\":360000,\"transferType\":\"in\"}"))
            .andExpect(status().isUnauthorized());

        verify(paymentFlow, never()).processIncomingTransfer(anyString(), anyLong(), any(), anyString());
    }

    @Test
    void vietQrWebhookProcessesIncomingTransferAndIgnoresOutgoingTransfer() throws Exception {
        when(gateways.requireActiveVietQrGateway()).thenReturn(activeVietQrGateway());
        when(paymentFlow.processIncomingTransfer(
            eq("MBVCB CK DHMQ1234567890"),
            eq(360_000L),
            eq("FT999"),
            anyString()
        )).thenReturn(new TransferProcessResult(
            TransferOutcome.SUCCESS,
            "DHMQ1234567890",
            360_000L,
            360_000L,
            List.of(UUID.randomUUID())
        ));

        mockMvc.perform(post("/api/public/payments/vietqr/webhook")
                .header("Authorization", "Apikey demo-vietqr-secret-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "referenceCode": "FT999",
                      "content": "MBVCB CK DHMQ1234567890",
                      "transferAmount": 360000,
                      "transferType": "in"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.outcome").value("SUCCESS"))
            .andExpect(jsonPath("$.txnRef").value("DHMQ1234567890"));

        mockMvc.perform(post("/api/public/payments/vietqr/webhook")
                .header("Authorization", "Apikey demo-vietqr-secret-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "referenceCode": "FT1000",
                      "content": "Chuyen tien ra",
                      "transferAmount": 50000,
                      "transferType": "out"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.outcome").value("IGNORED"));

        var bearerResp = publicPaymentController.vietQrWebhook(
            "Bearer demo-vietqr-secret-key",
            new com.minhnbnt.fashionstore.dto.payment.VietQrWebhookRequest(
                "MBVCB CK DHMQ1234567890",
                360_000L,
                "FT999",
                "in"
            )
        );
        org.assertj.core.api.Assertions.assertThat(bearerResp.outcome()).isEqualTo("SUCCESS");
    }
}
