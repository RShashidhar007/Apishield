import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from './models';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_r, state) => {
  const a = inject(AuthService), r = inject(Router);
  return a.isAuthenticated() ? true : r.createUrlTree(['/login'], { queryParams: { next: state.url } });
};
/** UX-level guard only: the backend enforces every permission independently. */
export const roleGuard = (...roles: Role[]): CanActivateFn => () => {
  const a = inject(AuthService), r = inject(Router);
  if (!a.isAuthenticated()) return r.createUrlTree(['/login']);
  return a.hasRole(...roles) ? true : r.createUrlTree(['/forbidden']);
};
export const guestGuard: CanActivateFn = () => {
  const a = inject(AuthService), r = inject(Router);
  return a.isAuthenticated() ? r.createUrlTree([a.homeRoute()]) : true;
};
