import { HttpErrorResponse } from '@angular/common/http';

/**
 * The message the server actually sent, or a sensible fallback.
 *
 * The API answers failures with a `GlobalErrorResponse` carrying a `message`
 * written for a human. Screens were discarding it and showing their own generic
 * line instead — "Payment failed. Please try again." in place of "Online payment
 * is temporarily unavailable", which is the difference between a user retrying
 * forever and a user knowing to come back later.
 *
 * Requests made with `responseType: 'text'` receive that envelope as an
 * unparsed JSON string, so it is parsed here rather than at every call site.
 */
export function apiMessage(err: unknown, fallback: string): string {
  const body = err instanceof HttpErrorResponse ? err.error : err;

  if (typeof body === 'string') {
    const trimmed = body.trim();
    if (!trimmed) {
      return fallback;
    }
    if (trimmed.startsWith('{')) {
      try {
        return messageFrom(JSON.parse(trimmed)) ?? fallback;
      } catch {
        return fallback;
      }
    }
    // A plain-text error body is already the message.
    return trimmed;
  }

  return messageFrom(body) ?? fallback;
}

function messageFrom(body: any): string | null {
  const message = body?.message ?? body?.error ?? body?.errorMessage;
  return typeof message === 'string' && message.trim() ? message.trim() : null;
}
