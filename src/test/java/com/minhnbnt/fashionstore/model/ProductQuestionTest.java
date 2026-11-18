package com.minhnbnt.fashionstore.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductQuestionTest {

    @Test
    void createInitializesWithUnansweredAndNewRecord() {
        var code = "Q-123456";
        var productId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var askerId = UUID.randomUUID();
        var askerName = "John Doe";
        var content = "Is this item available?";

        var question = ProductQuestion.create(code, productId, shopId, askerId, askerName, content);

        assertThat(question.getId()).isNotNull();
        assertThat(question.getCode()).isEqualTo(code);
        assertThat(question.getProductId()).isEqualTo(productId);
        assertThat(question.getShopId()).isEqualTo(shopId);
        assertThat(question.getAskerId()).isEqualTo(askerId);
        assertThat(question.getAskerDisplayName()).isEqualTo(askerName);
        assertThat(question.getContent()).isEqualTo(content);
        assertThat(question.getStatus()).isEqualTo(ProductQuestionStatus.UNANSWERED);
        assertThat(question.getClosedAt()).isNull();
        assertThat(question.getClosedBy()).isNull();
        assertThat(question.getEditedAt()).isNull();
        assertThat(question.getCreatedAt()).isNotNull();
        assertThat(question.getUpdatedAt()).isNotNull();
        assertThat(question.isNew()).isTrue();
    }

    @Test
    void editContentUpdatesContentAndTimestamps() {
        var question = ProductQuestion.create(
            "Q-123456",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "John Doe",
            "Initial content"
        );

        question.editContent("Updated content");

        assertThat(question.getContent()).isEqualTo("Updated content");
        assertThat(question.getEditedAt()).isNotNull();
        assertThat(question.getUpdatedAt()).isNotNull();
    }

    @Test
    void markAnsweredChangesStatusWhenNotClosed() {
        var question = ProductQuestion.create(
            "Q-123456",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "John Doe",
            "Initial content"
        );

        question.markAnswered();

        assertThat(question.getStatus()).isEqualTo(ProductQuestionStatus.ANSWERED);
    }

    @Test
    void markAnsweredDoesNothingWhenClosed() {
        var question = ProductQuestion.create(
            "Q-123456",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "John Doe",
            "Initial content"
        );
        question.close(UUID.randomUUID());

        question.markAnswered();

        assertThat(question.getStatus()).isEqualTo(ProductQuestionStatus.CLOSED);
    }

    @Test
    void markUnansweredChangesStatusWhenNotClosed() {
        var question = ProductQuestion.create(
            "Q-123456",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "John Doe",
            "Initial content"
        );
        question.markAnswered();

        question.markUnanswered();

        assertThat(question.getStatus()).isEqualTo(ProductQuestionStatus.UNANSWERED);
    }

    @Test
    void markUnansweredDoesNothingWhenClosed() {
        var question = ProductQuestion.create(
            "Q-123456",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "John Doe",
            "Initial content"
        );
        question.close(UUID.randomUUID());

        question.markUnanswered();

        assertThat(question.getStatus()).isEqualTo(ProductQuestionStatus.CLOSED);
    }

    @Test
    void closeSetsStatusClosedAndRecordsClosedBy() {
        var question = ProductQuestion.create(
            "Q-123456",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "John Doe",
            "Initial content"
        );
        var closerId = UUID.randomUUID();

        question.close(closerId);

        assertThat(question.getStatus()).isEqualTo(ProductQuestionStatus.CLOSED);
        assertThat(question.getClosedAt()).isNotNull();
        assertThat(question.getClosedBy()).isEqualTo(closerId);
    }

    @Test
    void reopenClearsClosedAtAndSetsAnsweredWhenShopAnswerExists() {
        var question = ProductQuestion.create(
            "Q-123456",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "John Doe",
            "Initial content"
        );
        question.close(UUID.randomUUID());

        question.reopen(true);

        assertThat(question.getStatus()).isEqualTo(ProductQuestionStatus.ANSWERED);
        assertThat(question.getClosedAt()).isNull();
        assertThat(question.getClosedBy()).isNull();
    }

    @Test
    void reopenClearsClosedAtAndSetsUnansweredWhenNoShopAnswer() {
        var question = ProductQuestion.create(
            "Q-123456",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "John Doe",
            "Initial content"
        );
        question.close(UUID.randomUUID());

        question.reopen(false);

        assertThat(question.getStatus()).isEqualTo(ProductQuestionStatus.UNANSWERED);
        assertThat(question.getClosedAt()).isNull();
        assertThat(question.getClosedBy()).isNull();
    }
}
