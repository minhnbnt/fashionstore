package com.minhnbnt.fashionstore.features.catalog.controller;

import com.minhnbnt.fashionstore.dto.catalog.ImageSearchResponse;
import com.minhnbnt.fashionstore.features.catalog.search.ImageSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Tìm hàng giống ảnh query bằng vector Fashion-CLIP")
public class ImageSearchController {

    private final ImageSearchService service;

    @PostMapping(path = "/products/by-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tìm sản phẩm giống ảnh", description = "Chỉ hàng ACTIVE của shop APPROVED, "
        + "xếp theo cosine distance tăng dần. Không nhận bộ lọc, không phân trang.")
    @ApiResponse(responseCode = "200", description = "Tối đa `limit` sản phẩm giống nhất")
    @ApiResponse(responseCode = "400", description = "File rỗng hoặc không decode được")
    @ApiResponse(responseCode = "413", description = "Ảnh lớn hơn 8MB")
    @ApiResponse(responseCode = "415", description = "Content-type không phải image/*")
    @ApiResponse(responseCode = "503", description = "Worker embedding không sẵn sàng")
    public ImageSearchResponse byImage(
        @RequestPart("file") MultipartFile file,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        var contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "cần ảnh");
        }
        // Chặn sớm để lỗi là 400/413 chứ không phải 500; service vẫn tự kiểm cho caller khác.
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ảnh rỗng");
        }
        if (file.getSize() > ImageSearchService.MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "ảnh quá lớn");
        }
        try {
            return service.search(file.getBytes(), limit);
        } catch (IOException exc) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "không đọc được file", exc);
        }
    }
}
