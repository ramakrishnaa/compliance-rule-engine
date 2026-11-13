package com.example.compliance.io;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import com.example.compliance.model.Diagnostics;
import com.example.compliance.model.Operator;
import com.example.compliance.model.Rule;
import com.example.compliance.model.Severity;
import com.example.compliance.util.Numbers;

/**
 * Reads rules.csv.
 *
 * <p>The file as a whole must be CSV with a header row containing every required column (otherwise
 * {@link InputFormatException}). Each data row is validated on its own; a row that cannot be turned into
 * a well-defined rule is skipped and a warning is recorded, so one bad row never hides the others.
 */
@Component
public class RuleCsvParser {

    static final String COL_RULE_ID = "rule_id";
    static final String COL_RULE_NAME = "rule_name";
    static final String COL_FIELD = "field";
    static final String COL_OPERATOR = "operator";
    static final String COL_VALUE = "value";
    static final String COL_SEVERITY = "severity";

    static final List<String> REQUIRED_COLUMNS =
            List.of(COL_RULE_ID, COL_RULE_NAME, COL_FIELD, COL_OPERATOR, COL_VALUE, COL_SEVERITY);

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .get();

    private final String sourceName;

    public RuleCsvParser() {
        this("rules.csv");
    }

    RuleCsvParser(String sourceName) {
        this.sourceName = sourceName;
    }

    public List<Rule> parse(Reader reader, Diagnostics diagnostics) {
        String content = readFully(reader);
        if (content.startsWith("﻿")) {
            content = content.substring(1); // UTF-8 BOM, common in Excel exports
        }
        if (content.isBlank()) {
            throw new InputFormatException(sourceName + " is empty");
        }

        try (CSVParser parser = CSVParser.builder().setReader(new StringReader(content)).setFormat(FORMAT).get()) {
            Map<String, Integer> header = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            header.putAll(parser.getHeaderMap());
            List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !header.containsKey(c)).toList();
            if (!missing.isEmpty()) {
                throw new InputFormatException(sourceName + " header is missing required column(s) " + missing
                        + "; found " + parser.getHeaderNames());
            }

            List<Rule> rules = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            for (CSVRecord record : parser) {
                parseRecord(record, diagnostics).ifPresent(rule -> {
                    if (seenIds.add(rule.ruleId())) {
                        rules.add(rule);
                    } else {
                        diagnostics.warn(location(record), "duplicate rule_id '" + rule.ruleId()
                                + "'; keeping the first definition and ignoring this row");
                    }
                });
            }
            return rules;
        } catch (IOException | java.io.UncheckedIOException | IllegalStateException | IllegalArgumentException e) {
            throw new InputFormatException(sourceName + " is not valid CSV: " + e.getMessage(), e);
        }
    }

    private Optional<Rule> parseRecord(CSVRecord record, Diagnostics diagnostics) {
        String where = location(record);
        if (!record.isConsistent()) {
            diagnostics.warn(where, "expected " + record.getParser().getHeaderNames().size() + " columns but found "
                    + record.size() + " (an unquoted comma in 'value'?); row skipped");
            return Optional.empty();
        }

        String ruleId = record.get(COL_RULE_ID);
        String ruleName = record.get(COL_RULE_NAME);
        String field = record.get(COL_FIELD);
        String rawOperator = record.get(COL_OPERATOR);
        String value = record.get(COL_VALUE);
        String rawSeverity = record.get(COL_SEVERITY);

        if (ruleId.isEmpty()) {
            diagnostics.warn(where, "rule_id is empty; row skipped");
            return Optional.empty();
        }
        where = where + " (" + ruleId + ")";
        if (field.isEmpty()) {
            diagnostics.warn(where, "field is empty; rule skipped");
            return Optional.empty();
        }

        Optional<Operator> operator = Operator.fromCode(rawOperator);
        if (operator.isEmpty()) {
            diagnostics.warn(where, "unknown operator '" + rawOperator + "' (expected one of "
                    + Arrays.stream(Operator.values()).map(Operator::code).toList() + "); rule skipped");
            return Optional.empty();
        }

        Optional<Severity> severity = Severity.parse(rawSeverity);
        if (severity.isEmpty()) {
            diagnostics.warn(where, "unknown severity '" + rawSeverity + "' (expected one of "
                    + Arrays.toString(Severity.values()) + "); rule skipped");
            return Optional.empty();
        }
        if (!severity.get().name().equals(rawSeverity)) {
            diagnostics.warn(where, "severity '" + rawSeverity + "' normalised to " + severity.get());
        }

        BigDecimal numericValue = Numbers.parse(value);
        if (operator.get().isNumeric() && numericValue == null) {
            diagnostics.warn(where, "operator '" + operator.get().code() + "' needs a numeric value but got '"
                    + value + "'; rule skipped");
            return Optional.empty();
        }

        List<String> listValues;
        if (operator.get() == Operator.IN) {
            listValues = Arrays.stream(value.split(",", -1)).map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (listValues.isEmpty()) {
                diagnostics.warn(where, "operator 'in' has an empty value list; rule skipped");
                return Optional.empty();
            }
        } else {
            listValues = List.of(value);
        }

        return Optional.of(new Rule(ruleId, ruleName, field, operator.get(), value, severity.get(),
                numericValue, listValues, record.getRecordNumber() + 1));
    }

    private String location(CSVRecord record) {
        // +1 because the header occupies line 1 (record numbers are 1-based data rows).
        return sourceName + " row " + (record.getRecordNumber() + 1);
    }


    private String readFully(Reader reader) {
        try {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192];
            int n;
            while ((n = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, n);
            }
            return sb.toString();
        } catch (IOException e) {
            throw new InputFormatException("could not read " + sourceName + ": " + e.getMessage(), e);
        }
    }
}
