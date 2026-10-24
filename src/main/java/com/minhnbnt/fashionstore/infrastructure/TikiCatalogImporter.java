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
import org.jspecify.annotations.Nullable;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Imports public Tiki fashion listings for local development. Image bytes are copied to the configured S3 bucket. */
@Component
@ConditionalOnProperty(name = "app.tiki-import.enabled", havingValue = "true")
@Slf4j
public class TikiCatalogImporter implements ApplicationRunner {

    private static final String SHOP_SLUG = "tiki-fashion-import";
    private static final int PAGE_SIZE = 20;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final List<SourceCategory> SOURCES = List.of(
        new SourceCategory(917, "ao-thun-nam", "Áo", "ao", 1),
        new SourceCategory(933, "ao-thun-nu", "Áo", "ao", 1),
        new SourceCategory(10382, "ao-hoodie-nam", "Áo khoác", "ao-khoac", 2),
        new SourceCategory(27598, "ao-len-ao-khoac-ni-nu", "Áo khoác", "ao-khoac", 2)
    );

    private final ShopService shopService;
    private final CatalogService catalogService;
    private final UserService userService;
    private final ShopRepository shops;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ShopMemberRepository members;
    private final S3Client s3;
    private final ObjectMapper objectMapper;
    private final String bucket;
    private final int productsPerCategory;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public TikiCatalogImporter(
        ShopService shopService,
        CatalogService catalogService,
        UserService userService,
        ShopRepository shops,
        CategoryRepository categories,
        ProductRepository products,
        ShopMemberRepository members,
        S3Client s3,
        ObjectMapper objectMapper,
        @Value("${s3.bucket}") String bucket,
        @Value("${app.tiki-import.products-per-category:5}") int productsPerCategory
    ) {
        this.shopService = shopService;
        this.catalogService = catalogService;
        this.userService = userService;
        this.shops = shops;
        this.categories = categories;
        this.products = products;
        this.members = members;
        this.s3 = s3;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
        this.productsPerCategory = normalizedLimit(productsPerCategory);
    }

    @Override
    public void run(ApplicationArguments args) {
        var context = importContext();

        var categoryIds = categoryIds();
        int imported = 0;
        int skipped = 0;
        for (var source : SOURCES) {
            var categoryId = Objects.requireNonNull(categoryIds.get(source.slug()));
            var result = importCategory(context.ownerId(), context.shopId(), categoryId, source);
            imported += result.imported();
            skipped += result.skipped();
        }
        log.info("Tiki fashion sync complete: {} imported, {} already present.", imported, skipped);
    }

    private ImportContext importContext() {
        var existing = shops.findBySlug(SHOP_SLUG).orElse(null);
        if (existing != null) {
            var ownerId = members.findOwner(existing.getId()).orElseThrow(
                () -> new IllegalStateException("Tiki import shop has no owner")
            );
            return new ImportContext(ownerId, existing.getId());
        }

        var ownerId = UUID.randomUUID();
        userService.upsert(ownerId, new CreateUserRequest("tiki-import@local", "Tiki Import", null));
        var created = shopService.register(
            ownerId,
            new CreateShopRequest(
                "Tiki Fashion Import",
                SHOP_SLUG,
                "Dữ liệu tham khảo được nhập từ danh mục công khai của Tiki.",
                "seeds/tiki-logo.webp",
                "seeds/tiki-banner.webp",
                "0909888999",
                "Kho tổng Tiki, TP. Hồ Chí Minh",
                "INDIVIDUAL",
                "TIKI TRADING",
                "079202009876",
                "15/03/2020",
                "Cục Cảnh sát QLHC về TTXH",
                null,
                "seeds/id-front.webp",
                "seeds/id-back.webp",
                "seeds/id-selfie.webp",
                null,
                "Techcombank",
                "19036668889999",
                "TIKI TRADING"
            )
        );
        shopService.approve(ownerId, created.id());
        return new ImportContext(ownerId, created.id());
    }

    private Map<String, UUID> categoryIds() {
        var ids = new HashMap<String, UUID>();
        for (var category : categories.findAllByOrderBySortOrderAsc()) {
            ids.put(category.getSlug(), category.getId());
        }
        for (var source : SOURCES) {
            if (ids.containsKey(source.slug())) {
                continue;
            }
            var saved = categories.save(Category.seed(source.name(), source.slug(), source.sortOrder()));
            ids.put(source.slug(), saved.getId());
        }
        return ids;
    }

    private ImportResult importCategory(UUID ownerId, UUID shopId, UUID categoryId, SourceCategory source) {
        int imported = 0;
        int skipped = 0;
        int inspected = 0;
        int page = 1;
        while (inspected < productsPerCategory) {
            try {
                var remaining = productsPerCategory - inspected;
                var pageLimit = Math.min(PAGE_SIZE, remaining);
                var url = ("https://tiki.vn/api/personalish/v1/blocks/listings"
                    + "?limit=%d&category=%d&page=%d&urlKey=%s")
                    .formatted(pageLimit, source.id(), page, source.urlKey());
                var data = getJson(url).path("data");
                if (data.isEmpty()) {
                    break;
                }
                for (var item : data) {
                    inspected++;
                    if (alreadyImported(shopId, item)) {
                        skipped++;
                        continue;
                    }
                    if (importProduct(ownerId, shopId, categoryId, item)) {
                        imported++;
                    }
                }
                page++;
            } catch (IOException | InterruptedException | RuntimeException e) {
                handleFailure("category " + source.id() + " page " + page, e);
                break;
            }
        }
        return new ImportResult(imported, skipped);
    }

