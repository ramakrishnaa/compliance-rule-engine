import { Component, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { ComplianceApiService } from './compliance-api.service';
import { EvaluationResponse, SEVERITIES, Severity } from './compliance.models';

type View = 'table' | 'text';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
})
export class AppComponent {
  private readonly api = inject(ComplianceApiService);

  readonly severities = SEVERITIES;

  readonly transactionsFile = signal<File | null>(null);
  readonly rulesFile = signal<File | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<EvaluationResponse | null>(null);
  readonly sourceLabel = signal('');

  readonly view = signal<View>('table');
  readonly flaggedOnly = signal(false);
  readonly severityFilter = signal<Severity | null>(null);
  readonly copied = signal(false);

  readonly canEvaluate = computed(() => !!this.transactionsFile() && !!this.rulesFile() && !this.loading());

  readonly visibleResults = computed(() => {
    const r = this.result();
    if (!r) {
      return [];
    }
    const severity = this.severityFilter();
    return r.results.filter(
      (t) =>
        (!this.flaggedOnly() || t.breachedRules.length > 0) &&
        (!severity || t.breachedRules.some((b) => b.severity === severity)),
    );
  });

  onFile(kind: 'transactions' | 'rules', event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    (kind === 'transactions' ? this.transactionsFile : this.rulesFile).set(file);
  }

  evaluateUploaded(): void {
    const tx = this.transactionsFile();
    const rules = this.rulesFile();
    if (tx && rules) {
      this.run(this.api.evaluate(tx, rules), `${tx.name} + ${rules.name}`);
    }
  }

  evaluateSample(): void {
    this.run(this.api.evaluateSample(), 'sample files from the brief');
  }

  toggleSeverity(severity: Severity): void {
    this.severityFilter.update((current) => (current === severity ? null : severity));
  }

  copyReport(): void {
    const text = this.result()?.textReport;
    if (!text || !navigator.clipboard) {
      return;
    }
    navigator.clipboard.writeText(text).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    });
  }

  downloadReport(): void {
    const text = this.result()?.textReport;
    if (!text) {
      return;
    }
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'compliance-report.txt';
    a.click();
    URL.revokeObjectURL(url);
  }

  private run(request: Observable<EvaluationResponse>, label: string): void {
    this.loading.set(true);
    this.error.set(null);
    request.subscribe({
      next: (response) => {
        this.result.set(response);
        this.sourceLabel.set(label);
        this.severityFilter.set(null);
        this.loading.set(false);
      },
      error: (e: Error) => {
        this.result.set(null);
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
