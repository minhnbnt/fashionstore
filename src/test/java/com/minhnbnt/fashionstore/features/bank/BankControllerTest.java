package com.minhnbnt.fashionstore.features.bank;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.bank.BankResponse;
import com.minhnbnt.fashionstore.dto.bank.LookupAccountRequest;
import com.minhnbnt.fashionstore.dto.bank.LookupAccountResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BankController.class)
@Import(SecurityConfig.class)
class BankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private BankService bankService;

    @Test
    @DisplayName("GET /api/public/banks returns list of banks without auth")
    void listsBanksWithoutAuth() throws Exception {
        when(bankService.listBanks()).thenReturn(List.of(
            new BankResponse(
                "17",
                "Ngân hàng TMCP Ngoại Thương Việt Nam",
                "VCB",
                "970436",
                "Vietcombank",
                "https://api.vietqr.io/img/VCB.png",
                true
            )
        ));

        mockMvc.perform(get("/api/public/banks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("VCB"))
            .andExpect(jsonPath("$[0].bin").value("970436"))
            .andExpect(jsonPath("$[0].shortName").value("Vietcombank"));
    }

    @Test
    @DisplayName("POST /api/public/banks/lookup-account returns account info when valid")
    void lookupAccountSuccess() throws Exception {
        when(bankService.lookupAccount("970436", "0011001234567"))
            .thenReturn(new LookupAccountResponse("VCB", "0011001234567", "NGUYEN VAN A"));

        var request = new LookupAccountRequest("970436", "0011001234567");

        mockMvc.perform(post("/api/public/banks/lookup-account")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bankCode").value("VCB"))
            .andExpect(jsonPath("$.accountNumber").value("0011001234567"))
            .andExpect(jsonPath("$.accountName").value("NGUYEN VAN A"));
    }

    @Test
    @DisplayName("POST /api/public/banks/lookup-account returns 400 when bin is invalid")
    void lookupAccountInvalidBin() throws Exception {
        var request = new LookupAccountRequest("123", "0011001234567");

        mockMvc.perform(post("/api/public/banks/lookup-account")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