    private boolean alreadyImported(UUID shopId, JsonNode item) {
        var sourceId = item.path("id").asLong(0);
        var name = item.path("name").asText("").trim();
        if (sourceId <= 0 || name.isBlank()) {
            return false;
        }
        return products.findByShopIdAndSlug(shopId, slug(name, sourceId)).isPresent();
    }

    private boolean importProduct(UUID ownerId, UUID shopId, UUID categoryId, JsonNode item) {
        var sourceId = item.path("id").asLong(0);
        var name = item.path("name").asText("").trim();
        var price = item.path("price").asLong(0);
        var imageUrl = item.path("thumbnail_url").asText("").trim();
        if (sourceId <= 0 || name.isBlank() || price < 0 || imageUrl.isBlank()) {
            return false;
        }

        var originalPrice = item.path("original_price").asLong(price);
        Long compareAtPrice = null;
        if (originalPrice > price) {
            compareAtPrice = originalPrice;
        }
        var productSlug = slug(name, sourceId);
        var variant = new CreateVariantRequest(
            "TIKI-" + sourceId,
            "Freesize",
            "Theo phân loại",
            price,
            compareAtPrice,
            20,
            true
        );
        var productRequest = new CreateProductRequest(
            name,
            productSlug,
            "Sản phẩm tham khảo nhập từ danh mục công khai Tiki, mã nguồn " + sourceId + ".",
            categoryId,
            List.of(variant),
            List.of(),
            null, null, null,
            null, null, null, null,
            null, null, null, null,
            null
        );

        try {
            var image = downloadImage(imageUrl);
            var key = "imports/tiki/" + sourceId + image.extension();
            s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(image.contentType()).build(),
                RequestBody.fromBytes(image.bytes())
            );
            var product = catalogService.createProduct(ownerId, shopId, productRequest);
            var imageRequest = new AttachImagesRequest(List.of(new ImageItemRequest(key, null, 0)));
            catalogService.attachImages(ownerId, shopId, product.id(), imageRequest);
            return true;
        } catch (IOException | InterruptedException | RuntimeException e) {
            handleFailure("product " + sourceId, e);
            return false;
        }
    }

    private JsonNode getJson(String url) throws IOException, InterruptedException {
        var request = request(url).header("Accept", "application/json").build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        requireSuccess(response.statusCode(), url);
        return objectMapper.readTree(response.body());
    }

    private DownloadedImage downloadImage(String url) throws IOException, InterruptedException {
        var response = http.send(request(url).build(), HttpResponse.BodyHandlers.ofByteArray());
        requireSuccess(response.statusCode(), url);
        var contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!isImage(contentType)) {
            throw new IOException("Remote response is not an image");
        }
        if (response.body().length == 0 || response.body().length > MAX_IMAGE_BYTES) {
            throw new IOException("Remote image size is invalid");
        }
        return new DownloadedImage(response.body(), normalizedContentType(contentType), extension(contentType));
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Mozilla/5.0 (compatible; FashionStoreCatalogImporter/1.0)")
            .GET();
    }

    private static void requireSuccess(int status, String url) throws IOException {
        if (status < 200 || status >= 300) {
            throw new IOException("Remote request failed with HTTP " + status + ": " + url);
        }
    }

    private void handleFailure(String target, Exception exception) {
        log.warn("Skipping Tiki {}: {}", target, exception.getMessage());
        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    static String slug(String name, long sourceId) {
        var normalized = Normalizer.normalize(name.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        normalized = normalized.replace('đ', 'd');
        normalized = NON_SLUG.matcher(normalized).replaceAll("-");
        normalized = normalized.replaceAll("(^-+|-+$)", "");

        var suffix = "-" + sourceId;
        var maxBaseLength = 180 - suffix.length();
        if (normalized.length() > maxBaseLength) {
            normalized = normalized.substring(0, maxBaseLength).replaceAll("-+$", "");
        }
        if (normalized.isBlank()) {
            normalized = "san-pham";
        }
        return normalized + suffix;
    }

    static boolean isImage(@Nullable String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    static int normalizedLimit(int requested) {
        return Math.clamp(requested, 1, 500);
    }

    private static String normalizedContentType(String contentType) {
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String extension(String contentType) {
        var normalized = normalizedContentType(contentType);
        if ("image/png".equals(normalized)) {
            return ".png";
        }
        if ("image/webp".equals(normalized)) {
            return ".webp";
        }
        if ("image/gif".equals(normalized)) {
            return ".gif";
        }
        return ".jpg";
    }

    private record SourceCategory(int id, String urlKey, String name, String slug, int sortOrder) {
    }

    private record DownloadedImage(byte[] bytes, String contentType, String extension) {
    }

    private record ImportContext(UUID ownerId, UUID shopId) {
    }

    private record ImportResult(int imported, int skipped) {
    }
}
