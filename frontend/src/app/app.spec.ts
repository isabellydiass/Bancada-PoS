import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './app';
import { Comparison } from './comparison';

const api = '/api/comparisons';
const id = 'run-001';
const result: Comparison = {
  id,
  createdAt: '2026-11-10T14:00:00Z',
  amountCents: 4290,
  firstDifference: 3,
  baseline: {
    firmware: '1.4.0',
    verdict: 'PASS',
    authorizationIds: ['auth-01'],
    steps: [{ order: 3, code: 'REUSED', description: 'Autorização original recuperada.' }],
  },
  candidate: {
    firmware: '1.5.0',
    verdict: 'REGRESSION',
    authorizationIds: ['auth-01', 'auth-02'],
    steps: [{ order: 3, code: 'DUPLICATE', description: 'Nova autorização registrada.' }],
  },
};

describe('Comparison workflow', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(App);
    page = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    http.verify();
  });

  function click(label: string): void {
    const button = [...page.querySelectorAll('button')].find(
      (item) => item.textContent.trim() === label,
    );
    expect(button).toBeDefined();
    button!.click();
  }

  async function start(): Promise<void> {
    click('Executar comparação');
    http.expectOne({ method: 'GET', url: '/api/readiness' }).flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    await Promise.resolve();
    await Promise.resolve();
    http
      .expectOne({ method: 'POST', url: api })
      .flush({ id }, { status: 202, statusText: 'Accepted' });
    await Promise.resolve();
  }

  it('waits for the cloud services before posting a comparison', async () => {
    vi.useFakeTimers();
    click('Executar comparação');
    http.expectOne('/api/readiness').flush(null, { status: 503, statusText: 'Unavailable' });
    http.expectNone(api);
    await vi.advanceTimersByTimeAsync(1_500);
    http.expectOne('/api/readiness').flush(null, { status: 204, statusText: 'No Content' });
    await Promise.resolve();
    await Promise.resolve();
    http.expectOne({ method: 'POST', url: api }).flush({ id }, {
      status: 202,
      statusText: 'Accepted',
    });
    await Promise.resolve();
    http.expectOne(`${api}/${id}`).flush(result);
    await Promise.resolve();
  });

  it('keeps the execution through a pending result and a temporary outage', async () => {
    vi.useFakeTimers();
    await start();
    http.expectOne({ method: 'GET', url: `${api}/${id}` }).flush(null, {
      status: 404,
      statusText: 'Not Found',
    });
    await vi.advanceTimersByTimeAsync(750);
    http.expectOne({ method: 'GET', url: `${api}/${id}` }).flush(null, {
      status: 502,
      statusText: 'Bad Gateway',
    });
    await vi.advanceTimersByTimeAsync(750);
    http.expectOne({ method: 'GET', url: `${api}/${id}` }).flush(result);
    await Promise.resolve();
    fixture.detectChanges();

    expect(page.textContent).toContain('Primeira divergência na etapa 3');
    expect(page.querySelector('tr.difference')?.textContent).toContain(
      'Nova autorização registrada.',
    );
    expect(page.textContent).toContain('42,90');
  });

  it('pauses a long poll and reuses the same id on manual retry', async () => {
    vi.useFakeTimers();
    await start();
    http.expectOne({ method: 'GET', url: `${api}/${id}` }).flush(null, {
      status: 404,
      statusText: 'Not Found',
    });
    await Promise.resolve();
    vi.setSystemTime(Date.now() + 20_000);
    await vi.advanceTimersByTimeAsync(750);
    fixture.detectChanges();

    expect(page.textContent).toContain('Consulta pausada');
    click('Consultar novamente');
    http.expectOne({ method: 'GET', url: `${api}/${id}` }).flush(result);
    http.expectNone({ method: 'POST', url: api });
    await Promise.resolve();
    fixture.detectChanges();
    expect(page.textContent).toContain('Evidências da comparação');
  });

  it('distinguishes a save failure from a download failure and avoids saving twice', async () => {
    await start();
    http.expectOne({ method: 'GET', url: `${api}/${id}` }).flush(result);
    await Promise.resolve();
    fixture.detectChanges();

    const report = `${api}/${id}/report`;
    click('Exportar relatório');
    http.expectOne({ method: 'POST', url: report }).flush(null, {
      status: 503,
      statusText: 'Unavailable',
    });
    await Promise.resolve();
    fixture.detectChanges();
    expect(page.textContent).toContain('Não foi possível salvar o relatório.');

    click('Exportar relatório');
    http.expectOne({ method: 'POST', url: report }).flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    await Promise.resolve();
    http.expectOne({ method: 'GET', url: report }).flush(null, {
      status: 503,
      statusText: 'Unavailable',
    });
    await Promise.resolve();
    fixture.detectChanges();
    expect(page.textContent).toContain('O relatório foi salvo, mas o download falhou.');

    click('Exportar relatório');
    http.expectNone({ method: 'POST', url: report });
    const download = http.expectOne({ method: 'GET', url: report });
    expect(download.request.responseType).toBe('blob');
    vi.useFakeTimers();
    const revoke = vi.fn();
    vi.stubGlobal(
      'URL',
      class extends URL {
        static override createObjectURL = vi.fn(() => 'blob:report');
        static override revokeObjectURL = revoke;
      },
    );
    const anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      expect(this.download).toBe('comparacao-run-001.json');
    });
    download.flush(new Blob(['{}'], { type: 'application/json' }));
    await Promise.resolve();
    fixture.detectChanges();
    expect(anchorClick).toHaveBeenCalledOnce();
    expect(page.textContent).toContain('Download do relatório iniciado.');
    await vi.advanceTimersByTimeAsync(1_000);
    expect(revoke).toHaveBeenCalledWith('blob:report');
  });
});
