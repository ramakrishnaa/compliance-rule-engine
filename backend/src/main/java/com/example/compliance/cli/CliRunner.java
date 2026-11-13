package com.example.compliance.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import com.example.compliance.io.InputFormatException;
import com.example.compliance.model.EvaluationReport;
import com.example.compliance.report.TextReportFormatter;
import com.example.compliance.service.ComplianceService;

/**
 * Command-line mode ({@code --cli}). The report goes to stdout and diagnostics to stderr, so stdout can
 * be compared with the reference output directly.
 *
 * <p>Exit codes: 0 = success (possibly with warnings), 1 = an input file was missing or unusable.
 */
@Component
public class CliRunner implements ApplicationRunner, ExitCodeGenerator {

    static final String OPT_CLI = "cli";
    static final String OPT_TRANSACTIONS = "transactions";
    static final String OPT_RULES = "rules";

    private final ComplianceService service;
    private final TextReportFormatter formatter;
    private final PrintStream out;
    private final PrintStream err;
    private int exitCode;

    @Autowired
    public CliRunner(ComplianceService service, TextReportFormatter formatter) {
        this(service, formatter, System.out, System.err);
    }

    CliRunner(ComplianceService service, TextReportFormatter formatter, PrintStream out, PrintStream err) {
        this.service = service;
        this.formatter = formatter;
        this.out = out;
        this.err = err;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(OPT_CLI)) {
            return; // web mode
        }
        Path transactions = Path.of(option(args, OPT_TRANSACTIONS, "transactions.json"));
        Path rules = Path.of(option(args, OPT_RULES, "rules.csv"));
        exitCode = execute(transactions, rules);
    }

    int execute(Path transactions, Path rules) {
        try (InputStream tx = Files.newInputStream(transactions);
                Reader rr = Files.newBufferedReader(rules, StandardCharsets.UTF_8)) {
            EvaluationReport report = service.evaluate(tx, rr);
            out.print(formatter.format(report.results()));
            out.flush();
            report.issues().forEach(err::println);
            return 0;
        } catch (NoSuchFileException e) {
            err.println("ERROR file not found: " + e.getFile());
        } catch (InputFormatException e) {
            err.println("ERROR " + e.getMessage());
        } catch (IOException e) {
            err.println("ERROR could not read input: " + e);
        }
        return 1;
    }

    private static String option(ApplicationArguments args, String name, String fallback) {
        List<String> values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? fallback : values.get(values.size() - 1);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
