package com.example.compliance.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.compliance.engine.RuleEvaluator;
import com.example.compliance.io.RuleCsvParser;
import com.example.compliance.io.TransactionJsonParser;
import com.example.compliance.report.TextReportFormatter;
import com.example.compliance.service.ComplianceService;

class CliRunnerTest {

    @TempDir
    Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final CliRunner runner = new CliRunner(
            new ComplianceService(new TransactionJsonParser(), new RuleCsvParser(), new RuleEvaluator()),
            new TextReportFormatter(),
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

    @Test
    void reportGoesToStdoutAndWarningsToStderr() throws IOException {
        Path tx = Files.writeString(dir.resolve("t.json"),
                "{\"transactions\":[{\"transaction_id\":\"T1\",\"amount\":5}]}");
        Path rules = Files.writeString(dir.resolve("r.csv"),
                "rule_id,rule_name,field,operator,value,severity\nR1,n,amount,lt,10,LOW\nR2,n,amount,zz,1,LOW\n");

        int code = runner.execute(tx, rules);

        assertThat(code).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("Transaction    Breached Rules\nT1    R1 (LOW)\n");
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("WARNING").contains("unknown operator 'zz'");
    }

    @Test
    void missingFileExitsWithError() {
        int code = runner.execute(dir.resolve("nope.json"), dir.resolve("nope.csv"));
        assertThat(code).isEqualTo(1);
        assertThat(out.size()).isZero();
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("file not found");
    }

    @Test
    void malformedFileExitsWithError() throws IOException {
        Path tx = Files.writeString(dir.resolve("t.json"), "not json");
        Path rules = Files.writeString(dir.resolve("r.csv"), "rule_id,rule_name,field,operator,value,severity\n");
        assertThat(runner.execute(tx, rules)).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("not valid JSON");
    }
}
