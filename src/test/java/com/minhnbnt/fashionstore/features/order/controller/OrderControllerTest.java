package com.minhnbnt.fashionstore.features.order.controller;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.order.OrderResponse;
import com.minhnbnt.fashionstore.features.order.OrderService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({OrderController.class, SellerOrderController.class})
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private OrderService orders;

    private static OrderResponse response(UUID id) {
        return new OrderResponse(
            id,
            "ORD-TEST01",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "PENDING",
            "UNPAID",
            200000L,
            0L,
            200000L,
            "An",
            "0901",
            "Ha Noi",
            null,
            List.of(),
            Instant.now()
        );
    }

    @Test
    void checkoutReturns201() throws Exception {
        var id = UUID.randomUUID();
        when(orders.checkout(any(), any())).thenReturn(response(id));

        mockMvc.perform(post("/api/orders")
                .with(jwt().jwt(builder -> builder.subject(id.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shopId\":\"" + UUID.randomUUID() + "\","
                    + "\"items\":[{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}],"
                    + "\"shippingName\":\"An\",\"shippingPhone\":\"0901\",\"shippingAddress\":\"Ha Noi\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void checkoutWithoutAuthIs401() throws Exception {
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shopId\":\"" + UUID.randomUUID() + "\","
                    + "\"items\":[{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}],"
                    + "\"shippingName\":\"An\",\"shippingPhone\":\"0901\",\"shippingAddress\":\"Ha Noi\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void mineNeedsAuth() throws Exception {
        mockMvc.perform(get("/api/orders/mine"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmNeedsSellerAuth() throws Exception {
        var orderId = UUID.randomUUID();

        mockMvc.perform(patch("/api/seller/orders/{id}/confirm", orderId))
            .andExpect(status().isUnauthorized());
    }
}
