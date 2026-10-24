package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.model.ShopRole;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ghim vô hiệu hóa nhân viên có hiệu lực ngay ở request kế tiếp và danh sách thành viên
 * trả email/tên thật từ user_profile, kể cả thành viên đã vô hiệu hóa.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StaffMemberStatusIntegrationTest {

    @Autowired
    private ShopStaffService staff;

    @Autowired
    private ShopAccessChecker access;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private ShopRoleRepository roles;

    @Autowired
    private ShopMemberRepository members;

    @Test
    void deactivatedMemberLosesAccessOnNextCheck() {
        var ownerId = user("owner");
        var shopId = shop(ownerId);
        var role = role(shopId);
        var staffId = user("staff");
        members.add(ShopMember.staff(shopId, staffId, role.getId(), ownerId));

        access.requireShopPermission(staffId, shopId, ShopPermission.ORDER_READ);

        staff.updateMemberStatus(ownerId, shopId, staffId, "DEACTIVATED");

        assertThat(access.permissionsOf(staffId, shopId)).isEmpty();
        assertThatThrownBy(() -> access.requireShopPermission(staffId, shopId, ShopPermission.ORDER_READ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void memberListReturnsEmailAndDisplayName() {
        var ownerId = user("owner");
        var shopId = shop(ownerId);
        var role = role(shopId);
        var staffId = user("staff");
        members.add(ShopMember.staff(shopId, staffId, role.getId(), ownerId));

        staff.updateMemberStatus(ownerId, shopId, staffId, "DEACTIVATED");

        var listed = staff.listMembers(ownerId, shopId);
        var staffRow = listed.stream()
            .filter(member -> member.userId().equals(staffId))
            .findFirst()
            .orElseThrow();

        assertThat(staffRow.email()).isEqualTo(users.getById(staffId).email());
        assertThat(staffRow.displayName()).isEqualTo("staff");
        assertThat(staffRow.status()).isEqualTo("DEACTIVATED");
    }

    private UUID user(String prefix) {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest(prefix + "-" + id + "@t.local", prefix, null));
        return id;
    }

    private UUID shop(UUID ownerId) {
        return shops.register(ownerId, ShopTestFixtures.createRequest(
            "Status Shop",
            "status-shop-" + UUID.randomUUID()
        )).id();
    }

    private ShopRole role(UUID shopId) {
        var role = ShopRole.create(shopId, "Status Role " + UUID.randomUUID(), new String[]{"ORDER_READ"}, false);
        return roles.save(role);
    }
}
