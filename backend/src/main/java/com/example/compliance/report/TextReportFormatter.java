package com.example.compliance.report;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.compliance.model.TransactionResult;

/**
 * Renders results in the format required by the brief:
 *
 * <pre>
 * Transaction    Breached Rules
 * TXN001    R001 (HIGH), R003 (MEDIUM)
 * TXN002    none
 * </pre>
 *
 * Lines end with a line feed on every platform so the output is identical everywhere.
 */
@Component
public class TextReportFormatter {

    public static final String HEADER = "Transaction    Breached Rules";
    public static final String SEPARATOR = "    ";
    public static final String NONE = "none";

    public String format(List<TransactionResult> results) {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        for (TransactionResult result : results) {
            out.append(result.transactionId()).append(SEPARATOR).append(formatRules(result)).append('\n');
        }
        return out.toString();
    }

    public static String formatRules(TransactionResult result) {
        if (result.breachedRules().isEmpty()) {
            return NONE;
        }
        return result.breachedRules().stream()
                .map(r -> r.ruleId() + " (" + r.severity() + ")")
                .collect(Collectors.joining(", "));
    }
}
