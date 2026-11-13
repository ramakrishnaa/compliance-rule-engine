package com.example.compliance.model;

/**
 * Something unexpected found in the customer data.
 *
 * @param level   WARNING when the item was handled (skipped, defaulted, coerced); ERROR is reserved for
 *                problems that stop the whole evaluation
 * @param source  where it was found, e.g. {@code rules.csv line 4} or {@code transactions.json #3 (TXN003)}
 * @param message what was found and what the program did about it
 */
public record Issue(Level level, String source, String message) {

    public enum Level { WARNING, ERROR }

    @Override
    public String toString() {
        return level + " [" + source + "] " + message;
    }
}
