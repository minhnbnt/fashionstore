package com.minhnbnt.fashionstore.infrastructure;

import com.minhnbnt.fashionstore.dto.catalog.AttachImagesRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.ImageItemRequest;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.UpdateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.VariantResponse;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.dto.shop.UpdateShopProfileRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.catalog.repository.CategoryRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopDecorationService;
import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.upload.S3UrlService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.model.Category;
import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.Shop;
import com.minhnbnt.fashionstore.model.ShopStatus;
import com.minhnbnt.fashionstore.model.Upload;
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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Imports the local multi-shop and deep PDP product manifests produced by the Playwright scraper. */
@Component
@ConditionalOnProperty(name = "app.browser-import.enabled", havingValue = "true")
@Slf4j
public class BrowserMarketplaceImporter implements ApplicationRunner {

    private static final String DEFAULT_SHOP_SLUG = "browser-marketplace-import";
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_GALLERY_IMAGES = 9;
    private static final int MAX_NAME_LENGTH = 300;
    private static final int MAX_DESCRIPTION_LENGTH = 20000;
    private static final String DEFAULT_WORKING_HOURS =
        "{\"mon\":\"08:00-21:30\",\"tue\":\"08:00-21:30\",\"wed\":\"08:00-21:30\","
            + "\"thu\":\"08:00-21:30\",\"fri\":\"08:00-22:00\",\"sat\":\"09:00-22:00\",\"sun\":\"09:00-21:00\"}";

    private static final List<ImportCategory> IMPORT_CATEGORIES = List.of(
        new ImportCategory("Áo", "ao", 1),
        new ImportCategory("Váy & Đầm", "vay-dam", 2),
        new ImportCategory("Áo khoác", "ao-khoac", 3),
        new ImportCategory("Quần", "quan", 4),
        new ImportCategory("Phụ kiện", "phu-kien", 5)
    );

    private final ShopService shopService;
    private final ShopDecorationService shopDecorationService;
    private final CatalogService catalogService;
    private final UserService userService;
    private final UploadRepository uploads;
    private final ShopRepository shops;
    private final ShopMemberRepository members;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final S3Client s3;
    private final S3UrlService s3Urls;
    private final ObjectMapper objectMapper;
    private final String bucket;
    private final Path manifestPath;
    private final boolean upsert;

    public BrowserMarketplaceImporter(
        ShopService shopService,
        ShopDecorationService shopDecorationService,
        CatalogService catalogService,
        UserService userService,
        UploadRepository uploads,
        ShopRepository shops,
        ShopMemberRepository members,
        CategoryRepository categories,
        ProductRepository products,
        S3Client s3,
        S3UrlService s3Urls,
        ObjectMapper objectMapper,
        @Value("${s3.bucket}") String bucket,
        @Value("${app.browser-import.manifest:data/marketplace-import/products.json}") String manifest,
        @Value("${app.browser-import.upsert:true}") boolean upsert
    ) {
        this.shopService = shopService;
        this.shopDecorationService = shopDecorationService;
        this.catalogService = catalogService;
        this.userService = userService;
        this.uploads = uploads;
        this.shops = shops;
        this.members = members;
        this.categories = categories;
        this.products = products;
        this.s3 = s3;
        this.s3Urls = s3Urls;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
        this.manifestPath = Path.of(manifest).toAbsolutePath().normalize();
        this.upsert = upsert;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!Files.isRegularFile(manifestPath)) {
            log.warn("Marketplace browser manifest not found: {}", manifestPath);
            return;
        }

        var uploadIdByS3Key = new HashMap<String, UUID>();
        var usedSkusByShop = new HashMap<UUID, Set<String>>();
        var kycUploads = ensureKycUploads(uploadIdByS3Key);
        var contextsByShopKey = loadShopsManifest(kycUploads, uploadIdByS3Key);
        var categoryIds = categoryIds();
        var root = objectMapper.readTree(Files.readAllBytes(manifestPath));

