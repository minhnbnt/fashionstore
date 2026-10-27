package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopCategorySummaryResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopProductQueryRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.model.ShopStatus;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopCatalogService {

    private final ShopRepository shops;
    private final ShopCatalogQueryRepository catalogQueries;
    private final CatalogService catalogService;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getShopProducts(
        @NonNull UUID shopId,
        @NonNull ShopProductQueryRequest request
    ) {
        var shop = shops.findById(shopId).orElse(null);
        if (shop == null || shop.getStatus() != ShopStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }

        var productIds = catalogQueries.findShopProductIds(shopId, request);
        var total = catalogQueries.countShopProducts(shopId, request);

        var items = new ArrayList<ProductResponse>();
        for (var id : productIds) {
            try {
                items.add(catalogService.getDetail(id));
            } catch (ResponseStatusException e) {
                log.debug("Product detail not available for id {}: {}", id, e.getMessage());
            }
        }

        int limit = request.limit();
        if (limit <= 0 || limit > 50) {
            limit = 12;
        }
        int page = request.page();
        if (page < 0) {
            page = 0;
        }

        return new PageResponse<>(items, total, page, limit);
    }

    @Transactional(readOnly = true)
    public List<ShopCategorySummaryResponse> getShopCategories(@NonNull UUID shopId) {
        var shop = shops.findById(shopId).orElse(null);
        if (shop == null || shop.getStatus() != ShopStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }

        return catalogQueries.findShopCategories(shopId);
    }
}
