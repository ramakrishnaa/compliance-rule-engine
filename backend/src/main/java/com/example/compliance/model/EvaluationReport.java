package com.example.compliance.model;

import java.util.List;

/** Everything produced by one evaluation run. */
public record EvaluationReport(
        List<TransactionResult> results,
        List<Issue> issues,
        int transactionCount,
        int ruleCount) {

    public EvaluationReport {
        results = List.copyOf(results);
        issues = List.copyOf(issues);
    }
}
