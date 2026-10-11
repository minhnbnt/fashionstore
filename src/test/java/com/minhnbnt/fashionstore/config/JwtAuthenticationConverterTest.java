package com.minhnbnt.fashionstore.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationConverterTest {

    private final SecurityConfig security = new SecurityConfig();

    private static Jwt token(Map<String, Object> claims) {
        var all = new java.util.HashMap<String, Object>();
        all.put("sub", UUID.randomUUID().toString());
        all.putAll(claims);
        return new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            all
        );
    }

    @Test
    void adminClaimBecomesAdminRole() {
        var jwt = token(Map.of("app_metadata", Map.of("role", "admin")));

        var auth = security.jwtAuthenticationConverter().convert(jwt);

        assertThat(auth.getAuthorities())
            .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void missingClaimGrantsNoAdmin() {
        var jwt = token(Map.of());

        var auth = security.jwtAuthenticationConverter().convert(jwt);

        assertThat(auth.getAuthorities())
            .doesNotContain(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
