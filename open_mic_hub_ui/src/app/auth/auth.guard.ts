import { CanActivateFn, ActivatedRouteSnapshot, RouterStateSnapshot, Router } from '@angular/router';
import { inject } from '@angular/core';

/**
 * Gate for authenticated routes.
 *
 * This is a convenience for the user, not a security boundary - the API enforces roles on every
 * endpoint. It previously blocked with alert() and returned false, which left the user on a dead
 * page with no way forward; it now redirects to sign in and remembers where they were going.
 */
export const authGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
) => {
  const router = inject(Router);
  const token = localStorage.getItem('authToken');

  if (!token) {
    return router.createUrlTree(['/auth/login'], {
      queryParams: { returnUrl: state.url }
    });
  }

  const allowedRoles = route.data['roles'] as string[] | undefined;
  if (allowedRoles?.length) {
    let userRoles: string[] = [];
    try {
      userRoles = JSON.parse(localStorage.getItem('urole') || '[]');
    } catch {
      userRoles = [];
    }

    if (!userRoles.some(role => allowedRoles.includes(role))) {
      return router.createUrlTree(['/']);
    }
  }

  return true;
};
