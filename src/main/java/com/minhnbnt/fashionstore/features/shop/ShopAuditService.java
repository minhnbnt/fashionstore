package com.minhnbnt.fashionstore.features.shop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Ghi nhật ký hoạt động của shop. Lỗi ghi audit chỉ được log,
 * không làm hỏng thao tác nghiệp vụ.
 * Khi đang có transaction, mỗi lần ghi chạy trong savepoint để lỗi audit không abort transaction
 * nghiệp vụ, còn audit row vẫn biến mất nếu transaction nghiệp vụ rollback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShopAuditService {

    private static final String INSERT_SQL = """
        INSERT INTO shop_member_audit (id, shop_id, member_id, action, target_type, target_id, detail)
        VALUES (:id, :shopId, :memberId, :action, :targetType, :targetId, :detail)
        """;

    private final JdbcClient jdbc;

    private final DataSource dataSource;

    public void record(
        UUID shopId,
        @Nullable UUID actorId,
        String action,
        @Nullable String targetType,
        @Nullable UUID targetId,
        @Nullable String detail
    ) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            recordInSavepoint(shopId, actorId, action, targetType, targetId, detail);
            return;
        }

        try {
            insert(shopId, actorId, action, targetType, targetId, detail);
        } catch (RuntimeException e) {
            logFailure(action, shopId, e);
        }
    }

    private void recordInSavepoint(
        UUID shopId,
        @Nullable UUID actorId,
        String action,
        @Nullable String targetType,
        @Nullable UUID targetId,
        @Nullable String detail
    ) {
        var connection = DataSourceUtils.getConnection(dataSource);

        try {
            var savepoint = connection.setSavepoint();
            try {
                insert(shopId, actorId, action, targetType, targetId, detail);
            } catch (RuntimeException e) {
                // Postgres đánh dấu transaction là aborted sau mọi lỗi statement; phải rollback về
                // savepoint thì lệnh nghiệp vụ tiếp theo và commit cuối mới chạy được.
                connection.rollback(savepoint);
                logFailure(action, shopId, e);
            }
        } catch (SQLException e) {
            // Không tạo/không rollback được savepoint: thà mất audit row
            // còn hơn làm hỏng nghiệp vụ.
            log.error("Failed to isolate shop audit action {} for shop {}", action, shopId, e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    /**
     * Ghi thẳng bằng JDBC, không qua repository: mọi method save của Spring Data JDBC đều là
     * {@code @Transactional}, lỗi insert sẽ đánh dấu rollback-only lên transaction nghiệp vụ và
     * làm hỏng luôn thao tác chính.
     */
    private void insert(
        UUID shopId,
        @Nullable UUID actorId,
        String action,
        @Nullable String targetType,
        @Nullable UUID targetId,
        @Nullable String detail
    ) {
        jdbc.sql(INSERT_SQL)
            .param("id", UUID.randomUUID())
            .param("shopId", shopId)
            .param("memberId", actorId)
            .param("action", action)
            .param("targetType", targetType)
            .param("targetId", targetId)
            .param("detail", detail)
            .update();
    }

    private static void logFailure(String action, UUID shopId, RuntimeException e) {
        log.error("Failed to record shop audit action {} for shop {}", action, shopId, e);
    }
}
