package com.minhnbnt.fashionstore.features.payment.repository;

import com.minhnbnt.fashionstore.dto.payment.VietQrOrderBrief;
import com.minhnbnt.fashionstore.infrastructure.SqlLike;
import com.minhnbnt.fashionstore.model.PaymentTransaction;
import com.minhnbnt.fashionstore.model.PaymentTransactionStatus;
import com.minhnbnt.fashionstore.model.RefundStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PaymentTransactionRepository {

    private final JdbcClient jdbc;

    public PaymentTransaction save(PaymentTransaction tx) {
        var sql = """
            INSERT INTO payment_transaction (
                id, buyer_id, provider, txn_ref, amount, received_amount, status,
                bank_bin, bank_code, account_number, account_name, qr_image_url,
                bank_transaction_id, transfer_content, paid_at, expires_at,
                refund_status, refund_amount, refund_reason, refunded_at,
                refunded_by, refund_note, raw_payload, created_at, updated_at
            ) VALUES (
                :id, :buyerId, :provider, :txnRef, :amount, :receivedAmount, :status,
                :bankBin, :bankCode, :accountNumber, :accountName, :qrImageUrl,
                :bankTransactionId, :transferContent, :paidAt, :expiresAt,
                :refundStatus, :refundAmount, :refundReason, :refundedAt,
                :refundedBy, :refundNote, :rawPayload, :createdAt, :updatedAt
            )
            ON CONFLICT (id) DO UPDATE SET
                buyer_id = EXCLUDED.buyer_id,
                provider = EXCLUDED.provider,
                txn_ref = EXCLUDED.txn_ref,
                amount = EXCLUDED.amount,
                received_amount = EXCLUDED.received_amount,
                status = EXCLUDED.status,
                bank_bin = EXCLUDED.bank_bin,
                bank_code = EXCLUDED.bank_code,
                account_number = EXCLUDED.account_number,
                account_name = EXCLUDED.account_name,
                qr_image_url = EXCLUDED.qr_image_url,
                bank_transaction_id = EXCLUDED.bank_transaction_id,
                transfer_content = EXCLUDED.transfer_content,
                paid_at = EXCLUDED.paid_at,
                expires_at = EXCLUDED.expires_at,
                refund_status = EXCLUDED.refund_status,
                refund_amount = EXCLUDED.refund_amount,
                refund_reason = EXCLUDED.refund_reason,
                refunded_at = EXCLUDED.refunded_at,
                refunded_by = EXCLUDED.refunded_by,
                refund_note = EXCLUDED.refund_note,
                raw_payload = EXCLUDED.raw_payload,
                updated_at = EXCLUDED.updated_at
            """;

        jdbc.sql(sql)
            .param("id", tx.getId())
            .param("buyerId", tx.getBuyerId())
            .param("provider", tx.getProvider())
            .param("txnRef", tx.getTxnRef())
            .param("amount", tx.getAmount())
            .param("receivedAmount", tx.getReceivedAmount())
            .param("status", tx.getStatus().name())
            .param("bankBin", tx.getBankBin())
            .param("bankCode", tx.getBankCode())
            .param("accountNumber", tx.getAccountNumber())
            .param("accountName", tx.getAccountName())
            .param("qrImageUrl", tx.getQrImageUrl())
            .param("bankTransactionId", tx.getBankTransactionId())
            .param("transferContent", tx.getTransferContent())
            .param("paidAt", utcOrNull(tx.getPaidAt()))
            .param("expiresAt", utcOf(tx.getExpiresAt()))
            .param("refundStatus", tx.getRefundStatus().name())
            .param("refundAmount", tx.getRefundAmount())
            .param("refundReason", tx.getRefundReason())
            .param("refundedAt", utcOrNull(tx.getRefundedAt()))
            .param("refundedBy", tx.getRefundedBy())
            .param("refundNote", tx.getRefundNote())
            .param("rawPayload", tx.getRawPayload())
            .param("createdAt", utcOf(tx.getCreatedAt()))
            .param("updatedAt", utcOf(tx.getUpdatedAt()))
            .update();

        tx.markPersisted();
        return tx;
    }

    public void linkOrders(UUID transactionId, List<UUID> orderIds) {
        var sql = """
            INSERT INTO payment_transaction_order (transaction_id, order_id)
            VALUES (:transactionId, :orderId)
            ON CONFLICT (transaction_id, order_id) DO NOTHING
            """;
        for (var orderId : orderIds) {
            jdbc.sql(sql)
                .param("transactionId", transactionId)
                .param("orderId", orderId)
                .update();
        }
    }

    public List<UUID> findOrderIdsByTransactionId(UUID transactionId) {
        var sql = """
            SELECT order_id
            FROM payment_transaction_order
            WHERE transaction_id = :transactionId
            ORDER BY order_id ASC
            """;
        return jdbc.sql(sql)
            .param("transactionId", transactionId)
            .query((rs, rowNum) -> Objects.requireNonNull(rs.getObject("order_id", UUID.class)))
            .list();
    }

    public List<VietQrOrderBrief> findOrderBriefsByTransactionId(UUID transactionId) {
        var sql = """
            SELECT o.id AS order_id,
                   o.code AS order_code,
                   o.shop_id AS shop_id,
                   s.name AS shop_name,
                   o.total AS total,
                   o.status AS order_status,
                   o.payment_status AS payment_status
            FROM payment_transaction_order pto
            JOIN orders o ON o.id = pto.order_id
            JOIN shop s ON s.id = o.shop_id
            WHERE pto.transaction_id = :transactionId
            ORDER BY o.created_at ASC, o.id ASC
            """;
        return jdbc.sql(sql)
            .param("transactionId", transactionId)
            .query((rs, rowNum) -> new VietQrOrderBrief(
                Objects.requireNonNull(rs.getObject("order_id", UUID.class)),
                Objects.requireNonNull(rs.getString("order_code")),
                Objects.requireNonNull(rs.getObject("shop_id", UUID.class)),
                Objects.requireNonNull(rs.getString("shop_name")),
                rs.getLong("total"),
                Objects.requireNonNull(rs.getString("order_status")),
                Objects.requireNonNull(rs.getString("payment_status"))
            ))
            .list();
    }

    public List<PaymentTransaction> findTransactionsByOrderId(UUID orderId) {
        var sql = """
            SELECT pt.*
            FROM payment_transaction pt
            JOIN payment_transaction_order pto ON pto.transaction_id = pt.id
            WHERE pto.order_id = :orderId
            ORDER BY pt.created_at DESC, pt.id DESC
            """;
        return jdbc.sql(sql)
            .param("orderId", orderId)
            .query(PaymentTransactionRepository::mapRow)
            .list();
    }

    public Optional<PaymentTransaction> findById(UUID id) {
        return jdbc.sql("SELECT * FROM payment_transaction WHERE id = :id")
            .param("id", id)
            .query(PaymentTransactionRepository::mapRow)
            .optional();
    }

    public Optional<PaymentTransaction> findByTxnRef(String txnRef) {
        return jdbc.sql("SELECT * FROM payment_transaction WHERE txn_ref = :txnRef")
            .param("txnRef", txnRef)
            .query(PaymentTransactionRepository::mapRow)
            .optional();
    }

    public Optional<PaymentTransaction> findLockedByTxnRef(String txnRef) {
        return jdbc.sql("SELECT * FROM payment_transaction WHERE txn_ref = :txnRef FOR UPDATE")
            .param("txnRef", txnRef)
            .query(PaymentTransactionRepository::mapRow)
            .optional();
    }

    public Optional<PaymentTransaction> findLockedById(UUID id) {
        return jdbc.sql("SELECT * FROM payment_transaction WHERE id = :id FOR UPDATE")
            .param("id", id)
            .query(PaymentTransactionRepository::mapRow)
            .optional();
    }

    public List<PaymentTransaction> searchForAdmin(
        @Nullable PaymentTransactionStatus status,
        @Nullable RefundStatus refundStatus,
        @Nullable String q,
        int page,
        int size
    ) {
        var where = adminWhereOf(status, refundStatus, q);
        var sql = "SELECT pt.* FROM payment_transaction pt WHERE " + where.sql()
            + " ORDER BY pt.created_at DESC, pt.id DESC LIMIT :limit OFFSET :offset";
        return jdbc.sql(sql)
            .params(where.params())
            .param("limit", size)
            .param("offset", (long) page * size)
            .query(PaymentTransactionRepository::mapRow)
            .list();
    }

    public long countForAdmin(
        @Nullable PaymentTransactionStatus status,
        @Nullable RefundStatus refundStatus,
        @Nullable String q
    ) {
        var where = adminWhereOf(status, refundStatus, q);
        var sql = "SELECT COUNT(*) FROM payment_transaction pt WHERE " + where.sql();
        return jdbc.sql(sql)
            .params(where.params())
            .query(Long.class)
            .single();
    }

    private record AdminFilterWhere(String sql, Map<String, Object> params) {
    }

    private static AdminFilterWhere adminWhereOf(
        @Nullable PaymentTransactionStatus status,
        @Nullable RefundStatus refundStatus,
        @Nullable String q
    ) {
        var clauses = new ArrayList<String>();
        var params = new HashMap<String, Object>();
        clauses.add("TRUE");

        if (status != null) {
            clauses.add("pt.status = :status");
            params.put("status", status.name());
        }
        if (refundStatus != null) {
            clauses.add("pt.refund_status = :refundStatus");
            params.put("refundStatus", refundStatus.name());
        }
        if (q != null && !q.isBlank()) {
            clauses.add("""
                (pt.txn_ref ILIKE :q
                 OR coalesce(pt.bank_transaction_id, '') ILIKE :q
                 OR EXISTS (
                     SELECT 1
                     FROM payment_transaction_order pto
                     JOIN orders o ON o.id = pto.order_id
                     WHERE pto.transaction_id = pt.id
                       AND o.code ILIKE :q
                 ))""");
            params.put("q", SqlLike.contains(q));
        }

        return new AdminFilterWhere(String.join(" AND ", clauses), params);
    }

    private static PaymentTransaction mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentTransaction(
            Objects.requireNonNull(rs.getObject("id", UUID.class)),
            Objects.requireNonNull(rs.getObject("buyer_id", UUID.class)),
            Objects.requireNonNull(rs.getString("provider")),
            Objects.requireNonNull(rs.getString("txn_ref")),
            rs.getLong("amount"),
            rs.getLong("received_amount"),
            PaymentTransactionStatus.valueOf(Objects.requireNonNull(rs.getString("status"))),
            Objects.requireNonNull(rs.getString("bank_bin")),
            Objects.requireNonNull(rs.getString("bank_code")),
            Objects.requireNonNull(rs.getString("account_number")),
            Objects.requireNonNull(rs.getString("account_name")),
            Objects.requireNonNull(rs.getString("qr_image_url")),
            rs.getString("bank_transaction_id"),
            rs.getString("transfer_content"),
            nullableInstantOf(rs, "paid_at"),
            instantOf(rs, "expires_at"),
            RefundStatus.valueOf(Objects.requireNonNull(rs.getString("refund_status"))),
            rs.getLong("refund_amount"),
            rs.getString("refund_reason"),
            nullableInstantOf(rs, "refunded_at"),
            rs.getObject("refunded_by", UUID.class),
            rs.getString("refund_note"),
            rs.getString("raw_payload"),
            instantOf(rs, "created_at"),
            instantOf(rs, "updated_at"),
            false
        );
    }

    private static Instant instantOf(ResultSet rs, String column) throws SQLException {
        return Objects.requireNonNull(rs.getObject(column, OffsetDateTime.class)).toInstant();
    }

    private static @Nullable Instant nullableInstantOf(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, OffsetDateTime.class);
        if (value == null) {
            return null;
        }
        return value.toInstant();
    }

    private static OffsetDateTime utcOf(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static @Nullable OffsetDateTime utcOrNull(@Nullable Instant instant) {
        if (instant == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
