import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, timer } from 'rxjs';

const READINESS_LIMIT = 110_000;
const REQUEST_TIMEOUT = 10_000;
const RETRY_DELAY = 1_500;
const TEMPORARY_STATUSES = [0, 502, 503, 504];

export async function waitForReadiness(
  http: HttpClient,
  url: string,
  retryNotFound = false,
): Promise<boolean> {
  const deadline = Date.now() + READINESS_LIMIT;
  while (Date.now() < deadline) {
    try {
      await firstValueFrom(
        http.get(url, { timeout: Math.min(REQUEST_TIMEOUT, deadline - Date.now()) }),
      );
      return true;
    } catch (error) {
      if (
        !(error instanceof HttpErrorResponse) ||
        (!TEMPORARY_STATUSES.includes(error.status) && !(retryNotFound && error.status === 404))
      ) {
        return false;
      }
      const remaining = deadline - Date.now();
      if (remaining > 0) await firstValueFrom(timer(Math.min(RETRY_DELAY, remaining)));
    }
  }
  return false;
}
