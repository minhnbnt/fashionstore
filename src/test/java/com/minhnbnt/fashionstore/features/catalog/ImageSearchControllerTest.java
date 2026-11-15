package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.catalog.ImageSearchHit;
import com.minhnbnt.fashionstore.dto.catalog.ImageSearchResponse;
import com.minhnbnt.fashionstore.features.catalog.controller.ImageSearchController;
import com.minhnbnt.fashionstore.features.catalog.search.ImageSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageSearchController.class)
@Import(SecurityConfig.class)
class ImageSearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ImageSearchService service;

    private static MockMultipartFile image(int size) {
        return new MockMultipartFile("file", "q.png", "image/png", new byte[size]);
    }

    @Test
    void returnsHits() throws Exception {
        when(service.search(any(), anyInt())).thenReturn(new ImageSearchResponse(List.of(
            new ImageSearchHit(UUID.randomUUID(), "P", "p", 1000L, "http://img", 0.93))));

        mvc.perform(multipart("/api/search/products/by-image").file(image(10)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].similarity").value(0.93));
    }

    @Test
    void usesDefaultLimit() throws Exception {
        when(service.search(any(), anyInt())).thenReturn(new ImageSearchResponse(List.of()));

        mvc.perform(multipart("/api/search/products/by-image").file(image(10)))
            .andExpect(status().isOk());

        verify(service).search(any(), eq(20));
    }

    @Test
    void rejectsNonImageContentType() throws Exception {
        var pdf = new MockMultipartFile("file", "q.pdf", "application/pdf", new byte[10]);

        mvc.perform(multipart("/api/search/products/by-image").file(pdf))
            .andExpect(status().isUnsupportedMediaType());

        verify(service, never()).search(any(), anyInt());
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        mvc.perform(multipart("/api/search/products/by-image").file(image(0)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTooLargeFileBeforeCalling() throws Exception {
        mvc.perform(multipart("/api/search/products/by-image").file(image(8 * 1024 * 1024 + 1)))
            .andExpect(status().isPayloadTooLarge());

        verify(service, never()).search(any(), anyInt());
    }

    @Test
    void rejectsLimitOutOfRange() throws Exception {
        mvc.perform(multipart("/api/search/products/by-image").file(image(10)).param("limit", "0"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void surfacesWorkerOutageAs503() throws Exception {
        when(service.search(any(), anyInt()))
            .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "down"));

        mvc.perform(multipart("/api/search/products/by-image").file(image(10)))
            .andExpect(status().isServiceUnavailable());
    }
}
