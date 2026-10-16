package com.minhnbnt.fashionstore.security;

import com.minhnbnt.fashionstore.features.identity.AccountProvisioner;
import com.minhnbnt.fashionstore.model.AccountStatus;
import com.minhnbnt.fashionstore.model.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chạy sau khi xác thực JWT: tạo user_profile nếu chưa có và chặn tài khoản bị admin khóa ngay lập tức
 * (không đợi access token hết hạn). Tài khoản khóa chỉ còn đọc profile của mình và các trang public.
 */
public class AccountFilter extends OncePerRequestFilter {

    private static final String LOCKED_BODY = """
        {"type":"about:blank","title":"Forbidden","status":403,\
        "detail":"Account is locked","code":"ACCOUNT_LOCKED"}""";

    private static final String OWN_PROFILE = "/api/users/me";

    private static final String MY_SHOPS = "/api/shops/mine";

    private final ObjectProvider<AccountProvisioner> provisioners;

    private final List<PathPattern> publicGetPatterns;

    private final List<PathPattern> publicPatterns;

    public AccountFilter(
        ObjectProvider<AccountProvisioner> provisioners,
        List<String> publicGetPaths,
        List<String> publicPaths
    ) {
        this.provisioners = provisioners;
        this.publicGetPatterns = parse(publicGetPaths);
        this.publicPatterns = parse(publicPaths);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {

        // Slice test (@WebMvcTest) không có bean này: bỏ qua, hành vi như trước khi có filter.
        var provisioner = provisioners.getIfAvailable();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (provisioner == null || !(authentication instanceof JwtAuthenticationToken token)) {
            chain.doFilter(request, response);
            return;
        }

        var jwt = token.getToken();
        var userId = uuidOf(jwt.getSubject());
        if (userId == null) {
            chain.doFilter(request, response);
            return;
        }

        var email = jwt.getClaimAsString("email");
        var status = provisioner.ensureAccount(userId, email, displayNameOf(jwt, email), roleOf(token));

        if (status == AccountStatus.LOCKED && !allowedWhileLocked(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/problem+json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(LOCKED_BODY);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean allowedWhileLocked(HttpServletRequest request) {
        var path = request.getRequestURI().substring(request.getContextPath().length());
        var container = PathContainer.parsePath(path);

        if (matchesAny(publicPatterns, container)) {
            return true;
        }

        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        if (OWN_PROFILE.equals(path)) {
            return true;
        }
        if (MY_SHOPS.equals(path)) {
            return false;
        }
        return matchesAny(publicGetPatterns, container);
    }

    private static boolean matchesAny(List<PathPattern> patterns, PathContainer path) {
        for (var pattern : patterns) {
            if (pattern.matches(path)) {
                return true;
            }
        }
        return false;
    }

    private static List<PathPattern> parse(List<String> paths) {
        return paths.stream()
            .map(PathPatternParser.defaultInstance::parse)
            .toList();
    }

    private static @Nullable UUID uuidOf(@Nullable String subject) {
        if (subject == null) {
            return null;
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UserRole roleOf(JwtAuthenticationToken token) {
        var authorities = token.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .toList();

        if (authorities.contains("ROLE_ADMIN")) {
            return UserRole.ADMIN;
        }
        if (authorities.contains("ROLE_SHOP")) {
            return UserRole.SHOP;
        }
        return UserRole.CUSTOMER;
    }

    /**
     * Google OAuth đặt tên ở user_metadata.full_name / name; đăng ký email thì không có, lấy phần trước @.
     */
    private static String displayNameOf(Jwt jwt, @Nullable String email) {
        var metadata = jwt.getClaimAsMap("user_metadata");
        var fromMetadata = nameFrom(metadata);
        if (fromMetadata != null) {
            return fromMetadata;
        }

        if (email == null || email.isBlank()) {
            return "User";
        }
        var at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        return email.substring(0, at);
    }

    private static @Nullable String nameFrom(@Nullable Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        for (var key : Arrays.asList("full_name", "name")) {
            var value = metadata.get(key);
            if (value instanceof String name && !name.isBlank()) {
                return name;
            }
        }
        return null;
    }
}
