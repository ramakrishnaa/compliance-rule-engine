package com.example.compliance.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.compliance.model.BreachedRule;
import com.example.compliance.model.Severity;
import com.example.compliance.model.TransactionResult;

class TextReportFormatterTest {

    private final TextReportFormatter formatter = new TextReportFormatter();

    @Test
    void formatsHeaderBreachesAndNone() {
        String out = formatter.format(List.of(
                new TransactionResult("TXN001", List.of(
                        new BreachedRule("R001", "large", Severity.HIGH),
                        new BreachedRule("R003", "late", Severity.MEDIUM))),
                new TransactionResult("TXN002", List.of())));

        assertThat(out).isEqualTo("""
                Transaction    Breached Rules
                TXN001    R001 (HIGH), R003 (MEDIUM)
                TXN002    none
                """);
    }

    @Test
    void emptyInputPrintsHeaderOnly() {
        assertThat(formatter.format(List.of())).isEqualTo("Transaction    Breached Rules\n");
    }
}
