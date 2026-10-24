package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ghim schema V28 và bộ quyền 5 nhóm của 4 vai trò hệ thống.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StaffSchemaMigrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private UserService users;

    @Autowired
    private ShopService shops;

    @Autowired
    private ShopStaffService staff;

    @Test
    void notificationTableExists() {
        var count = jdbc.sql("""
            SELECT count(*) FROM information_schema.tables WHERE table_name = 'user_notification'
            """)
            .query(Long.class)
            .single();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void invitationHasInviteeAndRevokedColumns() {
        var count = jdbc.sql("""
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = 'shop_invitation' AND column_name IN ('invitee_id', 'revoked_at')
            """)
            .query(Long.class)
            .single();

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void managerRoleHasAllPermissions() {
        var ownerId = owner();
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest(
            "Schema Shop",
            "staff-schema-" + UUID.randomUUID()
        ));

        staff.listRoles(ownerId, shop.id());

        var permissions = jdbc.sql("""
            SELECT permissions FROM shop_role
            WHERE shop_id = :shop AND is_system = TRUE AND name = 'Quản lý (Manager)'
            """)
            .param("shop", shop.id())
            .query((rs, rowNum) -> rs.getArray("permissions").getArray())
            .single();

        assertThat((String[]) permissions).contains("REVENUE_WITHDRAW", "ORDER_CANCEL");
    }

    private UUID owner() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("staff-schema-" + id + "@t.local", "Schema Owner", null));
        return id;
    }
}