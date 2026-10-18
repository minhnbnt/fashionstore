package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.dto.content.SitePageRequest;
import com.minhnbnt.fashionstore.dto.content.SitePageResponse;
import com.minhnbnt.fashionstore.dto.content.SitePageSummaryResponse;
import com.minhnbnt.fashionstore.features.content.SitePageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/pages")
@RequiredArgsConstructor
@Tag(
    name = "Admin - Pages",
    description = "Quản lý trang tĩnh (giới thiệu, chính sách...): soạn Markdown, nháp/xuất bản. Cần role admin."
)
public class AdminPageController {

    private final SitePageService pages;

    @GetMapping
    @Operation(
        operationId = "listAdminPages",
        summary = "Danh sách trang tĩnh",
        description = "Mọi trang kể cả nháp, mới sửa lên đầu, không kèm nội dung."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách trang")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public List<SitePageSummaryResponse> list() {
        return pages.listForAdmin();
    }

    @GetMapping("/{id}")
    @Operation(
        operationId = "getAdminPage",
        summary = "Chi tiết trang tĩnh",
        description = "Kèm nội dung Markdown, kể cả nháp."
    )
    @ApiResponse(responseCode = "200", description = "Chi tiết trang")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Trang không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public SitePageResponse detail(@Parameter(description = "id trang") @PathVariable UUID id) {
        return pages.getForAdmin(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "createPage",
        summary = "Tạo trang tĩnh",
        description = "Trang mới ở trạng thái nháp."
    )
    @ApiResponse(responseCode = "201", description = "Trang nháp đã tạo")
    @ApiResponse(responseCode = "400", description = "Thiếu trường hoặc slug sai định dạng")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "409", description = "Slug đã có trang khác dùng")
    @SecurityRequirement(name = "bearerAuth")
    public SitePageResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SitePageRequest request) {
        return pages.create(adminIdOf(jwt), request);
    }

    @PutMapping("/{id}")
    @Operation(
        operationId = "updatePage",
        summary = "Sửa trang tĩnh",
        description = "Sửa trang đã xuất bản thì public thấy ngay. Đổi slug làm link cũ hết hiệu lực."
    )
    @ApiResponse(responseCode = "200", description = "Trang sau khi sửa")
    @ApiResponse(responseCode = "400", description = "Thiếu trường hoặc slug sai định dạng")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Trang không tồn tại")
    @ApiResponse(responseCode = "409", description = "Slug đã có trang khác dùng")
    @SecurityRequirement(name = "bearerAuth")
    public SitePageResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id trang") @PathVariable UUID id,
        @Valid @RequestBody SitePageRequest request
    ) {
        return pages.update(adminIdOf(jwt), id, request);
    }

    @PatchMapping("/{id}/publish")
    @Operation(
        operationId = "publishPage",
        summary = "Xuất bản trang",
        description = "DRAFT → PUBLISHED, hiện ở footer + /pages/{slug}."
    )
    @ApiResponse(responseCode = "200", description = "Trang đã xuất bản")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Trang không tồn tại")
    @ApiResponse(responseCode = "409", description = "Trang đã xuất bản")
    @SecurityRequirement(name = "bearerAuth")
    public SitePageResponse publish(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id trang") @PathVariable UUID id
    ) {
        return pages.publish(adminIdOf(jwt), id);
    }

    @PatchMapping("/{id}/unpublish")
    @Operation(
        operationId = "unpublishPage",
        summary = "Gỡ xuất bản trang",
        description = "PUBLISHED → DRAFT, public trả 404."
    )
    @ApiResponse(responseCode = "200", description = "Trang đã về nháp")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Trang không tồn tại")
    @ApiResponse(responseCode = "409", description = "Trang đang là nháp")
    @SecurityRequirement(name = "bearerAuth")
    public SitePageResponse unpublish(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id trang") @PathVariable UUID id
    ) {
        return pages.unpublish(adminIdOf(jwt), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deletePage",
        summary = "Xóa trang tĩnh",
        description = "Chỉ xóa được trang nháp."
    )
    @ApiResponse(responseCode = "204", description = "Đã xóa")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Trang không tồn tại")
    @ApiResponse(responseCode = "409", description = "Trang đang xuất bản, gỡ xuất bản trước")
    @SecurityRequirement(name = "bearerAuth")
    public void delete(@Parameter(description = "id trang") @PathVariable UUID id) {
        pages.delete(id);
    }

    private static UUID adminIdOf(Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return UUID.fromString(subject);
    }
}
