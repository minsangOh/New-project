package com.example.pstarchive;

import com.example.pstarchive.search.RiskyQueryDetector;
import com.example.pstarchive.search.SearchQueryNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskyQueryDetectorTest {
    private final SearchQueryNormalizer normalizer = new SearchQueryNormalizer();
    private final RiskyQueryDetector detector = new RiskyQueryDetector();

    @Test
    void detectsRiskyPartNumbersAndMixedModelNames() {
        assertTrue(isRisky("DA96-01767C"));
        assertTrue(isRisky("1015#18"));
        assertTrue(isRisky("RWP90H"));
    }

    @Test
    void detectsShortKoreanQueriesAsRisky() {
        assertTrue(isRisky("\uC0BC\uC131\uC804\uC790"));
    }

    @Test
    void leavesNormalWordAsNotRisky() {
        assertFalse(isRisky("normal"));
    }

    private boolean isRisky(String query) {
        return detector.isRisky(normalizer.normalize(query));
    }
}