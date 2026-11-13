package com.example.compliance.service;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.compliance.engine.Outcome;
import com.example.compliance.engine.RuleEvaluator;
import com.example.compliance.engine.RuleIdComparator;
import com.example.compliance.io.RuleCsvParser;
import com.example.compliance.io.TransactionJsonParser;
import com.example.compliance.model.BreachedRule;
import com.example.compliance.model.Diagnostics;
import com.example.compliance.model.EvaluationReport;
import com.example.compliance.model.Rule;
import com.example.compliance.model.Transaction;
import com.example.compliance.model.TransactionResult;

/** Loads both inputs, evaluates every rule against every transaction and gathers diagnostics. */
@Service
public class ComplianceService {

    private final TransactionJsonParser transactionParser;
    private final RuleCsvParser ruleParser;
    private final RuleEvaluator evaluator;

    public ComplianceService(TransactionJsonParser transactionParser, RuleCsvParser ruleParser,
            RuleEvaluator evaluator) {
        this.transactionParser = transactionParser;
        this.ruleParser = ruleParser;
        this.evaluator = evaluator;
    }

    /**
     * @throws com.example.compliance.io.InputFormatException if either file is unusable as a whole
     */
    public EvaluationReport evaluate(InputStream transactionsJson, Reader rulesCsv) {
        Diagnostics diagnostics = new Diagnostics();
        List<Rule> rules = ruleParser.parse(rulesCsv, diagnostics);
        List<Transaction> transactions = transactionParser.parse(transactionsJson, diagnostics);
        List<TransactionResult> results = evaluate(transactions, rules, diagnostics);
        return new EvaluationReport(results, diagnostics.issues(), transactions.size(), rules.size());
    }

    public List<TransactionResult> evaluate(List<Transaction> transactions, List<Rule> rules,
            Diagnostics diagnostics) {
        List<Rule> ordered = rules.stream()
                .sorted(Comparator.comparing(Rule::ruleId, RuleIdComparator.INSTANCE))
                .toList();

        // rule id -> reason -> transaction ids, so each problem is reported once rather than per row.
        Map<String, Map<String, List<String>>> skipped = new LinkedHashMap<>();
        List<TransactionResult> results = new ArrayList<>(transactions.size());
        for (Transaction tx : transactions) {
            List<BreachedRule> breached = new ArrayList<>();
            for (Rule rule : ordered) {
                Outcome outcome = evaluator.evaluate(rule, tx);
                switch (outcome.status()) {
                    case BREACHED -> breached.add(BreachedRule.of(rule));
                    case NOT_EVALUABLE -> skipped
                            .computeIfAbsent(rule.ruleId(), k -> new LinkedHashMap<>())
                            .computeIfAbsent(outcome.reason(), k -> new ArrayList<>())
                            .add(tx.transactionId());
                    case NOT_BREACHED -> {
                        // nothing to record
                    }
                }
            }
            results.add(new TransactionResult(tx.transactionId(), breached));
        }

        skipped.forEach((ruleId, byReason) -> byReason.forEach((reason, txIds) -> diagnostics.warn(
                "rule " + ruleId,
                "not evaluated (treated as not breached) for " + txIds.size() + " transaction(s) "
                        + txIds + ": " + reason)));
        return results;
    }
}
