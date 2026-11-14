package com.minhnbnt.fashionstore.features.upload;

import com.minhnbnt.fashionstore.dto.upload.PresignRequest;
import com.minhnbnt.fashionstore.dto.upload.PresignResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
@Tag(name = "Uploads", description = "Xin URL upload ảnh/video trực tiếp lên S3")
public class UploadController {

    private final UploadService uploads;

    @PostMapping("/presign")
    @Operation(
        summary = "Xin URL upload",
        description = "Nhận image/* hoặc video/*. Tạo bản ghi upload ngay và trả uploadId + uploadUrl; "
            + "frontend PUT file lên uploadUrl rồi gửi uploadId vào các request nghiệp vụ. "
            + "purpose=kyc đặt key dưới prefix kyc/ (chặn đọc ẩn danh, xem qua presigned GET)."
    )
    @ApiResponse(responseCode = "200", description = "uploadId và URL upload")
    @ApiResponse(responseCode = "400", description = "Content-type không phải ảnh/video hoặc purpose không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public PresignResponse presign(@Valid @RequestBody PresignRequest request) {
        return uploads.presign(request);
    }
}
