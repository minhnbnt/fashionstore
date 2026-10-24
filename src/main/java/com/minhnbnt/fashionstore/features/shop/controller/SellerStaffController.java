package com.minhnbnt.fashionstore.features.shop.controller;

import com.minhnbnt.fashionstore.dto.shop.ChangeMemberRoleRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRoleRequest;
import com.minhnbnt.fashionstore.dto.shop.InviteStaffRequest;
import com.minhnbnt.fashionstore.dto.shop.MyPermissionsResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopActivityResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopMemberDetailResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopRoleResponse;
import com.minhnbnt.fashionstore.dto.shop.StaffInvitationResponse;
import com.minhnbnt.fashionstore.dto.shop.UpdateMemberStatusRequest;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.features.shop.ShopStaffService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/seller/shops/{shopId}")
@RequiredArgsConstructor
@Tag(name = "Seller - Staff & Roles", description = "Quản lý nhân viên gian hàng, phân quyền và lịch sử thao tác")
public class SellerStaffController {

    private final ShopStaffService staffService;

    @GetMapping("/roles")
    @Operation(summary = "Danh sách vai trò của shop", description = "Lấy danh sách các vai trò mặc định (Quản lý, CSKH, Kho vận, Marketing) và các vai trò tùy chỉnh.")
    @ApiResponse(responseCode = "200", description = "Danh sách vai trò")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không có quyền xem vai trò shop")
    @SecurityRequirement(name = "bearerAuth")
    public List<ShopRoleResponse> listRoles(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return staffService.listRoles(userId, shopId);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo vai trò tùy chỉnh", description = "Chủ shop tạo nhóm quyền mới cho nhân viên.")
    @ApiResponse(responseCode = "201", description = "Tạo vai trò thành công")
    @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Chỉ chủ shop mới được tạo vai trò")
    @ApiResponse(responseCode = "409", description = "Tên vai trò đã tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public ShopRoleResponse createRole(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Valid @RequestBody CreateShopRoleRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return staffService.createRole(userId, shopId, request);
    }

    @PutMapping("/roles/{roleId}")
    @Operation(summary = "Cập nhật vai trò tùy chỉnh", description = "Chỉnh sửa tên và quyền của vai trò tùy chỉnh. Không thể sửa vai trò hệ thống.")
    @ApiResponse(responseCode = "200", description = "Cập nhật thành công")
    @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Chỉ chủ shop mới được chỉnh sửa vai trò")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy vai trò")
    @ApiResponse(responseCode = "409", description = "Không thể sửa vai trò hệ thống")
    @SecurityRequirement(name = "bearerAuth")
    public ShopRoleResponse updateRole(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id vai trò") @PathVariable UUID roleId,
        @Valid @RequestBody CreateShopRoleRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return staffService.updateRole(userId, shopId, roleId, request);
    }

