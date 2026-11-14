package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.admin.AdminShopResponse;
import com.minhnbnt.fashionstore.dto.admin.StatusHistoryResponse;
import com.minhnbnt.fashionstore.features.upload.S3UrlService;
import com.minhnbnt.fashionstore.infrastructure.SqlLike;
import com.minhnbnt.fashionstore.model.ShopStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model cho màn admin: shop + owner (join shop_member OWNER → user_profile).
 */
@Repository
@RequiredArgsConstructor
public class ShopAdminQueryRepository {

    private static final String BASE_FROM = """
        FROM shop s
        LEFT JOIN shop_member m ON m.shop_id = s.id AND m.role = 'OWNER'
        LEFT JOIN user_profile u ON u.id = m.user_id
        LEFT JOIN upload lg ON lg.id = s.logo_upload_id
        LEFT JOIN upload bg ON bg.id = s.banner_upload_id
        LEFT JOIN upload df ON df.id = s.id_front_upload_id
        LEFT JOIN upload db ON db.id = s.id_back_upload_id
        LEFT JOIN upload ds ON ds.id = s.id_selfie_upload_id
        LEFT JOIN upload bc ON bc.id = s.business_cert_upload_id
        """;

    private static final String SELECT = """
        SELECT s.id, s.name, s.slug, s.description,
               lg.s3_key AS logo_s3_key, bg.s3_key AS banner_s3_key,
               s.status, s.status_reason,
               u.id AS owner_id, u.email AS owner_email, u.display_name AS owner_name, u.status AS owner_status,
               s.submitted_at, s.approved_at, s.created_at, s.updated_at,
               s.phone, s.address, s.legal_name, s.id_number, s.id_issued_date, s.id_issued_place,
               s.tax_code, df.s3_key AS id_front_s3_key, db.s3_key AS id_back_s3_key,
               ds.s3_key AS id_selfie_s3_key, bc.s3_key AS business_cert_s3_key,
               s.bank_name, s.bank_account, s.bank_holder, s.kyc_status, s.kyc_rejection_reason
        """ + BASE_FROM;

    private final JdbcClient jdbc;

    private final S3UrlService s3Urls;

    public List<AdminShopResponse> search(@Nullable ShopStatus status, @Nullable String query, int page, int size) {
        var filter = filterOf(status, query);

        var sql = SELECT + " WHERE " + filter.where()
            + " ORDER BY " + orderOf(status)
            + " LIMIT :limit OFFSET :offset";

        return jdbc.sql(sql)
            .params(filter.params())
            .param("limit", size)
            .param("offset", (long) page * size)
            .query(this::toAdminShop)
            .list();
    }

    public long count(@Nullable ShopStatus status, @Nullable String query) {
        var filter = filterOf(status, query);

        return jdbc.sql("SELECT count(*) " + BASE_FROM + " WHERE " + filter.where())
            .params(filter.params())
            .query(Long.class)
            .single();
    }

