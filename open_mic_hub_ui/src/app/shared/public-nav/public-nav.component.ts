import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Role } from '../role';
import { currentRoles, isSignedIn, signOut as clearSession } from '../session';

/**
 * The bar across the top of every page a signed-out visitor can reach.
 *
 * Discovery and the public artist profiles had no navigation at all. Both are reachable straight
 * from a search engine or a shared link, so someone could land on one with no way back to the
 * site, no way to sign in, and no indication they were already signed in — a dead end reached by
 * the two routes most likely to be someone's first visit.
 *
 * It knows whether there is a session, because the useful action differs: a visitor needs a way to
 * sign in, and someone already signed in needs their own area rather than an invitation to sign in
 * again. That comes from the shared session helper, which is what the artist profile already uses
 * to decide whether to offer a booking button. It decides what to draw, never what anyone is
 * allowed to do — the API settles that on every request.
 */
@Component({
  selector: 'omh-public-nav',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './public-nav.component.html',
})
export class PublicNavComponent implements OnInit {
  private readonly router = inject(Router);

  signedIn = false;
  homeLink = '/';

  ngOnInit(): void {
    this.signedIn = isSignedIn();
    this.homeLink = this.signedIn ? this.dashboardFor(currentRoles()) : '/';
  }

  signOut(): void {
    // Aliased on import: a bare `signOut()` here reads as a recursive call even though it is not.
    clearSession();
    this.signedIn = false;
    this.homeLink = '/';
    this.router.navigate(['/']);
  }

  /** Mirrors the routing the sign-in page uses, so "My area" lands where signing in would. */
  private dashboardFor(roles: string[]): string {
    if (roles.includes(Role.SUPER_ADMIN) || roles.includes(Role.ADMIN)) return '/admin';
    if (roles.includes(Role.ARTIST)) return '/artist/';
    if (roles.includes(Role.ORGANIZER) || roles.includes(Role.USER)) return '/user';
    return '/';
  }
}
