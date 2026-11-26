package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.dto.catalog.CategoryResponse;
import com.minhnbnt.fashionstore.dto.catalog.GarmentImageResponse;
import com.minhnbnt.fashionstore.dto.catalog.ImageResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductSummaryResponse;
import com.minhnbnt.fashionstore.dto.catalog.VariantResponse;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductSearchRepository.Hit;
import com.minhnbnt.fashionstore.model.Category;
import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.ProductGarment;
import com.minhnbnt.fashionstore.model.ProductImage;
import com.minhnbnt.fashionstore.model.ProductVariant;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static ProductResponse toResponse(
        Product product,
        List<VariantResponse> variants,
        List<ImageResponse> images,
        @Nullable Double averageRating,
        long reviewCount,
        @Nullable String videoUrl,
        @Nullable String garmentUrl
    ) {
        return toResponse(
            product, variants, images, averageRating, reviewCount, videoUrl, garmentUrl, List.of()
        );
    }

    public static ProductResponse toResponse(
        Product product,
        List<VariantResponse> variants,
        List<ImageResponse> images,
        @Nullable Double averageRating,
        long reviewCount,
        @Nullable String videoUrl,
        @Nullable String garmentUrl,
        List<GarmentImageResponse> garments
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
            product.getUpdatedAt(),
            averageRating,
            reviewCount,
            videoUrl,
            garmentUrl,
            product.getVideoUploadId(),
            product.getGarmentUploadId(),
            product.isTryOnEnabled(),
            product.getMaterial(),
            product.getOrigin(),
            product.getCareInstructions(),
            product.getSizeGuide(),
            product.getWeightGrams(),
            product.getLengthCm(),
            product.getWidthCm(),
            product.getHeightCm(),
            garments
        );
    }

    public static GarmentImageResponse toGarmentImageResponse(ProductGarment garment, @Nullable String url) {
        return new GarmentImageResponse(
            garment.getId(),
            Objects.requireNonNull(url, "upload row missing for garment " + garment.getId()),
            garment.getUploadId(),
            garment.getSortOrder()
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

    public static ImageResponse toImageResponse(ProductImage image, @Nullable String url) {
        return new ImageResponse(
            image.getId(),
            image.getVariantId(),
            Objects.requireNonNull(url, "upload row missing for image " + image.getId()),
            image.getUploadId(),
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
            hit.defaultImageUrl(),
            hit.rank(),
            hit.compareAtPrice(),
            hit.averageRating(),
            hit.reviewCount(),
            hit.soldCount(),
            hit.shopName(),
            hit.shopSlug(),
            hit.totalStock()
        );
    }

    public static ProductSummaryResponse toSummary(ProductResponse product) {
        return new ProductSummaryResponse(
            product.id(),
            product.shopId(),
            product.name(),
            product.slug(),
            minVariantPrice(product.variants()),
            firstImageUrl(product.images()),
            null,
            maxDiscountCompareAtPrice(product.variants()),
            product.averageRating(),
            product.reviewCount(),
            0L,
            null,
            null,
            totalVariantStock(product.variants())
        );
    }

    private static @Nullable Long minVariantPrice(List<VariantResponse> variants) {
        return variants.stream()
            .map(VariantResponse::price)
            .filter(Objects::nonNull)
            .min(Long::compare)
            .orElse(null);
    }

    private static @Nullable Long maxDiscountCompareAtPrice(List<VariantResponse> variants) {
        Long maxCompareAt = null;
        for (var variant : variants) {
            var compareAt = variant.compareAtPrice();
            var price = variant.price();
            if (compareAt != null && price != null && compareAt > price) {
                if (maxCompareAt == null || compareAt > maxCompareAt) {
                    maxCompareAt = compareAt;
                }
            }
        }
        return maxCompareAt;
    }

    private static long totalVariantStock(List<VariantResponse> variants) {
        long total = 0L;
        for (var variant : variants) {
            total += variant.stockQuantity();
        }
        return total;
    }

    private static @Nullable String firstImageUrl(List<ImageResponse> images) {
        if (images.isEmpty()) {
            return null;
        }
        return images.get(0).url();
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
