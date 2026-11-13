package com.example.compliance.io;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.compliance.model.Diagnostics;
import com.example.compliance.model.FieldValue;
import com.example.compliance.model.Transaction;
import com.example.compliance.util.Numbers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Reads transactions.json.
 *
 * <p>The document must be valid JSON whose root is an object with a {@code transactions} array (a bare
 * top-level array is also accepted, with a warning). Anything else is an {@link InputFormatException}.
 * Individual transactions are loaded leniently: values are kept as-is wherever possible and every
 * oddity is recorded as a warning, because silently dropping a transaction is the worst outcome for a
 * compliance check.
 */
@Component
public class TransactionJsonParser {

    public static final String FIELD_TRANSACTION_ID = "transaction_id";
    public static final String FIELD_TIMESTAMP = "timestamp";
    public static final String FIELD_AMOUNT = "amount";
    public static final String FIELD_CURRENCY = "currency";
    public static final String FIELD_COUNTRY = "country";

    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");
    private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Z]{2}");

    /** Amounts are read as BigDecimal so that e.g. 10000.00 vs 10000 compares exactly. */
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final String sourceName;

    public TransactionJsonParser() {
        this("transactions.json");
    }

    TransactionJsonParser(String sourceName) {
        this.sourceName = sourceName;
    }

    public List<Transaction> parse(InputStream input, Diagnostics diagnostics) {
        JsonNode root;
        try {
            root = mapper.readTree(input);
        } catch (JsonProcessingException e) {
            throw new InputFormatException(sourceName + " is not valid JSON: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new InputFormatException("could not read " + sourceName + ": " + e.getMessage(), e);
        }

        JsonNode array = locateTransactionArray(root, diagnostics);
        List<Transaction> transactions = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        int position = 0;
        for (JsonNode node : array) {
            position++;
            String where = sourceName + " #" + position;
            if (!node.isObject()) {
                diagnostics.warn(where, "expected a JSON object but found " + node.getNodeType() + "; skipped");
                continue;
            }
            Transaction tx = parseTransaction(node, position, where, diagnostics);
            if (!seenIds.add(tx.transactionId())) {
                diagnostics.warn(label(where, tx.transactionId()), "duplicate transaction_id; evaluated and "
                        + "reported again in file order");
            }
            transactions.add(tx);
        }
        if (transactions.isEmpty()) {
            diagnostics.warn(sourceName, "no transactions found");
        }
        return transactions;
    }

    private JsonNode locateTransactionArray(JsonNode root, Diagnostics diagnostics) {
        if (root == null || root.isMissingNode()) {
            throw new InputFormatException(sourceName + " is empty");
        }
        if (root.isArray()) {
            diagnostics.warn(sourceName, "root is a bare array, not an object with a 'transactions' array; "
                    + "treating the array as the transaction list");
            return root;
        }
        if (!root.isObject()) {
            throw new InputFormatException(sourceName + " root must be a JSON object, found " + root.getNodeType());
        }
        JsonNode array = root.get("transactions");
        if (array == null || !array.isArray()) {
            throw new InputFormatException(sourceName + " must contain a 'transactions' array");
        }
        return array;
    }

    private Transaction parseTransaction(JsonNode node, int position, String where, Diagnostics diagnostics) {
        String id = readId(node, position, where, diagnostics);
        String label = label(where, id);

        Map<String, FieldValue> fields = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String name = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isNull()) {
                continue; // treated exactly like an absent field
            } else if (value.isNumber()) {
                fields.put(name, new FieldValue.Numeric(value.decimalValue()));
            } else if (value.isTextual()) {
                String text = value.textValue();
                String trimmed = text.strip();
                if (!trimmed.equals(text)) {
                    diagnostics.warn(label, "field '" + name + "' has leading/trailing whitespace; trimmed");
                }
                fields.put(name, new FieldValue.Text(trimmed));
            } else if (value.isBoolean()) {
                fields.put(name, new FieldValue.Text(value.asText()));
            } else {
                diagnostics.warn(label, "field '" + name + "' is a " + value.getNodeType()
                        + ", not a scalar; ignored");
            }
        }

        validateKnownFields(fields, label, diagnostics);
        OffsetDateTime timestamp = parseTimestamp(fields.get(FIELD_TIMESTAMP), label, diagnostics);
        return new Transaction(id, position, fields, timestamp);
    }

    private String readId(JsonNode node, int position, String where, Diagnostics diagnostics) {
        JsonNode idNode = node.get(FIELD_TRANSACTION_ID);
        if (idNode != null && idNode.isValueNode() && !idNode.isNull() && !idNode.asText().isBlank()) {
            if (!idNode.isTextual()) {
                diagnostics.warn(where, "transaction_id is a " + idNode.getNodeType() + ", using its text form");
            }
            return idNode.asText().strip();
        }
        String placeholder = "UNKNOWN#" + position;
        diagnostics.warn(where, "missing or empty transaction_id; reported as '" + placeholder + "'");
        return placeholder;
    }

    private void validateKnownFields(Map<String, FieldValue> fields, String label, Diagnostics diagnostics) {
        FieldValue amount = fields.get(FIELD_AMOUNT);
        if (amount instanceof FieldValue.Text text) {
            diagnostics.warn(label, "amount is a string ('" + text.value() + "'), not a number"
                    + (Numbers.parse(text.value()) != null
                            ? "; it will be compared numerically"
                            : "; numeric rules on amount cannot be evaluated"));
        }
        checkCode(fields.get(FIELD_CURRENCY), FIELD_CURRENCY, CURRENCY_CODE, "an ISO 4217 code", label, diagnostics);
        checkCode(fields.get(FIELD_COUNTRY), FIELD_COUNTRY, COUNTRY_CODE, "an ISO 3166 alpha-2 code", label,
                diagnostics);
    }

    private static void checkCode(FieldValue value, String name, Pattern pattern, String expected, String label,
            Diagnostics diagnostics) {
        if (value != null && !pattern.matcher(value.asText()).matches()) {
            diagnostics.warn(label, name + " '" + value.asText() + "' does not look like " + expected
                    + "; compared as-is (comparisons are case-sensitive)");
        }
    }

    private OffsetDateTime parseTimestamp(FieldValue value, String label, Diagnostics diagnostics) {
        if (value == null) {
            return null; // reported by the evaluator only if a rule actually needs it
        }
        String text = value.asText();
        try {
            return OffsetDateTime.parse(text).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // fall through: maybe there is no offset
        }
        try {
            OffsetDateTime assumed = LocalDateTime.parse(text).atOffset(ZoneOffset.UTC);
            diagnostics.warn(label, "timestamp '" + text + "' has no UTC offset; assumed to be UTC");
            return assumed;
        } catch (DateTimeParseException e) {
            diagnostics.warn(label, "timestamp '" + text + "' is not an ISO 8601 date-time; "
                    + "timestamp rules cannot be evaluated");
            return null;
        }
    }

    private static String label(String where, String id) {
        return where + " (" + id + ")";
    }

}
