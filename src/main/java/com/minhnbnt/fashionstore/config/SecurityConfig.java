package com.minhnbnt.fashionstore.config;

import com.minhnbnt.fashionstore.features.identity.AccountProvisioner;
import com.minhnbnt.fashionstore.security.AccountFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Mọi method đều public. AccountFilter dùng chung list này để tài khoản bị khóa vẫn vào được.
     */
    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/public/**",
        "/actuator/health",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    );

    /**
     * GET public. Riêng GET /api/shops/mine cần đăng nhập, khai trước "/api/shops/*".
     */
    private static final List<String> PUBLIC_GET_PATHS = List.of(
        "/api/shops/*",
        "/api/categories",
        "/api/products/*",
        "/api/search/**"
    );

    @Bean
    public SecurityFilterChain filterChain(
        HttpSecurity http,
        ObjectProvider<AccountProvisioner> provisioners
    ) {

        http
            .cors(Customizer.withDefaults())
            .csrf(CsrfConfigurer::disable)
            .sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS.toArray(String[]::new)).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/shops/mine").authenticated()
                .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS.toArray(String[]::new)).permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/shop/**").hasRole("SHOP")
                .anyRequest()
                .authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
            ))
            .addFilterAfter(
                new AccountFilter(provisioners, PUBLIC_GET_PATHS, PUBLIC_PATHS),
                BearerTokenAuthenticationFilter.class
            );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        var converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            var metadata = jwt.getClaimAsMap("app_metadata");
            Object role = null;
            if (metadata != null) {
                role = metadata.get("role");
            }

            if ("admin".equals(role)) {
                return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }

            if ("shop".equals(role)) {
                return List.of(new SimpleGrantedAuthority("ROLE_SHOP"));
            }

            return List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        });

        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        var config = new CorsConfiguration();

        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
