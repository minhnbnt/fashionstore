package com.minhnbnt.fashionstore.test;

import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class ProductTestFixtures {

    private ProductTestFixtures() {
    }

    public static CreateProductRequest request(
        String name,
        String slug,
        @Nullable String description,
        @Nullable UUID categoryId,
        List<CreateVariantRequest> variants
    ) {
        return new CreateProductRequest(
            name,
            slug,
            description,
            categoryId,
            variants,
            List.of(),
            null, null, null,
            null, null, null, null,
            null, null, null, null,
            null
        );
    }
}
