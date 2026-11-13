package com.example.compliance.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.compliance.model.EvaluationReport;
import com.example.compliance.report.TextReportFormatter;
import com.example.compliance.service.ComplianceService;

@RestController
@RequestMapping("/api/evaluations")
public class ComplianceController {

    private final ComplianceService service;
    private final TextReportFormatter formatter;
    private final Path sampleTransactions;
    private final Path sampleRules;

    public ComplianceController(ComplianceService service, TextReportFormatter formatter,
            @Value("${compliance.sample.transactions-path}") String sampleTransactions,
            @Value("${compliance.sample.rules-path}") String sampleRules) {
        this.service = service;
        this.formatter = formatter;
        this.sampleTransactions = Path.of(sampleTransactions);
        this.sampleRules = Path.of(sampleRules);
    }

    /** Evaluates uploaded files (multipart parts {@code transactions} and {@code rules}). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvaluationResponse evaluate(@RequestPart("transactions") MultipartFile transactions,
            @RequestPart("rules") MultipartFile rules) throws IOException {
        try (InputStream tx = transactions.getInputStream();
                Reader rr = new InputStreamReader(rules.getInputStream(), StandardCharsets.UTF_8)) {
            return respond(service.evaluate(tx, rr));
        }
    }

    /** Evaluates the sample files shipped with the brief. */
    @GetMapping("/sample")
    public EvaluationResponse evaluateSample() throws IOException {
        try (InputStream tx = Files.newInputStream(sampleTransactions);
                Reader rr = Files.newBufferedReader(sampleRules, StandardCharsets.UTF_8)) {
            return respond(service.evaluate(tx, rr));
        } catch (NoSuchFileException e) {
            throw new SampleNotFoundException(e.getFile());
        }
    }

    private EvaluationResponse respond(EvaluationReport report) {
        return EvaluationResponse.from(report, formatter.format(report.results()));
    }

    static class SampleNotFoundException extends RuntimeException {
        SampleNotFoundException(String file) {
            super("sample file not found: " + Path.of(file).toAbsolutePath().normalize()
                    + " (configure compliance.sample.* or start the backend from the backend/ directory)");
        }
    }
}
