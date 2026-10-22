import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ApiDef, ApiError, ApiRequest, ApiStat, AuditEntry, Bucket, KeyCreate, KeyView, KeyWithSecret, LogView, Page, Quota, SecEvent, Summary, SystemInfo, TimePoint, User } from './models';
import { HttpErrorResponse } from '@angular/common/http';

const B = '/api/v1';
function params(o: Record<string, string | number | boolean | undefined | null>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(o)) if (v !== undefined && v !== null && v !== '') p = p.set(k, String(v));
  return p;
}
export function errorMessage(e: unknown): string {
  const he = e as HttpErrorResponse; const body = he?.error as ApiError | undefined;
  if (body?.details) return Object.entries(body.details).map(([f, m]) => `${f}: ${m}`).join('; ');
  if (body?.message) return body.message + (body.correlationId ? ` (ref ${body.correlationId.slice(0, 8)})` : '');
  if (he?.status === 0) return 'The server is unreachable.';
  return 'Something went wrong.';
}

/** Typed client for the management API contracts (see docs/API_DOCUMENTATION.md). */
@Injectable({ providedIn: 'root' })
export class Api {
  private http = inject(HttpClient);
  // APIs
  apis(f: { q?: string; status?: string; environment?: string; page?: number; size?: number } = {}) { return this.http.get<Page<ApiDef>>(`${B}/apis`, { params: params({ size: 100, ...f }) }); }
  api(id: number) { return this.http.get<ApiDef>(`${B}/apis/${id}`); }
  createApi(r: ApiRequest) { return this.http.post<ApiDef>(`${B}/apis`, r); }
  updateApi(id: number, r: ApiRequest) { return this.http.put<ApiDef>(`${B}/apis/${id}`, r); }
  setApiStatus(id: number, status: 'ACTIVE' | 'INACTIVE') { return this.http.post<ApiDef>(`${B}/apis/${id}/status`, { status }); }
  archiveApi(id: number) { return this.http.delete<void>(`${B}/apis/${id}`); }
  // keys
  keys(all = false) { return this.http.get<KeyView[]>(`${B}/api-keys`, { params: params({ all }) }); }
  createKey(r: KeyCreate) { return this.http.post<KeyWithSecret>(`${B}/api-keys`, r); }
  rotateKey(id: number) { return this.http.post<KeyWithSecret>(`${B}/api-keys/${id}/rotate`, {}); }
  revokeKey(id: number) { return this.http.post<void>(`${B}/api-keys/${id}/revoke`, {}); }
  // analytics
  summary(from?: string, to?: string, apiId?: number) { return this.http.get<Summary>(`${B}/analytics/summary`, { params: params({ from, to, apiId }) }); }
  timeseries(from?: string, to?: string, apiId?: number) { return this.http.get<TimePoint[]>(`${B}/analytics/timeseries`, { params: params({ from, to, apiId }) }); }
  byApi(from?: string, to?: string) { return this.http.get<ApiStat[]>(`${B}/analytics/by-api`, { params: params({ from, to }) }); }
  statusCodes(from?: string, to?: string, apiId?: number) { return this.http.get<Bucket[]>(`${B}/analytics/status-codes`, { params: params({ from, to, apiId }) }); }
  methods(from?: string, to?: string, apiId?: number) { return this.http.get<Bucket[]>(`${B}/analytics/methods`, { params: params({ from, to, apiId }) }); }
  quota(apiId: number, keyId?: number) { return this.http.get<Quota>(`${B}/analytics/quota`, { params: params({ apiId, keyId }) }); }
  logs(f: { from?: string; to?: string; apiId?: number; outcome?: string; status?: number; correlationId?: string; page?: number; size?: number }) { return this.http.get<Page<LogView>>(`${B}/analytics/logs`, { params: params(f) }); }
  // security
  events(f: { severity?: string; acknowledged?: boolean; page?: number; size?: number }) { return this.http.get<Page<SecEvent>>(`${B}/security/events`, { params: params(f) }); }
  unacknowledged() { return this.http.get<{ unacknowledged: number }>(`${B}/security/counts`); }
  acknowledge(id: number, note: string) { return this.http.post<SecEvent>(`${B}/security/events/${id}/acknowledge`, { note }); }
  // admin
  users(q?: string, page = 0) { return this.http.get<Page<User>>(`${B}/users`, { params: params({ q, page, size: 25 }) }); }
  updateUser(id: number, roles: string[], active: boolean) { return this.http.put<User>(`${B}/users/${id}`, { roles, active }); }
  audit(f: { action?: string; actor?: string; outcome?: string; from?: string; to?: string; page?: number; size?: number }) { return this.http.get<Page<AuditEntry>>(`${B}/audit`, { params: params(f) }); }
  systemInfo() { return this.http.get<SystemInfo>(`${B}/system/info`); }
}
