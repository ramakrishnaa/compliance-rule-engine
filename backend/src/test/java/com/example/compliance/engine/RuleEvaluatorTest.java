package com.example.compliance.engine;

import static com.example.compliance.TestData.rule;
import static com.example.compliance.TestData.tx;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.compliance.engine.Outcome.Status;
import com.example.compliance.model.Operator;
import com.example.compliance.model.Transaction;

class RuleEvaluatorTest {

    private final RuleEvaluator evaluator = new RuleEvaluator();

    private Status eval(String field, Operator op, String value, Transaction tx) {
        return evaluator.evaluate(rule("R1", field, op, value), tx).status();
    }

    @Nested
    class NumericOperators {

        @ParameterizedTest(name = "amount {0} gt 10000 -> {1}")
        @CsvSource({
                "10000.01, BREACHED",
                "10000.00, NOT_BREACHED", // strict: equal is not greater
                "10000,    NOT_BREACHED",
                "9999.99,  NOT_BREACHED",
        })
        void gtIsStrict(String amount, Status expected) {
            assertThat(eval("amount", Operator.GT, "10000", tx("T").number("amount", amount).build()))
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "amount {0} lt 1000 -> {1}")
        @CsvSource({
                "999.99,  BREACHED",
                "1000.00, NOT_BREACHED",
                "-5,      BREACHED",
        })
        void ltIsStrict(String amount, Status expected) {
            assertThat(eval("amount", Operator.LT, "1000", tx("T").number("amount", amount).build()))
                    .isEqualTo(expected);
        }

        @Test
        void comparesAsNumbersNotStrings() {
            // As strings "9" > "10000"; as numbers it is not.
            assertThat(eval("amount", Operator.GT, "10000", tx("T").number("amount", "9").build()))
                    .isEqualTo(Status.NOT_BREACHED);
        }

        @Test
        void numericStringAmountIsCoerced() {
            assertThat(eval("amount", Operator.GT, "10000", tx("T").text("amount", "15000.50").build()))
                    .isEqualTo(Status.BREACHED);
        }

        @Test
        void nonNumericValueIsNotEvaluable() {
            Outcome outcome = evaluator.evaluate(rule("R1", "amount", Operator.GT, "10000"),
                    tx("T").text("amount", "a lot").build());
            assertThat(outcome.status()).isEqualTo(Status.NOT_EVALUABLE);
            assertThat(outcome.reason()).contains("not numeric");
        }
    }

    @Nested
    class TimestampHour {

        @ParameterizedTest(name = "{0} hour gt 22 -> {1}")
        @CsvSource({
                "2024-01-15T23:30:00Z,      BREACHED",
                "2024-01-15T23:00:00Z,      BREACHED",
                "2024-01-15T22:59:59Z,      NOT_BREACHED", // hour is 22, not > 22
                "2024-01-15T00:15:00Z,      NOT_BREACHED",
                // 01:30 at +03:00 is 22:30 UTC on the previous day
                "2024-01-16T01:30:00+03:00, NOT_BREACHED",
                // 18:30 at -05:00 is 23:30 UTC
                "2024-01-15T18:30:00-05:00, BREACHED",
        })
        void usesUtcHour(String timestamp, Status expected) {
            assertThat(eval("timestamp.hour", Operator.GT, "22", tx("T").timestamp(timestamp).build()))
                    .isEqualTo(expected);
        }

        @Test
        void eqWorksOnHour() {
            assertThat(eval("timestamp.hour", Operator.EQ, "9", tx("T").timestamp("2024-01-15T09:05:00Z").build()))
                    .isEqualTo(Status.BREACHED);
        }

        @Test
        void missingTimestampIsNotEvaluable() {
            assertThat(eval("timestamp.hour", Operator.GT, "22", tx("T").build()))
                    .isEqualTo(Status.NOT_EVALUABLE);
        }
    }

    @Nested
    class StringOperators {

        @Test
        void eqIsCaseSensitive() {
            Transaction t = tx("T").text("transaction_type", "wire_transfer").build();
            assertThat(eval("transaction_type", Operator.EQ, "WIRE_TRANSFER", t)).isEqualTo(Status.NOT_BREACHED);
            assertThat(eval("transaction_type", Operator.EQ, "wire_transfer", t)).isEqualTo(Status.BREACHED);
        }

        @Test
        void neqIsCaseSensitive() {
            assertThat(eval("currency", Operator.NEQ, "USD", tx("T").text("currency", "USD").build()))
                    .isEqualTo(Status.NOT_BREACHED);
            assertThat(eval("currency", Operator.NEQ, "USD", tx("T").text("currency", "usd").build()))
                    .isEqualTo(Status.BREACHED);
        }

        @ParameterizedTest(name = "country {0} in IR,KP,SY -> {1}")
        @CsvSource({
                "IR, BREACHED",
                "SY, BREACHED",
                "US, NOT_BREACHED",
                "ir, NOT_BREACHED", // case-sensitive
                "I,  NOT_BREACHED", // whole-entry match, not substring
                "'IR,KP', NOT_BREACHED",
        })
        void inMatchesWholeEntries(String country, Status expected) {
            assertThat(eval("country", Operator.IN, "IR,KP,SY", tx("T").text("country", country).build()))
                    .isEqualTo(expected);
        }

        @Test
        void eqOnNumericFieldComparesNumerically() {
            Transaction t = tx("T").number("amount", "10000.00").build();
            assertThat(eval("amount", Operator.EQ, "10000", t)).isEqualTo(Status.BREACHED);
            assertThat(eval("amount", Operator.NEQ, "10000", t)).isEqualTo(Status.NOT_BREACHED);
            assertThat(eval("amount", Operator.IN, "5,10000", t)).isEqualTo(Status.BREACHED);
        }
    }

    @Nested
    class MissingData {

        @Test
        void missingFieldIsNotEvaluableForEveryOperator() {
            Transaction t = tx("T").build();
            for (Operator op : Operator.values()) {
                Outcome outcome = evaluator.evaluate(rule("R1", "currency", op, op.isNumeric() ? "1" : "USD"), t);
                assertThat(outcome.status()).as(op.code()).isEqualTo(Status.NOT_EVALUABLE);
                assertThat(outcome.reason()).contains("'currency' is missing");
            }
        }

        @Test
        void ruleOnUnknownFieldIsNotEvaluable() {
            assertThat(eval("merchant", Operator.EQ, "X", tx("T").text("currency", "USD").build()))
                    .isEqualTo(Status.NOT_EVALUABLE);
        }

        @Test
        void extraFieldsInTheDataCanBeTargetedByRules() {
            assertThat(eval("channel", Operator.EQ, "ONLINE", tx("T").text("channel", "ONLINE").build()))
                    .isEqualTo(Status.BREACHED);
        }
    }
}
