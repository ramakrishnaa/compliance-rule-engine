# Transaction Compliance Evaluator

Reads `transactions.json` and `rules.csv` and reports which rules each transaction breaches.

- **backend/**: Spring Boot 3.5 (Java 17+). Holds all the evaluation logic. It runs either as a
  **command-line program**, which prints the report in the format the brief asks for, or as a **REST API**.
- **frontend/**: Angular 19 UI. Upload the two files (or use the sample files), then view the results
  as a filterable table or as the exact text report. The UI also lists every data warning.

Output for the provided files:

```
Transaction    Breached Rules
TXN001    R001 (HIGH), R003 (MEDIUM), R006 (MEDIUM)
TXN002    R002 (HIGH), R005 (LOW), R007 (LOW), R008 (LOW)
TXN003    R005 (LOW)
TXN004    R001 (HIGH), R002 (HIGH), R003 (MEDIUM), R006 (MEDIUM), R007 (LOW)
TXN005    R002 (HIGH), R008 (LOW)
TXN006    R005 (LOW)
TXN007    R001 (HIGH), R004 (CRITICAL), R006 (MEDIUM), R007 (LOW)
TXN008    R001 (HIGH), R002 (HIGH), R003 (MEDIUM), R006 (MEDIUM), R007 (LOW), R008 (LOW)
```

## How to run

Prerequisites: JDK 17+, Maven 3.8+, and Node 18.19+/20.11+ (only needed for the UI).

### Command line (the deliverable described in the brief)

```bash
cd backend
mvn package -DskipTests
java -jar target/compliance-evaluator.jar --cli --transactions=../transactions.json --rules=../rules.csv
```

- The report goes to **stdout** and warnings go to **stderr**, so `... 2>/dev/null > out.txt` gives a file
  you can diff against the reference.
- Both options are optional. If you leave them out, the program reads `transactions.json` and `rules.csv`
  from the current directory.
- Exit code `0` means success, even if there were warnings. Exit code `1` means an input file is missing
  or unusable as a whole.
- Lines always end in `\n`, on every OS.

### Web UI + REST API

```bash
# terminal 1
cd backend
mvn spring-boot:run                 # http://localhost:8080

# terminal 2
cd frontend
npm install
npm start                           # http://localhost:4200 (proxies /api to :8080)
```

Start the backend from `backend/`, because the **Use the sample files** button reads `../transactions.json`
and `../rules.csv`. You can change these paths with `compliance.sample.*` in `application.properties`.

| Endpoint | Description |
|---|---|
| `POST /api/evaluations` | multipart parts `transactions` (JSON) and `rules` (CSV) |
| `GET /api/evaluations/sample` | evaluates the configured sample files |

The response includes `summary`, `results` (the breached rules for each transaction, in rule_id order),
`issues` (the warnings) and `textReport` (exactly what the CLI prints). If a file is unusable, the API
returns `400` with an RFC 7807 problem detail.

## How to run the tests

```bash
cd backend  && mvn test                                        # 83 JUnit tests
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless   # 9 Jasmine/Karma tests
```

What is tested and why:

- **Golden test** (`ComplianceServiceTest.sampleFilesProduceExpectedReport`): the provided files must produce
  the report above, byte for byte. I worked out the expected output by hand from the brief, not by
  copying what the program printed.
- **Operator semantics** (`RuleEvaluatorTest`):
  - `gt`/`lt` are strict at the boundary (10000.00 is not > 10000).
  - Numbers are compared as numbers, not strings ("9" vs "10000").
  - `eq`/`neq`/`in` are case-sensitive.
  - `in` matches whole entries, not substrings.
  - `timestamp.hour` is taken in **UTC** after converting offsets (`01:30+03:00` is hour 22, not 1). It is
    also checked at the 22:59 → 23:00 boundary that decides TXN006.
- **Malformed data** (`RuleCsvParserTest`, `TransactionJsonParserTest`): each kind of unexpected input
  listed below has a test, so the behaviour is deliberate rather than accidental.
- **Ordering** (`RuleIdComparatorTest`): ascending rule_id, including unpadded ids (`R2` before `R10`).
- **Isolation** (`oneBadRowDoesNotAffectTheOthers`): a bad rule or transaction must not change the results
  for the good ones.
- **CLI and API contracts**: stdout/stderr separation, exit codes, JSON shape, and 400 responses.
- **UI**: multipart upload, error messages, filtering, and rendering the exact text report.

## Assumptions and handling of unexpected data

Guiding principle: the data comes from the customer, so **one bad row should never hide the results of the
good rows**, and **nothing is dropped or changed silently**. Each item below produces a warning that says
where the problem is (`rules.csv row 4 (R003)`, `transactions.json #3 (TXN003)`) and what the program did.
The CLI prints these warnings to stderr; the UI shows them in the warnings panel.

### Evaluation semantics

| Situation | Behaviour |
|---|---|
| `gt` / `lt` | Numeric, strict. Uses `BigDecimal`, so there are no floating-point errors. |
| `eq` / `neq` / `in` | Case-sensitive exact string match. One exception: a **numeric field** (`amount`, `timestamp.hour`) against a numeric rule value is compared as numbers, so `amount eq 10000` matches `10000.00`. |
| `in` list | Split on commas, entries trimmed, empty entries dropped. |
| `timestamp.hour` | Hour 0–23 after converting the timestamp to UTC. |
| Rules targeting other fields | Any field in the transaction JSON can be used, for example a new `channel` field. |
| Breach ordering | Ascending rule_id, using "natural" ordering (`R2` < `R10`). For zero-padded ids this is the same as plain string order. |
| Field missing/null in a transaction | The rule is **not evaluable** for that transaction and is reported as *not breached*. This applies to `neq` too: a missing currency is not counted as "not USD". You get one warning per rule listing the affected transaction ids. |

### rules.csv

| Problem | Behaviour |
|---|---|
| Missing required column, empty file, unreadable CSV | **Fatal** (exit 1 / HTTP 400). Nothing can be evaluated safely. |
| UTF-8 BOM, CRLF, blank lines, whitespace around cells, header case, column order | Tolerated silently. |
| Wrong number of columns (usually an `in` list that isn't quoted) | Row skipped. |
| Unknown operator, or `gt`/`lt` with a value that isn't a number | Rule skipped. |
| Unknown severity | Rule skipped, because a valid severity can't be printed for it. |
| Severity/operator written in different case (`high`, `GT`) | Accepted and normalised, with a warning. |
| Empty `rule_id`/`field`, or an empty `in` list | Row skipped. |
| Duplicate `rule_id` | First definition kept, later rows ignored. |

### transactions.json

| Problem | Behaviour |
|---|---|
| Invalid JSON, trailing garbage, no `transactions` array | **Fatal**. |
| Root is a bare array | Accepted as the list, with a warning. |
| Element that isn't an object | Skipped. |
| Missing/empty `transaction_id` | Still evaluated, and printed as `UNKNOWN#<position>`. |
| Non-string `transaction_id` (e.g. `42`) | Its text form is used. |
| Duplicate `transaction_id` | Both evaluated and printed, in file order. |
| `amount` given as a string | Compared numerically if it parses as a number; otherwise numeric rules are not evaluable for it. |
| Timestamp with no offset | Assumed to be UTC. |
| Timestamp that can't be parsed | Timestamp rules are not evaluable for that transaction. |
| Leading/trailing whitespace in a string value | Trimmed. |
| `currency`/`country` that doesn't look like an ISO code (`usd`, `USA`) | Flagged but **left unchanged**, because comparisons are case-sensitive by spec. |
| Nested object/array values | Ignored. |
| Extra fields | Kept, and can be used by rules. |

## Project layout

```
backend/src/main/java/com/example/compliance/
  io/        RuleCsvParser, TransactionJsonParser    validation, warnings
  engine/    RuleEvaluator, RuleIdComparator, Outcome
  service/   ComplianceService                       orchestration, aggregates "not evaluable" warnings
  report/    TextReportFormatter                     exact output format
  cli/       CliRunner                               --cli mode
  web/       ComplianceController, ApiExceptionHandler, EvaluationResponse
  model/     Rule, Transaction, FieldValue, Issue, ...
frontend/src/app/
  compliance-api.service.ts, compliance.models.ts, app.component.*
```

To add an operator, you add one enum constant in `Operator` and one `case` in `RuleEvaluator`. The
compiler then flags every `switch` that doesn't handle it.

## What I would do next with more time

- **Combined rules**: add AND/OR conditions or a rule-group column. Today each rule is a single condition.
- **Strict mode**: a `--strict` flag that turns warnings into a non-zero exit code, for pipelines that
  should fail on bad data.
- **Per-field schema/type registry**: declare that `amount` is numeric and `currency` is ISO 4217, instead
  of the current lenient checks.
- **Streaming JSON** (Jackson streaming API) for very large transaction files.
- **Serve the Angular build from Spring Boot**, so there is a single deployable jar.
- **Property-based tests** for the operators and an end-to-end Playwright test for the UI.
- **Audit metadata**: which rule-file version was used and when, with results stored for later review.

## Tools used

- Java 17 / Spring Boot 3.5.8, Apache Commons CSV, Jackson; JUnit 5, AssertJ, MockMvc.
- Angular 19 (standalone components, signals), Jasmine/Karma.
- AI assistant: Claude Code (Anthropic) was used to help write the code, tests and this README.
  <!-- TODO(author): adjust to describe how you used it. -->

## Time taken

<!-- TODO(author): fill in. -->
