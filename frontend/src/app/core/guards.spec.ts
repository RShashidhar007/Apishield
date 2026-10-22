import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard, guestGuard, roleGuard } from './guards';

describe('route guards', () => {
  const user = (roles: string[]) => ({ id: 1, email: 'a@b.c', displayName: 'A', roles, active: true, createdAt: '' });
  const run = (g: any, url = '/x') => TestBed.runInInjectionContext(() => g({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot));
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }));

  it('redirects anonymous users to login and remembers the target', () => {
    const r = run(authGuard, '/keys') as UrlTree; expect(TestBed.inject(Router).serializeUrl(r)).toBe('/login?next=%2Fkeys');
  });
  it('allows authenticated users', () => { TestBed.inject(AuthService).user.set(user(['DEVELOPER']) as never); expect(run(authGuard)).toBe(true); });
  it('blocks developers from admin routes (UX only — backend enforces too)', () => {
    TestBed.inject(AuthService).user.set(user(['DEVELOPER']) as never);
    expect(TestBed.inject(Router).serializeUrl(run(roleGuard('ADMIN')) as UrlTree)).toBe('/forbidden');
  });
  it('allows a matching role', () => { TestBed.inject(AuthService).user.set(user(['API_MANAGER']) as never); expect(run(roleGuard('ADMIN', 'API_MANAGER'))).toBe(true); });
  it('keeps signed-in users away from the login page', () => {
    TestBed.inject(AuthService).user.set(user(['ADMIN']) as never);
    expect(TestBed.inject(Router).serializeUrl(run(guestGuard) as UrlTree)).toBe('/dashboard');
  });
});
