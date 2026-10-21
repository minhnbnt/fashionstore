package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.model.BannedKeyword;
import com.minhnbnt.fashionstore.model.ProductViolationType;
import org.junit.jupiter.api.Test;

import java.text.Normalizer;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordScannerTest {

    private static BannedKeyword keyword(String text) {
        return BannedKeyword.create(text, ProductViolationType.COUNTERFEIT, UUID.randomUUID());
    }

    @Test
    void matchesWholeWordIgnoringCase() {
        var fake = keyword("fake");

        var matched = KeywordScanner.matching("Áo thun FAKE 1:1 cực đẹp", List.of(fake));

        assertThat(matched).containsExactly(fake);
    }

    @Test
    void doesNotMatchInsideAnotherWord() {
        var matched = KeywordScanner.matching("Áo in chữ fakebook", List.of(keyword("fake")));

        assertThat(matched).isEmpty();
    }

    @Test
    void matchesVietnamesePhraseWithDiacritics() {
        var phrase = keyword("hàng nhái");

        var matched = KeywordScanner.matching("Túi xách HÀNG NHÁI cao cấp", List.of(phrase));

        assertThat(matched).containsExactly(phrase);
    }

    @Test
    void normalizesDecomposedUnicode() {
        var phrase = keyword("hàng nhái");
        var decomposed = Normalizer.normalize("hàng nhái loại 1", Normalizer.Form.NFD);

        var matched = KeywordScanner.matching(decomposed, List.of(phrase));

        assertThat(matched).containsExactly(phrase);
    }

    @Test
    void returnsEveryMatchedKeywordInListOrder() {
        var fake = keyword("fake");
        var replica = keyword("replica");
        var auth = keyword("like auth");

        var matched = KeywordScanner.matching("Giày replica like auth, không phải fake", List.of(fake, replica, auth));

        assertThat(matched).containsExactly(fake, replica, auth);
    }

    @Test
    void treatsRegexCharactersLiterally() {
        var matched = KeywordScanner.matching("Áo size XL", List.of(keyword(".*")));

        assertThat(matched).isEmpty();
    }
}
