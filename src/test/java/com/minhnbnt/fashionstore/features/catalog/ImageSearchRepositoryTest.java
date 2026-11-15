package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.features.catalog.search.ImageSearchRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.pgvector.PGvector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Gần vector query phải lên đầu; product ẩn/banned/shop chưa duyệt phải rớt. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ImageSearchRepositoryTest {

    @Autowired
    private ImageSearchRepository repository;

    @Autowired
    private JdbcClient jdbc;

    private UUID shopId;

    @BeforeEach
    void setUp() {
        // Mỗi context có database riêng nên không truncate; seed demo chưa có vector nên không lẫn.
        shopId = UUID.randomUUID();
        jdbc.sql("INSERT INTO shop (id, name, slug) VALUES (:id, 'S', :slug)")
            .param("id", shopId).param("slug", "s-" + shopId).update();
    }

    private UUID product(
        String slug, String status, String shopStatus, float @Nullable [] vector) {
        var id = UUID.randomUUID();
        jdbc.sql("UPDATE shop SET status = :st WHERE id = :id")
            .param("st", shopStatus).param("id", shopId).update();
        jdbc.sql("""
            INSERT INTO product (id, shop_id, name, slug, status)
            VALUES (:id, :shop, 'P', :slug, :status)
            """)
            .param("id", id).param("shop", shopId).param("slug", slug).param("status", status).update();
        if (vector != null) {
            image(id, vector);
        }
        return id;
    }

    private void image(UUID productId, float[] vector) {
        var uploadId = UUID.randomUUID();
        var imageId = UUID.randomUUID();
        jdbc.sql("INSERT INTO upload (id, s3_key, content_type) VALUES (:id, :key, 'image/jpeg')")
            .param("id", uploadId).param("key", "img-" + uploadId).update();
        jdbc.sql("INSERT INTO product_image (id, product_id, upload_id) VALUES (:id, :product, :upload)")
            .param("id", imageId).param("product", productId).param("upload", uploadId).update();
        jdbc.sql("""
            INSERT INTO product_embedding (product_image_id, product_id, embedding)
            VALUES (:imageId, :productId, cast(:vec as vector))
            """)
            .param("imageId", imageId)
            .param("productId", productId)
            .param("vec", new PGvector(vector).getValue())
            .update();
    }

    @Test
    void ordersByDistanceAndKeepsBestImagePerProduct() {
        var near = product("near", "ACTIVE", "APPROVED", unit(0));
        image(near, unit(1));
        var far = product("far", "ACTIVE", "APPROVED", unit(256));

        var ids = repository.search(unit(0), 50).stream()
            .map(ImageSearchRepository.Scored::productId)
            .toList();

        assertThat(ids.get(0)).isEqualTo(near);
        // Repo trả cả 2 ảnh của "near"; service mới dedupe.
        assertThat(ids).filteredOn(near::equals).hasSize(2);
        assertThat(ids.indexOf(far)).isGreaterThan(ids.lastIndexOf(near));
    }

    @Test
    void hidesInactiveProductsAndUnapprovedShops() {
        var hidden = product("hidden", "HIDDEN", "APPROVED", unit(0));
        var banned = product("banned", "BANNED", "APPROVED", unit(0));
        var pendingShop = product("pending-shop", "ACTIVE", "PENDING", unit(0));

        assertThat(repository.search(unit(0), 50))
            .extracting(ImageSearchRepository.Scored::productId)
            .doesNotContain(hidden, banned, pendingShop);
    }

    @Test
    void skipsRowsWithoutEmbedding() {
        var noVector = product("no-vector", "ACTIVE", "APPROVED", null);
        assertThat(repository.search(unit(0), 50))
            .extracting(ImageSearchRepository.Scored::productId)
            .doesNotContain(noVector);
    }

    private static float[] unit(int shift) {
        var vector = new float[512];
        vector[shift] = 1f;
        return vector;
    }
}
