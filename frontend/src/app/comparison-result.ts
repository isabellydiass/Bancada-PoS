import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Comparison, Step } from './comparison';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

@Component({
  selector: 'app-comparison-result',
  templateUrl: './comparison-result.html',
  styleUrl: './comparison-result.css',
})
export class ComparisonResult {
  private readonly http = inject(HttpClient);
  private reportSaved = false;

  readonly comparison = input.required<Comparison>();
  protected readonly exporting = signal(false);
  protected readonly exportFeedback = signal<{ error: boolean; message: string } | null>(null);
  protected readonly formattedAmount = computed(() =>
    currency.format(this.comparison().amountCents / 100),
  );
  protected readonly formattedDate = computed(() =>
    dateTime.format(new Date(this.comparison().createdAt)),
  );
  protected readonly rows = computed(() => {
    const { baseline, candidate } = this.comparison();
    const baselineSteps = new Map(baseline.steps.map((step) => [step.order, step]));
    const candidateSteps = new Map(candidate.steps.map((step) => [step.order, step]));
    const orders = new Set([...baselineSteps.keys(), ...candidateSteps.keys()]);

    return [...orders]
      .sort((first, second) => first - second)
      .map((order) => ({
        order,
        steps: [baselineSteps.get(order), candidateSteps.get(order)] as (Step | undefined)[],
      }));
  });

  protected async exportReport(): Promise<void> {
    if (this.exporting()) return;

    const id = this.comparison().id;
    const url = `/api/comparisons/${encodeURIComponent(id)}/report`;
    this.exporting.set(true);
    this.exportFeedback.set(null);

    try {
      if (!this.reportSaved) {
        await firstValueFrom(this.http.post(url, null, { timeout: 10_000 }));
        this.reportSaved = true;
      }

      const report = await firstValueFrom(
        this.http.get(url, { responseType: 'blob', timeout: 10_000 }),
      );
      const objectUrl = URL.createObjectURL(report);
      const download = document.createElement('a');
      download.href = objectUrl;
      download.download = `comparacao-${id}.json`;
      document.body.append(download);
      download.click();
      download.remove();
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1_000);
      this.exportFeedback.set({ error: false, message: 'Download do relatório iniciado.' });
    } catch {
      this.exportFeedback.set({
        error: true,
        message: this.reportSaved
          ? 'O relatório foi salvo, mas o download falhou. Tente exportar novamente.'
          : 'Não foi possível salvar o relatório. Tente exportar novamente.',
      });
    } finally {
      this.exporting.set(false);
    }
  }
}
