package com.minhnbnt.fashionstore.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TikiCatalogImporterTest {

    @Test
    void createsStableVietnameseSlugWithSourceId() {
        assertThat(TikiCatalogImporter.slug("Áo thun Nam Cotton 100%", 12345L))
            .isEqualTo("ao-thun-nam-cotton-100-12345");
    }

    @Test
    void limitsLongSlugWithoutRemovingSourceId() {
        var longName = "Áo thời trang cao cấp ".repeat(30);

        var slug = TikiCatalogImporter.slug(longName, 98765L);

        assertThat(slug).hasSizeLessThanOrEqualTo(180);
        assertThat(slug).endsWith("-98765");
    }

    @Test
    void rejectsNonImageContentType() {
        assertThat(TikiCatalogImporter.isImage("image/jpeg")).isTrue();
        assertThat(TikiCatalogImporter.isImage("IMAGE/WEBP; charset=binary")).isTrue();
        assertThat(TikiCatalogImporter.isImage("text/html")).isFalse();
        assertThat(TikiCatalogImporter.isImage(null)).isFalse();
    }

    @Test
    void acceptsLargerImportTargetsWithinSafetyLimit() {
        assertThat(TikiCatalogImporter.normalizedLimit(100)).isEqualTo(100);
        assertThat(TikiCatalogImporter.normalizedLimit(0)).isEqualTo(1);
        assertThat(TikiCatalogImporter.normalizedLimit(2_000)).isEqualTo(500);
    }
}
