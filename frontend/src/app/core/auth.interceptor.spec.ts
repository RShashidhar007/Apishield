import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient, ctl: HttpTestingController, auth: AuthService;
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpClient); ctl = TestBed.inject(HttpTestingController); auth = TestBed.inject(AuthService);
    auth.user.set({ id: 1, email: 'a', displayName: 'a', roles: ['ADMIN'], active: true, createdAt: '' }); auth.accessToken.set('tok'); sessionStorage.setItem('apishield.rt', 'rt');
  });
  afterEach(() => ctl.verify());

  it('adds the bearer token to management API calls only', () => {
    http.get('/api/v1/apis').subscribe(); expect(ctl.expectOne('/api/v1/apis').request.headers.get('Authorization')).toBe('Bearer tok');
    http.get('/gateway/catalog/products').subscribe(); expect(ctl.expectOne('/gateway/catalog/products').request.headers.has('Authorization')).toBe(false);
  });
  it('never sends the bearer token to auth endpoints', () => {
    http.post('/api/v1/auth/login', {}).subscribe(); expect(ctl.expectOne('/api/v1/auth/login').request.headers.has('Authorization')).toBe(false);
  });
  it('refreshes once on 401 and retries with the new token', () => {
    let result: unknown; http.get('/api/v1/apis').subscribe(r => (result = r));
    ctl.expectOne('/api/v1/apis').flush({}, { status: 401, statusText: 'Unauthorized' });
    ctl.expectOne('/api/v1/auth/refresh').flush({ accessToken: 'new', refreshToken: 'rt2', expiresIn: 900, tokenType: 'Bearer', user: auth.user() });
    const retry = ctl.expectOne('/api/v1/apis'); expect(retry.request.headers.get('Authorization')).toBe('Bearer new'); retry.flush({ ok: true });
    expect(result).toEqual({ ok: true });
  });
  it('signs the user out when the refresh also fails', () => {
    http.get('/api/v1/apis').subscribe({ error: () => {} });
    ctl.expectOne('/api/v1/apis').flush({}, { status: 401, statusText: 'x' });
    ctl.expectOne('/api/v1/auth/refresh').flush({}, { status: 401, statusText: 'x' });
    ctl.expectOne('/api/v1/auth/logout').flush(null); // best-effort server-side logout
    expect(auth.isAuthenticated()).toBe(false);
  });
});
