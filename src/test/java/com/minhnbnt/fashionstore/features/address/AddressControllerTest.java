package com.minhnbnt.fashionstore.features.address;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.address.AddressResponse;
import com.minhnbnt.fashionstore.features.address.controller.AddressController;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AddressController.class)
@Import(SecurityConfig.class)
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AddressService addresses;

    private static AddressResponse response(UUID id) {
        return new AddressResponse(id, "12", "Nguyen Trai", "Ben Thanh", "Quan 1", "Sai Gon", true);
    }

    @Test
    void listsAddresses() throws Exception {
        var userId = UUID.randomUUID();
        var id = UUID.randomUUID();
        when(addresses.getAddresses(userId)).thenReturn(List.of(response(id)));

        mockMvc.perform(get("/api/users/me/addresses")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].city").value("Sai Gon"));
    }

    @Test
    void addsAddress() throws Exception {
        var userId = UUID.randomUUID();
        var id = UUID.randomUUID();
        when(addresses.addAddress(any(), any())).thenReturn(response(id));

        mockMvc.perform(post("/api/users/me/addresses")
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"city\": \"Sai Gon\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.city").value("Sai Gon"));
    }

    @Test
    void editsAddress() throws Exception {
        var userId = UUID.randomUUID();
        var id = UUID.randomUUID();
        when(addresses.updateAddress(any(), any(), any())).thenReturn(response(id));

        mockMvc.perform(put("/api/users/me/addresses/{id}", id)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"city\": \"Da Nang\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void changesDefault() throws Exception {
        var userId = UUID.randomUUID();
        var id = UUID.randomUUID();
        when(addresses.changeDefaultAddress(any(), any())).thenReturn(response(id));

        mockMvc.perform(put("/api/users/me/addresses/{id}/default", id)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.defaultAddress").value(true));
    }

    @Test
    void deletesAddress() throws Exception {
        var userId = UUID.randomUUID();
        var id = UUID.randomUUID();

        mockMvc.perform(delete("/api/users/me/addresses/{id}", id)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isNoContent());
    }

    @Test
    void addressEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me/addresses"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/users/me/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"city\": \"Sai Gon\"}"))
            .andExpect(status().isUnauthorized());
    }
}
