package com.example.compliance.web;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.example.compliance.model.EvaluationReport;
import com.example.compliance.model.Issue;
import com.example.compliance.model.Severity;
import com.example.compliance.model.TransactionResult;

/** JSON returned by the evaluation endpoints. {@code textReport} is the exact CLI output. */
public record EvaluationResponse(
        Summary summary,
        List<TransactionResult> results,
        List<Issue> issues,
        String textReport) {

    public record Summary(
            int transactionCount,
            int ruleCount,
            long flaggedTransactionCount,
            Map<Severity, Long> breachesBySeverity) {
    }

    public static EvaluationResponse from(EvaluationReport report, String textReport) {
        Map<Severity, Long> bySeverity = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) {
            bySeverity.put(s, 0L);
        }
        report.results().forEach(r -> r.breachedRules().forEach(b -> bySeverity.merge(b.severity(), 1L, Long::sum)));
        long flagged = report.results().stream().filter(r -> !r.breachedRules().isEmpty()).count();
        return new EvaluationResponse(
                new Summary(report.transactionCount(), report.ruleCount(), flagged, bySeverity),
                report.results(), report.issues(), textReport);
    }
}
