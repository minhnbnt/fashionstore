package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.dto.catalog.CategoryResponse;
import com.minhnbnt.fashionstore.dto.catalog.ImageResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductSummaryResponse;
import com.minhnbnt.fashionstore.dto.catalog.VariantResponse;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductSearchRepository.Hit;
import com.minhnbnt.fashionstore.model.Category;
import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.ProductImage;
import com.minhnbnt.fashionstore.model.ProductVariant;

import java.util.List;

public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static ProductResponse toResponse(
        Product product,
        List<VariantResponse> variants,
        List<ImageResponse> images
    ) {
        return new ProductResponse(
            product.getId(),
            product.getShopId(),
            product.getCategoryId(),
            product.getName(),
            product.getSlug(),
            product.getDescription(),
            product.getStatus().name(),
            variants,
            images,
            product.getUpdatedAt()
        );
    }

    public static VariantResponse toVariantResponse(ProductVariant variant) {
        return new VariantResponse(
            variant.getId(),
            variant.getSku(),
            variant.getSize(),
            variant.getColor(),
            variant.getPrice(),
            variant.getCompareAtPrice(),
            variant.getStockQuantity(),
            variant.isDefaultVariant(),
            variant.getUpdatedAt()
        );
    }

    public static ImageResponse toImageResponse(ProductImage image) {
        return new ImageResponse(
            image.getId(),
            image.getVariantId(),
            image.getS3Key(),
            image.getSortOrder()
        );
    }

    public static ProductSummaryResponse toSummary(Hit hit) {
        return new ProductSummaryResponse(
            hit.id(),
            hit.shopId(),
            hit.name(),
            hit.slug(),
            hit.minPrice(),
            hit.defaultImageKey(),
            hit.rank()
        );
    }

    public static CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getSlug(),
            category.getSortOrder()
        );
    }
}
