import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ComplianceApiService } from './compliance-api.service';
import { EvaluationResponse } from './compliance.models';

describe('ComplianceApiService', () => {
  let service: ComplianceApiService;
  let http: HttpTestingController;

  const response: EvaluationResponse = {
    summary: {
      transactionCount: 1,
      ruleCount: 1,
      flaggedTransactionCount: 0,
      breachesBySeverity: { LOW: 0, MEDIUM: 0, HIGH: 0, CRITICAL: 0 },
    },
    results: [{ transactionId: 'T1', breachedRules: [] }],
    issues: [],
    textReport: 'Transaction    Breached Rules\nT1    none\n',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ComplianceApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts both files as multipart parts', () => {
    const tx = new File(['{}'], 'transactions.json');
    const rules = new File(['x'], 'rules.csv');
    let received: EvaluationResponse | undefined;

    service.evaluate(tx, rules).subscribe((r) => (received = r));

    const req = http.expectOne('/api/evaluations');
    expect(req.request.method).toBe('POST');
    const body = req.request.body as FormData;
    expect((body.get('transactions') as File).name).toBe('transactions.json');
    expect((body.get('rules') as File).name).toBe('rules.csv');
    req.flush(response);
    expect(received).toEqual(response);
  });

  it('requests the sample evaluation', () => {
    service.evaluateSample().subscribe();
    const req = http.expectOne('/api/evaluations/sample');
    expect(req.request.method).toBe('GET');
    req.flush(response);
  });

  it('turns a problem detail into a readable error', () => {
    let message = '';
    service.evaluateSample().subscribe({ error: (e: Error) => (message = e.message) });
    http
      .expectOne('/api/evaluations/sample')
      .flush({ title: 'Invalid input file', detail: 'rules.csv is empty' }, { status: 400, statusText: 'Bad Request' });
    expect(message).toBe('Invalid input file: rules.csv is empty');
  });

  it('explains when the backend is unreachable', () => {
    let message = '';
    service.evaluateSample().subscribe({ error: (e: Error) => (message = e.message) });
    http.expectOne('/api/evaluations/sample').error(new ProgressEvent('error'), { status: 0 });
    expect(message).toContain('Cannot reach the backend');
  });
});
