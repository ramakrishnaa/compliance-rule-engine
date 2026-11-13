package com.example.compliance.model;

import java.util.Locale;
import java.util.Optional;

public enum Operator {
    GT("gt", true),
    LT("lt", true),
    EQ("eq", false),
    NEQ("neq", false),
    IN("in", false);

    private final String code;
    private final boolean numeric;

    Operator(String code, boolean numeric) {
        this.code = code;
        this.numeric = numeric;
    }

    public String code() {
        return code;
    }

    /** True for operators that only make sense on numbers ({@code gt}, {@code lt}). */
    public boolean isNumeric() {
        return numeric;
    }

    /** Case-insensitive lookup by code; returns empty for unknown operators. */
    public static Optional<Operator> fromCode(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (Operator op : values()) {
            if (op.code.equals(normalized)) {
                return Optional.of(op);
            }
        }
        return Optional.empty();
    }
}
