package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.shop.DecorationResponse;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.ShopDecoration;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopDecorationService {

    private final ShopDecorationRepository decorations;

    private final ShopAccessChecker access;

    @Transactional(readOnly = true)
    public DecorationResponse getPublishedLayout(@NonNull UUID shopId) {
        var deco = decorations.findByShopId(shopId)
            .orElseGet(() -> ShopDecoration.createDefault(shopId));

        return new DecorationResponse(
            deco.getId(),
            deco.getShopId(),
            deco.getLayout(),
            null,
            deco.getPublishedAt(),
            deco.getUpdatedAt()
        );
    }

    @Transactional
    public DecorationResponse getSellerDecoration(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

        var deco = decorations.findByShopId(shopId)
            .orElseGet(() -> decorations.save(ShopDecoration.createDefault(shopId)));

        return toResponse(deco);
    }

    @Transactional
    public DecorationResponse saveDraft(@NonNull UUID userId, @NonNull UUID shopId, @NonNull String draftJson) {
        access.requireShopPermission(userId, shopId, ShopPermission.DECORATION_WRITE);

        var deco = decorations.findByShopId(shopId)
            .orElseGet(() -> ShopDecoration.createDefault(shopId));

        deco.saveDraft(draftJson);
        return toResponse(decorations.save(deco));
    }

    @Transactional
    public DecorationResponse publish(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.DECORATION_WRITE);

        var deco = decorations.findByShopId(shopId)
            .orElseGet(() -> ShopDecoration.createDefault(shopId));

        deco.publish();
        return toResponse(decorations.save(deco));
    }

    @Transactional
    public DecorationResponse discardDraft(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.DECORATION_WRITE);

        var deco = decorations.findByShopId(shopId)
            .orElseGet(() -> ShopDecoration.createDefault(shopId));

        deco.discardDraft();
        return toResponse(decorations.save(deco));
    }

    private static DecorationResponse toResponse(ShopDecoration d) {
        return new DecorationResponse(
            d.getId(),
            d.getShopId(),
            d.getLayout(),
            d.getDraft(),
            d.getPublishedAt(),
            d.getUpdatedAt()
        );
    }
}
