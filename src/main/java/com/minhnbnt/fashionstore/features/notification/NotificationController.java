package com.minhnbnt.fashionstore.features.notification;

import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.notification.NotificationCountResponse;
import com.minhnbnt.fashionstore.dto.notification.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Thông báo trong website cho người dùng")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(
        operationId = "listNotifications",
        summary = "Danh sách thông báo của tôi",
        description = "Lấy danh sách thông báo của người dùng hiện tại theo phân trang, mới nhất trước."
    )
    @ApiResponse(responseCode = "200", description = "Trang thông báo")
    @ApiResponse(responseCode = "400", description = "Category không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<NotificationResponse> listNotifications(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Lọc theo nhóm thông báo: ORDER, ACTIVITY, SELLING. Phân tách bằng dấu phẩy")
        @RequestParam(required = false) @Nullable String categories,
        @Parameter(description = "Trang hiện tại, đếm từ 0") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Số phần tử mỗi trang, tối đa 100")
        @RequestParam(defaultValue = "20") int size
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return notificationService.page(userId, parseCategories(categories), page, size);
    }

    @GetMapping("/unread-count")
    @Operation(
        operationId = "getUnreadNotificationCount",
        summary = "Số thông báo chưa đọc",
        description = "Đếm số lượng thông báo chưa đọc của người dùng hiện tại."
    )
    @ApiResponse(responseCode = "200", description = "Số thông báo chưa đọc")
    @ApiResponse(responseCode = "400", description = "Category không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public NotificationCountResponse getUnreadNotificationCount(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Lọc theo nhóm thông báo: ORDER, ACTIVITY, SELLING. Phân tách bằng dấu phẩy")
        @RequestParam(required = false) @Nullable String categories
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return new NotificationCountResponse(notificationService.unreadCount(userId, parseCategories(categories)));
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "markNotificationRead",
        summary = "Đánh dấu thông báo đã đọc",
        description = "Đánh dấu một thông báo cụ thể của người dùng là đã đọc."
    )
    @ApiResponse(responseCode = "204", description = "Đã đánh dấu đã đọc")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy thông báo")
    @SecurityRequirement(name = "bearerAuth")
    public void markNotificationRead(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id thông báo") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        notificationService.markRead(userId, id);
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "markAllNotificationsRead",
        summary = "Đánh dấu tất cả thông báo đã đọc",
        description = "Đánh dấu toàn bộ thông báo của người dùng là đã đọc."
    )
    @ApiResponse(responseCode = "204", description = "Đã đánh dấu tất cả đã đọc")
    @ApiResponse(responseCode = "400", description = "Category không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public void markAllNotificationsRead(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Lọc theo nhóm thông báo: ORDER, ACTIVITY, SELLING. Phân tách bằng dấu phẩy")
        @RequestParam(required = false) @Nullable String categories
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        notificationService.markAllRead(userId, parseCategories(categories));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deleteNotification",
        summary = "Xóa thông báo",
        description = "Xóa một thông báo của người dùng hiện tại."
    )
    @ApiResponse(responseCode = "204", description = "Đã xóa thành công")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy thông báo")
    @SecurityRequirement(name = "bearerAuth")
    public void deleteNotification(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id thông báo") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        notificationService.deleteNotification(userId, id);
    }

    private @Nullable List<String> parseCategories(@Nullable String categories) {
        if (categories == null || categories.isBlank()) {
            return null;
        }

        var parts = categories.split(",", -1);
        var result = new ArrayList<String>();
        for (var rawPart : parts) {
            var part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            try {
                NotificationCategory.valueOf(part);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown notification category: " + part);
            }
            result.add(part);
        }

        if (result.isEmpty()) {
            return null;
        }
        return result;
    }
}
