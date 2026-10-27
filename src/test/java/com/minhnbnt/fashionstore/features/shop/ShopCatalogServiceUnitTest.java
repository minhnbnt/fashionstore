package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopCategorySummaryResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopProductCollectionType;
import com.minhnbnt.fashionstore.dto.shop.ShopProductQueryRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.model.Shop;
import com.minhnbnt.fashionstore.model.ShopStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopCatalogServiceUnitTest {

    @Mock
    private ShopRepository shops;

    @Mock
    private ShopCatalogQueryRepository catalogQueries;

    @Mock
    private CatalogService catalogService;

    @InjectMocks
    private ShopCatalogService shopCatalogService;

    @Test
    void getShopProductsThrowsNotFoundWhenShopDoesNotExist() {
        var shopId = UUID.randomUUID();
        var req = new ShopProductQueryRequest(null, null, null, null, 0, 10);

        when(shops.findById(shopId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopCatalogService.getShopProducts(shopId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Shop not found");
    }

    @Test
    void getShopProductsReturnsPageResponseForApprovedShop() {
        var shopId = UUID.randomUUID();
        var prodId = UUID.randomUUID();
        var req = new ShopProductQueryRequest("ao", ShopProductCollectionType.NEW_ARRIVALS, null, null, 0, 10);

        var shop = mock(Shop.class);
        when(shop.getStatus()).thenReturn(ShopStatus.APPROVED);
        when(shops.findById(shopId)).thenReturn(Optional.of(shop));

        when(catalogQueries.findShopProductIds(eq(shopId), eq(req))).thenReturn(List.of(prodId));
        when(catalogQueries.countShopProducts(eq(shopId), eq(req))).thenReturn(1L);

        var product = new ProductResponse(
            prodId, shopId, null, "Áo Thun Test", "ao-thun-test", "mô tả", "ACTIVE",
            List.of(), List.of(), Instant.now(), 5.0, 10L
        );
        when(catalogService.getDetails(List.of(prodId))).thenReturn(List.of(product));

        var response = shopCatalogService.getShopProducts(shopId, req);

        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().name()).isEqualTo("Áo Thun Test");
    }

    @Test
    void getShopCategoriesReturnsCategoryList() {
        var shopId = UUID.randomUUID();
        var catId = UUID.randomUUID();

        var shop = mock(Shop.class);
        when(shop.getStatus()).thenReturn(ShopStatus.APPROVED);
        when(shops.findById(shopId)).thenReturn(Optional.of(shop));

        when(catalogQueries.findShopCategories(shopId)).thenReturn(List.of(
            new ShopCategorySummaryResponse(catId, "Áo Nam", "ao-nam", 5L)
        ));

        var categories = shopCatalogService.getShopCategories(shopId);

        assertThat(categories).hasSize(1);
        assertThat(categories.getFirst().name()).isEqualTo("Áo Nam");
        assertThat(categories.getFirst().productCount()).isEqualTo(5L);
    }

    @Test
    void skipsUnfetchableProductsAndKeepsTotal() {
        var shopId = UUID.randomUUID();
        var visibleId = UUID.randomUUID();
        var unavailableId = UUID.randomUUID();
        var req = new ShopProductQueryRequest(null, null, null, null, 0, 10);

        var shop = mock(Shop.class);
        when(shop.getStatus()).thenReturn(ShopStatus.APPROVED);
        when(shops.findById(shopId)).thenReturn(Optional.of(shop));

        when(catalogQueries.findShopProductIds(eq(shopId), eq(req))).thenReturn(List.of(visibleId, unavailableId));
        when(catalogQueries.countShopProducts(eq(shopId), eq(req))).thenReturn(2L);
        when(catalogService.getDetails(any())).thenReturn(List.of(productResponse(visibleId, shopId)));

        var response = shopCatalogService.getShopProducts(shopId, req);

        assertThat(response.total()).isEqualTo(2L);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo(visibleId);
    }

    @Test
    void normalizesOversizedLimitAndNegativePage() {
        var shopId = UUID.randomUUID();
        var prodId = UUID.randomUUID();
        var req = new ShopProductQueryRequest(null, null, null, null, -1, 100);

        var shop = mock(Shop.class);
        when(shop.getStatus()).thenReturn(ShopStatus.APPROVED);
        when(shops.findById(shopId)).thenReturn(Optional.of(shop));
        when(catalogQueries.findShopProductIds(eq(shopId), eq(req))).thenReturn(List.of(prodId));
        when(catalogQueries.countShopProducts(eq(shopId), eq(req))).thenReturn(1L);
        when(catalogService.getDetails(any())).thenReturn(List.of(productResponse(prodId, shopId)));

        var response = shopCatalogService.getShopProducts(shopId, req);

        assertThat(response.size()).isEqualTo(12);
        assertThat(response.page()).isEqualTo(0);
    }

    @Test
    void throwsNotFoundWhenShopNotApproved() {
        var shopId = UUID.randomUUID();
        var req = new ShopProductQueryRequest(null, null, null, null, 0, 10);

        var shop = mock(Shop.class);
        when(shop.getStatus()).thenReturn(ShopStatus.PENDING);
        when(shops.findById(shopId)).thenReturn(Optional.of(shop));

        assertThatThrownBy(() -> shopCatalogService.getShopProducts(shopId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Shop not found");
    }

    private ProductResponse productResponse(UUID productId, UUID shopId) {
        return new ProductResponse(
            productId, shopId, null, "Áo Thun Test", "ao-thun-test", "mô tả", "ACTIVE",
            List.of(), List.of(), Instant.now(), 5.0, 10L
        );
    }
}
