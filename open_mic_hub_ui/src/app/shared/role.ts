/**
 * Roles the API issues, and the only strings the guards and navigation match on.
 *
 * These have to track the backend's UserRole enum exactly. When ADMIN was split
 * into SUPER_ADMIN and ADMIN server-side, this file was not updated, so an
 * administrator signed in successfully and was then bounced out of /admin by the
 * route guard — a token with the right access and a UI that did not recognise it.
 */
export enum Role {
  /** Platform owner: payouts, refunds, role assignment, configuration. */
  SUPER_ADMIN = 'SUPER_ADMIN',
  /** Platform staff: moderation and read-only access to financial records. */
  ADMIN = 'ADMIN',
  /** Performer: profile, availability, posts, earnings. */
  ARTIST = 'ARTIST',
  /** Books and pays for artists. */
  ORGANIZER = 'ORGANIZER',
  /** Audience: browses artists, reviews their own bookings. */
  USER = 'USER',
}

/** Both administrative tiers, for routes either may reach. */
export const ADMIN_ROLES: Role[] = [Role.SUPER_ADMIN, Role.ADMIN];

/** Everyone who books: the organizer area serves both. */
export const BOOKER_ROLES: Role[] = [Role.ORGANIZER, Role.USER];
