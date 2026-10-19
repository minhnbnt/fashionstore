package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.dto.content.BannerRequest;
import com.minhnbnt.fashionstore.dto.content.BannerResponse;
import com.minhnbnt.fashionstore.features.content.BannerService;
import com.minhnbnt.fashionstore.model.BannerPlacement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Banner gọi thẳng {@link BannerService}: không điều phối liên domain nên không cần lớp Admin*Service chuyển tiếp.
 */
@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
@Tag(name = "Admin - Banners", description = "Quản lý banner trang chủ theo vị trí + lịch chạy. Cần role admin.")
public class AdminBannerController {

    private final BannerService banners;

    @GetMapping
    @Operation(
        operationId = "listAdminBanners",
        summary = "Danh sách banner",
        description = "Mọi banner kể cả đã tắt/hết hạn, theo vị trí rồi thứ tự, kèm trạng thái lịch chạy."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách banner")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public List<BannerResponse> list(
        @Parameter(description = "Chỉ lấy một vị trí, bỏ trống = tất cả", example = "HOME_CAROUSEL")
        @RequestParam(required = false) @Nullable BannerPlacement placement
    ) {
        return banners.listForAdmin(placement);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "createBanner",
        summary = "Tạo banner",
        description = "Ảnh upload trước qua /api/uploads/presign rồi gửi S3 key. "
            + "Link chỉ nhận đường dẫn nội bộ hoặc http(s)."
    )
    @ApiResponse(responseCode = "201", description = "Banner đã tạo")
    @ApiResponse(responseCode = "400", description = "Thiếu trường, link sai hoặc giờ kết thúc không sau giờ bắt đầu")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public BannerResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BannerRequest request) {
        return banners.create(adminIdOf(jwt), request);
    }

    @PutMapping("/{id}")
    @Operation(
        operationId = "updateBanner",
        summary = "Sửa banner",
        description = "Thay toàn bộ nội dung, lịch chạy, bật/tắt."
    )
    @ApiResponse(responseCode = "200", description = "Banner sau khi sửa")
    @ApiResponse(responseCode = "400", description = "Thiếu trường, link sai hoặc giờ kết thúc không sau giờ bắt đầu")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Banner không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public BannerResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id banner") @PathVariable UUID id,
        @Valid @RequestBody BannerRequest request
    ) {
        return banners.update(adminIdOf(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deleteBanner",
        summary = "Xóa banner",
        description = "Xóa hẳn. Muốn tạm ẩn thì tắt active."
    )
    @ApiResponse(responseCode = "204", description = "Đã xóa")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Banner không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public void delete(@Parameter(description = "id banner") @PathVariable UUID id) {
        banners.delete(id);
    }

    private static UUID adminIdOf(Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return UUID.fromString(subject);
    }
}
