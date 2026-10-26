package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductVariantRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.StockMovementRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.ProductVariant;
import com.minhnbnt.fashionstore.model.StockMovement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductStockServiceUnitTest {

    @Mock
    private ProductRepository products;

    @Mock
    private ProductVariantRepository variants;

    @Mock
    private StockMovementRepository movements;

    @Mock
    private ShopService shops;

    @InjectMocks
    private ProductStockService service;

    @Test
    @DisplayName("decrementVariantStock returns true when stock is sufficient")
    void decrementVariantStock_sufficientStock_returnsTrue() {
        var variantId = UUID.randomUUID();
        when(variants.decrementStock(variantId, 2)).thenReturn(true);

        var result = service.decrementVariantStock(variantId, 2);

        assertThat(result).isTrue();
        verify(variants).decrementStock(variantId, 2);
    }

    @Test
    @DisplayName("decrementVariantStock returns false when stock is insufficient")
    void decrementVariantStock_insufficientStock_returnsFalse() {
        var variantId = UUID.randomUUID();
        when(variants.decrementStock(variantId, 5)).thenReturn(false);

        var result = service.decrementVariantStock(variantId, 5);

        assertThat(result).isFalse();
        verify(variants).decrementStock(variantId, 5);
    }

    @Test
    @DisplayName("incrementVariantStock calls repository correctly")
    void incrementVariantStock_callsRepository() {
        var variantId = UUID.randomUUID();

        service.incrementVariantStock(variantId, 3);

        verify(variants).incrementStock(variantId, 3);
    }

    @Test
    @DisplayName("requireActiveProduct throws 404 when product not found")
    void requireActiveProduct_notFound_throws404() {
        var productId = UUID.randomUUID();
        when(products.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActiveProduct(productId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("requireActiveProduct throws 404 when product is inactive")
    void requireActiveProduct_inactiveProduct_throws404() {
        var shopId = UUID.randomUUID();
        var product = Product.create(shopId, null, "Ao thun", "ao-thun", "Mo ta", null);
        product.hide();
        when(products.findById(product.getId())).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.requireActiveProduct(product.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("requireActiveProduct throws 404 when shop is not approved")
    void requireActiveProduct_unapprovedShop_throws404() {
        var shopId = UUID.randomUUID();
        var product = Product.create(shopId, null, "Ao thun", "ao-thun", "Mo ta", null);
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
        when(shops.isShopApproved(shopId)).thenReturn(false);

        assertThatThrownBy(() -> service.requireActiveProduct(product.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("requireActiveProduct returns product when product and shop are valid")
    void requireActiveProduct_validProduct_returnsProduct() {
        var shopId = UUID.randomUUID();
        var product = Product.create(shopId, null, "Ao thun", "ao-thun", "Mo ta", null);
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
        when(shops.isShopApproved(shopId)).thenReturn(true);

        var result = service.requireActiveProduct(product.getId());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(product.getId());
    }

    @Test
    @DisplayName("requireVariant returns variant when found")
    void requireVariant_found_returnsVariant() {
        var variantId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var variant = ProductVariant.create(
            shopId,
            productId,
            "SKU-1",
            "M",
            "Red",
            100000L,
            null,
            10,
            true
        );
        when(variants.findById(variantId)).thenReturn(Optional.of(variant));

        var result = service.requireVariant(variantId);

        assertThat(result).isEqualTo(variant);
    }

    @Test
    @DisplayName("recordStockMovement saves StockMovement entity")
    void recordStockMovement_savesEntity() {
        var variantId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var orderId = UUID.randomUUID();

        service.recordStockMovement(variantId, shopId, 2, "ORDER_CHECKOUT", orderId);

        verify(movements).save(any(StockMovement.class));
    }
}
