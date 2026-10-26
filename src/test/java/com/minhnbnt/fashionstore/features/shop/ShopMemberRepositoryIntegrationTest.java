package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopRole;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ghim query findAccess: role STAFF có role_id thì trả permissions, role_id null thì mảng rỗng,
 * không phải member thì Optional.empty().
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ShopMemberRepositoryIntegrationTest {

    @Autowired
    private ShopMemberRepository members;

    @Autowired
    private ShopRoleRepository roles;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Test
    void findAccessReturnsPermissionsOfAssignedRole() {
        var ownerId = user();
        var shopId = shop(ownerId);
        var staffId = user();

        var role = ShopRole.create(shopId, "Order Desk", new String[]{"ORDER_READ", "ORDER_PROCESS"}, false);
        roles.save(role);
        members.add(ShopMember.staff(shopId, staffId, role.getId(), ownerId));

        var access = members.findAccess(shopId, staffId).orElseThrow();

        assertThat(access.role().name()).isEqualTo("STAFF");
        assertThat(access.roleId()).isEqualTo(role.getId());
        assertThat(access.status()).isEqualTo("ACTIVE");
        assertThat(access.permissions()).containsExactlyInAnyOrder("ORDER_READ", "ORDER_PROCESS");
    }

    @Test
    void findAccessReturnsEmptyPermissionsWhenRoleIdIsNull() {
        var ownerId = user();
        var shopId = shop(ownerId);
        var staffId = user();

        members.add(ShopMember.staff(shopId, staffId, null, ownerId));

        var access = members.findAccess(shopId, staffId).orElseThrow();

        assertThat(access.role().name()).isEqualTo("STAFF");
        assertThat(access.roleId()).isNull();
        assertThat(access.permissions()).isNotNull().isEmpty();
    }

    @Test
    void findAccessReturnsEmptyPermissionsWhenRoleBelongsToAnotherShop() {
        var ownerId = user();
        var shopId = shop(ownerId);
        var otherShopId = shop(user());
        var staffId = user();

        var foreignRole = ShopRole.create(
            otherShopId,
            "Foreign Desk",
            new String[]{"ORDER_READ", "ORDER_PROCESS"},
            false
        );
        roles.save(foreignRole);
        members.add(ShopMember.staff(shopId, staffId, foreignRole.getId(), ownerId));

        var access = members.findAccess(shopId, staffId).orElseThrow();

        assertThat(access.role().name()).isEqualTo("STAFF");
        assertThat(access.roleId()).isEqualTo(foreignRole.getId());
        assertThat(access.permissions()).isNotNull().isEmpty();
    }

    @Test
    void findAccessIsEmptyForNonMember() {
        var ownerId = user();
        var shopId = shop(ownerId);

        assertThat(members.findAccess(shopId, user())).isEmpty();
    }

    @Test
    void findMembershipsByUserReturnsAllShopsAndRoles() {
        var userId = user();
        var ownedShopId = shop(userId);

        var otherOwnerId = user();
        var staffShopId = shop(otherOwnerId);
        var warehouseRole = ShopRole.create(
            staffShopId,
            "Kho vận",
            new String[]{"INVENTORY_READ", "ORDER_READ"},
            false
        );
        roles.save(warehouseRole);
        members.add(ShopMember.staff(staffShopId, userId, warehouseRole.getId(), otherOwnerId));

        var rows = members.findMembershipsByUser(userId);

        assertThat(rows).hasSize(2);
        var staffRow = rows.stream().filter(r -> r.shopId().equals(staffShopId)).findFirst().orElseThrow();
        assertThat(staffRow.role().name()).isEqualTo("STAFF");
        assertThat(staffRow.roleName()).isEqualTo("Kho vận");
        assertThat(staffRow.permissions()).containsExactlyInAnyOrder("INVENTORY_READ", "ORDER_READ");
        assertThat(staffRow.memberStatus()).isEqualTo("ACTIVE");

        var ownerRow = rows.stream().filter(r -> r.shopId().equals(ownedShopId)).findFirst().orElseThrow();
        assertThat(ownerRow.role().name()).isEqualTo("OWNER");
        assertThat(ownerRow.memberStatus()).isEqualTo("ACTIVE");
    }

    private UUID user() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("member-access-" + id + "@t.local", "Member Access", null));
        return id;
    }

    private UUID shop(UUID ownerId) {
        return shops.register(ownerId, ShopTestFixtures.createRequest(
            "Access Shop",
            "member-access-" + UUID.randomUUID()
        )).id();
    }
}
