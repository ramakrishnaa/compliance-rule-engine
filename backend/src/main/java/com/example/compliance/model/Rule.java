package com.example.compliance.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * A validated rule loaded from rules.csv.
 *
 * @param numericValue the comparison value as a number, when it parses as one (always set for gt/lt)
 * @param listValues   the comparison values: the comma-separated entries for {@code in}, otherwise a
 *                     single-element list holding {@code value}
 * @param sourceLine   the CSV line number the rule came from, for diagnostics
 */
public record Rule(
        String ruleId,
        String ruleName,
        String field,
        Operator operator,
        String value,
        Severity severity,
        BigDecimal numericValue,
        List<String> listValues,
        long sourceLine) {

    public Rule {
        listValues = List.copyOf(listValues);
    }
}
