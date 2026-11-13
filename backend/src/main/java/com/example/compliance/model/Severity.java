package com.example.compliance.model;

import java.util.Locale;
import java.util.Optional;

public enum Severity {
    LOW, MEDIUM, HIGH, CRITICAL;

    /** Case-insensitive lookup; returns empty for anything that is not a known severity. */
    public static Optional<Severity> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
