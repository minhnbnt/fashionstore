package com.minhnbnt.fashionstore.features.order.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrderStockRepository {

    private final JdbcClient jdbc;

    public boolean decrement(UUID variantId, int quantity) {
        var updated = jdbc.sql(
            """
            UPDATE product_variant
            SET stock_quantity = stock_quantity - :qty
            WHERE id = :id AND stock_quantity >= :qty
            """
        )
            .param("qty", quantity)
            .param("id", variantId)
            .update();

        return updated == 1;
    }

    public boolean increment(UUID variantId, int quantity) {
        var updated = jdbc.sql(
            """
            UPDATE product_variant
            SET stock_quantity = stock_quantity + :qty
            WHERE id = :id
            """
        )
            .param("qty", quantity)
            .param("id", variantId)
            .update();

        return updated == 1;
    }
}
