package com.minhnbnt.fashionstore.infrastructure;

import com.minhnbnt.fashionstore.dto.catalog.AttachImagesRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.ImageItemRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.catalog.repository.CategoryRepository;
import com.minhnbnt.fashionstore.features.chat.ChatService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.order.ReturnRequestService;
import com.minhnbnt.fashionstore.features.order.RevenueService;
import com.minhnbnt.fashionstore.features.order.repository.SellerSettlementRepository;
import com.minhnbnt.fashionstore.features.order.repository.SellerWithdrawalRepository;
import com.minhnbnt.fashionstore.features.review.ReviewService;
import com.minhnbnt.fashionstore.features.shop.ShopDecorationService;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.shop.ShopStaffService;
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dev-only demo data. Enabled with {@code app.demo-seed=true} (local config only,
 * never production). Idempotent: skips when the demo shop already exists.
 *
 * <p>Reset demo data (FK-safe order — {@code orders} is the only table referencing
 * {@code shop} without {@code ON DELETE CASCADE}, everything else cascades):
 *
 * <pre>
 * DELETE FROM orders WHERE shop_id IN (SELECT id FROM shop WHERE slug = 'net-lab');
 * DELETE FROM shop WHERE slug = 'net-lab';
 * DELETE FROM user_profile WHERE email LIKE '%@demo.local' OR email = 'demo-owner@local';
 * </pre>
 *
 * <p>After resetting, restart with {@code app.demo-seed=true} to seed everything again
 * (shop, catalog, buyers, orders across all statuses, reviews, returns, chat,
 * decoration draft, staff, wallet history).
 */
@Component
@ConditionalOnProperty(name = "app.demo-seed", havingValue = "true")
@Slf4j
public class DemoSeeder implements ApplicationRunner {

    private static final String SHOP_SLUG = "net-lab";

    private static final String SHOP_NAME = "nét lab";

    private static final String IMAGE_PREFIX = "demo/";

    private final ShopService shopService;

    private final CatalogService catalogService;

    private final UserService userService;

    private final ShopRepository shopRepository;

    private final CategoryRepository categoryRepository;

    private final S3Client s3;

    private final String bucket;

    private final DemoOrderSeeder orderSeeder;

    private final DemoEngagementSeeder engagementSeeder;

    private final DemoShopOpsSeeder opsSeeder;

    private final HttpClient http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public DemoSeeder(
        ShopService shopService,
        CatalogService catalogService,
        UserService userService,
        ShopRepository shopRepository,
        CategoryRepository categoryRepository,
        S3Client s3,
        @Value("${s3.bucket}") String bucket,
        OrderService orderService,
        ReviewService reviewService,
        ReturnRequestService returnRequestService,
        ChatService chatService,
        ShopStaffService shopStaffService,
        ShopDecorationService shopDecorationService,
        RevenueService revenueService,
        SellerSettlementRepository settlementRepository,
        SellerWithdrawalRepository withdrawalRepository
    ) {
        this.shopService = shopService;
        this.catalogService = catalogService;
        this.userService = userService;
        this.shopRepository = shopRepository;
        this.categoryRepository = categoryRepository;
        this.s3 = s3;
        this.bucket = bucket;
        this.orderSeeder = new DemoOrderSeeder(userService, orderService);
        this.engagementSeeder = new DemoEngagementSeeder(reviewService, returnRequestService, chatService);
        this.opsSeeder = new DemoShopOpsSeeder(
            shopService,
            shopStaffService,
            shopDecorationService,
            revenueService,
            settlementRepository,
            withdrawalRepository,
            userService
        );
    }

