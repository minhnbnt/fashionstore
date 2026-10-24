package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.ShopDecoration;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopDecorationServiceUnitTest {

    @Mock
    private ShopDecorationRepository decorations;

    @Mock
    private ShopAccessChecker access;

    @Mock
    private ShopAuditService audit;

    @InjectMocks
    private ShopDecorationService decorationService;

    @Test
    void saveDraftCreatesNewDecorationIfAbsent() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var draftJson = "{\"blocks\":[{\"type\":\"banner\",\"data\":\"url\"}]}";

        when(decorations.findByShopId(shopId)).thenReturn(Optional.empty());
        when(decorations.save(any(ShopDecoration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var resp = decorationService.saveDraft(userId, shopId, draftJson);

        verify(access).requireShopPermission(userId, shopId, ShopPermission.DECORATION_WRITE);
        assertThat(resp.draft()).isEqualTo(draftJson);
        verify(decorations).save(any(ShopDecoration.class));
    }

    @Test
    void publishReplacesPublishedLayoutWithDraft() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var deco = ShopDecoration.createDefault(shopId);
        var draftJson = "{\"blocks\":[{\"type\":\"carousel\"}]}";
        deco.saveDraft(draftJson);

        when(decorations.findByShopId(shopId)).thenReturn(Optional.of(deco));
        when(decorations.save(any(ShopDecoration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var resp = decorationService.publish(userId, shopId);

        assertThat(resp.layout()).isEqualTo(draftJson);
        assertThat(deco.getPublishedAt()).isNotNull();
        verify(decorations).save(deco);
    }

    @Test
    void discardDraftResetsDraftLayoutToNull() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var deco = ShopDecoration.createDefault(shopId);
        deco.saveDraft("{\"blocks\":[]}");

        when(decorations.findByShopId(shopId)).thenReturn(Optional.of(deco));
        when(decorations.save(any(ShopDecoration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var resp = decorationService.discardDraft(userId, shopId);

        assertThat(resp.draft()).isNull();
        verify(decorations).save(deco);
    }
}
