import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const PUBLIC = ['/api/v1/auth/login', '/api/v1/auth/register', '/api/v1/auth/refresh', '/api/v1/auth/logout'];

/** Attaches the bearer token to management-API calls only (never to /gateway test calls) and retries once after a refresh. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  if (!req.url.startsWith('/api/v1/') || PUBLIC.some(p => req.url.startsWith(p))) return next(req);
  const withToken = (t: string | null) => (t ? req.clone({ setHeaders: { Authorization: `Bearer ${t}` } }) : req);
  return next(withToken(auth.accessToken())).pipe(
    catchError((e: HttpErrorResponse) => {
      if (e.status !== 401 || !auth.isAuthenticated()) return throwError(() => e);
      try {
        return auth.refresh().pipe(
          switchMap(r => next(withToken(r.accessToken))),
          catchError(err => { auth.logout(); return throwError(() => err); }));
      } catch { auth.logout(); return throwError(() => e); }
    }));
};
