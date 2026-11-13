package com.example.compliance.model;

import java.math.BigDecimal;

/** A scalar transaction attribute: either a JSON string (kept verbatim) or a JSON number. */
public sealed interface FieldValue permits FieldValue.Text, FieldValue.Numeric {

    /** Textual form used for string comparisons. */
    String asText();

    record Text(String value) implements FieldValue {
        @Override
        public String asText() {
            return value;
        }
    }

    record Numeric(BigDecimal value) implements FieldValue {
        @Override
        public String asText() {
            return value.toPlainString();
        }
    }
}
