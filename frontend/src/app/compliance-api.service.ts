import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';

import { EvaluationResponse, ProblemDetail } from './compliance.models';

@Injectable({ providedIn: 'root' })
export class ComplianceApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/evaluations';

  /** Uploads both customer files and returns the evaluation. */
  evaluate(transactions: File, rules: File): Observable<EvaluationResponse> {
    const form = new FormData();
    form.append('transactions', transactions, transactions.name);
    form.append('rules', rules, rules.name);
    return this.http
      .post<EvaluationResponse>(this.baseUrl, form)
      .pipe(catchError((e) => throwError(() => new Error(toMessage(e)))));
  }

  /** Evaluates the sample files configured on the server. */
  evaluateSample(): Observable<EvaluationResponse> {
    return this.http
      .get<EvaluationResponse>(`${this.baseUrl}/sample`)
      .pipe(catchError((e) => throwError(() => new Error(toMessage(e)))));
  }
}

export function toMessage(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return 'Cannot reach the backend. Is it running on http://localhost:8080?';
  }
  const problem = error.error as ProblemDetail | null;
  if (problem && typeof problem === 'object' && (problem.detail || problem.title)) {
    return [problem.title, problem.detail].filter(Boolean).join(': ');
  }
  return `Request failed (${error.status} ${error.statusText})`;
}
