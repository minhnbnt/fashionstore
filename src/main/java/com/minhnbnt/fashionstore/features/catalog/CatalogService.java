package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.dto.catalog.AttachImagesRequest;
import com.minhnbnt.fashionstore.dto.catalog.CategoryResponse;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.ImageResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductSearchRequest;
import com.minhnbnt.fashionstore.dto.catalog.ProductSearchResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductSort;
import com.minhnbnt.fashionstore.dto.catalog.SearchFacetsResponse;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.UpdateVariantRequest;
import com.minhnbnt.fashionstore.features.catalog.repository.CategoryRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductImageRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductSearchRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductVariantRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.ProductImage;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.ProductVariant;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ProductRepository products;

    private final ProductVariantRepository variants;

    private final ProductImageRepository images;

    private final CategoryRepository categories;

    private final ProductSearchRepository search;

    private final ShopService shops;

    private final ShopAccessChecker access;

    @Transactional
    public ProductResponse createProduct(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull CreateProductRequest request
    ) {
        access.requireApprovedShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

        requireSingleDefault(request.variants());

        for (var variant : request.variants()) {
            requireValidPrice(variant.price(), variant.compareAtPrice());
        }

        requireCategoryExists(request.categoryId());

        var product = Product.create(
            shopId,
            request.categoryId(),
            request.name(),
            request.slug(),
            Objects.requireNonNullElse(request.description(), ""),
            userId
        );
        try {
            products.save(product);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already taken");
        }

        var createdVariants = request.variants().stream()
            .map(variant -> ProductVariant.create(
                shopId,
                product.getId(),
                variant.sku(),
                variant.size(),
                variant.color(),
                variant.price(),
                variant.compareAtPrice(),
                variant.stockQuantity(),
                variant.defaultVariant()
            ))
            .toList();

        try {
            variants.saveAll(createdVariants);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already taken");
        }

        return toResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID productId,
        @NonNull UpdateProductRequest request
    ) {
        var product = products.findByShopIdAndId(shopId, productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        access.requireApprovedShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

        if (request.categoryId() != null) {
            requireCategoryExists(request.categoryId());
        }

        product.updateDetails(request.name(), request.description(), request.categoryId());

        if (request.status() != null) {
            applyStatus(product, request.status());
        }

        if (request.variants() != null) {
            applyVariantUpdates(product, request.variants());
        }

        return toResponse(products.save(product));
    }

    @Transactional
    public List<ImageResponse> attachImages(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID productId,
        @NonNull AttachImagesRequest request
    ) {
        var product = products.findByShopIdAndId(shopId, productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        access.requireApprovedShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

        for (var item : request.images()) {
            if (item.variantId() != null) {
                requireVariantInProduct(product.getId(), item.variantId());
            }
        }

        var created = request.images().stream()
            .map(item -> ProductImage.create(
                product.getId(),
                item.variantId(),
                item.s3Key(),
                item.sortOrder()
            ))
            .toList();

        var savedImages = images.saveAll(created).spliterator();

        return StreamSupport.stream(savedImages, false)
            .map(CatalogMapper::toImageResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public long countProductsByShop(@NonNull UUID shopId) {
        return products.countByShopId(shopId);
    }

    @Transactional(readOnly = true)
    public ProductResponse getDetail(@NonNull UUID productId) {
        var product = products.findById(productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        if (!shops.isShopApproved(product.getShopId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductSearchResponse search(@NonNull ProductSearchRequest request) {
        var criteria = toCriteria(request);

        var items = search.search(criteria).stream()
            .map(CatalogMapper::toSummary)
            .toList();
        var total = search.count(criteria);

        var page = Math.max(request.page(), 0);
        return new ProductSearchResponse(items, total, page, criteria.limit());
    }

    @Transactional(readOnly = true)
    public SearchFacetsResponse facets(@Nullable String query, @Nullable String categorySlug) {
        var criteria = toCriteria(new ProductSearchRequest(
            query,
            categorySlug,
            null,
            null,
            null,
            null,
            null,
            0,
            1
        ));

        var priceRange = search.priceRange(criteria);

        return new SearchFacetsResponse(
            search.sizes(criteria),
            search.colors(criteria),
            priceRange.min(),
            priceRange.max()
        );
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categories.findAllByOrderBySortOrderAsc().stream()
            .map(CatalogMapper::toCategoryResponse)
            .toList();
    }

    private void applyStatus(Product product, String status) {
        if ("HIDDEN".equals(status)) {
            product.hide();
            return;
        }

        product.activate();
    }

    private void applyVariantUpdates(Product product, List<UpdateVariantRequest> updates) {
        for (var update : updates) {
            var variant = requireVariantInProduct(product.getId(), update.id());

            var price = update.price() == null ? variant.getPrice() : update.price();
            var compareAtPrice = update.compareAtPrice() == null
                ? variant.getCompareAtPrice()
                : update.compareAtPrice();
            requireValidPrice(price, compareAtPrice);

            if (Boolean.TRUE.equals(update.defaultVariant()) && !variant.isDefaultVariant()) {
                unsetCurrentDefault(product.getId(), variant.getId());
            }

            if (Boolean.FALSE.equals(update.defaultVariant()) && variant.isDefaultVariant()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot unset the only default variant"
                );
            }

            variant.updatePricing(
                update.price(),
                update.compareAtPrice(),
                update.stockQuantity(),
                update.defaultVariant()
            );

            variants.save(variant);
        }
    }

    private void unsetCurrentDefault(UUID productId, UUID exceptVariantId) {
        for (var variant : variants.findByProductIdOrderByCreatedAtAsc(productId)) {
            if (variant.isDefaultVariant() && !exceptVariantId.equals(variant.getId())) {
                variant.unsetDefault();
                variants.save(variant);
            }
        }
    }

    private ProductVariant requireVariantInProduct(UUID productId, UUID variantId) {
        var variant = variants.findById(variantId).orElse(null);
        if (variant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
        }
        if (!variant.getProductId().equals(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
        }

        return variant;
    }

    private static ProductSearchRepository.Criteria toCriteria(ProductSearchRequest request) {
        var minPrice = request.minPrice();
        var maxPrice = request.maxPrice();
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "minPrice must not be greater than maxPrice"
            );
        }

        var query = blankToNull(request.q());
        var limit = Math.clamp(request.limit(), 1, 100);
        var page = Math.max(request.page(), 0);

        return new ProductSearchRepository.Criteria(
            query,
            blankToNull(request.category()),
            minPrice,
            maxPrice,
            blankToNull(request.size()),
            blankToNull(request.color()),
            resolveSort(request.sort(), query),
            (long) page * limit,
            limit
        );
    }

    private static ProductSort resolveSort(@Nullable ProductSort requested, @Nullable String query) {
        if (requested == null && query != null) {
            return ProductSort.RELEVANCE;
        }
        if (requested == null) {
            return ProductSort.NEWEST;
        }
        // Không có từ khóa thì không có điểm để xếp theo độ khớp.
        if (requested == ProductSort.RELEVANCE && query == null) {
            return ProductSort.NEWEST;
        }

        return requested;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            return null;
        }

        return value.strip();
    }

    private void requireCategoryExists(@Nullable UUID categoryId) {
        if (categoryId == null) {
            return;
        }
        if (!categories.existsById(categoryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
        }
    }

    private static void requireSingleDefault(List<CreateVariantRequest> variants) {

        var count = variants.stream()
            .filter(CreateVariantRequest::defaultVariant)
            .count();

        if (count != 1) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Exactly one default variant is required"
            );
        }
    }

    private static void requireValidPrice(long price, @Nullable Long compareAtPrice) {

        if (compareAtPrice == null) {
            return;
        }

        if (compareAtPrice <= price) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "compare_at_price must be greater than price"
            );
        }
    }

    private ProductResponse toResponse(Product product) {

        var variantResponses = variants.findByProductIdOrderByCreatedAtAsc(product.getId()).stream()
            .map(CatalogMapper::toVariantResponse)
            .toList();

        var imageResponses = images.findByProductIdOrderBySortOrderAsc(product.getId()).stream()
            .map(CatalogMapper::toImageResponse)
            .toList();

        return CatalogMapper.toResponse(product, variantResponses, imageResponses);
    }
}
