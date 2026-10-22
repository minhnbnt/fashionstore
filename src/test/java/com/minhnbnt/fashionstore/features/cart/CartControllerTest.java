package com.minhnbnt.fashionstore.features.cart;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.cart.CartCheckoutRequest;
import com.minhnbnt.fashionstore.dto.cart.CartItemRequest;
import com.minhnbnt.fashionstore.dto.cart.CartItemResponse;
import com.minhnbnt.fashionstore.dto.cart.CartResponse;
import com.minhnbnt.fashionstore.dto.cart.CheckoutBlockedItem;
import com.minhnbnt.fashionstore.features.cart.controller.CartController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import(SecurityConfig.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CartService carts;

    private static CartResponse cart(UUID variantId) {
        return new CartResponse(
            List.of(new CartItemResponse(
                variantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Shop Thoi Trang",
                "Ao thun",
                "demo/ao.jpg",
                "M / Den",
                "SKU-1",
                100000L,
                100000L,
                false,
                2,
                10
            )),
            2,
            200000L
        );
    }

    @Test
    void viewCartReturnsCart() throws Exception {
        var buyerId = UUID.randomUUID();
        var variantId = UUID.randomUUID();
        when(carts.getCart(buyerId)).thenReturn(cart(variantId));

        mockMvc.perform(get("/api/cart")
                .with(jwt().jwt(builder -> builder.subject(buyerId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].variantId").value(variantId.toString()))
            .andExpect(jsonPath("$.subtotal").value(200000L));
    }

    @Test
    void addItemReturnsCart() throws Exception {
        var buyerId = UUID.randomUUID();
        var variantId = UUID.randomUUID();
        when(carts.addItem(any(), any())).thenReturn(cart(variantId));

        mockMvc.perform(post("/api/cart/items")
                .with(jwt().jwt(builder -> builder.subject(buyerId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\": \"" + variantId + "\", \"quantity\": 2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void updateQuantityReturnsCart() throws Exception {
        var buyerId = UUID.randomUUID();
        var variantId = UUID.randomUUID();
        when(carts.updateQuantity(any(), any(), anyInt())).thenReturn(cart(variantId));

        mockMvc.perform(put("/api/cart/items/{variantId}", variantId)
                .with(jwt().jwt(builder -> builder.subject(buyerId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 2}"))
            .andExpect(status().isOk());
    }

    @Test
    void checkoutReturnsOrders() throws Exception {
        var buyerId = UUID.randomUUID();
        when(carts.checkout(any(), any())).thenReturn(List.of());

        mockMvc.perform(post("/api/cart/checkout")
                .with(jwt().jwt(builder -> builder.subject(buyerId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingName\": \"An\", \"shippingPhone\": \"0901\", \"shippingAddress\": \"Ha Noi\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void checkoutBlockedReturns409WithItems() throws Exception {
        var buyerId = UUID.randomUUID();
        var variantId = UUID.randomUUID();
        when(carts.checkout(any(), any())).thenThrow(
            new CartCheckoutBlockedException(List.of(new CheckoutBlockedItem(variantId, "Sold out")))
        );

        mockMvc.perform(post("/api/cart/checkout")
                .with(jwt().jwt(builder -> builder.subject(buyerId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingName\": \"An\", \"shippingPhone\": \"0901\", \"shippingAddress\": \"Ha Noi\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.items[0].variantId").value(variantId.toString()));
    }

    @Test
    void cartEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/cart"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/cart/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingName\": \"An\", \"shippingPhone\": \"0901\", \"shippingAddress\": \"Ha Noi\"}"))
            .andExpect(status().isUnauthorized());
    }
}
