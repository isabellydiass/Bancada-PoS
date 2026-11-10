import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { firstValueFrom, timer } from 'rxjs';
import { Comparison } from './comparison';
import { ComparisonResult } from './comparison-result';

type ViewState =
  | { kind: 'idle' | 'starting' }
  | { kind: 'error'; message: string }
  | { kind: 'waiting'; id: string }
  | { kind: 'paused'; id: string; message: string }
  | { kind: 'ready'; result: Comparison };

const COMPARISONS = '/api/comparisons';
const REQUEST_TIMEOUT = 5_000;
const POLL_INTERVAL = 750;
const POLL_LIMIT = 20_000;
const RETRYABLE_STATUSES = [0, 404, 502, 503, 504];

@Component({
  selector: 'app-root',
  imports: [ComparisonResult],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly http = inject(HttpClient);

  protected readonly state = signal<ViewState>({ kind: 'idle' });
  protected readonly busy = computed(() => {
    const kind = this.state().kind;
    return kind === 'starting' || kind === 'waiting';
  });

  protected async run(): Promise<void> {
    if (this.busy()) return;

    this.state.set({ kind: 'starting' });

    let id: string;
    try {
      const response = await firstValueFrom(
        this.http.post<{ id: string }>(COMPARISONS, null, { timeout: REQUEST_TIMEOUT }),
      );
      id = response.id;
    } catch {
      this.state.set({
        kind: 'error',
        message: 'Não foi possível confirmar o início da comparação. Tente executar novamente.',
      });
      return;
    }

    await this.poll(id);
  }

  protected async poll(id: string): Promise<void> {
    this.state.set({ kind: 'waiting', id });
    const deadline = Date.now() + POLL_LIMIT;
    const url = `${COMPARISONS}/${encodeURIComponent(id)}`;
    let message = 'A comparação ainda está em processamento. Consulte novamente em instantes.';

    while (Date.now() < deadline) {
      try {
        const result = await firstValueFrom(
          this.http.get<Comparison>(url, {
            timeout: Math.min(REQUEST_TIMEOUT, deadline - Date.now()),
          }),
        );
        this.state.set({ kind: 'ready', result });
        return;
      } catch (error) {
        if (!(error instanceof HttpErrorResponse) || !RETRYABLE_STATUSES.includes(error.status)) {
          message = 'Não foi possível consultar esta execução. Tente consultar novamente.';
          break;
        }

        message =
          error.status === 404
            ? 'A comparação ainda está em processamento. Consulte novamente em instantes.'
            : 'O serviço de evidências está temporariamente indisponível. Consulte novamente.';
      }

      const remaining = deadline - Date.now();
      if (remaining > 0) {
        await firstValueFrom(timer(Math.min(POLL_INTERVAL, remaining)));
      }
    }

    this.state.set({ kind: 'paused', id, message });
  }
}
