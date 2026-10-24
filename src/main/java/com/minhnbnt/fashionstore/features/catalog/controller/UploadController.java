package com.minhnbnt.fashionstore.features.catalog.controller;

import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.dto.catalog.PresignRequest;
import com.minhnbnt.fashionstore.dto.catalog.PresignResponse;
import com.minhnbnt.fashionstore.infrastructure.S3Storage;
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
@Tag(name = "Uploads", description = "Xin URL upload ảnh trực tiếp lên S3")
public class UploadController {

    private final S3Storage storage;

    @PostMapping("/presign")
    @Operation(
        summary = "Xin URL upload",
        description = "Nhận image/* hoặc video/*. Frontend PUT file lên url, rồi lấy key gắn vào product."
    )
    @ApiResponse(responseCode = "200", description = "Key và URL upload")
    @ApiResponse(responseCode = "400", description = "Content-type không phải ảnh hoặc video")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public PresignResponse presign(@Valid @RequestBody PresignRequest request) {
        var upload = storage.presign(request.contentType());

        return new PresignResponse(
            upload.key(),
            upload.url(),
            upload.expiresAt()
        );
    }
}
