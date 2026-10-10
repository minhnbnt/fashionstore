package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.dto.shop.ShopResponse;
import com.minhnbnt.fashionstore.dto.shop.UpdateShopRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.model.ShopStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ShopServiceTest {

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "U", null));
        return id;
    }

    @Test
    void registerCreatesPendingShopWithOwnerMember() {
        var ownerId = mirroredUser();

        var created = shops.register(
            ownerId,
            new CreateShopRequest("Shop Mot", "shop-mot-reg", "Mo ta", null, null)
        );

        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(shops.getMine(ownerId)).hasSize(1);
    }

    @Test
    void approveActivatesShop() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var created = shops.register(
            ownerId,
            new CreateShopRequest("Shop Hai", "shop-hai-appr", null, null, null)
        );

        var approved = shops.approve(adminId, created.id());

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(shops.getBySlug("shop-hai-appr").id()).isEqualTo(created.id());
    }

    @Test
    void approveTwiceFails() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var created = shops.register(
            ownerId,
            new CreateShopRequest("Shop Ba", "shop-ba-twice", null, null, null)
        );
        shops.approve(adminId, created.id());

        assertThatThrownBy(() -> shops.approve(adminId, created.id()))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateByNonMemberFails() {
        var ownerId = mirroredUser();
        var created = shops.register(
            ownerId,
            new CreateShopRequest("Shop Nam", "shop-nam-stranger", null, null, null)
        );

        assertThatThrownBy(() -> shops.update(
            UUID.randomUUID(),
            created.id(),
            new UpdateShopRequest("Ten moi", null, null, null)
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getBySlugHidesPendingShop() {
        var ownerId = mirroredUser();
        shops.register(
            ownerId,
            new CreateShopRequest("Shop Sau", "shop-sau-hidden", null, null, null)
        );

        assertThatThrownBy(() -> shops.getBySlug("shop-sau-hidden"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void listPendingShowsOnlyPendingShops() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        shops.register(ownerId, new CreateShopRequest("Cho Duyet", "cho-duyet", null, null, null));
        var approved = shops.register(ownerId, new CreateShopRequest("Da Duyet", "da-duyet", null, null, null));
        shops.approve(adminId, approved.id());

        var pending = shops.listByStatus(ShopStatus.PENDING);

        assertThat(pending).extracting(ShopResponse::slug).contains("cho-duyet");
        assertThat(pending).extracting(ShopResponse::slug).doesNotContain("da-duyet");
    }
}
