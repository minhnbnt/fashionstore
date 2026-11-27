package com.minhnbnt.fashionstore.features.voucher.repository;

import com.minhnbnt.fashionstore.model.ShopVoucher;
import com.minhnbnt.fashionstore.model.VoucherDiscountType;
import com.minhnbnt.fashionstore.model.VoucherProductScope;
import com.minhnbnt.fashionstore.model.VoucherVisibility;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ShopVoucherRepository {

    private final JdbcClient jdbc;

    public ShopVoucher save(ShopVoucher voucher) {
        var sql = """
            INSERT INTO shop_voucher (
                id, shop_id, code, name, description, discount_type, discount_value,
                max_discount_amount, min_order_subtotal, product_scope, visibility,
                usage_limit, used_count, per_buyer_limit, starts_at, ends_at,
                active, created_by, created_at, updated_at
            ) VALUES (
                :id, :shopId, :code, :name, :description, :discountType, :discountValue,
                :maxDiscountAmount, :minOrderSubtotal, :productScope, :visibility,
                :usageLimit, :usedCount, :perBuyerLimit, :startsAt, :endsAt,
                :active, :createdBy, :createdAt, :updatedAt
            )
            ON CONFLICT (id) DO UPDATE SET
                shop_id = EXCLUDED.shop_id,
                code = EXCLUDED.code,
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                discount_type = EXCLUDED.discount_type,
                discount_value = EXCLUDED.discount_value,
                max_discount_amount = EXCLUDED.max_discount_amount,
                min_order_subtotal = EXCLUDED.min_order_subtotal,
                product_scope = EXCLUDED.product_scope,
                visibility = EXCLUDED.visibility,
                usage_limit = EXCLUDED.usage_limit,
                used_count = EXCLUDED.used_count,
                per_buyer_limit = EXCLUDED.per_buyer_limit,
                starts_at = EXCLUDED.starts_at,
                ends_at = EXCLUDED.ends_at,
                active = EXCLUDED.active,
                created_by = EXCLUDED.created_by,
                updated_at = EXCLUDED.updated_at
            """;

        jdbc.sql(sql)
            .param("id", voucher.getId())
            .param("shopId", voucher.getShopId())
            .param("code", voucher.getCode())
            .param("name", voucher.getName())
            .param("description", voucher.getDescription())
            .param("discountType", voucher.getDiscountType().name())
            .param("discountValue", voucher.getDiscountValue())
            .param("maxDiscountAmount", voucher.getMaxDiscountAmount())
            .param("minOrderSubtotal", voucher.getMinOrderSubtotal())
            .param("productScope", voucher.getProductScope().name())
            .param("visibility", voucher.getVisibility().name())
            .param("usageLimit", voucher.getUsageLimit())
            .param("usedCount", voucher.getUsedCount())
            .param("perBuyerLimit", voucher.getPerBuyerLimit())
            .param("startsAt", utcOf(voucher.getStartsAt()))
            .param("endsAt", utcOf(voucher.getEndsAt()))
            .param("active", voucher.isActive())
            .param("createdBy", voucher.getCreatedBy())
            .param("createdAt", utcOf(voucher.getCreatedAt()))
            .param("updatedAt", utcOf(voucher.getUpdatedAt()))
            .update();

        voucher.markPersisted();
        return voucher;
    }

    public Optional<ShopVoucher> findById(UUID id) {
        return jdbc.sql("SELECT * FROM shop_voucher WHERE id = :id")
            .param("id", id)
            .query(ShopVoucherRepository::mapRow)
            .optional();
    }

    public Optional<ShopVoucher> findByShopIdAndId(UUID shopId, UUID id) {
        return jdbc.sql("SELECT * FROM shop_voucher WHERE shop_id = :shopId AND id = :id")
            .param("shopId", shopId)
            .param("id", id)
            .query(ShopVoucherRepository::mapRow)
            .optional();
    }

    public Optional<ShopVoucher> findByShopIdAndCode(UUID shopId, String code) {
        return jdbc.sql("SELECT * FROM shop_voucher WHERE shop_id = :shopId AND code = :code")
            .param("shopId", shopId)
            .param("code", code)
            .query(ShopVoucherRepository::mapRow)
            .optional();
    }

    public Optional<ShopVoucher> findLockedByShopIdAndId(UUID shopId, UUID id) {
        return jdbc.sql("SELECT * FROM shop_voucher WHERE shop_id = :shopId AND id = :id FOR UPDATE")
            .param("shopId", shopId)
            .param("id", id)
            .query(ShopVoucherRepository::mapRow)
            .optional();
    }

    public Optional<ShopVoucher> findLockedByShopIdAndCode(UUID shopId, String code) {
        return jdbc.sql("SELECT * FROM shop_voucher WHERE shop_id = :shopId AND code = :code FOR UPDATE")
            .param("shopId", shopId)
            .param("code", code)
            .query(ShopVoucherRepository::mapRow)
            .optional();
    }

    public List<ShopVoucher> findByShopIdOrderByCreatedAtDesc(UUID shopId) {
        var sql = """
            SELECT *
            FROM shop_voucher
            WHERE shop_id = :shopId
            ORDER BY created_at DESC, id DESC
            """;
        return jdbc.sql(sql)
            .param("shopId", shopId)
            .query(ShopVoucherRepository::mapRow)
            .list();
    }

    public void deleteById(UUID id) {
        jdbc.sql("DELETE FROM shop_voucher WHERE id = :id")
            .param("id", id)
            .update();
    }

    public void replaceProductIds(UUID voucherId, Collection<UUID> productIds) {
        jdbc.sql("DELETE FROM shop_voucher_product WHERE voucher_id = :voucherId")
            .param("voucherId", voucherId)
            .update();

        if (productIds.isEmpty()) {
            return;
        }

        var sql = new StringBuilder(
            "INSERT INTO shop_voucher_product (voucher_id, product_id) VALUES "
        );
        var params = new HashMap<String, Object>();
        params.put("voucherId", voucherId);
        int idx = 0;
        for (var productId : productIds) {
            if (idx > 0) {
                sql.append(", ");
            }
            var paramName = "p" + idx;
            sql.append("(:voucherId, :").append(paramName).append(')');
            params.put(paramName, productId);
            idx++;
        }
        sql.append(" ON CONFLICT (voucher_id, product_id) DO NOTHING");

        jdbc.sql(sql.toString())
            .params(params)
            .update();
    }

    public void replaceVoucherProducts(UUID voucherId, Collection<UUID> productIds) {
        replaceProductIds(voucherId, productIds);
    }

    public List<UUID> findProductIdsByVoucherId(UUID voucherId) {
        var sql = """
            SELECT product_id
            FROM shop_voucher_product
            WHERE voucher_id = :voucherId
            ORDER BY product_id ASC
            """;
        return jdbc.sql(sql)
            .param("voucherId", voucherId)
            .query((rs, rowNum) -> Objects.requireNonNull(rs.getObject("product_id", UUID.class)))
            .list();
    }

    public Map<UUID, Set<UUID>> findProductIdsByVoucherIds(Collection<UUID> voucherIds) {
        if (voucherIds.isEmpty()) {
            return Map.of();
        }

        var sql = """
            SELECT voucher_id, product_id
            FROM shop_voucher_product
            WHERE voucher_id IN (:voucherIds)
            ORDER BY product_id ASC
            """;
        var result = new HashMap<UUID, Set<UUID>>();
        jdbc.sql(sql)
            .param("voucherIds", voucherIds)
            .query((rs, rowNum) -> {
                var voucherId = Objects.requireNonNull(rs.getObject("voucher_id", UUID.class));
                var productId = Objects.requireNonNull(rs.getObject("product_id", UUID.class));
                result.computeIfAbsent(voucherId, ignored -> new LinkedHashSet<>()).add(productId);
                return voucherId;
            })
            .list();
        return result;
    }

    public int countActiveUsagesByBuyer(UUID voucherId, UUID buyerId) {
        var sql = """
            SELECT COUNT(*)
            FROM orders
            WHERE voucher_id = :voucherId
              AND buyer_id = :buyerId
              AND status <> 'CANCELLED'
            """;
        return jdbc.sql(sql)
            .param("voucherId", voucherId)
            .param("buyerId", buyerId)
            .query(Integer.class)
            .single();
    }

    public Map<UUID, Integer> countActiveUsagesByBuyerForVouchers(Collection<UUID> voucherIds, UUID buyerId) {
        if (voucherIds.isEmpty()) {
            return Map.of();
        }
        var sql = """
            SELECT voucher_id, COUNT(*) AS cnt
            FROM orders
            WHERE buyer_id = :buyerId
              AND voucher_id IN (:voucherIds)
              AND status <> 'CANCELLED'
            GROUP BY voucher_id
            """;
        var result = new HashMap<UUID, Integer>();
        jdbc.sql(sql)
            .param("buyerId", buyerId)
            .param("voucherIds", voucherIds)
            .query((rs, rowNum) -> {
                var vId = Objects.requireNonNull(rs.getObject("voucher_id", UUID.class));
                result.put(vId, rs.getInt("cnt"));
                return vId;
            })
            .list();
        return result;
    }

    public void decrementUsedCount(UUID voucherId, Instant now) {
        var sql = """
            UPDATE shop_voucher
            SET used_count = GREATEST(0, used_count - 1),
                updated_at = :now
            WHERE id = :voucherId
            """;
        jdbc.sql(sql)
            .param("voucherId", voucherId)
            .param("now", utcOf(now))
            .update();
    }

    private static ShopVoucher mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ShopVoucher(
            Objects.requireNonNull(rs.getObject("id", UUID.class)),
            Objects.requireNonNull(rs.getObject("shop_id", UUID.class)),
            Objects.requireNonNull(rs.getString("code")),
            Objects.requireNonNull(rs.getString("name")),
            Objects.requireNonNull(rs.getString("description")),
            VoucherDiscountType.valueOf(Objects.requireNonNull(rs.getString("discount_type"))),
            rs.getLong("discount_value"),
            nullableLongOf(rs, "max_discount_amount"),
            rs.getLong("min_order_subtotal"),
            VoucherProductScope.valueOf(Objects.requireNonNull(rs.getString("product_scope"))),
            VoucherVisibility.valueOf(Objects.requireNonNull(rs.getString("visibility"))),
            rs.getInt("usage_limit"),
            rs.getInt("used_count"),
            rs.getInt("per_buyer_limit"),
            instantOf(rs, "starts_at"),
            instantOf(rs, "ends_at"),
            rs.getBoolean("active"),
            rs.getObject("created_by", UUID.class),
            instantOf(rs, "created_at"),
            instantOf(rs, "updated_at"),
            false
        );
    }

    private static @Nullable Long nullableLongOf(ResultSet rs, String column) throws SQLException {
        var value = rs.getLong(column);
        if (rs.wasNull()) {
            return null;
        }
        return value;
    }

    private static Instant instantOf(ResultSet rs, String column) throws SQLException {
        return Objects.requireNonNull(rs.getObject(column, OffsetDateTime.class)).toInstant();
    }

    private static OffsetDateTime utcOf(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
