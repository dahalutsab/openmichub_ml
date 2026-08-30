/**
 * Who is signed in, as far as the browser knows.
 *
 * Browsing is public and only acting requires an account, so screens need to
 * ask this without an interceptor or a guard getting involved. The token is
 * still verified server-side on every call — this only decides what the UI
 * offers.
 */

/** The key the login flow writes. */
const TOKEN_KEY = 'authToken';
const ROLES_KEY = 'urole';

export function isSignedIn(): boolean {
  try {
    return !!localStorage.getItem(TOKEN_KEY);
  } catch {
    // Private browsing and blocked site data both throw on access.
    return false;
  }
}

export function currentRoles(): string[] {
  try {
    return JSON.parse(localStorage.getItem(ROLES_KEY) || '[]');
  } catch {
    return [];
  }
}

/** Forgets the session in this browser. The token expires server-side on its own. */
export function signOut(): void {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ROLES_KEY);
  } catch {
    // Nothing was stored to begin with if storage is unavailable.
  }
}

/** Whether this account is allowed to raise a booking. */
export function canBook(): boolean {
  const roles = currentRoles();
  return roles.includes('ORGANIZER') || roles.includes('USER');
}

/**
 * Whether this account may move money — pay out or refuse a withdrawal.
 *
 * Platform staff can read the financial records and only the owner can disburse, so the queue is
 * shown to both and the buttons only to one. The server enforces the same rule; this keeps an ADMIN
 * from being offered an action that would come back 403.
 */
export function canDisburse(): boolean {
  return currentRoles().includes('SUPER_ADMIN');
}
