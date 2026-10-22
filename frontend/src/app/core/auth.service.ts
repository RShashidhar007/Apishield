import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, finalize, map, of, shareReplay, tap } from 'rxjs';
import { Role, TokenResponse, User } from './models';

/**
 * Token strategy (documented in docs/SECURITY.md):
 *  - access token (15 min JWT): memory only, never persisted
 *  - refresh token (rotating, replay-detected): sessionStorage so a reload keeps the session but closing the tab ends it.
 *  Risk: sessionStorage is readable by any script running in the page (XSS). Mitigation: strict CSP in nginx, no innerHTML of user data,
 *  short access TTL, refresh-token rotation with family revocation on replay.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient); private router = inject(Router);
  private static readonly RT = 'apishield.rt';
  readonly user = signal<User | null>(null);
  readonly accessToken = signal<string | null>(null);
  readonly isAuthenticated = computed(() => !!this.user());
  private refreshing$: Observable<TokenResponse> | null = null;

  hasRole(...roles: Role[]): boolean { const u = this.user(); return !!u && roles.some(r => u.roles.includes(r)); }
  get canManage(): boolean { return this.hasRole('ADMIN', 'API_MANAGER'); }
  homeRoute(): string { return this.canManage ? '/dashboard' : '/portal'; }

  login(email: string, password: string) {
    return this.http.post<TokenResponse>('/api/v1/auth/login', { email, password }).pipe(tap(r => this.accept(r)));
  }
  register(email: string, displayName: string, password: string) {
    return this.http.post<User>('/api/v1/auth/register', { email, displayName, password });
  }
  /** Single-flight refresh: concurrent 401s share one refresh call (rotation would otherwise trigger replay detection). */
  refresh(): Observable<TokenResponse> {
    const rt = sessionStorage.getItem(AuthService.RT);
    if (!rt) throw new Error('no refresh token');
    if (!this.refreshing$) {
      this.refreshing$ = this.http.post<TokenResponse>('/api/v1/auth/refresh', { refreshToken: rt }).pipe(
        tap(r => this.accept(r)), finalize(() => (this.refreshing$ = null)), shareReplay(1));
    }
    return this.refreshing$;
  }
  /** Called once at startup: restores the session from the refresh token if present. */
  restore(): Observable<boolean> {
    if (!sessionStorage.getItem(AuthService.RT)) return of(false);
    return this.refresh().pipe(map(() => true), tap({ error: () => this.clear() }));
  }
  logout() {
    const rt = sessionStorage.getItem(AuthService.RT);
    if (rt) this.http.post('/api/v1/auth/logout', { refreshToken: rt }).subscribe({ error: () => {} });
    this.clear(); this.router.navigateByUrl('/login');
  }
  clear() { sessionStorage.removeItem(AuthService.RT); this.accessToken.set(null); this.user.set(null); }
  private accept(r: TokenResponse) { sessionStorage.setItem(AuthService.RT, r.refreshToken); this.accessToken.set(r.accessToken); this.user.set(r.user); }
}
