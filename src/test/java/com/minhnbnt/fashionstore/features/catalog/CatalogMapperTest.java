package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.dto.catalog.ImageResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.VariantResponse;
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
            List.of(image("p/1.jpg", 0), image("p/2.jpg", 1)),
            Instant.now(), null, 0L
        );

        var summary = CatalogMapper.toSummary(response);

        assertThat(summary.id()).isEqualTo(id);
        assertThat(summary.shopId()).isEqualTo(shopId);
        assertThat(summary.name()).isEqualTo("Áo thun");
        assertThat(summary.slug()).isEqualTo("ao-thun");
        assertThat(summary.minPrice()).isEqualTo(150000L);
        assertThat(summary.defaultImageKey()).isEqualTo("p/1.jpg");
        assertThat(summary.rank()).isNull();
    }

    @Test
    void noVariantMeansNullMinPrice() {
        var response = new ProductResponse(
            UUID.randomUUID(), UUID.randomUUID(), null, "Áo thun", "ao-thun", "Mô tả", "ACTIVE",
            List.of(), List.of(image("p/1.jpg", 0)),
            Instant.now(), null, 0L
        );

        var summary = CatalogMapper.toSummary(response);

        assertThat(summary.minPrice()).isNull();
    }

    @Test
    void noImageMeansNullDefaultImageKey() {
        var response = new ProductResponse(
            UUID.randomUUID(), UUID.randomUUID(), null, "Áo thun", "ao-thun", "Mô tả", "ACTIVE",
            List.of(variant(100000L)), List.of(),
            Instant.now(), null, 0L
        );

        var summary = CatalogMapper.toSummary(response);

        assertThat(summary.defaultImageKey()).isNull();
    }

    private static VariantResponse variant(Long price) {
        return new VariantResponse(UUID.randomUUID(), "SKU-1", "M", "Đen", price, null, 10, true, Instant.now());
    }

    private static ImageResponse image(String s3Key, int sortOrder) {
        return new ImageResponse(UUID.randomUUID(), null, s3Key, sortOrder);
    }
}
