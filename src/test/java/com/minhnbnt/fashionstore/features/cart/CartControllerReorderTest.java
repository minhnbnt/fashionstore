package com.minhnbnt.fashionstore.features.cart;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.cart.CartItemResponse;
import com.minhnbnt.fashionstore.dto.cart.CartResponse;
import com.minhnbnt.fashionstore.dto.cart.ReorderItemIssue;
import com.minhnbnt.fashionstore.dto.cart.ReorderResponse;
import com.minhnbnt.fashionstore.features.cart.controller.CartController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ResourceLock("cart-controller-mock")
@WebMvcTest(CartController.class)
@Import(SecurityConfig.class)
class CartControllerReorderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CartService carts;

    @Test
    void reorderReturns200WithIssuesAndRequiresAuth() throws Exception {
        var buyerId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var variantId = UUID.randomUUID();
        var response = new ReorderResponse(
            new CartResponse(
                List.of(new CartItemResponse(
                    variantId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Shop Test",
                    "Ao Thun",
                    "ao.jpg",
                    "M / Den",
                    "SKU-1",
                    100000L,
                    100000L,
                    false,
                    1,
                    5
                )),
                1,
                100000L
            ),
            2,
            1,
            0,
            1,
            List.of(),
            List.of(new ReorderItemIssue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Quan Jeans",
                "L / Xanh",
                "OUT_OF_STOCK",
                2,
                0
            ))
        );
        when(carts.reorder(buyerId, orderId)).thenReturn(response);

        mockMvc.perform(post("/api/cart/reorder/{orderId}", orderId)
                .with(jwt().jwt(b -> b.subject(buyerId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.addedCount").value(1))
            .andExpect(jsonPath("$.skippedCount").value(1))
            .andExpect(jsonPath("$.skippedItems[0].reason").value("OUT_OF_STOCK"));

        mockMvc.perform(post("/api/cart/reorder/{orderId}", orderId))
            .andExpect(status().isUnauthorized());
    }
}

