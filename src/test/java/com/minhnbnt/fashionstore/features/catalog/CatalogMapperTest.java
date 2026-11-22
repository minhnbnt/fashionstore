package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.dto.catalog.ImageResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.VariantResponse;
import com.minhnbnt.fashionstore.model.ProductImage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMapperTest {

    @Test
    void mapsFieldsAndMinPriceIsCheapestVariantAndFirstImageIsDefault() {
        var id = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var response = new ProductResponse(
            id, shopId, null, "Áo thun", "ao-thun", "Mô tả", "ACTIVE",
            List.of(variant(300000L), variant(150000L)),
            List.of(image("http://s3.test/p/1.jpg", 0), image("p/2.jpg", 1)),
            Instant.now(), null, 0L
        );

        var summary = CatalogMapper.toSummary(response);

        assertThat(summary.id()).isEqualTo(id);
        assertThat(summary.shopId()).isEqualTo(shopId);
        assertThat(summary.name()).isEqualTo("Áo thun");
        assertThat(summary.slug()).isEqualTo("ao-thun");
        assertThat(summary.minPrice()).isEqualTo(150000L);
        assertThat(summary.defaultImageUrl()).isEqualTo("http://s3.test/p/1.jpg");
        assertThat(summary.rank()).isNull();
    }

    @Test
    void noVariantMeansNullMinPrice() {
        var response = new ProductResponse(
            UUID.randomUUID(), UUID.randomUUID(), null, "Áo thun", "ao-thun", "Mô tả", "ACTIVE",
            List.of(), List.of(image("http://s3.test/p/1.jpg", 0)),
            Instant.now(), null, 0L
        );

        var summary = CatalogMapper.toSummary(response);

        assertThat(summary.minPrice()).isNull();
    }

    @Test
    void noImageMeansNullDefaultImageUrl() {
        var response = new ProductResponse(
            UUID.randomUUID(), UUID.randomUUID(), null, "Áo thun", "ao-thun", "Mô tả", "ACTIVE",
            List.of(variant(100000L)), List.of(),
            Instant.now(), null, 0L
        );

        var summary = CatalogMapper.toSummary(response);

        assertThat(summary.defaultImageUrl()).isNull();
    }

    @Test
    void imageResponseCarriesUploadIdForEditEcho() {
        var uploadId = UUID.randomUUID();
        var image = ProductImage.create(UUID.randomUUID(), null, uploadId, 0);

        var response = CatalogMapper.toImageResponse(image, "http://s3.test/p/1.jpg");

        assertThat(response.uploadId()).isEqualTo(uploadId);
        assertThat(response.url()).isEqualTo("http://s3.test/p/1.jpg");
    }

    @Test
    void mapsEnrichedSummaryFieldsFromProductResponseAndHit() {
        var id = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var discounted = new VariantResponse(
            UUID.randomUUID(), "SKU-SALE", "L", "Trắng", 199000L, 299000L, 7, false, Instant.now()
        );
        var regular = new VariantResponse(
            UUID.randomUUID(), "SKU-REG", "M", "Đen", 220000L, 200000L, 5, true, Instant.now()
        );
        var response = new ProductResponse(
            id, shopId, null, "Áo sơ mi", "ao-so-mi", "Mô tả", "ACTIVE",
            List.of(discounted, regular),
            List.of(image("http://s3.test/p/1.jpg", 0)),
            Instant.now(), 4.8, 12L
        );

        var summary = CatalogMapper.toSummary(response);

        assertThat(summary.minPrice()).isEqualTo(199000L);
        assertThat(summary.compareAtPrice()).isEqualTo(299000L);
        assertThat(summary.averageRating()).isEqualTo(4.8);
        assertThat(summary.reviewCount()).isEqualTo(12L);
        assertThat(summary.totalStock()).isEqualTo(12L);

        var hit = new com.minhnbnt.fashionstore.features.catalog.repository.ProductSearchRepository.Hit(
            id, shopId, "Áo sơ mi", "ao-so-mi", 199000L, "http://s3.test/p/1.jpg", -1.2,
            299000L, 4.8, 12L, 45L, "DHMQ Studio", "dhmq-studio", 12L
        );

        var hitSummary = CatalogMapper.toSummary(hit);

        assertThat(hitSummary.compareAtPrice()).isEqualTo(299000L);
        assertThat(hitSummary.averageRating()).isEqualTo(4.8);
        assertThat(hitSummary.reviewCount()).isEqualTo(12L);
        assertThat(hitSummary.soldCount()).isEqualTo(45L);
        assertThat(hitSummary.shopName()).isEqualTo("DHMQ Studio");
        assertThat(hitSummary.shopSlug()).isEqualTo("dhmq-studio");
        assertThat(hitSummary.totalStock()).isEqualTo(12L);
    }

    private static VariantResponse variant(Long price) {
        return new VariantResponse(UUID.randomUUID(), "SKU-1", "M", "Đen", price, null, 10, true, Instant.now());
    }

    private static ImageResponse image(String url, int sortOrder) {
        return new ImageResponse(UUID.randomUUID(), null, url, UUID.randomUUID(), sortOrder);
    }
}
