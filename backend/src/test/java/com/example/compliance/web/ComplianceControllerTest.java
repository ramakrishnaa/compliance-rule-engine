package com.example.compliance.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "compliance.sample.transactions-path=src/test/resources/sample/../sample/transactions.json",
        "compliance.sample.rules-path=src/test/resources/sample/rules.csv"})
@AutoConfigureMockMvc
class ComplianceControllerTest {

    private static final String RULES = "rule_id,rule_name,field,operator,value,severity\n"
            + "R001,large_transaction,amount,gt,10000,HIGH\n";

    @Autowired
    MockMvc mvc;

    private static MockMultipartFile file(String part, String content) {
        return new MockMultipartFile(part, part, null, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void evaluatesUploadedFiles() throws Exception {
        mvc.perform(multipart("/api/evaluations")
                        .file(file("transactions", "{\"transactions\":[{\"transaction_id\":\"T1\",\"amount\":20000}]}"))
                        .file(file("rules", RULES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.transactionCount").value(1))
                .andExpect(jsonPath("$.summary.flaggedTransactionCount").value(1))
                .andExpect(jsonPath("$.summary.breachesBySeverity.HIGH").value(1))
                .andExpect(jsonPath("$.results[0].transactionId").value("T1"))
                .andExpect(jsonPath("$.results[0].breachedRules[0].ruleId").value("R001"))
                .andExpect(jsonPath("$.results[0].breachedRules[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.textReport").value("Transaction    Breached Rules\nT1    R001 (HIGH)\n"))
                .andExpect(jsonPath("$.issues").isEmpty());
    }

    @Test
    void malformedJsonGives400ProblemDetail() throws Exception {
        mvc.perform(multipart("/api/evaluations")
                        .file(file("transactions", "{oops"))
                        .file(file("rules", RULES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid input file"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("not valid JSON")));
    }

    @Test
    void missingPartGives400() throws Exception {
        mvc.perform(multipart("/api/evaluations").file(file("rules", RULES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing file"));
    }

    @Test
    void sampleEndpointEvaluatesBundledFiles() throws Exception {
        mvc.perform(get("/api/evaluations/sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.transactionCount").value(8))
                .andExpect(jsonPath("$.summary.breachesBySeverity.CRITICAL").value(1))
                .andExpect(jsonPath("$.results[5].transactionId").value("TXN006"))
                .andExpect(jsonPath("$.results[5].breachedRules.length()").value(1));
    }
}
