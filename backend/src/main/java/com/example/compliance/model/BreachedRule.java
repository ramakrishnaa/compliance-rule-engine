package com.example.compliance.model;

public record BreachedRule(String ruleId, String ruleName, Severity severity) {

    public static BreachedRule of(Rule rule) {
        return new BreachedRule(rule.ruleId(), rule.ruleName(), rule.severity());
    }
}
