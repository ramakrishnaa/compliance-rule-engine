package com.example.compliance.io;

import static com.example.compliance.TestData.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.compliance.model.Diagnostics;
import com.example.compliance.model.FieldValue;
import com.example.compliance.model.Issue;
import com.example.compliance.model.Transaction;

class TransactionJsonParserTest {

    private final TransactionJsonParser parser = new TransactionJsonParser();
    private final Diagnostics diagnostics = new Diagnostics();

    private List<Transaction> parse(String json) {
        return parser.parse(stream(json), diagnostics);
    }

    private static String wrap(String... items) {
        return "{\"transactions\":[" + String.join(",", items) + "]}";
    }

    private List<String> warnings() {
        return diagnostics.issues().stream().map(Issue::message).toList();
    }

    @Test
    void parsesAllFieldsWithExactDecimalAmounts() {
        List<Transaction> txs = parse(wrap("""
                {"transaction_id":"TXN001","timestamp":"2024-01-15T23:30:00Z","amount":15000.00,
                 "currency":"USD","country":"US","customer_id":"CUST001",
                 "account_type":"PERSONAL","transaction_type":"WIRE_TRANSFER"}"""));

        Transaction t = txs.get(0);
        assertThat(t.transactionId()).isEqualTo("TXN001");
        assertThat(t.field("amount")).get().isInstanceOfSatisfying(FieldValue.Numeric.class,
                n -> assertThat(n.value()).isEqualByComparingTo(new BigDecimal("15000")));
        assertThat(t.field("currency")).contains(new FieldValue.Text("USD"));
        assertThat(t.timestampUtc().getHour()).isEqualTo(23);
        assertThat(diagnostics.isEmpty()).isTrue();
    }

    @Test
    void preservesFileOrder() {
        List<Transaction> txs = parse(wrap("{\"transaction_id\":\"B\"}", "{\"transaction_id\":\"A\"}"));
        assertThat(txs).extracting(Transaction::transactionId).containsExactly("B", "A");
    }

    @Test
    void timestampWithOffsetIsConvertedToUtc() {
        Transaction t = parse(wrap("{\"transaction_id\":\"T\",\"timestamp\":\"2024-01-16T01:30:00+03:00\"}")).get(0);
        assertThat(t.timestampUtc().getHour()).isEqualTo(22);
        assertThat(t.timestampUtc().getDayOfMonth()).isEqualTo(15);
    }

    @Test
    void timestampWithoutOffsetIsAssumedUtc() {
        Transaction t = parse(wrap("{\"transaction_id\":\"T\",\"timestamp\":\"2024-01-15T23:30:00\"}")).get(0);
        assertThat(t.timestampUtc().getHour()).isEqualTo(23);
        assertThat(warnings()).anyMatch(m -> m.contains("no UTC offset"));
    }

    @Test
    void invalidTimestampIsKeptAsNullWithWarning() {
        Transaction t = parse(wrap("{\"transaction_id\":\"T\",\"timestamp\":\"15/01/2024 23:30\"}")).get(0);
        assertThat(t.timestampUtc()).isNull();
        assertThat(warnings()).anyMatch(m -> m.contains("not an ISO 8601"));
    }

    @Test
    void missingIdGetsPlaceholderAndIsStillEvaluated() {
        List<Transaction> txs = parse(wrap("{\"amount\":1}", "{\"transaction_id\":\"  \"}"));
        assertThat(txs).extracting(Transaction::transactionId).containsExactly("UNKNOWN#1", "UNKNOWN#2");
        assertThat(warnings()).filteredOn(m -> m.contains("missing or empty transaction_id")).hasSize(2);
    }

    @Test
    void numericIdIsAcceptedAsText() {
        assertThat(parse(wrap("{\"transaction_id\":42}")).get(0).transactionId()).isEqualTo("42");
        assertThat(warnings()).anyMatch(m -> m.contains("using its text form"));
    }

    @Test
    void duplicateIdsAreBothKept() {
        List<Transaction> txs = parse(wrap("{\"transaction_id\":\"T\"}", "{\"transaction_id\":\"T\"}"));
        assertThat(txs).hasSize(2);
        assertThat(warnings()).anyMatch(m -> m.contains("duplicate transaction_id"));
    }

    @Test
    void nonObjectElementsAreSkipped() {
        List<Transaction> txs = parse(wrap("42", "null", "{\"transaction_id\":\"T\"}"));
        assertThat(txs).extracting(Transaction::transactionId).containsExactly("T");
        assertThat(txs.get(0).position()).isEqualTo(3);
        assertThat(warnings()).filteredOn(m -> m.contains("expected a JSON object")).hasSize(2);
    }

    @Test
    void nullFieldsAreTreatedAsAbsent() {
        Transaction t = parse(wrap("{\"transaction_id\":\"T\",\"currency\":null}")).get(0);
        assertThat(t.field("currency")).isEmpty();
    }

    @Test
    void stringAmountIsKeptAndFlagged() {
        Transaction t = parse(wrap("{\"transaction_id\":\"T\",\"amount\":\"15000\"}")).get(0);
        assertThat(t.field("amount")).contains(new FieldValue.Text("15000"));
        assertThat(warnings()).anyMatch(m -> m.contains("amount is a string") && m.contains("compared numerically"));
    }

    @Test
    void whitespaceAroundStringsIsTrimmedWithWarning() {
        Transaction t = parse(wrap("{\"transaction_id\":\"T\",\"country\":\" IR \"}")).get(0);
        assertThat(t.field("country")).contains(new FieldValue.Text("IR"));
        assertThat(warnings()).anyMatch(m -> m.contains("whitespace"));
    }

    @Test
    void suspiciousCodesAreFlaggedButNotChanged() {
        Transaction t = parse(wrap("{\"transaction_id\":\"T\",\"currency\":\"usd\",\"country\":\"USA\"}")).get(0);
        assertThat(t.field("currency")).contains(new FieldValue.Text("usd"));
        assertThat(warnings()).anyMatch(m -> m.startsWith("currency 'usd'"));
        assertThat(warnings()).anyMatch(m -> m.startsWith("country 'USA'"));
    }

    @Test
    void nestedValuesAreIgnored() {
        Transaction t = parse(wrap("{\"transaction_id\":\"T\",\"meta\":{\"a\":1}}")).get(0);
        assertThat(t.field("meta")).isEmpty();
        assertThat(warnings()).anyMatch(m -> m.contains("not a scalar"));
    }

    @Test
    void bareArrayRootIsAcceptedWithWarning() {
        assertThat(parse("[{\"transaction_id\":\"T\"}]")).hasSize(1);
        assertThat(warnings()).anyMatch(m -> m.contains("bare array"));
    }

    @Test
    void invalidJsonIsFatal() {
        assertThatThrownBy(() -> parse("{\"transactions\": [")).isInstanceOf(InputFormatException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void trailingGarbageIsFatal() {
        assertThatThrownBy(() -> parse(wrap() + " }")).isInstanceOf(InputFormatException.class);
    }

    @Test
    void missingTransactionsArrayIsFatal() {
        assertThatThrownBy(() -> parse("{\"txns\":[]}")).isInstanceOf(InputFormatException.class)
                .hasMessageContaining("'transactions' array");
    }

    @Test
    void emptyInputIsFatal() {
        assertThatThrownBy(() -> parse("")).isInstanceOf(InputFormatException.class);
    }

    @Test
    void emptyArrayGivesWarning() {
        assertThat(parse(wrap())).isEmpty();
        assertThat(warnings()).contains("no transactions found");
    }
}
