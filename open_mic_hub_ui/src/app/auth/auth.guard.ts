import { CanActivateFn, ActivatedRouteSnapshot, RouterStateSnapshot, Router } from '@angular/router';

export const authGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
) => {
  const token = localStorage.getItem('authToken');
  const userRoles = JSON.parse(localStorage.getItem('urole') || '[]') as string[];
  const allowedRoles = route.data['roles'] as string[];

  // If no token, redirect to login or block
  if (!token) {
    alert('You must be logged in.');
    return false;
  }

  // If roles are specified on the route, check if user has at least one
  if (allowedRoles && !userRoles.some(role => allowedRoles.includes(role))) {
    alert('Access denied. Insufficient permissions.');
    return false;
  }

  return true;
};
