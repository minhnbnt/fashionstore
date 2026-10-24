package com.minhnbnt.fashionstore.features.address;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.address.AddressResponse;
import com.minhnbnt.fashionstore.features.address.controller.AddressController;
import com.minhnbnt.fashionstore.model.AddressLabel;
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
        return new AddressResponse(
            id,
            "Nguyen Van A",
            "0912345678",
            "12 Nguyen Trai, Phuong Ben Thanh, Sai Gon",
            AddressLabel.HOME,
            true
        );
    }

    @Test
    void listsAddresses() throws Exception {
        var userId = UUID.randomUUID();
        var id = UUID.randomUUID();
        when(addresses.getAddresses(userId)).thenReturn(List.of(response(id)));

        mockMvc.perform(get("/api/users/me/addresses")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].fullAddress").value("12 Nguyen Trai, Phuong Ben Thanh, Sai Gon"))
            .andExpect(jsonPath("$[0].recipientName").value("Nguyen Van A"))
            .andExpect(jsonPath("$[0].phoneNumber").value("0912345678"))
            .andExpect(jsonPath("$[0].label").value("HOME"))
            .andExpect(jsonPath("$[0].defaultAddress").value(true));
    }

    @Test
    void addsAddress() throws Exception {
        var userId = UUID.randomUUID();
        var id = UUID.randomUUID();
        when(addresses.addAddress(any(), any())).thenReturn(response(id));

        mockMvc.perform(post("/api/users/me/addresses")
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "recipientName": "Nguyen Van A",
                        "phoneNumber": "0912345678",
                        "fullAddress": "12 Nguyen Trai, Phuong Ben Thanh, Sai Gon",
                        "label": "HOME",
                        "defaultAddress": false
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fullAddress").value("12 Nguyen Trai, Phuong Ben Thanh, Sai Gon"))
            .andExpect(jsonPath("$.recipientName").value("Nguyen Van A"));
    }

    @Test
    void addsAddressRejectsBlankFullAddress() throws Exception {
        var userId = UUID.randomUUID();

        mockMvc.perform(post("/api/users/me/addresses")
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "recipientName": "Nguyen Van A",
                        "phoneNumber": "0912345678",
                        "fullAddress": "   ",
                        "label": "HOME",
                        "defaultAddress": false
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void addsAddressRejectsMissingField() throws Exception {
        var userId = UUID.randomUUID();

        mockMvc.perform(post("/api/users/me/addresses")
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "recipientName": "Nguyen Van A",
                        "phoneNumber": "0912345678",
                        "fullAddress": "So 18 Duy Tan, Phuong Dich Vong, Ha Noi",
                        "label": "HOME"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void editsAddress() throws Exception {
        var userId = UUID.randomUUID();
        var id = UUID.randomUUID();
        when(addresses.updateAddress(any(), any(), any())).thenReturn(response(id));

        mockMvc.perform(put("/api/users/me/addresses/{id}", id)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "recipientName": "Tran Thi B",
                        "phoneNumber": "0987654321",
                        "fullAddress": "Toa nha Keangnam, Phuong Me Tri, Ha Noi",
                        "label": "OFFICE",
                        "defaultAddress": true
                    }
                    """))
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
                .content("{\"fullAddress\": \"Sai Gon\"}"))
            .andExpect(status().isUnauthorized());
    }
}
