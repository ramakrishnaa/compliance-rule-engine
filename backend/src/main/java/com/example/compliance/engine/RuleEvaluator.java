package com.example.compliance.engine;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.compliance.model.FieldValue;
import com.example.compliance.model.Rule;
import com.example.compliance.model.Transaction;
import com.example.compliance.util.Numbers;

/**
 * Applies a single rule to a single transaction.
 *
 * <ul>
 *   <li>{@code gt}/{@code lt} compare numerically. A string field value that parses as a number is
 *       accepted; anything else makes the rule not evaluable.</li>
 *   <li>{@code eq}/{@code neq}/{@code in} are case-sensitive string comparisons. The one exception is a
 *       numeric field (e.g. {@code amount}, {@code timestamp.hour}) against a numeric rule value, which is
 *       compared as numbers so that {@code 10000} equals {@code 10000.00}.</li>
 *   <li>{@code timestamp.hour} is the hour (0-23) of the timestamp converted to UTC.</li>
 *   <li>A missing field makes the rule not evaluable for that transaction (for every operator,
 *       including {@code neq}): it is not reported as a breach, and the caller records a warning.</li>
 * </ul>
 */
@Component
public class RuleEvaluator {

    public static final String TIMESTAMP_HOUR = "timestamp.hour";

    public Outcome evaluate(Rule rule, Transaction tx) {
        Optional<FieldValue> resolved = resolve(rule.field(), tx);
        if (resolved.isEmpty()) {
            return Outcome.notEvaluable(TIMESTAMP_HOUR.equals(rule.field())
                    ? "timestamp is missing or not a valid ISO 8601 date-time"
                    : "field '" + rule.field() + "' is missing");
        }
        FieldValue actual = resolved.get();

        return switch (rule.operator()) {
            case GT, LT -> compareNumerically(rule, actual);
            case EQ -> Outcome.of(matches(actual, rule.value(), rule.numericValue()));
            case NEQ -> Outcome.of(!matches(actual, rule.value(), rule.numericValue()));
            case IN -> Outcome.of(rule.listValues().stream()
                    .anyMatch(candidate -> matches(actual, candidate, Numbers.parse(candidate))));
        };
    }

    static Optional<FieldValue> resolve(String field, Transaction tx) {
        if (TIMESTAMP_HOUR.equals(field)) {
            return Optional.ofNullable(tx.timestampUtc())
                    .map(ts -> new FieldValue.Numeric(BigDecimal.valueOf(ts.getHour())));
        }
        return tx.field(field);
    }

    private static Outcome compareNumerically(Rule rule, FieldValue actual) {
        BigDecimal number = Numbers.of(actual);
        if (number == null) {
            return Outcome.notEvaluable("field '" + rule.field() + "' value '" + actual.asText()
                    + "' is not numeric");
        }
        int cmp = number.compareTo(rule.numericValue());
        return Outcome.of(switch (rule.operator()) {
            case GT -> cmp > 0;
            case LT -> cmp < 0;
            default -> throw new IllegalStateException("not a numeric operator: " + rule.operator());
        });
    }

    private static boolean matches(FieldValue actual, String expected, BigDecimal expectedNumber) {
        if (actual instanceof FieldValue.Numeric n && expectedNumber != null) {
            return n.value().compareTo(expectedNumber) == 0;
        }
        return actual.asText().equals(expected);
    }
}
