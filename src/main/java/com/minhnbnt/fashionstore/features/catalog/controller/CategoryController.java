package com.minhnbnt.fashionstore.features.catalog.controller;

import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.dto.catalog.CategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Danh mục public, admin seed tay bằng SQL")
public class CategoryController {

    private final CatalogService catalog;

    @GetMapping
    @Operation(summary = "Liệt kê danh mục", description = "Public, xếp theo sort_order.")
    @ApiResponse(responseCode = "200", description = "Danh sách danh mục")
    public List<CategoryResponse> list() {
        return catalog.listCategories();
    }
}
