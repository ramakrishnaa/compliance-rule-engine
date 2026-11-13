package com.example.compliance.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RuleIdComparatorTest {

    private static List<String> sorted(String... ids) {
        List<String> list = new ArrayList<>(List.of(ids));
        list.sort(RuleIdComparator.INSTANCE);
        return list;
    }

    @Test
    void zeroPaddedIdsSortLikeStrings() {
        assertThat(sorted("R008", "R001", "R010", "R002")).containsExactly("R001", "R002", "R008", "R010");
    }

    @Test
    void unpaddedIdsSortNumerically() {
        assertThat(sorted("R10", "R2", "R1")).containsExactly("R1", "R2", "R10");
    }

    @Test
    void mixedPaddingIsStableAndTotal() {
        assertThat(sorted("R1", "R01", "R2")).containsExactly("R01", "R1", "R2");
    }

    @Test
    void differentPrefixesSortByPrefix() {
        assertThat(sorted("B1", "A2", "A10")).containsExactly("A2", "A10", "B1");
    }
}
