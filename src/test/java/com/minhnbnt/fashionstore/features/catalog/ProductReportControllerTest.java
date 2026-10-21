package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductReportRequest;
import com.minhnbnt.fashionstore.dto.catalog.ProductReportResponse;
import com.minhnbnt.fashionstore.features.catalog.controller.ProductReportController;
import com.minhnbnt.fashionstore.model.ProductViolationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductReportController.class)
@Import(SecurityConfig.class)
class ProductReportControllerTest {

    private static final UUID BUYER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ProductReportService reports;

    @Test
    void reportUsesReporterFromToken() throws Exception {
        var productId = UUID.randomUUID();
        var request = new CreateProductReportRequest(ProductViolationType.COUNTERFEIT, "Logo in lệch");
        when(reports.report(BUYER_ID, productId, request))
            .thenReturn(new ProductReportResponse(UUID.randomUUID(), "COUNTERFEIT", "OPEN", Instant.now()));

        mockMvc.perform(post("/api/products/{id}/reports", productId)
                .with(jwt().jwt(builder -> builder.subject(BUYER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"COUNTERFEIT\",\"description\":\"Logo in lệch\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void reportNeedsLogin() throws Exception {
        mockMvc.perform(post("/api/products/{id}/reports", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SPAM\"}"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(reports);
    }

    @Test
    void reportNeedsReason() throws Exception {
        mockMvc.perform(post("/api/products/{id}/reports", UUID.randomUUID())
                .with(jwt().jwt(builder -> builder.subject(BUYER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Spam\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(reports);
    }

    @Test
    void reportRejectsOverlongDescription() throws Exception {
        var body = "{\"reason\":\"SPAM\",\"description\":\"" + "a".repeat(1001) + "\"}";

        mockMvc.perform(post("/api/products/{id}/reports", UUID.randomUUID())
                .with(jwt().jwt(builder -> builder.subject(BUYER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(reports);
    }
}
