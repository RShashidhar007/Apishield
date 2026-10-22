import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Api, errorMessage } from './api.service';

describe('Api client contracts', () => {
  let api: Api, ctl: HttpTestingController;
  beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); api = TestBed.inject(Api); ctl = TestBed.inject(HttpTestingController); });
  afterEach(() => ctl.verify());

  it('filters analytics by api and range using the documented query names', () => {
    api.summary('2026-01-01T00:00:00Z', '2026-01-02T00:00:00Z', 7).subscribe();
    const r = ctl.expectOne(x => x.url === '/api/v1/analytics/summary'); expect(r.request.params.get('apiId')).toBe('7'); expect(r.request.params.get('from')).toBe('2026-01-01T00:00:00Z'); r.flush({});
  });
  it('omits empty filters', () => {
    api.logs({ apiId: undefined, outcome: '', page: 0 }).subscribe();
    const r = ctl.expectOne(x => x.url === '/api/v1/analytics/logs'); expect(r.request.params.has('apiId')).toBe(false); expect(r.request.params.has('outcome')).toBe(false); r.flush({});
  });
  it('creates keys, rotates and revokes against the right endpoints', () => {
    api.createKey({ name: 'k', apiIds: [1], scopes: ['READ'] }).subscribe(); ctl.expectOne({ method: 'POST', url: '/api/v1/api-keys' }).flush({});
    api.rotateKey(3).subscribe(); ctl.expectOne({ method: 'POST', url: '/api/v1/api-keys/3/rotate' }).flush({});
    api.revokeKey(3).subscribe(); ctl.expectOne({ method: 'POST', url: '/api/v1/api-keys/3/revoke' }).flush(null);
  });
  it('updates API status and archives via DELETE', () => {
    api.setApiStatus(2, 'ACTIVE').subscribe(); ctl.expectOne({ method: 'POST', url: '/api/v1/apis/2/status' }).flush({});
    api.archiveApi(2).subscribe(); ctl.expectOne({ method: 'DELETE', url: '/api/v1/apis/2' }).flush(null);
  });
  it('turns server errors into readable messages with a correlation reference', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 400, error: { code: 'X', message: 'Bad thing', correlationId: 'abcdef12-3456' } }))).toBe('Bad thing (ref abcdef12)');
    expect(errorMessage(new HttpErrorResponse({ status: 400, error: { code: 'VALIDATION_FAILED', message: 'm', details: { name: 'required' } } }))).toBe('name: required');
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toBe('The server is unreachable.');
  });
});
