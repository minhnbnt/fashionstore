package com.minhnbnt.fashionstore.infrastructure;

import com.minhnbnt.fashionstore.dto.catalog.AttachImagesRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.ImageItemRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.catalog.repository.CategoryRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.Category;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Imports the local manifest and image files produced by the interactive Playwright scraper. */
@Component
@ConditionalOnProperty(name = "app.browser-import.enabled", havingValue = "true")
@Slf4j
public class BrowserMarketplaceImporter implements ApplicationRunner {

    private static final String SHOP_SLUG = "browser-marketplace-import";
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final List<ImportCategory> IMPORT_CATEGORIES = List.of(
        new ImportCategory("Áo", "ao", 1),
        new ImportCategory("Váy & Đầm", "vay-dam", 2),
        new ImportCategory("Áo khoác", "ao-khoac", 3),
        new ImportCategory("Quần", "quan", 4)
    );

    private final ShopService shopService;
    private final CatalogService catalogService;
    private final UserService userService;
    private final ShopRepository shops;
    private final ShopMemberRepository members;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final S3Client s3;
    private final ObjectMapper objectMapper;
    private final String bucket;
    private final Path manifestPath;

    public BrowserMarketplaceImporter(
        ShopService shopService,
        CatalogService catalogService,
        UserService userService,
        ShopRepository shops,
        ShopMemberRepository members,
        CategoryRepository categories,
        ProductRepository products,
        S3Client s3,
        ObjectMapper objectMapper,
        @Value("${s3.bucket}") String bucket,
        @Value("${app.browser-import.manifest:data/marketplace-import/products.json}") String manifest
    ) {
        this.shopService = shopService;
        this.catalogService = catalogService;
        this.userService = userService;
        this.shops = shops;
        this.members = members;
        this.categories = categories;
        this.products = products;
        this.s3 = s3;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
        this.manifestPath = Path.of(manifest).toAbsolutePath().normalize();
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!Files.isRegularFile(manifestPath)) {
            log.warn("Marketplace browser manifest not found: {}", manifestPath);
            return;
        }
        var context = importContext();
        var categoryIds = categoryIds();
        var root = objectMapper.readTree(Files.readAllBytes(manifestPath));
        int imported = 0;
        int skipped = 0;
        for (var item : root) {
            if (importOne(context, categoryIds, item)) {
                imported++;
            } else {
                skipped++;
            }
        }
        log.info("Browser marketplace import complete: {} imported, {} skipped.", imported, skipped);
    }

    private ImportContext importContext() {
        var existing = shops.findBySlug(SHOP_SLUG).orElse(null);
        if (existing != null) {
            var ownerId = members.findOwner(existing.getId()).orElseThrow(
                () -> new IllegalStateException("Browser marketplace import shop has no owner")
            );
            return new ImportContext(ownerId, existing.getId());
        }

        var ownerId = UUID.randomUUID();
        userService.upsert(ownerId, new CreateUserRequest("browser-import@local", "Browser Import", null));
        var shop = shopService.register(
            ownerId,
            new CreateShopRequest(
                "Shopee & Lazada Import",
                SHOP_SLUG,
                "Dữ liệu nhập qua phiên Chrome tương tác trên máy phát triển.",
                "seeds/market-logo.webp",
                "seeds/market-banner.webp",
                "0907777888",
                "Kho tổng liên sàn, Hà Nội",
                "INDIVIDUAL",
                "MARKETPLACE SELLER",
                "001202008888",
                "10/10/2020",
                "Cục Cảnh sát QLHC về TTXH",
                null,
                "seeds/id-front.webp",
                "seeds/id-back.webp",
                "seeds/id-selfie.webp",
                null,
                "VietinBank",
                "108877665544",
                "MARKETPLACE SELLER"
            )
        );
        shopService.approve(ownerId, shop.id());
        return new ImportContext(ownerId, shop.id());
    }

    private Map<String, UUID> categoryIds() {
        var ids = new HashMap<String, UUID>();
        for (var category : categories.findAllByOrderBySortOrderAsc()) {
            ids.put(category.getSlug(), category.getId());
        }
        for (var definition : IMPORT_CATEGORIES) {
            if (ids.containsKey(definition.slug())) {
                continue;
            }
            var saved = categories.save(Category.seed(definition.name(), definition.slug(), definition.sortOrder()));
            ids.put(definition.slug(), saved.getId());
        }
        return ids;
    }

    private boolean importOne(ImportContext context, Map<String, UUID> categoryIds, JsonNode item) {
        var source = item.path("source").asString("").trim().toUpperCase(Locale.ROOT);
        var sourceId = item.path("sourceId").asString("").trim();
        var name = item.path("name").asString("").trim();
        var imageFile = item.path("imageFile").asString("").trim();
        var categorySlug = item.path("categorySlug").asString("ao").trim();
        var price = item.path("price").asLong(-1);
        if (source.isBlank() || sourceId.isBlank() || name.isBlank() || imageFile.isBlank() || price < 0) {
            return false;
        }

        var slug = TikiCatalogImporter.slug(name + "-" + source.toLowerCase(Locale.ROOT), stableId(sourceId));
        if (products.findByShopIdAndSlug(context.shopId(), slug).isPresent()) {
            return false;
        }
        var categoryId = categoryIds.get(categorySlug);
        if (categoryId == null) {
            categoryId = Objects.requireNonNull(categoryIds.get("ao"));
        }

        try {
            var imagePath = resolveImage(imageFile);
            var bytes = Files.readAllBytes(imagePath);
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                throw new IOException("Image size is invalid");
            }
            var key = "imports/" + source.toLowerCase(Locale.ROOT) + "/" + safeKey(sourceId) + extension(imagePath);
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType(imagePath))
                    .build(),
                RequestBody.fromBytes(bytes)
            );

            var originalPrice = item.path("originalPrice").asLong(price);
            Long compareAtPrice = null;
            if (originalPrice > price) {
                compareAtPrice = originalPrice;
            }
            var variant = new CreateVariantRequest(
                source + "-" + safeKey(sourceId),
                "Freesize",
                "Theo phân loại",
                price,
                compareAtPrice,
                20,
                true
            );
            var sourceUrl = item.path("sourceUrl")
                .asString("")
                .trim();
            var product = catalogService.createProduct(
                context.ownerId(),
                context.shopId(),
                new CreateProductRequest(
                    name, slug, "Nguồn " + source + ", mã " + sourceId + ". " + sourceUrl, categoryId, List.of(variant)
                )
            );
            catalogService.attachImages(
                context.ownerId(),
                context.shopId(),
                product.id(),
                new AttachImagesRequest(List.of(new ImageItemRequest(key, null, 0)))
            );
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("Skipping {} product {}: {}", source, sourceId, e.getMessage());
            return false;
        }
    }

    private Path resolveImage(String relative) throws IOException {
        var base = Objects.requireNonNull(manifestPath.getParent());
        var resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base) || !Files.isRegularFile(resolved)) {
            throw new IOException("Image file is outside import directory or missing");
        }
        return resolved;
    }

    private static long stableId(String sourceId) {
        return Integer.toUnsignedLong(sourceId.hashCode());
    }

    private static String safeKey(String sourceId) {
        return sourceId.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    private static String extension(Path image) {
        var name = image.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return ".png";
        }
        if (name.endsWith(".webp")) {
            return ".webp";
        }
        return ".jpg";
    }

    private static String contentType(Path image) {
        var value = extension(image);
        if (".png".equals(value)) {
            return "image/png";
        }
        if (".webp".equals(value)) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private record ImportContext(UUID ownerId, UUID shopId) {
    }

    private record ImportCategory(String name, String slug, int sortOrder) {
    }
}