        ImportContext defaultContext = null;
        int imported = 0;
        int skipped = 0;
        for (var item : root) {
            var targetContext = resolveTargetShop(item, contextsByShopKey, kycUploads, uploadIdByS3Key);
            if (targetContext == null) {
                if (defaultContext == null) {
                    defaultContext = ensureDefaultShopContext(kycUploads, uploadIdByS3Key);
                }
                targetContext = defaultContext;
            }

            var usedSkus = usedSkusByShop.computeIfAbsent(targetContext.shopId(), k -> new HashSet<>());
            if (importOne(targetContext, categoryIds, item, uploadIdByS3Key, usedSkus)) {
                imported++;
            } else {
                skipped++;
            }
            var processed = imported + skipped;
            if (processed % 100 == 0) {
                log.info(
                    "Browser marketplace import progress: {} processed ({} imported/upgraded, {} skipped)",
                    processed,
                    imported,
                    skipped
                );
            }
        }
        log.info(
            "Browser marketplace import complete: {} shops loaded, {} products imported/upgraded, {} skipped.",
            contextsByShopKey.size(),
            imported,
            skipped
        );
    }

    private Map<String, ImportContext> loadShopsManifest(
        KycUploads kycUploads,
        Map<String, UUID> uploadIdByS3Key
    ) {
        var contexts = new LinkedHashMap<String, ImportContext>();
        var parent = manifestPath.getParent();
        if (parent == null) {
            return contexts;
        }
        var shopsPath = parent.resolve("shops.json").normalize();
        if (!Files.isRegularFile(shopsPath)) {
            return contexts;
        }

        try {
            var root = objectMapper.readTree(Files.readAllBytes(shopsPath));
            if (!root.isArray()) {
                return contexts;
            }
            for (var shopNode : root) {
                var context = ensureShopFromNode(shopNode, kycUploads, uploadIdByS3Key);
                if (context == null) {
                    continue;
                }
                var shopKey = shopNode.path("shopKey").asString("").trim();
                if (!shopKey.isBlank()) {
                    contexts.put(shopKey, context);
                }
                contexts.put(context.slug(), context);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to read shops.json from {}: {}", shopsPath, e.getMessage());
        }
        return contexts;
    }

    private @Nullable ImportContext resolveTargetShop(
        JsonNode item,
        Map<String, ImportContext> contextsByShopKey,
        KycUploads kycUploads,
        Map<String, UUID> uploadIdByS3Key
    ) {
        var shopKey = item.path("shopKey").asString("").trim();
        if (!shopKey.isBlank()) {
            var found = contextsByShopKey.get(shopKey);
            if (found != null) {
                return found;
            }
        }

        var inlineShop = item.get("shop");
        if (inlineShop != null && inlineShop.isObject()) {
            var created = ensureShopFromNode(inlineShop, kycUploads, uploadIdByS3Key);
            if (created != null) {
                if (!shopKey.isBlank()) {
                    contextsByShopKey.put(shopKey, created);
                }
                contextsByShopKey.put(created.slug(), created);
                return created;
            }
        }
        return null;
    }

    private @Nullable ImportContext ensureShopFromNode(
        JsonNode shopNode,
        KycUploads kycUploads,
        Map<String, UUID> uploadIdByS3Key
    ) {
        var name = shopNode.path("name").asString("").trim();
        if (name.isBlank()) {
            return null;
        }

        var shopKey = shopNode.path("shopKey").asString("").trim();
        var rawSlug = shopNode.path("slug").asString("").trim();
        String slug;
        if (!rawSlug.isBlank()) {
            slug = sanitizeSlug(rawSlug, name, shopKey);
        } else {
            slug = TikiCatalogImporter.slug(name, stableId(shopKey + "-" + name));
        }

        var description = shopNode.path("description")
            .asString("Gian hàng thời trang chính hãng trên hệ thống.")
            .trim();
        if (description.isBlank()) {
            description = "Gian hàng thời trang chính hãng trên hệ thống.";
        }

        var sellerType = shopNode.path("sellerType").asString("INDIVIDUAL").trim().toUpperCase(Locale.ROOT);
        if (!"CORPORATE".equals(sellerType) && !"INDIVIDUAL".equals(sellerType)) {
            sellerType = "INDIVIDUAL";
        }

        var address = shopNode.path("address").asString("Quận Cầu Giấy, Hà Nội").trim();
        if (address.isBlank()) {
            address = "Quận Cầu Giấy, Hà Nội";
        }

        var phone = shopNode.path("phone").asString("0901234567").trim();
        if (phone.isBlank()) {
            phone = "0901234567";
        }

        var processingDays = Math.clamp(shopNode.path("processingDays").asInt(2), 1, 3);
        var defaultReturnPolicy =
            "Hỗ trợ đổi trả trong 15 ngày nếu sản phẩm lỗi từ nhà sản xuất hoặc không vừa kích cỡ.";
        var returnPolicy = shopNode.path("returnPolicy")
            .asString(defaultReturnPolicy)
            .trim();
        if (returnPolicy.isBlank()) {
            returnPolicy = defaultReturnPolicy;
        }

        var logoFile = shopNode.path("logoFile").asString("").trim();
        var bannerFile = shopNode.path("bannerFile").asString("").trim();
        var logoUpload = ensureShopAsset(slug, "logo", logoFile, 120, 120, 0x1E293B, uploadIdByS3Key);
        var bannerUpload = ensureShopAsset(slug, "banner", bannerFile, 360, 120, 0x334155, uploadIdByS3Key);

        var existing = shops.findBySlug(slug).orElse(null);
        UUID ownerId;
        UUID shopId;
        if (existing != null) {
            shopId = existing.getId();
            ownerId = members.findOwner(shopId).orElseThrow(
                () -> new IllegalStateException("Imported shop has no owner: " + slug)
            );
            if (existing.getStatus() != ShopStatus.APPROVED) {
                shopService.approve(ownerId, shopId);
            }
            if (!existing.isTrusted()) {
                shopService.setTrusted(ownerId, shopId, true);
            }
        } else {
            ownerId = UUID.randomUUID();
            userService.upsert(ownerId, new CreateUserRequest("shop-" + slug + "@import.local", name));
            var legalName = legalNameOf(slug);
            String taxCode = null;
            if ("CORPORATE".equals(sellerType)) {
                var taxSuffix = Math.floorMod(slug.hashCode(), 10_000_000);
                taxCode = "010" + String.format(Locale.ROOT, "%07d", taxSuffix);
            }
            var created = shopService.register(
                ownerId,
                new CreateShopRequest(
                    name,
                    slug,
                    description,
                    logoUpload.uploadId(),
                    bannerUpload.uploadId(),
                    phone,
                    address,
                    sellerType,
                    legalName,
                    "001202008888",
                    "10/10/2020",
                    "Cục Cảnh sát QLHC về TTXH",
                    taxCode,
                    kycUploads.idFrontUploadId(),
                    kycUploads.idBackUploadId(),
                    kycUploads.idSelfieUploadId(),
                    null,
                    "VietinBank",
                    "108877665544",
                    legalName
                )
            );
            shopId = created.id();
            shopService.approve(ownerId, shopId);
            shopService.setTrusted(ownerId, shopId, true);
        }

        shopService.updateProfile(
            ownerId,
            shopId,
            new UpdateShopProfileRequest(
                phone,
                address,
                returnPolicy,
                processingDays,
                null,
                null,
                null,
                "Chào bạn, cảm ơn bạn đã quan tâm tới gian hàng! Shop sẽ phản hồi tin nhắn ngay.",
                DEFAULT_WORKING_HOURS
            )
        );

        updateShopMetricsIfPresent(shopId, shopNode);
        publishDecorationIfNeeded(ownerId, shopId, name, bannerUpload.s3Key());

        return new ImportContext(ownerId, shopId, slug);
    }

    private ImportContext ensureDefaultShopContext(
        KycUploads kycUploads,
        Map<String, UUID> uploadIdByS3Key
    ) {
        var logoUpload = ensureShopAsset(
            DEFAULT_SHOP_SLUG,
            "logo",
            "",
            120,
            120,
            0x0F172A,
            uploadIdByS3Key
        );
        var bannerUpload = ensureShopAsset(
            DEFAULT_SHOP_SLUG,
            "banner",
            "",
            360,
            120,
            0x1E293B,
            uploadIdByS3Key
        );

        var existing = shops.findBySlug(DEFAULT_SHOP_SLUG).orElse(null);
        UUID ownerId;
        UUID shopId;
        if (existing != null) {
            shopId = existing.getId();
            ownerId = members.findOwner(shopId).orElseThrow(
                () -> new IllegalStateException("Browser marketplace import shop has no owner")
            );
            if (existing.getStatus() != ShopStatus.APPROVED) {
                shopService.approve(ownerId, shopId);
            }
            if (!existing.isTrusted()) {
                shopService.setTrusted(ownerId, shopId, true);
            }
        } else {
            ownerId = UUID.randomUUID();
            userService.upsert(ownerId, new CreateUserRequest("browser-import@local", "Browser Import"));
            var shop = shopService.register(
                ownerId,
                new CreateShopRequest(
                    "Shopee & Lazada Import",
                    DEFAULT_SHOP_SLUG,
                    "Dữ liệu nhập qua phiên Chrome tương tác trên máy phát triển.",
                    logoUpload.uploadId(),
                    bannerUpload.uploadId(),
                    "0907777888",
                    "Kho tổng liên sàn, Hà Nội",
                    "INDIVIDUAL",
                    "MARKETPLACE SELLER",
                    "001202008888",
                    "10/10/2020",
                    "Cục Cảnh sát QLHC về TTXH",
                    null,
                    kycUploads.idFrontUploadId(),
                    kycUploads.idBackUploadId(),
                    kycUploads.idSelfieUploadId(),
                    null,
                    "VietinBank",
                    "108877665544",
                    "MARKETPLACE SELLER"
                )
            );
            shopId = shop.id();
            shopService.approve(ownerId, shopId);
            shopService.setTrusted(ownerId, shopId, true);
        }

        shopService.updateProfile(
            ownerId,
            shopId,
            new UpdateShopProfileRequest(
                "0907777888",
                "Kho tổng liên sàn, Hà Nội",
                "Hỗ trợ đổi trả trong 15 ngày nếu sản phẩm lỗi từ nhà sản xuất.",
                2,
                null,
                null,
                null,
                "Chào bạn, shop sẽ phản hồi tin nhắn trong ít phút!",
                DEFAULT_WORKING_HOURS
            )
        );
        publishDecorationIfNeeded(ownerId, shopId, "Shopee & Lazada Import", bannerUpload.s3Key());

        return new ImportContext(ownerId, shopId, DEFAULT_SHOP_SLUG);
    }

    private void updateShopMetricsIfPresent(UUID shopId, JsonNode shopNode) {
        var ratingNode = shopNode.get("rating");
        var ratingCountNode = shopNode.get("ratingCount");
        var followerCountNode = shopNode.get("followerCount");

        var hasRating = ratingNode != null && ratingNode.isNumber();
        var hasRatingCount = ratingCountNode != null && ratingCountNode.isNumber();
        var hasFollowerCount = followerCountNode != null && followerCountNode.isNumber();
        if (!hasRating && !hasRatingCount && !hasFollowerCount) {
            return;
        }

        var current = shops.findById(shopId).orElse(null);
        if (current == null) {
            return;
        }

        Double rating = current.getRating();
        if (hasRating) {
            rating = ratingNode.doubleValue();
        }
        int ratingCount = current.getRatingCount();
        if (hasRatingCount) {
            ratingCount = Math.max(0, ratingCountNode.intValue());
        }
        int followerCount = current.getFollowerCount();
        if (hasFollowerCount) {
            followerCount = Math.max(0, followerCountNode.intValue());
        }

        var rebuilt = new Shop(
            current.getId(),
            current.getName(),
            current.getSlug(),
            current.getDescription(),
            current.getLogoUploadId(),
            current.getBannerUploadId(),
            current.getStatus(),
            current.getStatusReason(),
            current.getApprovedBy(),
            current.getApprovedAt(),
            current.getSubmittedAt(),
            current.getPhone(),
            current.getAddress(),
            current.getReturnPolicy(),
            current.getProcessingDays(),
            current.getSellerType(),
            current.getTaxCode(),
            current.getIdNumber(),
            current.getIdDocUploadId(),
            current.getBusinessCertUploadId(),
            rating,
            ratingCount,
            followerCount,
            current.getBankName(),
            current.getBankAccount(),
            current.getBankHolder(),
            current.getAutoReplyText(),
            current.getWorkingHours(),
            current.getLegalName(),
            current.getIdFrontUploadId(),
            current.getIdBackUploadId(),
            current.getIdSelfieUploadId(),
            current.getIdIssuedDate(),
            current.getIdIssuedPlace(),
            current.getKycStatus(),
            current.getKycRejectionReason(),
            current.isTrusted(),
            current.getCreatedAt(),
            Instant.now()
        );
        shops.save(rebuilt);
    }

    private void publishDecorationIfNeeded(UUID ownerId, UUID shopId, String shopName, String bannerS3Key) {
        var existingDecoration = shopDecorationService.getPublishedLayout(shopId);
        if (existingDecoration.publishedAt() != null) {
            return;
        }

        var bannerUrl = s3Urls.publicUrl(bannerS3Key);
        var slide = new LinkedHashMap<String, Object>();
        slide.put("id", "slide-1");
        slide.put("imageUrl", bannerUrl);
        slide.put("title", shopName);

        var bannerBlock = new LinkedHashMap<String, Object>();
        bannerBlock.put("id", "block-banner");
        bannerBlock.put("type", "BANNER_SLIDER");
        bannerBlock.put("title", "Bộ sưu tập nổi bật");
        bannerBlock.put("enabled", true);
        bannerBlock.put("banners", List.of(slide));

        var categoryBlock = new LinkedHashMap<String, Object>();
        categoryBlock.put("id", "block-categories");
        categoryBlock.put("type", "CATEGORY_GRID");
        categoryBlock.put("title", "Danh mục gian hàng");
        categoryBlock.put("enabled", true);

        var newArrivalsBlock = new LinkedHashMap<String, Object>();
        newArrivalsBlock.put("id", "block-new-arrivals");
        newArrivalsBlock.put("type", "PRODUCT_COLLECTION");
        newArrivalsBlock.put("title", "Sản phẩm mới về");
        newArrivalsBlock.put("enabled", true);
        newArrivalsBlock.put("collectionType", "NEW_ARRIVALS");
        newArrivalsBlock.put("itemLimit", 8);
        newArrivalsBlock.put("displayStyle", "GRID");

        var saleOffBlock = new LinkedHashMap<String, Object>();
        saleOffBlock.put("id", "block-sale-off");
        saleOffBlock.put("type", "PRODUCT_COLLECTION");
        saleOffBlock.put("title", "Ưu đãi hấp dẫn");
        saleOffBlock.put("enabled", true);
        saleOffBlock.put("collectionType", "SALE_OFF");
        saleOffBlock.put("itemLimit", 8);
        saleOffBlock.put("displayStyle", "GRID");

        var blocks = List.of(bannerBlock, categoryBlock, newArrivalsBlock, saleOffBlock);
        var layoutJson = objectMapper.writeValueAsString(blocks);
        shopDecorationService.saveDraft(ownerId, shopId, layoutJson);
        shopDecorationService.publish(ownerId, shopId);
    }

    private KycUploads ensureKycUploads(Map<String, UUID> uploadIdByS3Key) {
        var frontBytes = createPlaceholderPng(120, 80, 0x3B82F6);
        var backBytes = createPlaceholderPng(120, 80, 0x2563EB);
        var selfieBytes = createPlaceholderPng(100, 100, 0x1D4ED8);

        var frontId = ensureUpload("seeds/kyc/id-front.png", frontBytes, "image/png", uploadIdByS3Key);
        var backId = ensureUpload("seeds/kyc/id-back.png", backBytes, "image/png", uploadIdByS3Key);
        var selfieId = ensureUpload("seeds/kyc/id-selfie.png", selfieBytes, "image/png", uploadIdByS3Key);
        return new KycUploads(frontId, backId, selfieId);
    }

    private UploadedMedia ensureShopAsset(
        String shopSlug,
        String kind,
        String relativePath,
        int fallbackWidth,
        int fallbackHeight,
        int fallbackRgb,
        Map<String, UUID> uploadIdByS3Key
    ) {
        if (!relativePath.isBlank()) {
            try {
                var file = resolveImage(relativePath);
                var bytes = readValidImageBytes(file);
                var ext = extension(file);
                var mime = contentType(file);
                var s3Key = "imports/shops/" + shopSlug + "-" + kind + ext;
                var uploadId = ensureUpload(s3Key, bytes, mime, uploadIdByS3Key);
                return new UploadedMedia(uploadId, s3Key, file, mime, ext);
            } catch (IOException e) {
                log.debug("Using placeholder for shop {} {}: {}", shopSlug, kind, e.getMessage());
            }
        }

        var fallbackBytes = createPlaceholderPng(fallbackWidth, fallbackHeight, fallbackRgb);
        var fallbackKey = "imports/shops/" + shopSlug + "-" + kind + ".png";
        var uploadId = ensureUpload(fallbackKey, fallbackBytes, "image/png", uploadIdByS3Key);
        return new UploadedMedia(uploadId, fallbackKey, null, "image/png", ".png");
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

    private boolean importOne(
        ImportContext context,
        Map<String, UUID> categoryIds,
        JsonNode item,
        Map<String, UUID> uploadIdByS3Key,
        Set<String> usedSkusInShop
    ) {
        var source = item.path("source").asString("").trim().toUpperCase(Locale.ROOT);
        var sourceId = item.path("sourceId").asString("").trim();
        var name = item.path("name").asString("").trim();
        var categorySlug = item.path("categorySlug").asString("ao").trim();
        var price = item.path("price").asLong(-1);
        var galleryFiles = extractImageFileList(item);
        if (source.isBlank() || sourceId.isBlank() || name.isBlank() || galleryFiles.isEmpty() || price < 0) {
            return false;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH).trim();
        }

        var rawSlug = item.path("slug").asString("").trim();
        String slug;
        if (!rawSlug.isBlank()) {
            slug = sanitizeSlug(rawSlug, name, sourceId);
        } else {
            slug = TikiCatalogImporter.slug(name + "-" + source.toLowerCase(Locale.ROOT), stableId(sourceId));
        }

        var categoryId = categoryIds.get(categorySlug);
        if (categoryId == null) {
            categoryId = Objects.requireNonNull(categoryIds.get("ao"));
        }

        var existingProduct = products.findByShopIdAndSlug(context.shopId(), slug).orElse(null);
        if (existingProduct != null && !upsert) {
            return false;
        }

        try {
            var uploadedGallery = new ArrayList<UploadedMedia>();
            var seenGalleryUploadIds = new HashSet<UUID>();
            for (var relPath : galleryFiles) {
                var media = uploadProductMediaFile(relPath, uploadIdByS3Key);
                if (seenGalleryUploadIds.add(media.uploadId())) {
                    uploadedGallery.add(media);
                }
            }
            if (uploadedGallery.isEmpty()) {
                return false;
            }

            var galleryImageRequests = new ArrayList<ImageItemRequest>();
            for (int i = 0; i < uploadedGallery.size(); i++) {
                galleryImageRequests.add(new ImageItemRequest(uploadedGallery.get(i).uploadId(), null, i));
            }

            var garmentFiles = extractGarmentFileList(item);
            var garmentUploadIds = new ArrayList<UUID>();
            var seenGarmentUploadIds = new HashSet<UUID>();
            for (var garmentPath : garmentFiles) {
                var uploadedGarment = uploadProductMediaFile(garmentPath, uploadIdByS3Key);
                if (seenGarmentUploadIds.add(uploadedGarment.uploadId()) && garmentUploadIds.size() < MAX_GALLERY_IMAGES) {
                    garmentUploadIds.add(uploadedGarment.uploadId());
                }
            }

            var tryOnEnabled = item.path("tryOnEnabled").asBoolean(false);
            UUID garmentUploadId = null;
            if (!garmentUploadIds.isEmpty()) {
                garmentUploadId = garmentUploadIds.getFirst();
            } else if (tryOnEnabled) {
                garmentUploadId = galleryImageRequests.getFirst().uploadId();
                garmentUploadIds.add(garmentUploadId);
            }

            ProductResponse existingDetail = null;
            var existingProductSkus = new HashSet<String>();
            if (existingProduct != null) {
                existingDetail = catalogService.getSellerProductDetail(
                    context.ownerId(),
                    context.shopId(),
                    existingProduct.getId()
                );
                for (var existingVar : existingDetail.variants()) {
                    existingProductSkus.add(existingVar.sku());
                }
            }

            var parsedVariants = parseVariants(
                item,
                source,
                sourceId,
                price,
                uploadIdByS3Key,
                usedSkusInShop,
                existingProductSkus,
                seenGalleryUploadIds
            );

            var description = resolveDescription(item, source, sourceId);
            var material = optionalText(item, "material");
            var origin = optionalText(item, "origin");
            var careInstructions = optionalText(item, "careInstructions");
            var sizeGuide = optionalText(item, "sizeGuide");
            var weightGrams = optionalPositiveInt(item, "weightGrams");
            var lengthCm = optionalPositiveInt(item, "lengthCm");
            var widthCm = optionalPositiveInt(item, "widthCm");
            var heightCm = optionalPositiveInt(item, "heightCm");

            if (existingProduct == null) {
                var createVariants = new ArrayList<CreateVariantRequest>();
                for (var pv : parsedVariants) {
                    createVariants.add(pv.toCreateRequest());
                }
                catalogService.createProduct(
                    context.ownerId(),
                    context.shopId(),
                    new CreateProductRequest(
                        name,
                        slug,
                        description,
                        categoryId,
                        createVariants,
                        galleryImageRequests,
                        null,
                        garmentUploadId,
                        tryOnEnabled,
                        material,
                        origin,
                        careInstructions,
                        sizeGuide,
                        weightGrams,
                        lengthCm,
                        widthCm,
                        heightCm,
                        "ACTIVE",
                        garmentUploadIds
                    )
                );
                for (var pv : parsedVariants) {
                    usedSkusInShop.add(pv.sku());
                }
                return true;
            }

            var nonNullDetail = Objects.requireNonNull(existingDetail);
            if (
                !needsUpgrade(
                    nonNullDetail,
                    parsedVariants,
                    galleryImageRequests,
                    garmentUploadIds,
                    material,
                    tryOnEnabled
                )
            ) {
                for (var existingVar : nonNullDetail.variants()) {
                    usedSkusInShop.add(existingVar.sku());
                }
                return false;
            }

            upgradeExistingProduct(
                context,
                existingProduct,
                nonNullDetail,
                name,
                description,
                categoryId,
                galleryImageRequests,
                garmentUploadId,
                garmentUploadIds,
                tryOnEnabled,
                material,
                origin,
                careInstructions,
                sizeGuide,
                weightGrams,
                lengthCm,
                widthCm,
                heightCm,
                parsedVariants,
                usedSkusInShop
            );
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("Skipping {} product {}: {}", source, sourceId, e.getMessage());
            return false;
        }
    }

    private boolean needsUpgrade(
        ProductResponse existingDetail,
        List<ParsedVariant> targetVariants,
        List<ImageItemRequest> galleryImageRequests,
        List<UUID> targetGarmentUploadIds,
        @Nullable String material,
        boolean tryOnEnabled
    ) {
        if (existingDetail.variants().size() < targetVariants.size()) {
            return true;
        }

        var seenExistingUploadIds = new HashSet<UUID>();
        int existingGalleryCount = 0;
        for (var img : existingDetail.images()) {
            if (!seenExistingUploadIds.add(img.uploadId())) {
                return true;
            }
            if (img.variantId() == null) {
                existingGalleryCount++;
            }
        }
        if (existingGalleryCount != galleryImageRequests.size()) {
            return true;
        }

        var existingGarments = existingDetail.garments();
        if (existingGarments.size() != targetGarmentUploadIds.size()) {
            return true;
        }
        for (int i = 0; i < targetGarmentUploadIds.size(); i++) {
            if (!targetGarmentUploadIds.get(i).equals(existingGarments.get(i).uploadId())) {
                return true;
            }
        }

        if (existingDetail.variants().size() == 1 && !targetVariants.isEmpty()) {
            var oldColor = existingDetail.variants().getFirst().color();
            var newColor = targetVariants.getFirst().color();
            if ("Theo phân loại".equals(oldColor) && !"Theo phân loại".equals(newColor)) {
                return true;
            }
        }

        if (existingDetail.material() == null && material != null) {
            return true;
        }
        return !Boolean.TRUE.equals(existingDetail.tryOnEnabled()) && tryOnEnabled;
    }

    private void upgradeExistingProduct(
        ImportContext context,
        Product existingProduct,
        ProductResponse existingDetail,
        String name,
        String description,
        UUID categoryId,
        List<ImageItemRequest> galleryImageRequests,
        @Nullable UUID garmentUploadId,
        List<UUID> garmentUploadIds,
        boolean tryOnEnabled,
        @Nullable String material,
        @Nullable String origin,
        @Nullable String careInstructions,
        @Nullable String sizeGuide,
        @Nullable Integer weightGrams,
        @Nullable Integer lengthCm,
        @Nullable Integer widthCm,
        @Nullable Integer heightCm,
        List<ParsedVariant> parsedVariants,
        Set<String> usedSkusInShop
    ) {
        var orderedExisting = orderExistingVariantsDefaultFirst(existingDetail.variants());
        var variantUpdates = new ArrayList<UpdateVariantRequest>();
        for (int i = 0; i < parsedVariants.size(); i++) {
            var pv = parsedVariants.get(i);
            UUID existingVariantId = null;
            if (i < orderedExisting.size()) {
                existingVariantId = orderedExisting.get(i).id();
            }
            variantUpdates.add(
                new UpdateVariantRequest(
                    existingVariantId,
                    pv.sku(),
                    pv.size(),
                    pv.color(),
                    pv.price(),
                    pv.compareAtPrice(),
                    pv.stockQuantity(),
                    pv.defaultVariant()
                )
            );
        }

        var updated = catalogService.updateProduct(
            context.ownerId(),
            context.shopId(),
            existingProduct.getId(),
            new UpdateProductRequest(
                name,
                description,
                categoryId,
                "ACTIVE",
                variantUpdates,
                galleryImageRequests,
                existingDetail.videoUploadId(),
                garmentUploadId,
                tryOnEnabled,
                material,
                origin,
                careInstructions,
                sizeGuide,
                weightGrams,
                lengthCm,
                widthCm,
                heightCm,
                garmentUploadIds
            )
        );

        var variantImageItems = new ArrayList<ImageItemRequest>();
        var attachedUploadIds = new HashSet<UUID>();
        for (var gImg : galleryImageRequests) {
            attachedUploadIds.add(gImg.uploadId());
        }
        for (var pv : parsedVariants) {
            var imgUploadId = pv.imageUploadId();
            if (imgUploadId == null || !attachedUploadIds.add(imgUploadId)) {
                continue;
            }
            var matchedVariantId = findVariantIdBySku(updated.variants(), pv.sku());
            if (matchedVariantId != null) {
                variantImageItems.add(new ImageItemRequest(imgUploadId, matchedVariantId, 0));
            }
        }
        if (!variantImageItems.isEmpty()) {
            catalogService.attachImages(
                context.ownerId(),
                context.shopId(),
                updated.id(),
                new AttachImagesRequest(variantImageItems)
            );
        }

        for (var pv : parsedVariants) {
            usedSkusInShop.add(pv.sku());
        }
    }

    private static List<VariantResponse> orderExistingVariantsDefaultFirst(List<VariantResponse> variants) {
        var ordered = new ArrayList<VariantResponse>(variants.size());
        for (var variant : variants) {
            if (Boolean.TRUE.equals(variant.defaultVariant())) {
                ordered.add(variant);
            }
        }
        for (var variant : variants) {
            if (!Boolean.TRUE.equals(variant.defaultVariant())) {
                ordered.add(variant);
            }
        }
        return ordered;
    }

    private static @Nullable UUID findVariantIdBySku(List<VariantResponse> variants, String sku) {
        for (var variant : variants) {
            if (sku.equals(variant.sku())) {
                return variant.id();
            }
        }
        return null;
    }

    private List<ParsedVariant> parseVariants(
        JsonNode item,
        String source,
        String sourceId,
        long fallbackPrice,
        Map<String, UUID> uploadIdByS3Key,
        Set<String> usedSkusInShop,
        Set<String> existingProductSkus,
        Set<UUID> galleryUploadIds
    ) throws IOException {
        var rawVariants = new ArrayList<ParsedVariant>();
        var seenColorSize = new HashSet<String>();
        var localSkus = new HashSet<String>();
        var attachedVariantUploadIds = new HashSet<UUID>(galleryUploadIds);
        var variantsNode = item.get("variants");

        if (variantsNode != null && variantsNode.isArray() && !variantsNode.isEmpty()) {
            int index = 0;
            for (var vNode : variantsNode) {
                index++;
                var size = vNode.path("size").asString("Freesize").trim();
                if (size.isBlank()) {
                    size = "Freesize";
                }
                var color = vNode.path("color").asString("Mặc định").trim();
                if (color.isBlank()) {
                    color = "Mặc định";
                }
                var pairKey = color.toLowerCase(Locale.ROOT) + "__" + size.toLowerCase(Locale.ROOT);
                if (!seenColorSize.add(pairKey)) {
                    continue;
                }

                var variantPrice = vNode.path("price").asLong(fallbackPrice);
                if (variantPrice < 0) {
                    variantPrice = fallbackPrice;
                }

                var rawCompareAt = vNode.path("compareAtPrice").asLong(
                    vNode.path("originalPrice").asLong(0)
                );
                Long compareAtPrice = null;
                if (rawCompareAt > variantPrice) {
                    compareAtPrice = rawCompareAt;
                }

                var stockQuantity = vNode.path("stockQuantity").asInt(20);
                if (stockQuantity < 0) {
                    stockQuantity = 20;
                }

                var candidateSku = vNode.path("sku").asString("").trim();
                var finalSku = allocateUniqueSku(
                    candidateSku,
                    source,
                    sourceId,
                    index,
                    usedSkusInShop,
                    existingProductSkus,
                    localSkus
                );
                localSkus.add(finalSku);

                UUID variantImageUploadId = null;
                var variantImageFile = vNode.path("imageFile").asString("").trim();
                if (!variantImageFile.isBlank()) {
                    var candidateUploadId = uploadProductMediaFile(variantImageFile, uploadIdByS3Key).uploadId();
                    if (attachedVariantUploadIds.add(candidateUploadId)) {
                        variantImageUploadId = candidateUploadId;
                    }
                }

                var isDefault = vNode.path("defaultVariant").asBoolean(false);
                rawVariants.add(
                    new ParsedVariant(
                        finalSku,
                        size,
                        color,
                        variantPrice,
                        compareAtPrice,
                        stockQuantity,
                        isDefault,
                        variantImageUploadId
                    )
                );
            }
        }

        if (rawVariants.isEmpty()) {
            var originalPrice = item.path("originalPrice").asLong(fallbackPrice);
            Long compareAtPrice = null;
            if (originalPrice > fallbackPrice) {
                compareAtPrice = originalPrice;
            }
            var fallbackSku = allocateUniqueSku(
                source + "-" + safeKey(sourceId),
                source,
                sourceId,
                1,
                usedSkusInShop,
                existingProductSkus,
                localSkus
            );
            return List.of(
                new ParsedVariant(
                    fallbackSku,
                    "Freesize",
                    "Theo phân loại",
                    fallbackPrice,
                    compareAtPrice,
                    20,
                    true,
                    null
                )
            );
        }

        int defaultIndex = -1;
        for (int i = 0; i < rawVariants.size(); i++) {
            if (rawVariants.get(i).defaultVariant()) {
                defaultIndex = i;
                break;
            }
        }
        if (defaultIndex < 0) {
            defaultIndex = 0;
        }

        var normalized = new ArrayList<ParsedVariant>(rawVariants.size());
        normalized.add(rawVariants.get(defaultIndex).withDefault(true));
        for (int i = 0; i < rawVariants.size(); i++) {
            if (i != defaultIndex) {
                normalized.add(rawVariants.get(i).withDefault(false));
            }
        }
        return normalized;
    }

    private static String allocateUniqueSku(
        String candidateSku,
        String source,
        String sourceId,
        int index,
        Set<String> usedSkusInShop,
        Set<String> existingProductSkus,
        Set<String> localSkus
    ) {
        var base = candidateSku;
        if (base.isBlank()) {
            base = source + "-" + safeKey(sourceId) + "-" + index;
        }
        if (!isSkuTaken(base, usedSkusInShop, existingProductSkus, localSkus)) {
            return base;
        }

        var disambiguated = base + "-" + safeKey(sourceId) + "-" + index;
        if (!isSkuTaken(disambiguated, usedSkusInShop, existingProductSkus, localSkus)) {
            return disambiguated;
        }

        int counter = 2;
        while (isSkuTaken(disambiguated + "-" + counter, usedSkusInShop, existingProductSkus, localSkus)) {
            counter++;
        }
        return disambiguated + "-" + counter;
    }

    private static boolean isSkuTaken(
        String sku,
        Set<String> usedSkusInShop,
        Set<String> existingProductSkus,
        Set<String> localSkus
    ) {
        if (localSkus.contains(sku)) {
            return true;
        }
        if (existingProductSkus.contains(sku)) {
            return false;
        }
        return usedSkusInShop.contains(sku);
    }

    private static List<String> extractImageFileList(JsonNode item) {
        var result = new ArrayList<String>();
        var arrayNode = item.get("imageFiles");
        if (arrayNode != null && arrayNode.isArray()) {
            for (var el : arrayNode) {
                var path = el.asString("").trim();
                if (!path.isBlank() && result.size() < MAX_GALLERY_IMAGES) {
                    result.add(path);
                }
            }
        }
        if (result.isEmpty()) {
            var single = item.path("imageFile").asString("").trim();
            if (!single.isBlank()) {
                result.add(single);
            }
        }
        return result;
    }

    private static List<String> extractGarmentFileList(JsonNode item) {
        var result = new ArrayList<String>();
        var arrayNode = item.get("garmentImageFiles");
        if (arrayNode != null && arrayNode.isArray()) {
            for (var el : arrayNode) {
                var path = el.asString("").trim();
                if (!path.isBlank()) {
                    result.add(path);
                }
            }
        }
        if (result.isEmpty()) {
            var single = item.path("garmentImageFile").asString("").trim();
            if (!single.isBlank()) {
                result.add(single);
            }
        }
        return result;
    }

    private static String resolveDescription(JsonNode item, String source, String sourceId) {
        var desc = item.path("description").asString("").trim();
        if (desc.isBlank()) {
            var sourceUrl = item.path("sourceUrl").asString("").trim();
            desc = ("Nguồn " + source + ", mã " + sourceId + ". " + sourceUrl).trim();
        }
        if (desc.length() > MAX_DESCRIPTION_LENGTH) {
            desc = desc.substring(0, MAX_DESCRIPTION_LENGTH);
        }
        return desc;
    }

    private static @Nullable String optionalText(JsonNode item, String field) {
        var node = item.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        var text = node.asString("").trim();
        if (text.isBlank()) {
            return null;
        }
        return text;
    }

    private static @Nullable Integer optionalPositiveInt(JsonNode item, String field) {
        var node = item.get(field);
        if (node == null || !node.isNumber()) {
            return null;
        }
        var value = node.intValue();
        if (value <= 0) {
            return null;
        }
        return value;
    }

    private UploadedMedia uploadProductMediaFile(
        String relativePath,
        Map<String, UUID> uploadIdByS3Key
    ) throws IOException {
        var imagePath = resolveImage(relativePath);
        var bytes = readValidImageBytes(imagePath);
        var ext = extension(imagePath);
        var mime = contentType(imagePath);
        var cleanRel = relativePath.replace('\\', '/').replaceAll("^/+", "");
        var s3Key = "imports/" + cleanRel;
        var uploadId = ensureUpload(s3Key, bytes, mime, uploadIdByS3Key);
        return new UploadedMedia(uploadId, s3Key, imagePath, mime, ext);
    }

    private UUID ensureUpload(
        String s3Key,
        byte[] bytes,
        String contentType,
        Map<String, UUID> uploadIdByS3Key
    ) {
        var cached = uploadIdByS3Key.get(s3Key);
        if (cached != null) {
            return cached;
        }

        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(bytes)
        );

        var existing = uploads.findByS3Key(s3Key).orElse(null);
        UUID uploadId;
        if (existing != null) {
            uploadId = existing.getId();
        } else {
            uploadId = uploads.save(Upload.create(s3Key, contentType)).getId();
        }
        uploadIdByS3Key.put(s3Key, uploadId);
        return uploadId;
    }

    private Path resolveImage(String relative) throws IOException {
        var base = Objects.requireNonNull(manifestPath.getParent());
        var resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base) || !Files.isRegularFile(resolved)) {
            throw new IOException("Image file is outside import directory or missing: " + relative);
        }
        return resolved;
    }

    private static byte[] readValidImageBytes(Path imagePath) throws IOException {
        var bytes = Files.readAllBytes(imagePath);
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            throw new IOException("Image size is invalid: " + imagePath);
        }
        return bytes;
    }

    static byte[] createPlaceholderPng(int width, int height, int rgb) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(rgb));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode placeholder PNG", e);
        }
        return out.toByteArray();
    }

    private static String sanitizeSlug(String rawSlug, String fallbackName, String fallbackId) {
        var cleaned = rawSlug.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("(^-+|-+$)", "");
        if (cleaned.isBlank()) {
            return TikiCatalogImporter.slug(fallbackName, stableId(fallbackId));
        }
        if (cleaned.length() > 180) {
            cleaned = cleaned.substring(0, 180).replaceAll("-+$", "");
        }
        return cleaned;
    }

    private static String legalNameOf(String slug) {
        var lettersOnly = slug.replaceAll("[^a-zA-Z]+", " ").trim().toUpperCase(Locale.ROOT);
        if (lettersOnly.isBlank()) {
            return "MARKETPLACE SELLER";
        }
        return "SHOP " + lettersOnly;
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

    private record ImportContext(UUID ownerId, UUID shopId, String slug) {
    }

    private record ImportCategory(String name, String slug, int sortOrder) {
    }

    private record KycUploads(UUID idFrontUploadId, UUID idBackUploadId, UUID idSelfieUploadId) {
    }

    private record UploadedMedia(
        UUID uploadId,
        String s3Key,
        @Nullable Path sourcePath,
        String contentType,
        String extension
    ) {
    }

    private record ParsedVariant(
        String sku,
        String size,
        String color,
        long price,
        @Nullable Long compareAtPrice,
        int stockQuantity,
        boolean defaultVariant,
        @Nullable UUID imageUploadId
    ) {
        ParsedVariant withDefault(boolean isDefault) {
            return new ParsedVariant(
                sku,
                size,
                color,
                price,
                compareAtPrice,
                stockQuantity,
                isDefault,
                imageUploadId
            );
        }

        CreateVariantRequest toCreateRequest() {
            return new CreateVariantRequest(
                sku,
                size,
                color,
                price,
                compareAtPrice,
                stockQuantity,
                defaultVariant,
                imageUploadId
            );
        }
    }
}
