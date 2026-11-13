package com.example.compliance;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.compliance.model.FieldValue;
import com.example.compliance.model.Operator;
import com.example.compliance.model.Rule;
import com.example.compliance.model.Severity;
import com.example.compliance.model.Transaction;
import com.example.compliance.util.Numbers;

/** Small builders so tests read as "this rule against this transaction". */
public final class TestData {

    private TestData() {
    }

    public static Rule rule(String id, String field, Operator op, String value) {
        return rule(id, field, op, value, Severity.HIGH);
    }

    public static Rule rule(String id, String field, Operator op, String value, Severity severity) {
        List<String> list = op == Operator.IN
                ? Arrays.stream(value.split(",")).map(String::trim).toList()
                : List.of(value);
        return new Rule(id, id.toLowerCase(), field, op, value, severity, Numbers.parse(value), list, 0);
    }

    public static TxBuilder tx(String id) {
        return new TxBuilder(id);
    }

    public static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    public static final class TxBuilder {
        private final String id;
        private final Map<String, FieldValue> fields = new LinkedHashMap<>();
        private OffsetDateTime timestamp;

        private TxBuilder(String id) {
            this.id = id;
            fields.put("transaction_id", new FieldValue.Text(id));
        }

        public TxBuilder text(String name, String value) {
            fields.put(name, new FieldValue.Text(value));
            return this;
        }

        public TxBuilder number(String name, String value) {
            fields.put(name, new FieldValue.Numeric(new BigDecimal(value)));
            return this;
        }

        public TxBuilder timestamp(String iso) {
            fields.put("timestamp", new FieldValue.Text(iso));
            timestamp = OffsetDateTime.parse(iso).withOffsetSameInstant(ZoneOffset.UTC);
            return this;
        }

        public Transaction build() {
            return new Transaction(id, 1, fields, timestamp);
        }
    }
}
