package com.minhnbnt.fashionstore.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductAnswerTest {

    @Test
    void createShopAnswerSetsRoleShopAndNotVerifiedBuyer() {
        var questionId = UUID.randomUUID();
        var authorId = UUID.randomUUID();
        var shopName = "StyleShop";
        var content = "This is a shop reply.";

        var answer = ProductAnswer.createShopAnswer(questionId, authorId, shopName, content);

        assertThat(answer.getId()).isNotNull();
        assertThat(answer.getQuestionId()).isEqualTo(questionId);
        assertThat(answer.getAuthorId()).isEqualTo(authorId);
        assertThat(answer.getAuthorDisplayName()).isEqualTo(shopName);
        assertThat(answer.getAuthorRole()).isEqualTo(ProductAnswerRole.SHOP);
        assertThat(answer.isVerifiedBuyer()).isFalse();
        assertThat(answer.getContent()).isEqualTo(content);
        assertThat(answer.getEditedAt()).isNull();
        assertThat(answer.getCreatedAt()).isNotNull();
        assertThat(answer.getUpdatedAt()).isNotNull();
        assertThat(answer.isNew()).isTrue();
    }

    @Test
    void createVerifiedBuyerAnswerSetsRoleVerifiedBuyer() {
        var questionId = UUID.randomUUID();
        var authorId = UUID.randomUUID();
        var buyerName = "Jane Doe";
        var content = "I bought this, it fits well.";

        var answer = ProductAnswer.createVerifiedBuyerAnswer(questionId, authorId, buyerName, content);

        assertThat(answer.getId()).isNotNull();
        assertThat(answer.getQuestionId()).isEqualTo(questionId);
        assertThat(answer.getAuthorId()).isEqualTo(authorId);
        assertThat(answer.getAuthorDisplayName()).isEqualTo(buyerName);
        assertThat(answer.getAuthorRole()).isEqualTo(ProductAnswerRole.VERIFIED_BUYER);
        assertThat(answer.isVerifiedBuyer()).isTrue();
        assertThat(answer.getContent()).isEqualTo(content);
        assertThat(answer.getEditedAt()).isNull();
        assertThat(answer.getCreatedAt()).isNotNull();
        assertThat(answer.getUpdatedAt()).isNotNull();
        assertThat(answer.isNew()).isTrue();
    }

    @Test
    void editContentUpdatesContentAndTimestamps() {
        var answer = ProductAnswer.createShopAnswer(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Shop",
            "Initial reply"
        );

        answer.editContent("Updated reply");

        assertThat(answer.getContent()).isEqualTo("Updated reply");
        assertThat(answer.getEditedAt()).isNotNull();
        assertThat(answer.getUpdatedAt()).isNotNull();
    }
}
