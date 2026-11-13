package com.example.compliance.service;

import static com.example.compliance.TestData.rule;
import static com.example.compliance.TestData.stream;
import static com.example.compliance.TestData.tx;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.compliance.engine.RuleEvaluator;
import com.example.compliance.io.RuleCsvParser;
import com.example.compliance.io.TransactionJsonParser;
import com.example.compliance.model.BreachedRule;
import com.example.compliance.model.Diagnostics;
import com.example.compliance.model.EvaluationReport;
import com.example.compliance.model.Operator;
import com.example.compliance.model.Rule;
import com.example.compliance.model.Severity;
import com.example.compliance.model.TransactionResult;
import com.example.compliance.report.TextReportFormatter;

class ComplianceServiceTest {

    private final ComplianceService service =
            new ComplianceService(new TransactionJsonParser(), new RuleCsvParser(), new RuleEvaluator());

    /** The provided sample files must produce exactly the expected report (independently hand-checked). */
    @Test
    void sampleFilesProduceExpectedReport() throws IOException {
        EvaluationReport report;
        try (InputStream tx = resource("sample/transactions.json");
                Reader rules = new InputStreamReader(resource("sample/rules.csv"), StandardCharsets.UTF_8)) {
            report = service.evaluate(tx, rules);
        }
        String expected;
        try (InputStream in = resource("sample/expected-output.txt")) {
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }

        assertThat(new TextReportFormatter().format(report.results())).isEqualTo(expected);
        assertThat(report.issues()).isEmpty();
        assertThat(report.transactionCount()).isEqualTo(8);
        assertThat(report.ruleCount()).isEqualTo(8);
    }

    @Test
    void breachesAreListedInAscendingRuleIdRegardlessOfFileOrder() {
        List<Rule> rules = List.of(
                rule("R10", "amount", Operator.GT, "0", Severity.LOW),
                rule("R002", "amount", Operator.GT, "0", Severity.HIGH),
                rule("R001", "amount", Operator.GT, "0", Severity.MEDIUM));
        List<TransactionResult> results =
                service.evaluate(List.of(tx("T").number("amount", "5").build()), rules, new Diagnostics());

        assertThat(results.get(0).breachedRules()).extracting(BreachedRule::ruleId)
                .containsExactly("R001", "R002", "R10");
    }

    @Test
    void transactionWithNoBreachesHasEmptyList() {
        List<TransactionResult> results = service.evaluate(List.of(tx("T").number("amount", "5").build()),
                List.of(rule("R1", "amount", Operator.GT, "10")), new Diagnostics());
        assertThat(results.get(0).breachedRules()).isEmpty();
    }

    @Test
    void unevaluableRulesAreReportedOncePerRuleAndReason() {
        Diagnostics diagnostics = new Diagnostics();
        service.evaluate(
                List.of(tx("A").build(), tx("B").build(), tx("C").text("currency", "USD").build()),
                List.of(rule("R8", "currency", Operator.NEQ, "USD")), diagnostics);

        assertThat(diagnostics.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.source()).isEqualTo("rule R8");
            assertThat(issue.message()).contains("2 transaction(s) [A, B]").contains("'currency' is missing");
        });
    }

    @Test
    void oneBadRowDoesNotAffectTheOthers() {
        String rules = "rule_id,rule_name,field,operator,value,severity\n"
                + "R001,ok,amount,gt,100,HIGH\n"
                + "R002,bad,amount,between,1,HIGH\n"
                + "R003,ok,country,in,\"IR,KP\",LOW\n";
        String txs = "{\"transactions\":["
                + "{\"transaction_id\":\"T1\",\"amount\":500,\"country\":\"IR\"},"
                + "\"garbage\","
                + "{\"transaction_id\":\"T2\",\"amount\":\"n/a\",\"country\":\"US\"}]}";

        EvaluationReport report = service.evaluate(stream(txs), new java.io.StringReader(rules));

        assertThat(new TextReportFormatter().format(report.results())).isEqualTo("""
                Transaction    Breached Rules
                T1    R001 (HIGH), R003 (LOW)
                T2    none
                """);
        assertThat(report.ruleCount()).isEqualTo(2);
        assertThat(report.issues()).extracting(i -> i.message())
                .anyMatch(m -> m.contains("unknown operator 'between'"))
                .anyMatch(m -> m.contains("expected a JSON object"))
                .anyMatch(m -> m.contains("'n/a' is not numeric"));
    }

    private static InputStream resource(String path) {
        InputStream in = ComplianceServiceTest.class.getClassLoader().getResourceAsStream(path);
        assertThat(in).as(path).isNotNull();
        return in;
    }
}
