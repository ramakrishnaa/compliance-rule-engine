package com.example.compliance.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.compliance.model.Diagnostics;
import com.example.compliance.model.Issue;
import com.example.compliance.model.Operator;
import com.example.compliance.model.Rule;
import com.example.compliance.model.Severity;

class RuleCsvParserTest {

    private static final String HEADER = "rule_id,rule_name,field,operator,value,severity\n";

    private final RuleCsvParser parser = new RuleCsvParser();
    private final Diagnostics diagnostics = new Diagnostics();

    private List<Rule> parse(String csv) {
        return parser.parse(new StringReader(csv), diagnostics);
    }

    private List<String> warnings() {
        return diagnostics.issues().stream().map(Issue::message).toList();
    }

    @Test
    void parsesValidRules() {
        List<Rule> rules = parse(HEADER
                + "R001,large_transaction,amount,gt,10000,HIGH\n"
                + "R002,high_risk_country,country,in,\"IR,KP,SY\",HIGH\n");

        assertThat(rules).hasSize(2);
        Rule r1 = rules.get(0);
        assertThat(r1.ruleId()).isEqualTo("R001");
        assertThat(r1.operator()).isEqualTo(Operator.GT);
        assertThat(r1.numericValue()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(r1.severity()).isEqualTo(Severity.HIGH);

        assertThat(rules.get(1).listValues()).containsExactly("IR", "KP", "SY");
        assertThat(diagnostics.isEmpty()).isTrue();
    }

    @Test
    void inListEntriesAreTrimmedAndEmptiesDropped() {
        List<Rule> rules = parse(HEADER + "R1,n,country,in,\" IR , KP,,\",LOW\n");
        assertThat(rules.get(0).listValues()).containsExactly("IR", "KP");
    }

    @Test
    void unquotedCommaInValueSkipsRowWithWarning() {
        List<Rule> rules = parse(HEADER
                + "R1,n,country,in,IR,KP,SY,HIGH\n"
                + "R2,n,amount,gt,5,LOW\n");
        assertThat(rules).extracting(Rule::ruleId).containsExactly("R2");
        assertThat(warnings()).anyMatch(m -> m.contains("expected 6 columns but found 8"));
    }

    @Test
    void unknownOperatorSkipsRule() {
        List<Rule> rules = parse(HEADER + "R1,n,amount,gte,5,LOW\n");
        assertThat(rules).isEmpty();
        assertThat(warnings()).anyMatch(m -> m.contains("unknown operator 'gte'"));
    }

    @Test
    void numericOperatorWithNonNumericValueSkipsRule() {
        List<Rule> rules = parse(HEADER + "R1,n,amount,gt,ten thousand,LOW\n");
        assertThat(rules).isEmpty();
        assertThat(warnings()).anyMatch(m -> m.contains("needs a numeric value"));
    }

    @Test
    void unknownSeveritySkipsRule() {
        List<Rule> rules = parse(HEADER + "R1,n,amount,gt,5,URGENT\n");
        assertThat(rules).isEmpty();
        assertThat(warnings()).anyMatch(m -> m.contains("unknown severity 'URGENT'"));
    }

    @Test
    void caseOfSeverityAndOperatorIsNormalised() {
        List<Rule> rules = parse(HEADER + "R1,n,amount,GT,5,high\n");
        assertThat(rules).singleElement().satisfies(r -> {
            assertThat(r.operator()).isEqualTo(Operator.GT);
            assertThat(r.severity()).isEqualTo(Severity.HIGH);
        });
        assertThat(warnings()).anyMatch(m -> m.contains("normalised to HIGH"));
    }

    @Test
    void duplicateRuleIdKeepsFirst() {
        List<Rule> rules = parse(HEADER
                + "R1,first,amount,gt,5,LOW\n"
                + "R1,second,amount,gt,6,LOW\n");
        assertThat(rules).singleElement().extracting(Rule::ruleName).isEqualTo("first");
        assertThat(warnings()).anyMatch(m -> m.contains("duplicate rule_id 'R1'"));
    }

    @Test
    void emptyRuleIdOrFieldSkipsRow() {
        List<Rule> rules = parse(HEADER
                + ",n,amount,gt,5,LOW\n"
                + "R2,n,,eq,X,LOW\n");
        assertThat(rules).isEmpty();
        assertThat(warnings()).hasSize(2);
    }

    @Test
    void emptyInListSkipsRule() {
        assertThat(parse(HEADER + "R1,n,country,in,\"\",LOW\n")).isEmpty();
        assertThat(warnings()).anyMatch(m -> m.contains("empty value list"));
    }

    @Test
    void toleratesBomBlankLinesWhitespaceCrlfAndHeaderCase() {
        String csv = "﻿RULE_ID, Rule_Name ,field,operator,value,severity\r\n"
                + "\r\n"
                + " R1 , n , amount , gt , 5 , LOW \r\n";
        List<Rule> rules = parse(csv);
        assertThat(rules).singleElement().satisfies(r -> {
            assertThat(r.ruleId()).isEqualTo("R1");
            assertThat(r.field()).isEqualTo("amount");
        });
        assertThat(diagnostics.isEmpty()).isTrue();
    }

    @Test
    void columnsMayBeInAnyOrder() {
        List<Rule> rules = parse("severity,value,operator,field,rule_name,rule_id\nLOW,5,gt,amount,n,R1\n");
        assertThat(rules).singleElement().extracting(Rule::ruleId).isEqualTo("R1");
    }

    @Test
    void missingRequiredColumnIsFatal() {
        assertThatThrownBy(() -> parse("rule_id,rule_name,field,operator,value\nR1,n,amount,gt,5\n"))
                .isInstanceOf(InputFormatException.class)
                .hasMessageContaining("[severity]");
    }

    @Test
    void emptyFileIsFatal() {
        assertThatThrownBy(() -> parse("  \n")).isInstanceOf(InputFormatException.class);
    }

    @Test
    void headerOnlyGivesNoRules() {
        assertThat(parse(HEADER)).isEmpty();
    }
}
