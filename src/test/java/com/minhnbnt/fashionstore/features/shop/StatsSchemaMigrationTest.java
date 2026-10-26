package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StatsSchemaMigrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void shopVisitLogTableExists() {
        var count = jdbc.sql("""
            SELECT count(*)
            FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = 'shop_visit_log'
            """)
            .query(Long.class)
            .single();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void productHasViewCountColumn() {
        var count = jdbc.sql("""
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_name = 'product' AND column_name = 'view_count'
            """)
            .query(Long.class)
            .single();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void ordersShopStatusCreatedIndexExists() {
        var count = jdbc.sql("""
            SELECT count(*)
            FROM pg_indexes
            WHERE tablename = 'orders' AND indexname = 'idx_orders_shop_status_created'
            """)
            .query(Long.class)
            .single();

        assertThat(count).isEqualTo(1L);
    }
}
