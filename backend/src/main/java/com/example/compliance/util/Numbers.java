package com.example.compliance.util;

import java.math.BigDecimal;

import com.example.compliance.model.FieldValue;

public final class Numbers {

    private Numbers() {
    }

    /** Parses a decimal number, tolerating surrounding whitespace; returns {@code null} if it is not one. */
    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A JSON number as-is, or a string that parses as a number; otherwise {@code null}. */
    public static BigDecimal of(FieldValue value) {
        if (value instanceof FieldValue.Numeric n) {
            return n.value();
        }
        return parse(value.asText());
    }
}
