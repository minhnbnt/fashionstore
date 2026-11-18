package com.minhnbnt.fashionstore.features.qna.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductQnaEscapeLikeTest {

    @Test
    void escapeLikeEscapesBackslashPercentAndUnderscore() {
        var raw = "100% cotton_blend \\ test";
        var escaped = ProductQnaQueryRepository.escapeLike(raw);

        assertThat(escaped).isEqualTo("100\\% cotton\\_blend \\\\ test");
    }

    @Test
    void escapeLikeLeavesNormalTextUntouched() {
        var raw = "Ao thun trang size L";
        var escaped = ProductQnaQueryRepository.escapeLike(raw);

        assertThat(escaped).isEqualTo("Ao thun trang size L");
    }
}
