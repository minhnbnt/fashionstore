package com.minhnbnt.fashionstore.infrastructure;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.catalog.repository.CategoryRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.ShopStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "test.context.id=demo-seeder")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DemoSeederTest {

    @Autowired
    private ShopService shopService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private UserService userService;

    @Autowired
    private ShopRepository shops;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ProductRepository products;

    @Autowired
    private S3Client s3;

    @Value("${s3.bucket}")
    private String bucket;

    private DemoSeeder seeder() {
        return new DemoSeeder(shopService, catalogService, userService, shops, categories, s3, bucket);
    }

    @Test
    void seedsOnceThenSkips() {
        var fresh = shops.findBySlug("net-lab").isEmpty();
        var before = products.count();
        seeder().run(new DefaultApplicationArguments());
        if (fresh) {
            // 9 demo products, khong hon khong kem
            assertThat(products.count()).isEqualTo(before + 9);
        }

        var shop = shops.findBySlug("net-lab").orElseThrow();
        assertThat(shop.getStatus()).isEqualTo(ShopStatus.APPROVED);
        assertThat(categories.count()).isGreaterThanOrEqualTo(5);

        var steady = products.count();
        seeder().run(new DefaultApplicationArguments());
        assertThat(products.count()).isEqualTo(steady);
    }

    @Test
    void gatedBehindProperty() {
        var conditional = DemoSeeder.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(conditional).isNotNull();
        assertThat(conditional.name()).containsExactly("app.demo-seed");
        assertThat(conditional.havingValue()).isEqualTo("true");
    }
}