    public Optional<AdminShopResponse> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE s.id = :id")
            .param("id", id)
            .query(this::toAdminShop)
            .optional();
    }

    public List<StatusHistoryResponse> history(UUID shopId) {
        var sql = """
            SELECT h.from_status, h.to_status, h.changed_by, u.email AS changed_by_email, h.reason, h.created_at
            FROM shop_status_history h
            LEFT JOIN user_profile u ON u.id = h.changed_by
            WHERE h.shop_id = :shop
            ORDER BY h.created_at, h.id
            """;

        return jdbc.sql(sql)
            .param("shop", shopId)
            .query((rs, rowNum) -> new StatusHistoryResponse(
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getObject("changed_by", UUID.class),
                rs.getString("changed_by_email"),
                rs.getString("reason"),
                instantOf(rs, "created_at")
            ))
            .list();
    }

    public Map<ShopStatus, Long> countByStatus() {
        var counts = new EnumMap<ShopStatus, Long>(ShopStatus.class);
        for (var status : ShopStatus.values()) {
            counts.put(status, 0L);
        }

        var rows = jdbc.sql("SELECT status, count(*) AS total FROM shop GROUP BY status")
            .query((rs, rowNum) -> Map.entry(ShopStatus.valueOf(rs.getString("status")), rs.getLong("total")))
            .list();
        for (var row : rows) {
            counts.put(row.getKey(), row.getValue());
        }

        return counts;
    }

    /**
     * Đếm bản ghi mới theo ngày tạo (tính theo {@code zone}) từ {@code from} đến nay. Ngày không có thì không có key.
     */
    public Map<LocalDate, Long> countCreatedPerDay(Instant from, ZoneId zone) {
        var sql = """
            SELECT (created_at AT TIME ZONE :zone)::date AS day, count(*) AS total
            FROM shop
            WHERE created_at >= :from
            GROUP BY day
            """;

        var rows = jdbc.sql(sql)
            .param("zone", zone.getId())
            .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
            .query((rs, rowNum) -> Map.entry(rs.getObject("day", LocalDate.class), rs.getLong("total")))
            .list();

        var result = new HashMap<LocalDate, Long>();
        for (var row : rows) {
            result.put(row.getKey(), row.getValue());
        }

        return result;
    }

    public List<UUID> findApprovedShopIdsOwnedBy(UUID userId) {
        var sql = """
            SELECT s.id
            FROM shop s
            JOIN shop_member m ON m.shop_id = s.id
            WHERE m.user_id = :user AND m.role = 'OWNER' AND s.status = 'APPROVED'
            ORDER BY s.created_at
            """;

        return jdbc.sql(sql)
            .param("user", userId)
            .query((rs, rowNum) -> rs.getObject("id", UUID.class))
            .list();
    }

    private record Filter(String where, Map<String, Object> params) {
    }

    private static Filter filterOf(@Nullable ShopStatus status, @Nullable String query) {
        var clauses = new ArrayList<String>();
        var params = new HashMap<String, Object>();
        clauses.add("TRUE");

        if (status != null) {
            clauses.add("s.status = :status");
            params.put("status", status.name());
        }

        if (query != null && !query.isBlank()) {
            clauses.add("(s.name ILIKE :q OR s.slug ILIKE :q OR u.email ILIKE :q)");
            params.put("q", SqlLike.contains(query));
        }

        return new Filter(String.join(" AND ", clauses), params);
    }

    /**
     * Hàng đợi chờ duyệt: ai nộp trước duyệt trước. Các tab khác: mới thay đổi lên đầu.
     */
    private static String orderOf(@Nullable ShopStatus status) {
        if (status == ShopStatus.PENDING) {
            return "s.submitted_at ASC, s.id";
        }
        return "s.updated_at DESC, s.id";
    }

    private AdminShopResponse toAdminShop(ResultSet rs, int rowNum) throws SQLException {
        var kycStatus = rs.getString("kyc_status");
        if (kycStatus == null) {
            kycStatus = "PENDING_REVIEW";
        }
        return new AdminShopResponse(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getString("description"),
            publicUrlOf(rs.getString("logo_s3_key")),
            publicUrlOf(rs.getString("banner_s3_key")),
            rs.getString("status"),
            rs.getString("status_reason"),
            rs.getObject("owner_id", UUID.class),
            rs.getString("owner_email"),
            rs.getString("owner_name"),
            rs.getString("owner_status"),
            instantOf(rs, "submitted_at"),
            nullableInstantOf(rs, "approved_at"),
            instantOf(rs, "created_at"),
            instantOf(rs, "updated_at"),
            rs.getString("phone"),
            rs.getString("address"),
            rs.getString("legal_name"),
            rs.getString("id_number"),
            rs.getString("id_issued_date"),
            rs.getString("id_issued_place"),
            rs.getString("tax_code"),
            presignedUrlOf(rs.getString("id_front_s3_key")),
            presignedUrlOf(rs.getString("id_back_s3_key")),
            presignedUrlOf(rs.getString("id_selfie_s3_key")),
            presignedUrlOf(rs.getString("business_cert_s3_key")),
            rs.getString("bank_name"),
            rs.getString("bank_account"),
            rs.getString("bank_holder"),
            kycStatus,
            rs.getString("kyc_rejection_reason")
        );
    }

    private @Nullable String publicUrlOf(@Nullable String s3Key) {

        if (s3Key == null) {
            return null;
        }
        return s3Urls.publicUrl(s3Key);
    }

    /** Giấy tờ KYC: presigned GET TTL ~15 phút, URL đổi theo thời gian ký. */
    private @Nullable String presignedUrlOf(@Nullable String s3Key) {

        if (s3Key == null) {
            return null;
        }
        return s3Urls.presignKycRead(s3Key).url();
    }

    private static Instant instantOf(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static @Nullable Instant nullableInstantOf(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, OffsetDateTime.class);
        if (value == null) {
            return null;
        }
        return value.toInstant();
    }
}
