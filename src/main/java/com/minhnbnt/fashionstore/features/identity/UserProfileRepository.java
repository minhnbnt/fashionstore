package com.minhnbnt.fashionstore.features.identity;

import com.minhnbnt.fashionstore.model.UserProfile;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends CrudRepository<UserProfile, UUID> {

    /**
     * SELECT ... FOR UPDATE cho khóa/mở khóa/nâng role, tránh 2 admin ghi đè nhau.
     */
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<UserProfile> findLockedById(UUID id);

    /**
     * JIT provisioning: trùng id hoặc trùng email thì bỏ qua, không ném lỗi làm hỏng transaction.
     */
    @Modifying
    @Query("""
        INSERT INTO user_profile (id, email, display_name, created_at)
        VALUES (:id, :email, :displayName, now())
        ON CONFLICT DO NOTHING
        """)
    boolean insertIfAbsent(
        @Param("id") UUID id,
        @Param("email") String email,
        @Param("displayName") String displayName
    );

    /**
     * Chỉ ghi cột role, không đụng status, để không đè thao tác khóa của admin đang chạy song song.
     */
    @Modifying
    @Query("UPDATE user_profile SET role = :role WHERE id = :id")
    boolean updateRole(@Param("id") UUID id, @Param("role") String role);
}