    @Override
    public void run(ApplicationArguments args) {
        if (shopRepository.findBySlug(SHOP_SLUG).isPresent()) {
            log.info("Demo data already present, skipping.");
            return;
        }
        log.info("Seeding demo data (shop '{}')...", SHOP_SLUG);

        var ownerId = UUID.randomUUID();
        userService.upsert(ownerId, new CreateUserRequest("demo-owner@local", "Demo Owner", null));

        var shop = shopService.register(
            ownerId,
            new CreateShopRequest(SHOP_NAME, SHOP_SLUG, "Mặc đẹp là nét riêng của bạn.", null, null)
        );
        shopService.approve(ownerId, shop.id());
        opsSeeder.seedProfile(ownerId, shop.id());

        var categoryIds = seedCategories();

        var variantIdBySku = new HashMap<String, UUID>();
        var productIdBySlug = new HashMap<String, UUID>();
        int productCount = 0;
        int uploaded = 0;
        int skipped = 0;
        for (var item : DemoCatalog.ITEMS) {
            var product = catalogService.createProduct(ownerId, shop.id(), item.request(categoryIds));
            productCount++;
            productIdBySlug.put(item.slug(), product.id());
            for (var variant : product.variants()) {
                variantIdBySku.put(variant.sku(), variant.id());
            }
            var keys = uploadImages(item);
            uploaded += keys.size();
            skipped += item.imageCount() - keys.size();
            if (keys.isEmpty()) {
                continue;
            }
            catalogService.attachImages(ownerId, shop.id(), product.id(), toImageRequest(keys));
        }

        var buyers = orderSeeder.seedBuyers();
        var orderIds = orderSeeder.seedOrders(ownerId, shop.id(), variantIdBySku, buyers);
        engagementSeeder.seed(ownerId, shop.id(), productIdBySlug, buyers, orderIds);
        opsSeeder.seedStaff(ownerId, shop.id());
        opsSeeder.seedDecoration(ownerId, shop.id());
        opsSeeder.seedRevenue(ownerId, shop.id(), orderIds.aoThunLinh());

        log.info("Demo seed done: {} products, images uploaded {}, skipped {}.", productCount, uploaded, skipped);
    }

    private Map<String, UUID> seedCategories() {
        var saved = categoryRepository.saveAll(DemoCatalog.CATEGORIES);
        var ids = new HashMap<String, UUID>();
        for (var category : saved) {
            ids.put(category.getSlug(), category.getId());
        }
        return ids;
    }

    private AttachImagesRequest toImageRequest(List<String> keys) {
        var items = new ArrayList<ImageItemRequest>();
        for (int i = 0; i < keys.size(); i++) {
            items.add(new ImageItemRequest(keys.get(i), null, i));
        }
        return new AttachImagesRequest(items);
    }

