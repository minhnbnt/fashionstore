package com.minhnbnt.fashionstore.features.identity;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.identity.UserResponse;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserService userService;

    @Test
    void publicHelloWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/public/hello"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Hello, world!"));
    }

    @Test
    void publicHelloWithAuth() throws Exception {
        mockMvc.perform(get("/api/public/hello")
                .with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Hello, user!"));
    }

    @Test
    void getMeWithoutAuthIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getMeReturnsProfile() throws Exception {
        var id = UUID.randomUUID();
        var response = new UserResponse(id, "a@t.local", "An", null, Instant.now());
        when(userService.getById(id)).thenReturn(response);

        mockMvc.perform(get("/api/users/me")
                .with(jwt().jwt(builder -> builder.subject(id.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("a@t.local"));
    }

    @Test
    void upsertUserReturnsProfile() throws Exception {
        var id = UUID.randomUUID();
        var response = new UserResponse(id, "a@t.local", "An", null, Instant.now());
        when(userService.upsert(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/users")
                .with(jwt().jwt(builder -> builder.subject(id.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@t.local\",\"displayName\":\"An\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void upsertUserRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/users")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"displayName\":\"An\"}"))
            .andExpect(status().isBadRequest());
    }
}
