/** Mirrors com.example.compliance.web.EvaluationResponse on the backend. */

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export const SEVERITIES: readonly Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

export interface BreachedRule {
  ruleId: string;
  ruleName: string;
  severity: Severity;
}

export interface TransactionResult {
  transactionId: string;
  breachedRules: BreachedRule[];
}

export interface Issue {
  level: 'WARNING' | 'ERROR';
  source: string;
  message: string;
}

export interface EvaluationSummary {
  transactionCount: number;
  ruleCount: number;
  flaggedTransactionCount: number;
  breachesBySeverity: Record<Severity, number>;
}

export interface EvaluationResponse {
  summary: EvaluationSummary;
  results: TransactionResult[];
  issues: Issue[];
  textReport: string;
}

/** RFC 7807 problem detail returned by the backend on errors. */
export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
}
