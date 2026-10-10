package com.minhnbnt.fashionstore.features.identity;

import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.identity.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Users", description = "Mirror GoTrue user vào user_profile")
public class UserController {

    private final UserService users;

    @PostMapping("/api/users")
    @Operation(
        summary = "Tạo hoặc cập nhật profile",
        description = "id lấy từ JWT sub, không tin id client gửi. Gọi 1 lần sau login, lần sau là update."
    )
    @ApiResponse(responseCode = "200", description = "Profile sau upsert")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse upsert(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateUserRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());

        return users.upsert(UUID.fromString(subject), request);
    }

    @GetMapping("/api/users/me")
    @Operation(summary = "Profile của tôi")
    @ApiResponse(responseCode = "200", description = "Profile hiện tại")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Chưa mirror, gọi POST /api/users trước")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());

        return users.getById(UUID.fromString(subject));
    }

    @GetMapping("/api/public/hello")
    @Operation(
        summary = "Chào public",
        description = "Không cần auth. Nếu gửi JWT thì message cá nhân hóa theo sub."
    )
    @ApiResponse(responseCode = "200", description = "Lời chào")
    public Map<String, String> publicHello(@AuthenticationPrincipal @Nullable Jwt jwt) {

        var message = "Hello, world!";
        if (jwt != null) {
            message = String.format("Hello, %s!", jwt.getSubject());
        }

        return Map.of("message", message);
    }
}
