import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AppComponent } from './app.component';
import { ComplianceApiService } from './compliance-api.service';
import { EvaluationResponse } from './compliance.models';

describe('AppComponent', () => {
  const response: EvaluationResponse = {
    summary: {
      transactionCount: 2,
      ruleCount: 2,
      flaggedTransactionCount: 1,
      breachesBySeverity: { LOW: 0, MEDIUM: 1, HIGH: 1, CRITICAL: 0 },
    },
    results: [
      {
        transactionId: 'TXN001',
        breachedRules: [
          { ruleId: 'R001', ruleName: 'large_transaction', severity: 'HIGH' },
          { ruleId: 'R003', ruleName: 'after_hours', severity: 'MEDIUM' },
        ],
      },
      { transactionId: 'TXN002', breachedRules: [] },
    ],
    issues: [{ level: 'WARNING', source: 'rules.csv row 9 (R009)', message: "unknown operator 'gte'; rule skipped" }],
    textReport: 'Transaction    Breached Rules\nTXN001    R001 (HIGH), R003 (MEDIUM)\nTXN002    none\n',
  };

  let api: jasmine.SpyObj<ComplianceApiService>;

  beforeEach(async () => {
    api = jasmine.createSpyObj<ComplianceApiService>('ComplianceApiService', ['evaluate', 'evaluateSample']);
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [{ provide: ComplianceApiService, useValue: api }],
    }).compileComponents();
  });

  function render() {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('disables Evaluate until both files are chosen', () => {
    const { fixture, el } = render();
    const button = el.querySelector<HTMLButtonElement>('[data-testid="evaluate-btn"]')!;
    expect(button.disabled).toBeTrue();

    fixture.componentInstance.transactionsFile.set(new File(['{}'], 't.json'));
    fixture.detectChanges();
    expect(button.disabled).toBeTrue();

    fixture.componentInstance.rulesFile.set(new File(['x'], 'r.csv'));
    fixture.detectChanges();
    expect(button.disabled).toBeFalse();
  });

  it('renders results, "none" rows and warnings', () => {
    api.evaluateSample.and.returnValue(of(response));
    const { fixture, el } = render();

    el.querySelector<HTMLButtonElement>('[data-testid="sample-btn"]')!.click();
    fixture.detectChanges();

    const rows = el.querySelectorAll('[data-testid="results-table"] tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('TXN001');
    expect(rows[0].querySelectorAll('.badge').length).toBe(2);
    expect(rows[1].textContent).toContain('none');
    expect(el.querySelector('[data-testid="flagged-count"]')!.textContent!.trim()).toBe('1');
    expect(el.querySelector('[data-testid="issues"]')!.textContent).toContain("unknown operator 'gte'");
  });

  it('filters to transactions with breaches of the selected severity', () => {
    api.evaluateSample.and.returnValue(of(response));
    const { fixture } = render();
    const app = fixture.componentInstance;
    app.evaluateSample();

    app.flaggedOnly.set(true);
    expect(app.visibleResults().map((r) => r.transactionId)).toEqual(['TXN001']);

    app.flaggedOnly.set(false);
    app.toggleSeverity('CRITICAL');
    expect(app.visibleResults()).toEqual([]);
    app.toggleSeverity('CRITICAL');
    expect(app.visibleResults().length).toBe(2);
  });

  it('shows the exact text report', () => {
    api.evaluateSample.and.returnValue(of(response));
    const { fixture, el } = render();
    fixture.componentInstance.evaluateSample();
    fixture.detectChanges();

    el.querySelector<HTMLButtonElement>('[data-testid="text-tab"]')!.click();
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="text-report"]')!.textContent).toBe(response.textReport);
  });

  it('shows backend errors', () => {
    api.evaluateSample.and.returnValue(throwError(() => new Error('Invalid input file: bad JSON')));
    const { fixture, el } = render();
    fixture.componentInstance.evaluateSample();
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="error"]')!.textContent).toContain('bad JSON');
    expect(el.querySelector('[data-testid="results-table"]')).toBeNull();
  });
});