    private List<String> uploadImages(DemoProduct item) {
        var keys = new ArrayList<String>();
        for (int i = 1; i <= item.imageCount(); i++) {
            var key = IMAGE_PREFIX + item.slug() + "-" + i + ".jpg";
            if (uploadOne(item.slug(), i, key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private boolean uploadOne(String slug, int index, String key) {
        try {
            var request = HttpRequest.newBuilder(
                URI.create("https://picsum.photos/seed/net-" + slug + "-" + index + "/600/750")
            ).GET().build();
            var body = http.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
            s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType("image/jpeg").build(),
                RequestBody.fromBytes(body)
            );
            return true;
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.warn("Skipping demo image {}: {}", key, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private record DemoVariant(
        String sku,
        String size,
        String color,
        long price,
        @Nullable Long compareAtPrice,
        int stock,
        boolean defaultVariant
    ) {
        CreateVariantRequest request() {
            return new CreateVariantRequest(sku, size, color, price, compareAtPrice, stock, defaultVariant);
        }
    }

    private record DemoProduct(
        String slug,
        String name,
        String description,
        String categorySlug,
        List<DemoVariant> variants,
        int imageCount
    ) {
        CreateProductRequest request(Map<String, UUID> categoryIds) {
            var variantRequests = new ArrayList<CreateVariantRequest>();
            for (var variant : variants) {
                variantRequests.add(variant.request());
            }
            return new CreateProductRequest(name, slug, description, categoryIds.get(categorySlug), variantRequests);
        }
    }

    private static final class DemoCatalog {

        private static final List<Category> CATEGORIES = List.of(
            Category.seed("Áo", "ao", 1),
            Category.seed("Váy & Đầm", "vay-dam", 2),
            Category.seed("Áo khoác", "ao-khoac", 3),
            Category.seed("Quần", "quan", 4),
            Category.seed("Phụ kiện", "phu-kien", 5)
        );

        private static final List<DemoProduct> ITEMS = List.of(
            new DemoProduct(
                "ao-thun-oversize", "Áo thun oversize nét", "Cotton 260gsm, form rộng cá tính.",
                "ao",
                List.of(
                    new DemoVariant("AO-THUN-OVERSIZE-M", "M", "Trắng", 391000, null, 12, true),
                    new DemoVariant("AO-THUN-OVERSIZE-L", "L", "Đen", 391000, null, 8, false)
                ),
                3
            ),
            new DemoProduct(
                "ao-len-cotton", "Áo len cotton mềm", "100% cotton, 6 màu kẹo ngọt.",
                "ao",
                List.of(
                    new DemoVariant("AO-LEN-COTTON-S", "S", "Kem", 686000, null, 20, true),
                    new DemoVariant("AO-LEN-COTTON-M", "M", "Hồng", 686000, null, 15, false)
                ),
                2
            ),
            new DemoProduct(
                "vay-midi-xep-ly", "Váy midi xếp ly", "Nhẹ tênh, xoay là bung xòe.",
                "vay-dam",
                List.of(
                    new DemoVariant("VAY-MIDI-XEP-LY-S", "S", "Hồng", 784000, 980000L, 8, true),
                    new DemoVariant("VAY-MIDI-XEP-LY-M", "M", "Đen", 784000, 980000L, 5, false)
                ),
                3
            ),
            new DemoProduct(
                "chan-vay-chu-a", "Chân váy chữ A", "Tôn dáng, phối cùng áo thun trắng là đẹp.",
                "vay-dam",
                List.of(
                    new DemoVariant("CHAN-VAY-CHU-A-S", "S", "Đen", 588000, null, 10, true),
                    new DemoVariant("CHAN-VAY-CHU-A-M", "M", "Be", 588000, null, 6, false)
                ),
                2
            ),
            new DemoProduct(
                "ao-khoac-parka", "Áo khoác parka layer", "Phối caro, đi mưa đi nắng đều ổn.",
                "ao-khoac",
                List.of(
                    new DemoVariant("AO-KHOAC-PARKA-M", "M", "Rêu", 980000, null, 5, true),
                    new DemoVariant("AO-KHOAC-PARKA-L", "L", "Đen", 980000, null, 4, false)
                ),
                2
            ),
            new DemoProduct(
                "quan-jeans-ong-suong", "Quần jeans ống suông", "Denim thật, mặc cùng áo len rất hợp.",
                "quan",
                List.of(
                    new DemoVariant("QUAN-JEANS-ONG-SUONG-M", "M", "Xanh đậm", 784000, 980000L, 15, true),
                    new DemoVariant("QUAN-JEANS-ONG-SUONG-L", "L", "Xanh nhạt", 784000, 980000L, 9, false)
                ),
                2
            ),
            new DemoProduct(
                "quan-ni-ong-rong", "Quần nỉ ống rộng", "Mềm, ấm, mặc nhà ra phố đều đẹp.",
                "quan",
                List.of(
                    new DemoVariant("QUAN-NI-ONG-RONG-M", "M", "Xám", 588000, 784000L, 0, true),
                    new DemoVariant("QUAN-NI-ONG-RONG-L", "L", "Đen", 588000, 784000L, 0, false)
                ),
                2
            ),
            new DemoProduct(
                "tui-tote-canvas", "Túi tote canvas", "Đựng vừa laptop, hợp mọi set áo quần.",
                "phu-kien",
                List.of(
                    new DemoVariant("TUI-TOTE-CANVAS-FREE", "Freesize", "Trắng", 391000, null, 30, true)
                ),
                2
            ),
            new DemoProduct(
                "vay-hoa-nhi", "Váy hoa nhí cổ vuông", "Mát rượi, chụp hình ăn ảnh.",
                "vay-dam",
                List.of(
                    new DemoVariant("VAY-HOA-NHI-S", "S", "Hoa vàng", 649000, 799000L, 7, true),
                    new DemoVariant("VAY-HOA-NHI-M", "M", "Hoa xanh", 649000, 799000L, 7, false)
                ),
                2
            )
        );
    }
}
