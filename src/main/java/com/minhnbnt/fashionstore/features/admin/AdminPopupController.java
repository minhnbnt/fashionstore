package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.dto.content.PopupRequest;
import com.minhnbnt.fashionstore.dto.content.PopupResponse;
import com.minhnbnt.fashionstore.features.content.PopupService;
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
@RequestMapping("/api/admin/popups")
@RequiredArgsConstructor
@Tag(
    name = "Admin - Popups",
    description = "Quản lý popup khuyến mãi trang chủ: đối tượng, tần suất, ưu tiên, lịch chạy. Cần role admin."
)
public class AdminPopupController {

    private final PopupService popups;

    @GetMapping
    @Operation(
        operationId = "listAdminPopups",
        summary = "Danh sách popup",
        description = "Mọi popup theo ưu tiên giảm dần, kèm trạng thái lịch chạy."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách popup")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public List<PopupResponse> list() {
        return popups.listForAdmin();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        operationId = "createPopup",
        summary = "Tạo popup",
        description = "Cần ảnh hoặc tiêu đề. Nhiều popup cùng chạy thì trình duyệt hiện "
            + "popup ưu tiên cao nhất còn hợp lệ."
    )
    @ApiResponse(responseCode = "201", description = "Popup đã tạo")
    @ApiResponse(responseCode = "400", description = "Thiếu ảnh và tiêu đề, link không hợp lệ hoặc lịch sai")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public PopupResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PopupRequest request) {
        return popups.create(adminIdOf(jwt), request);
    }

    @PutMapping("/{id}")
    @Operation(
        operationId = "updatePopup",
        summary = "Sửa popup",
        description = "Thay toàn bộ nội dung, lịch chạy, bật/tắt."
    )
    @ApiResponse(responseCode = "200", description = "Popup sau khi sửa")
    @ApiResponse(responseCode = "400", description = "Thiếu ảnh và tiêu đề, link không hợp lệ hoặc lịch sai")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Popup không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public PopupResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id popup") @PathVariable UUID id,
        @Valid @RequestBody PopupRequest request
    ) {
        return popups.update(adminIdOf(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deletePopup",
        summary = "Xóa popup",
        description = "Xóa hẳn. Muốn tạm ẩn thì tắt active."
    )
    @ApiResponse(responseCode = "204", description = "Đã xóa")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "404", description = "Popup không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public void delete(@Parameter(description = "id popup") @PathVariable UUID id) {
        popups.delete(id);
    }

    private static UUID adminIdOf(Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return UUID.fromString(subject);
    }
}
