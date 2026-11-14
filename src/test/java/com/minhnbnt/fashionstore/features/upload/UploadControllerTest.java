package com.minhnbnt.fashionstore.features.upload;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.upload.PresignResponse;
import com.minhnbnt.fashionstore.dto.upload.UploadPurpose;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UploadController.class)
@Import(SecurityConfig.class)
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UploadService uploads;

    @Test
    void presignNeedsAuth() throws Exception {
        mockMvc.perform(post("/api/uploads/presign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void presignRejectsNonImageContentType() throws Exception {
        mockMvc.perform(post("/api/uploads/presign")
                .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"text/plain\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void presignRejectsUnknownPurpose() throws Exception {
        mockMvc.perform(post("/api/uploads/presign")
                .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\",\"purpose\":\"admin\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void presignReturnsUploadIdAndUrlWithoutKey() throws Exception {
        var uploadId = UUID.randomUUID();
        var expiresAt = Instant.now().plusSeconds(600);
        when(uploads.presign(any()))
            .thenReturn(new PresignResponse(uploadId, "http://x/put", "http://x/pub", expiresAt));

        mockMvc.perform(post("/api/uploads/presign")
                .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uploadId").value(uploadId.toString()))
            .andExpect(jsonPath("$.uploadUrl").value("http://x/put"))
            .andExpect(jsonPath("$.publicUrl").value("http://x/pub"))
            .andExpect(jsonPath("$.expiresAt").exists())
            .andExpect(jsonPath("$.key").doesNotExist());
    }

    @Test
    void presignForwardsPurposeToService() throws Exception {
        when(uploads.presign(any()))
            .thenReturn(new PresignResponse(UUID.randomUUID(), "http://x", "http://x/pub", Instant.now()));

        mockMvc.perform(post("/api/uploads/presign")
                .with(jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\",\"purpose\":\"kyc\"}"))
            .andExpect(status().isOk());

        verify(uploads).presign(argThat(request -> request.purpose() == UploadPurpose.KYC));
    }
}