    @DeleteMapping("/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa vai trò tùy chỉnh", description = "Xóa một vai trò tùy chỉnh. Không thể xóa vai trò hệ thống.")
    @ApiResponse(responseCode = "204", description = "Đã xóa vai trò thành công")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Chỉ chủ shop mới được xóa vai trò")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy vai trò")
    @ApiResponse(responseCode = "409", description = "Không thể xóa vai trò hệ thống")
    @SecurityRequirement(name = "bearerAuth")
    public void deleteRole(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id vai trò") @PathVariable UUID roleId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        staffService.deleteRole(userId, shopId, roleId);
    }

    @GetMapping("/members")
    @Operation(summary = "Danh sách thành viên shop", description = "Lấy danh sách chủ shop và nhân viên kèm theo vai trò chi tiết.")
    @ApiResponse(responseCode = "200", description = "Danh sách thành viên")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không có quyền xem thành viên shop")
    @SecurityRequirement(name = "bearerAuth")
    public List<ShopMemberDetailResponse> listMembers(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return staffService.listMembers(userId, shopId);
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Mời nhân viên vào shop", description = "Gửi lời mời tham gia shop cho tài khoản đã đăng ký theo email kèm theo vai trò. Lời mời được giao qua thông báo trong website, không gửi email.")
    @ApiResponse(responseCode = "201", description = "Tạo lời mời thành công")
    @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Chỉ chủ shop mới được mời nhân viên")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy vai trò hoặc email chưa có tài khoản")
    @ApiResponse(responseCode = "409", description = "Email đã là thành viên hoặc đang có lời mời chờ")
    @SecurityRequirement(name = "bearerAuth")
    public StaffInvitationResponse inviteStaff(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Valid @RequestBody InviteStaffRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return staffService.inviteStaff(userId, shopId, request);
    }

    @GetMapping("/invitations")
    @Operation(
        operationId = "listInvitations",
        summary = "Danh sách lời mời đang chờ",
        description = "Chủ shop xem các lời mời chưa chấp nhận và chưa hết hạn của shop."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách lời mời đang chờ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Chỉ chủ shop mới được xem lời mời")
    @SecurityRequirement(name = "bearerAuth")
    public List<StaffInvitationResponse> listInvitations(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return staffService.listInvitations(userId, shopId);
    }

    @DeleteMapping("/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "revokeInvitation",
        summary = "Thu hồi lời mời",
        description = "Chủ shop thu hồi một lời mời đang chờ; thông báo trong website của người được mời cũng bị xóa."
    )
    @ApiResponse(responseCode = "204", description = "Đã thu hồi lời mời")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Chỉ chủ shop mới được thu hồi lời mời")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy lời mời")
    @ApiResponse(responseCode = "409", description = "Lời mời đã được chấp nhận")
    @SecurityRequirement(name = "bearerAuth")
    public void revokeInvitation(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id lời mời") @PathVariable UUID invitationId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        staffService.revokeInvitation(userId, shopId, invitationId);
    }

    @PatchMapping("/members/{memberId}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Thay đổi vai trò của nhân viên", description = "Chủ shop cập nhật vai trò/quyền hạn mới cho một nhân viên.")
    @ApiResponse(responseCode = "204", description = "Cập nhật vai trò thành công")
    @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Chỉ chủ shop mới được đổi vai trò")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy thành viên hoặc vai trò")
    @ApiResponse(responseCode = "409", description = "Không thể thay đổi vai trò OWNER")
    @SecurityRequirement(name = "bearerAuth")
    public void changeMemberRole(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id thành viên") @PathVariable UUID memberId,
        @Valid @RequestBody ChangeMemberRoleRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        staffService.changeMemberRole(userId, shopId, memberId, request);
    }

    @PatchMapping("/members/{memberId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "updateMemberStatus",
        summary = "Kích hoạt hoặc vô hiệu hóa nhân viên",
        description = "Chủ shop đổi trạng thái hoạt động của nhân viên. Nhân viên bị vô hiệu hóa mất quyền "
            + "truy cập shop ngay ở request kế tiếp, không cần đăng xuất."
    )
    @ApiResponse(responseCode = "204", description = "Cập nhật trạng thái thành công")
    @ApiResponse(responseCode = "400", description = "Trạng thái không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Chỉ chủ shop mới được đổi trạng thái nhân viên")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy thành viên")
    @ApiResponse(responseCode = "409", description = "Không thể đổi trạng thái chủ shop (OWNER)")
    @SecurityRequirement(name = "bearerAuth")
    public void updateMemberStatus(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id thành viên") @PathVariable UUID memberId,
        @Valid @RequestBody UpdateMemberStatusRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        staffService.updateMemberStatus(userId, shopId, memberId, request.status());
    }

    @DeleteMapping("/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Vô hiệu hóa nhân viên khỏi shop",
        description = "Chủ shop vô hiệu hóa nhân viên: nhân viên mất quyền truy cập shop ngay lập tức "
            + "nhưng vẫn còn trong danh sách thành viên để có thể kích hoạt lại."
    )
    @ApiResponse(responseCode = "204", description = "Đã vô hiệu hóa nhân viên")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Chỉ chủ shop mới được vô hiệu hóa nhân viên")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy thành viên")
    @ApiResponse(responseCode = "409", description = "Không thể vô hiệu hóa chủ shop (OWNER)")
    @SecurityRequirement(name = "bearerAuth")
    public void removeMember(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Id thành viên") @PathVariable UUID memberId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        staffService.removeMember(userId, shopId, memberId);
    }

    @GetMapping("/my-permissions")
    @Operation(
        operationId = "getMyPermissions",
        summary = "Quyền của tôi trong shop",
        description = "Trả về cờ chủ shop và danh sách quyền hiện có của người dùng trong shop, "
            + "dùng để ẩn/hiện menu và nút thao tác trên giao diện người bán."
    )
    @ApiResponse(responseCode = "200", description = "Quyền của người dùng hiện tại")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên đang hoạt động của shop")
    @SecurityRequirement(name = "bearerAuth")
    public MyPermissionsResponse getMyPermissions(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return staffService.myPermissions(userId, shopId);
    }

    @GetMapping("/audit")
    @Operation(
        operationId = "listAuditLogs",
        summary = "Nhật ký hoạt động của shop",
        description = "Chủ shop xem nhật ký mọi thao tác nghiệp vụ và nhân sự trong shop, mới nhất trước. "
            + "Có thể lọc theo mã hành động."
    )
    @ApiResponse(responseCode = "200", description = "Trang nhật ký hoạt động")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Chỉ chủ shop mới được xem nhật ký")
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<ShopActivityResponse> listAuditLogs(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @PathVariable UUID shopId,
        @Parameter(description = "Lọc theo mã hành động, ví dụ PRODUCT_CREATE")
        @RequestParam(required = false) String action,
        @Parameter(description = "Trang hiện tại, đếm từ 0") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Số phần tử mỗi trang, tối đa 100") @RequestParam(defaultValue = "20") int size
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return staffService.listAuditLogs(userId, shopId, action, page, size);
    }
}
