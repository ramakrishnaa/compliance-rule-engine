package com.example.compliance.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * A transaction loaded from transactions.json.
 *
 * @param transactionId the id as it will be printed (a placeholder if the source had none)
 * @param position      1-based position in the input file
 * @param fields        all scalar attributes of the source object, keyed by JSON field name
 * @param timestampUtc  the parsed timestamp normalised to UTC, or {@code null} if missing/unparseable
 */
public record Transaction(
        String transactionId,
        int position,
        Map<String, FieldValue> fields,
        OffsetDateTime timestampUtc) {

    public Transaction {
        fields = Map.copyOf(fields);
    }

    public Optional<FieldValue> field(String name) {
        return Optional.ofNullable(fields.get(name));
    }
}
