package com.minhnbnt.fashionstore.features.order.controller;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.order.OrderResponse;
import com.minhnbnt.fashionstore.dto.order.OrderTrackingEventResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SellerOrderController.class)
@Import(SecurityConfig.class)
class SellerOrderTrackingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private OrderService orders;

    private static OrderResponse shippedOrder(UUID id) {
        return new OrderResponse(
            id,
            "ORD-SHIP01",
            UUID.randomUUID(),
            "Shop Test",
            "shop-test",
            UUID.randomUUID(),
            "SHIPPING",
            "UNPAID",
            "COD",
            150000L,
            0L,
            150000L,
            "An",
            "0901",
            "Ha Noi",
            "Viettel Post",
            "VTP123",
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            null,
            null,
            null,
            null,
            null,
            List.of(),
            Instant.now()
        );
    }

    @Test
    void shipAcceptsOptionalCarrierAndCodeAndTrackingEndpointsWork() throws Exception {
        var sellerId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        when(orders.ship(eq(sellerId), eq(orderId), any())).thenReturn(shippedOrder(orderId));
        var event = new OrderTrackingEventResponse(
            UUID.randomUUID(),
            "IN_TRANSIT",
            "Dang trung chuyen",
            "Kho Da Nang",
            null,
            Instant.now(),
            Instant.now()
        );
        when(orders.getSellerTracking(sellerId, orderId)).thenReturn(List.of(event));
        when(orders.addTrackingEvent(eq(sellerId), eq(orderId), any())).thenReturn(event);

        mockMvc.perform(patch("/api/seller/orders/{id}/ship", orderId)
                .with(jwt().jwt(b -> b.subject(sellerId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingCarrier\":\"Viettel Post\",\"trackingCode\":\"VTP123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shippingCarrier").value("Viettel Post"))
            .andExpect(jsonPath("$.trackingCode").value("VTP123"));

        mockMvc.perform(patch("/api/seller/orders/{id}/ship", orderId)
                .with(jwt().jwt(b -> b.subject(sellerId.toString()))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/seller/orders/{id}/tracking", orderId)
                .with(jwt().jwt(b -> b.subject(sellerId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].location").value("Kho Da Nang"));

        mockMvc.perform(post("/api/seller/orders/{id}/tracking", orderId)
                .with(jwt().jwt(b -> b.subject(sellerId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statusCode\":\"IN_TRANSIT\",\"title\":\"Dang trung chuyen\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/seller/orders/{id}/tracking", orderId)
                .with(jwt().jwt(b -> b.subject(sellerId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statusCode\":\"IN_TRANSIT\",\"title\":\"\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/seller/orders/{id}/tracking", orderId))
            .andExpect(status().isUnauthorized());
    }
}

