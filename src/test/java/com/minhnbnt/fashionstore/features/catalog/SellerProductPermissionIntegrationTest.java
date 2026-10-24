package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.AdjustStockRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.features.catalog.repository.CategoryRepository;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;
import com.minhnbnt.fashionstore.features.shop.ShopRoleRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.Category;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.model.ShopRole;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ghim phân quyền chi tiết của seller catalog: staff chỉ có PRODUCT_READ được xem sản phẩm,
 * nhưng không được ghi sản phẩm hay ghi tồn kho.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SellerProductPermissionIntegrationTest {

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private ShopMemberRepository members;

    @Autowired
    private ShopRoleRepository roles;

    @Autowired
    private CategoryRepository categories;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("perm-" + id + "@t.local", "Perm User", null));
        return id;
    }

    private UUID approvedShop(UUID ownerId, String slug) {
        var adminId = mirroredUser();
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest("Shop " + slug, slug));
        shops.approve(adminId, shop.id());
        return shop.id();
    }

    private UUID productReadOnlyStaff(UUID ownerId, UUID shopId) {
        var staffId = mirroredUser();
        var role = ShopRole.create(
            shopId,
            "Product Reader " + UUID.randomUUID(),
            new String[]{ShopPermission.PRODUCT_READ.name()},
            false
        );
        roles.save(role);
        members.add(ShopMember.staff(shopId, staffId, role.getId(), ownerId));

        return staffId;
    }

    private UUID testCategory() {
        return categories.findAllByOrderBySortOrderAsc().stream()
            .findFirst()
            .map(Category::getId)
            .orElseGet(() -> categories.save(Category.seed("Áo Nam", "ao-nam", 0)).getId());
    }

    private CreateProductRequest productRequest(String slug) {
        return new CreateProductRequest(
            "Áo Thun " + slug,
            slug,
            "Mô tả chi tiết " + slug,
            testCategory(),
            List.of(new CreateVariantRequest("SKU-" + slug, "M", "Den", 100000L, null, 10, true)),
            List.of(),
            null, null, null, null, null, null, null, null, null, null, null, "ACTIVE"
        );
    }

    private static void assertForbidden(ResponseStatusException e) {
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void staffWithReadCannotCreateProduct() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-create-" + UUID.randomUUID());
        var staffId = productReadOnlyStaff(ownerId, shopId);

        assertThatThrownBy(() -> catalog.createProduct(staffId, shopId, productRequest("perm-create-denied")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertForbidden((ResponseStatusException) e));
    }

    @Test
    void staffWithReadCanListProducts() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-list-" + UUID.randomUUID());
        var staffId = productReadOnlyStaff(ownerId, shopId);
        catalog.createProduct(ownerId, shopId, productRequest("perm-list"));

        var listed = catalog.listSellerProducts(staffId, shopId, null, null, 0, 10);

        assertThat(listed).extracting(item -> item.slug()).contains("perm-list");
    }

    @Test
    void staffWithoutInventoryWriteCannotAdjustStock() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-stock-" + UUID.randomUUID());
        var staffId = productReadOnlyStaff(ownerId, shopId);
        var created = catalog.createProduct(ownerId, shopId, productRequest("perm-stock"));
        var variantId = created.variants().getFirst().id();

        assertThatThrownBy(() -> catalog.adjustStock(
            staffId,
            shopId,
            variantId,
            new AdjustStockRequest(5, "RESTOCK")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertForbidden((ResponseStatusException) e));
    }

    @Test
    void ownerCanCreateProduct() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-owner-" + UUID.randomUUID());

        var created = catalog.createProduct(ownerId, shopId, productRequest("perm-owner"));

        assertThat(created.slug()).isEqualTo("perm-owner");
    }
}
