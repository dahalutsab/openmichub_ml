import { Component, OnInit, inject } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { Role } from '../../shared/role';
import { ToastrService } from 'ngx-toastr';
import { DiscoveryService } from '../../discovery/discovery.service';

/**
 * Where the browser lands after signing in with Google or Facebook.
 *
 * The backend finishes the handshake and sends the token here in the URL *fragment* rather than
 * the query string: a fragment is never transmitted to a server, so the token stays out of access
 * logs, out of any proxy in between, and out of the Referer header of the next request. It is read
 * once, stored, and wiped from the address bar so it does not sit in browser history.
 */
@Component({
  selector: 'app-social',
  standalone: false,
  templateUrl: './social.component.html',
})
export class SocialComponent implements OnInit {
  error: string | null = null;

  private readonly discovery = inject(DiscoveryService);

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private toast: ToastrService,
  ) {}

  ngOnInit(): void {
    const fragment = new URLSearchParams(window.location.hash.replace(/^#/, ''));

    const failure = fragment.get('error');
    if (failure) {
      this.fail(failure);
      return;
    }

    const token = fragment.get('token');
    const roles = (fragment.get('roles') ?? '').split(',').filter(Boolean);
    if (!token || roles.length === 0) {
      this.fail('Sign-in did not complete. Please try again.');
      return;
    }

    localStorage.setItem('authToken', token);
    localStorage.setItem('urole', JSON.stringify(roles));

    // Drop the fragment before navigating, so the token is not left in history.
    history.replaceState(null, '', window.location.pathname);

    // Carry over what this browser did while signed out. Not awaited: onboarding and the dashboard
    // do not depend on it, and the move is idempotent if it lands a moment later.
    this.discovery.claimVisitorHistory();

    // A first sign-in has an account but has not said what it is for. Google can supply a name,
    // an address and a picture; it cannot say whether this person books artists or performs.
    if (fragment.get('onboarding') === 'true') {
      this.router.navigate(['/auth/complete-profile']);
      return;
    }

    this.redirectUser(roles);
  }

  private fail(message: string): void {
    this.error = message;
    this.toast.error(message);
    history.replaceState(null, '', window.location.pathname);
    this.router.navigate(['/auth/login']);
  }

  /**
   * Mirrors the password login's routing, so arriving by either route lands in the same place.
   */
  private redirectUser(roles: string[]): void {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    if (returnUrl && returnUrl.startsWith('/')) {
      this.router.navigateByUrl(returnUrl);
      return;
    }

    if (roles.includes(Role.SUPER_ADMIN) || roles.includes(Role.ADMIN)) {
      this.router.navigate(['/admin']);
    } else if (roles.includes(Role.ARTIST)) {
      this.router.navigate(['/artist/']);
    } else if (roles.includes(Role.ORGANIZER) || roles.includes(Role.USER)) {
      this.router.navigate(['/user']);
    } else {
      this.router.navigate(['/']);
    }
  }
}
