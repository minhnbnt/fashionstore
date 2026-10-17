package com.minhnbnt.fashionstore.features.wishlist;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.wishlist.WishlistItemResponse;
import com.minhnbnt.fashionstore.dto.wishlist.WishlistToggleResponse;
import com.minhnbnt.fashionstore.features.wishlist.controller.WishlistController;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WishlistController.class)
@Import(SecurityConfig.class)
class WishlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private WishlistService wishlist;

    @Test
    void listsWishlist() throws Exception {
        var userId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        when(wishlist.getWishlist(userId)).thenReturn(List.of(
            new WishlistItemResponse(productId, "Ao thun", "ao-thun", 100000L, null, true, null)
        ));

        mockMvc.perform(get("/api/wishlist")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].available").value(true));
    }

    @Test
    void togglesWishlist() throws Exception {
        var userId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        when(wishlist.toggle(any(), any())).thenReturn(new WishlistToggleResponse(productId, true));

        mockMvc.perform(post("/api/wishlist/items")
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": \"" + productId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saved").value(true));
    }

    @Test
    void removesFromWishlist() throws Exception {
        var userId = UUID.randomUUID();
        var productId = UUID.randomUUID();

        mockMvc.perform(delete("/api/wishlist/items/{productId}", productId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isNoContent());
    }

    @Test
    void wishlistEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/wishlist"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/wishlist/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": \"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }
}
