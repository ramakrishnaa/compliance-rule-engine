package com.example.compliance.model;

import java.util.List;

/** The rules one transaction breaches, in ascending rule_id order. */
public record TransactionResult(String transactionId, List<BreachedRule> breachedRules) {

    public TransactionResult {
        breachedRules = List.copyOf(breachedRules);
    }
}
