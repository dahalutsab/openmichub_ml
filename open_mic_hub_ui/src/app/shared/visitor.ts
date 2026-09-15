/**
 * This browser's discovery identity, for a visitor who has not signed in.
 *
 * A random id, generated here and kept in localStorage - not an IP address, not a fingerprint -
 * sent to the API in the `X-Visitor-Id` header. It lets the searches and profiles someone opens
 * before creating an account shape what discovery shows them next, and it is handed over to the
 * account when they sign in (`DiscoveryService.claimVisitorHistory`), then replaced, so the next
 * person to browse signed-out on this machine starts clean. The server deletes history no account
 * ever claims after 90 days.
 *
 * Storage can be unavailable - private windows, blocked site data - and then there is simply no id:
 * discovery works exactly as it does for any anonymous request.
 */

const VISITOR_KEY = 'omhVisitorId';

export const VISITOR_HEADER = 'X-Visitor-Id';

function newId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  // Older browsers: the same v4 shape from getRandomValues.
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** The id, created on first use. Null when storage cannot be used. */
export function visitorId(): string | null {
  try {
    let id = localStorage.getItem(VISITOR_KEY);
    if (!id) {
      id = newId();
      localStorage.setItem(VISITOR_KEY, id);
    }
    return id;
  } catch {
    return null;
  }
}

/** Starts a fresh id, once the old one's history belongs to an account. */
export function rotateVisitorId(): void {
  try {
    localStorage.setItem(VISITOR_KEY, newId());
  } catch {
    // Nothing stored, nothing to rotate.
  }
}
