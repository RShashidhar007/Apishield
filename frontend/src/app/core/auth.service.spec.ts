import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { TokenResponse } from './models';

const tokens = (roles: string[] = ['DEVELOPER']): TokenResponse => ({ accessToken: 'acc', refreshToken: 'ref', expiresIn: 900, tokenType: 'Bearer',
  user: { id: 1, email: 'a@b.c', displayName: 'A', roles: roles as never, active: true, createdAt: '' } });

describe('AuthService', () => {
  let auth: AuthService, http: HttpTestingController;
  beforeEach(() => { sessionStorage.clear(); TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] }); auth = TestBed.inject(AuthService); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());

  it('keeps the access token in memory only and the refresh token in sessionStorage', () => {
    auth.login('a@b.c', 'pw').subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokens());
    expect(auth.accessToken()).toBe('acc'); expect(sessionStorage.getItem('apishield.rt')).toBe('ref'); expect(localStorage.length).toBe(0);
    expect(JSON.stringify(sessionStorage)).not.toContain('acc"');
    expect(auth.isAuthenticated()).toBe(true);
  });
  it('derives permissions and the home route from roles', () => {
    auth.login('a@b.c', 'pw').subscribe(); http.expectOne('/api/v1/auth/login').flush(tokens(['API_MANAGER']));
    expect(auth.canManage).toBe(true); expect(auth.hasRole('ADMIN')).toBe(false); expect(auth.homeRoute()).toBe('/dashboard');
  });
  it('developers land on the portal', () => {
    auth.login('a@b.c', 'pw').subscribe(); http.expectOne('/api/v1/auth/login').flush(tokens());
    expect(auth.homeRoute()).toBe('/portal');
  });
  it('shares one refresh call between concurrent callers (rotation would otherwise trigger replay detection)', () => {
    sessionStorage.setItem('apishield.rt', 'ref');
    auth.refresh().subscribe(); auth.refresh().subscribe();
    http.expectOne('/api/v1/auth/refresh').flush(tokens());
  });
  it('clears everything on a failed session restore', () => {
    sessionStorage.setItem('apishield.rt', 'stale');
    auth.restore().subscribe({ error: () => {} });
    http.expectOne('/api/v1/auth/refresh').flush({ code: 'REFRESH_TOKEN_REUSED' }, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isAuthenticated()).toBe(false); expect(sessionStorage.getItem('apishield.rt')).toBeNull();
  });
});
