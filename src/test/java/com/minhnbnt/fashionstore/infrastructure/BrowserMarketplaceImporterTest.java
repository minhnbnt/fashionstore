package com.minhnbnt.fashionstore.infrastructure;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.ImageResponse;
import com.minhnbnt.fashionstore.dto.catalog.VariantResponse;
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
import com.minhnbnt.fashionstore.model.ShopStatus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "test.context.id=browser-marketplace-importer")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BrowserMarketplaceImporterTest {

    @Autowired
    private ShopService shopService;

    @Autowired
    private ShopDecorationService shopDecorationService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private UserService userService;

    @Autowired
    private UploadRepository uploads;

    @Autowired
    private ShopRepository shops;

    @Autowired
    private ShopMemberRepository members;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ProductRepository products;

    @Autowired
    private S3Client s3;

    @Autowired
    private S3UrlService s3Urls;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${s3.bucket}")
    private String bucket;

    private BrowserMarketplaceImporter importer(Path manifestFile) {
        return new BrowserMarketplaceImporter(
            shopService,
            shopDecorationService,
            catalogService,
            userService,
            uploads,
            shops,
            members,
            categories,
            products,
            s3,
            s3Urls,
            objectMapper,
            bucket,
            manifestFile.toString(),
            true
        );
    }

    @Test
    void importsMultiShopManifestWithRichProductsS3AssetsAndUpsert(@TempDir Path tempDir) throws Exception {
        writePng(tempDir.resolve("images/shops/routine-logo.png"), 0x1E293B);
        writePng(tempDir.resolve("images/shops/routine-banner.png"), 0x334155);
        writePng(tempDir.resolve("images/shops/vintage-logo.png"), 0x7C2D12);
        writePng(tempDir.resolve("images/shops/vintage-banner.png"), 0x9A3412);

        writePng(tempDir.resolve("images/products/polo-1.png"), 0x111827);
        writePng(tempDir.resolve("images/products/polo-2.png"), 0x1F2937);
        writePng(tempDir.resolve("images/products/polo-3.png"), 0x374151);
        writePng(tempDir.resolve("images/products/polo-garment-1.png"), 0x4B5563);
        writePng(tempDir.resolve("images/products/polo-garment-2.png"), 0x6B7280);
        writePng(tempDir.resolve("images/products/polo-black.png"), 0x000000);
        writePng(tempDir.resolve("images/products/polo-white.png"), 0xF9FAFB);

        writePng(tempDir.resolve("images/products/bag-1.png"), 0x78350F);
        writePng(tempDir.resolve("images/products/bag-2.png"), 0x92400E);
        writePng(tempDir.resolve("images/products/bag-3.png"), 0xB45309);

        writePng(tempDir.resolve("images/products/legacy-shirt.png"), 0x1D4ED8);

        var shopsJson = """
            [
              {
                "shopKey": "SHOPEE-101",
                "source": "SHOPEE",
                "sourceShopId": "101",
                "name": "Routine Official Store",
                "slug": "routine-official-store-101",
                "description": "Gian hàng thời trang nam nữ chính hãng Routine.",
                "sellerType": "CORPORATE",
                "address": "Quận Cầu Giấy, Hà Nội",
                "phone": "0901112233",
                "logoFile": "images/shops/routine-logo.png",
                "bannerFile": "images/shops/routine-banner.png",
                "rating": 4.9,
                "ratingCount": 128,
                "followerCount": 5420,
                "processingDays": 1,
                "returnPolicy": "Hỗ trợ đổi trả miễn phí trong 15 ngày."
              },
              {
                "shopKey": "LAZADA-202",
                "source": "LAZADA",
                "sourceShopId": "202",
                "name": "Tiệm Đồ Vintage",
                "slug": "tiem-do-vintage-202",
                "description": "Phụ kiện và trang phục phong cách cổ điển.",
                "sellerType": "INDIVIDUAL",
                "address": "Quận 3, TP. Hồ Chí Minh",
                "phone": "0904445566",
                "logoFile": "images/shops/vintage-logo.png",
                "bannerFile": "images/shops/vintage-banner.png",
                "rating": 4.7,
                "ratingCount": 64,
                "followerCount": 1250,
                "processingDays": 2,
                "returnPolicy": "Đổi trả trong 7 ngày nếu lỗi sản xuất."
              }
            ]
            """;
        Files.writeString(tempDir.resolve("shops.json"), shopsJson, StandardCharsets.UTF_8);

        var productsJson = """
            [
              {
                "source": "SHOPEE",
                "sourceId": "p1001",
                "shopKey": "SHOPEE-101",
                "name": "Áo Polo Nam Routine Pique",
                "slug": "ao-polo-nam-routine-pique-p1001",
                "categorySlug": "ao",
                "price": 299000,
                "originalPrice": 399000,
                "description": "## Áo Polo Nam Routine\\nChất liệu thoáng mát.",
                "material": "Cotton Pique 100%",
                "origin": "Việt Nam",
                "careInstructions": "Giặt nhẹ ở nhiệt độ thường, không dùng thuốc tẩy.",
                "sizeGuide": "M: 55-65kg | L: 66-75kg",
                "weightGrams": 250,
                "lengthCm": 28,
                "widthCm": 20,
                "heightCm": 5,
                "tryOnEnabled": true,
                "imageFiles": [
                  "images/products/polo-1.png",
                  "images/products/polo-2.png",
                  "images/products/polo-3.png"
                ],
                "garmentImageFiles": [
                  "images/products/polo-garment-1.png",
                  "images/products/polo-garment-2.png"
                ],
                "variants": [
                  {
                    "sku": "ROUTINE-POLO-DEN-M",
                    "size": "M",
                    "color": "Đen",
                    "price": 299000,
                    "compareAtPrice": 399000,
                    "stockQuantity": 25,
                    "defaultVariant": true,
                    "imageFile": "images/products/polo-black.png"
                  },
                  {
                    "sku": "ROUTINE-POLO-DEN-L",
                    "size": "L",
                    "color": "Đen",
                    "price": 299000,
                    "compareAtPrice": 399000,
                    "stockQuantity": 18,
                    "defaultVariant": false,
                    "imageFile": "images/products/polo-black.png"
                  },
                  {
                    "sku": "ROUTINE-POLO-TRANG-M",
                    "size": "M",
                    "color": "Trắng",
                    "price": 309000,
                    "compareAtPrice": 409000,
                    "stockQuantity": 30,
                    "defaultVariant": false,
                    "imageFile": "images/products/polo-white.png"
                  },
                  {
                    "sku": "ROUTINE-POLO-TRANG-L",
                    "size": "L",
                    "color": "Trắng",
                    "price": 309000,
                    "compareAtPrice": 409000,
                    "stockQuantity": 12,
                    "defaultVariant": false,
                    "imageFile": "images/products/polo-white.png"
                  }
                ],
                "sourceUrl": "https://shopee.vn/product/101/1001"
              },
              {
                "source": "LAZADA",
                "sourceId": "p2002",
                "shopKey": "LAZADA-202",
                "name": "Túi Tote Da Vintage Cổ Điển",
                "slug": "tui-tote-da-vintage-co-dien-p2002",
                "categorySlug": "phu-kien",
                "price": 450000,
                "originalPrice": 520000,
                "description": "Túi tote da PU cao cấp đựng vừa laptop 14 inch.",
                "material": "Da PU cao cấp",
                "origin": "Việt Nam",
                "careInstructions": "Lau sạch bằng khăn ẩm mềm.",
                "sizeGuide": "Freesize: 38x28x10cm",
                "weightGrams": 400,
                "lengthCm": 38,
                "widthCm": 28,
                "heightCm": 10,
                "tryOnEnabled": false,
                "imageFiles": [
                  "images/products/bag-1.png",
                  "images/products/bag-2.png",
                  "images/products/bag-3.png"
                ],
                "variants": [
                  {
                    "sku": "VINTAGE-BAG-NAU-FREE",
                    "size": "Freesize",
                    "color": "Nâu",
                    "price": 450000,
                    "compareAtPrice": 520000,
                    "stockQuantity": 15,
                    "defaultVariant": true
                  }
                ],
                "sourceUrl": "https://www.lazada.vn/products/i2002.html"
              },
              {
                "source": "SHOPEE",
                "sourceId": "legacy3003",
                "name": "Áo Sơ Mi Legacy",
                "categorySlug": "ao",
                "price": 199000,
                "originalPrice": 199000,
                "imageFile": "images/products/legacy-shirt.png",
                "sourceUrl": "https://shopee.vn/product/999/3003"
              }
            ]
            """;
        var manifestFile = tempDir.resolve("products.json");
        Files.writeString(manifestFile, productsJson, StandardCharsets.UTF_8);

        var countBefore = products.count();
        importer(manifestFile).run(new DefaultApplicationArguments());

        assertThat(products.count()).isEqualTo(countBefore + 3);

        var categorySlugs = new ArrayList<String>();
        for (Category category : categories.findAllByOrderBySortOrderAsc()) {
            categorySlugs.add(category.getSlug());
        }
        assertThat(categorySlugs).contains("ao", "vay-dam", "ao-khoac", "quan", "phu-kien");

        var routineShop = shops.findBySlug("routine-official-store-101").orElseThrow();
        assertThat(routineShop.getStatus()).isEqualTo(ShopStatus.APPROVED);
        assertThat(routineShop.isTrusted()).isTrue();
        assertThat(routineShop.getSellerType()).isEqualTo("CORPORATE");
        assertThat(routineShop.getAddress()).isEqualTo("Quận Cầu Giấy, Hà Nội");
        assertThat(routineShop.getReturnPolicy()).isEqualTo("Hỗ trợ đổi trả miễn phí trong 15 ngày.");
        assertThat(routineShop.getProcessingDays()).isEqualTo(1);
        assertThat(routineShop.getRating()).isEqualTo(4.9);
        assertThat(routineShop.getRatingCount()).isEqualTo(128);
        assertThat(routineShop.getFollowerCount()).isEqualTo(5420);

        var routineDeco = shopDecorationService.getPublishedLayout(routineShop.getId());
        assertThat(routineDeco.publishedAt()).isNotNull();
        assertThat(routineDeco.layout()).contains("BANNER_SLIDER", "CATEGORY_GRID", "PRODUCT_COLLECTION");

        var vintageShop = shops.findBySlug("tiem-do-vintage-202").orElseThrow();
        assertThat(vintageShop.getStatus()).isEqualTo(ShopStatus.APPROVED);
        assertThat(vintageShop.isTrusted()).isTrue();
        assertThat(vintageShop.getSellerType()).isEqualTo("INDIVIDUAL");
        assertThat(vintageShop.getAddress()).isEqualTo("Quận 3, TP. Hồ Chí Minh");
        assertThat(vintageShop.getReturnPolicy()).isEqualTo("Đổi trả trong 7 ngày nếu lỗi sản xuất.");
        assertThat(shopDecorationService.getPublishedLayout(vintageShop.getId()).publishedAt()).isNotNull();

        assertUploadExistsOnS3(routineShop.getLogoUploadId());
        assertUploadExistsOnS3(routineShop.getBannerUploadId());
        assertUploadExistsOnS3(routineShop.getIdFrontUploadId());
        assertUploadExistsOnS3(routineShop.getIdBackUploadId());
        assertUploadExistsOnS3(routineShop.getIdSelfieUploadId());
        assertUploadExistsOnS3(vintageShop.getLogoUploadId());
        assertUploadExistsOnS3(vintageShop.getBannerUploadId());

        var poloEntity = products.findByShopIdAndSlug(
            routineShop.getId(),
            "ao-polo-nam-routine-pique-p1001"
        ).orElseThrow();
        var poloDetail = catalogService.getDetail(poloEntity.getId());
        assertThat(poloDetail.tryOnEnabled()).isTrue();
        assertThat(poloDetail.garmentUploadId()).isNotNull();
        assertThat(poloDetail.garmentUrl()).isNotBlank();
        assertUploadExistsOnS3(poloDetail.garmentUploadId());
        var secondGarmentUpload = uploads.findByS3Key("imports/images/products/polo-garment-2.png").orElseThrow();
        assertUploadExistsOnS3(secondGarmentUpload.getId());
        assertThat(poloDetail.garments()).hasSize(2);
        assertThat(poloDetail.material()).isEqualTo("Cotton Pique 100%");
        assertThat(poloDetail.origin()).isEqualTo("Việt Nam");
        assertThat(poloDetail.careInstructions())
            .isEqualTo("Giặt nhẹ ở nhiệt độ thường, không dùng thuốc tẩy.");
        assertThat(poloDetail.sizeGuide()).isEqualTo("M: 55-65kg | L: 66-75kg");
        assertThat(poloDetail.weightGrams()).isEqualTo(250);
        assertThat(poloDetail.lengthCm()).isEqualTo(28);
        assertThat(poloDetail.widthCm()).isEqualTo(20);
        assertThat(poloDetail.heightCm()).isEqualTo(5);

        assertThat(poloDetail.variants()).hasSize(4);
        var galleryImages = new ArrayList<ImageResponse>();
        var variantImages = new ArrayList<ImageResponse>();
        for (var image : poloDetail.images()) {
            assertUploadExistsOnS3(image.uploadId());
            if (image.variantId() == null) {
                galleryImages.add(image);
            } else {
                variantImages.add(image);
            }
        }
        assertThat(galleryImages).hasSize(3);
        assertThat(variantImages).hasSize(2);

        var defaultVariantIds = new ArrayList<UUID>();
        for (VariantResponse variant : poloDetail.variants()) {
            if ("M".equals(variant.size())) {
                defaultVariantIds.add(variant.id());
            }
        }
        assertThat(defaultVariantIds).hasSize(2);
        for (UUID variantId : defaultVariantIds) {
            var hasColorImage = false;
            for (var vImg : variantImages) {
                if (variantId.equals(vImg.variantId()) && !vImg.url().isBlank()) {
                    hasColorImage = true;
                }
            }
            assertThat(hasColorImage).isTrue();
        }

        var defaultShop = shops.findBySlug("browser-marketplace-import").orElseThrow();
        assertThat(defaultShop.getStatus()).isEqualTo(ShopStatus.APPROVED);
        assertUploadExistsOnS3(defaultShop.getLogoUploadId());
        assertUploadExistsOnS3(defaultShop.getBannerUploadId());

        var legacySlug = TikiCatalogImporter.slug(
            "Áo Sơ Mi Legacy-shopee",
            Integer.toUnsignedLong("legacy3003".hashCode())
        );
        var legacyEntity = products.findByShopIdAndSlug(defaultShop.getId(), legacySlug).orElseThrow();
        var legacyDetail = catalogService.getDetail(legacyEntity.getId());
        assertThat(legacyDetail.variants()).hasSize(1);
        assertThat(legacyDetail.variants().getFirst().compareAtPrice()).isNull();
        assertThat(legacyDetail.images()).hasSize(1);

        var countAfterFirstRun = products.count();
        importer(manifestFile).run(new DefaultApplicationArguments());
        assertThat(products.count()).isEqualTo(countAfterFirstRun);

        var upgradedProductsJson = productsJson.replace(
            """
              {
                "source": "SHOPEE",
                "sourceId": "legacy3003",
                "name": "Áo Sơ Mi Legacy",
                "categorySlug": "ao",
                "price": 199000,
                "originalPrice": 199000,
                "imageFile": "images/products/legacy-shirt.png",
                "sourceUrl": "https://shopee.vn/product/999/3003"
              }
            """,
            """
              {
                "source": "SHOPEE",
                "sourceId": "legacy3003",
                "name": "Áo Sơ Mi Legacy",
                "categorySlug": "ao",
                "price": 199000,
                "originalPrice": 259000,
                "material": "Linen tự nhiên",
                "origin": "Việt Nam",
                "tryOnEnabled": true,
                "imageFiles": [
                  "images/products/polo-1.png",
                  "images/products/polo-2.png",
                  "images/products/polo-3.png"
                ],
                "garmentImageFiles": [
                  "images/products/polo-garment-1.png"
                ],
                "variants": [
                  {
                    "sku": "LEGACY-3003-DEN-M",
                    "size": "M",
                    "color": "Đen",
                    "price": 199000,
                    "compareAtPrice": 259000,
                    "stockQuantity": 20,
                    "defaultVariant": true,
                    "imageFile": "images/products/polo-black.png"
                  },
                  {
                    "sku": "LEGACY-3003-TRANG-L",
                    "size": "L",
                    "color": "Trắng",
                    "price": 199000,
                    "compareAtPrice": 259000,
                    "stockQuantity": 15,
                    "defaultVariant": false,
                    "imageFile": "images/products/polo-white.png"
                  }
                ],
                "sourceUrl": "https://shopee.vn/product/999/3003"
              }
            """
        );
        Files.writeString(manifestFile, upgradedProductsJson, StandardCharsets.UTF_8);

        importer(manifestFile).run(new DefaultApplicationArguments());
        assertThat(products.count()).isEqualTo(countAfterFirstRun);

        var upgradedLegacyDetail = catalogService.getDetail(legacyEntity.getId());
        assertThat(upgradedLegacyDetail.variants()).hasSize(2);
        assertThat(upgradedLegacyDetail.material()).isEqualTo("Linen tự nhiên");
        assertThat(upgradedLegacyDetail.tryOnEnabled()).isTrue();
        assertThat(upgradedLegacyDetail.garmentUploadId()).isNotNull();
    }

    @Test
    void gatedBehindProperty() {
        var conditional = BrowserMarketplaceImporter.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(conditional).isNotNull();
        assertThat(conditional.name()).containsExactly("app.browser-import.enabled");
        assertThat(conditional.havingValue()).isEqualTo("true");
    }

    private void assertUploadExistsOnS3(@Nullable UUID uploadId) {
        assertThat(uploadId).isNotNull();
        var nonNullId = java.util.Objects.requireNonNull(uploadId);
        var upload = uploads.findById(nonNullId).orElseThrow();
        s3.headObject(
            HeadObjectRequest.builder()
                .bucket(bucket)
                .key(upload.getS3Key())
                .build()
        );
    }

    private static void writePng(Path file, int rgb) throws IOException {
        var parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(rgb));
            graphics.fillRect(0, 0, 32, 32);
        } finally {
            graphics.dispose();
        }
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        Files.write(file, out.toByteArray());
    }
}
