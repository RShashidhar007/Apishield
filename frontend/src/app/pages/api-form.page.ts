import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api, errorMessage } from '../core/api.service';
import { ApiRequest } from '../core/models';
import { Ui } from '../core/ui.service';

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'];
const csv = (s: string) => s.split(',').map(x => x.trim()).filter(Boolean);

@Component({
  selector: 'app-api-form', imports: [ReactiveFormsModule, RouterLink],
  template: `
  <p><a routerLink="/apis">← API registry</a></p><h1>{{ id() ? 'Edit API' : 'Register an API' }}</h1>
  @if (loading()) { <div class="skeleton" style="height:300px"></div> } @else {
  <form [formGroup]="f" (ngSubmit)="save()" class="stack" novalidate>
    @if (serverError()) { <div class="banner bad" role="alert">{{ serverError() }}</div> }
    <section class="panel"><h2>Identity</h2><div class="grid-2">
      <div class="field" [class.invalid]="bad('name')"><label for="n">Name</label><input id="n" type="text" formControlName="name">@if (bad('name')) { <span class="error-text">Name is required.</span> }</div>
      <div class="field" [class.invalid]="bad('version')"><label for="v">Version</label><input id="v" type="text" formControlName="version" placeholder="v1">@if (bad('version')) { <span class="error-text">Letters, digits, . _ - only.</span> }</div>
      <div class="field"><label for="e">Environment</label><select id="e" formControlName="environment"><option>DEV</option><option>STAGING</option><option>PROD</option></select></div></div>
      <div class="field"><label for="d">Description</label><textarea id="d" rows="2" formControlName="description"></textarea></div></section>

    <section class="panel"><h2>Routing</h2><div class="grid-2">
      <div class="field" [class.invalid]="bad('gatewayPath')"><label for="p">Public path (after /gateway)</label><input id="p" type="text" formControlName="gatewayPath" placeholder="/catalog"><span class="hint">Clients call <code>/gateway{{ f.value.gatewayPath }}/…</code></span>@if (bad('gatewayPath')) { <span class="error-text">Lower-case path such as /catalog or /catalog/v1.</span> }</div>
      <div class="field" [class.invalid]="bad('upstreamUrl')"><label for="u">Upstream URL</label><input id="u" type="text" formControlName="upstreamUrl" placeholder="http://catalog-service:8081"><span class="hint">Host must be on the server-side upstream allow-list. Private/metadata/loopback addresses are rejected.</span>@if (bad('upstreamUrl')) { <span class="error-text">Upstream URL is required.</span> }</div></div>
      <div class="field"><span class="lbl">Allowed methods</span><div class="checks">@for (m of methods; track m) { <label><input type="checkbox" [checked]="has(m)" (change)="toggleMethod(m, $any($event.target).checked)"> {{ m }}</label> }</div>@if (f.controls.methods.invalid && f.controls.methods.touched) { <span class="error-text">Pick at least one method.</span> }</div></section>

    <section class="panel"><h2>Access &amp; limits</h2><div class="grid-2">
      <div class="field"><label for="a">Authentication policy</label><select id="a" formControlName="authPolicy"><option value="API_KEY">API key (X-API-Key header)</option><option value="JWT">JWT (Authorization: Bearer)</option><option value="NONE">None — public</option></select>
        @if (f.value.authPolicy === 'NONE') { <span class="error-text">⚠ Public APIs are rate limited by client IP only.</span> }</div>
      <div class="field"><label for="mb">Max request size (bytes)</label><input id="mb" type="number" formControlName="maxRequestBytes"><span class="hint">0 rejects any request body.</span></div>
      <div class="field"><label for="ct">Connect timeout (ms)</label><input id="ct" type="number" formControlName="connectTimeoutMs"></div>
      <div class="field"><label for="rt">Response timeout (ms)</label><input id="rt" type="number" formControlName="responseTimeoutMs"></div></div>
      <div class="field"><label class="row"><input type="checkbox" formControlName="rateLimitEnabled"> Enable rate limiting (Redis token bucket)</label></div>
      @if (f.value.rateLimitEnabled) { <div class="grid-2">
        <div class="field"><label for="rr">Requests</label><input id="rr" type="number" formControlName="rateLimitRequests"></div>
        <div class="field"><label for="rw">Per window (seconds)</label><input id="rw" type="number" formControlName="rateLimitWindowSeconds"></div>
        <div class="field"><label for="rb">Burst capacity (extra)</label><input id="rb" type="number" formControlName="rateLimitBurst"></div></div> }</section>

    <section class="panel"><h2>Headers &amp; CORS</h2>
      <div class="field"><label for="h">Extra request headers to forward (comma separated)</label><input id="h" type="text" formControlName="headers" placeholder="x-tenant-id, x-trace-id"><span class="hint">Everything else except a safe default set is stripped. Credentials, Host and X-Forwarded-* can never be forwarded.</span></div>
      <div class="field"><label for="c">Allowed CORS origins (comma separated, exact origins)</label><input id="c" type="text" formControlName="cors" placeholder="https://app.example.com"></div></section>

    <div class="row"><button class="btn primary" type="submit" [disabled]="busy()">{{ busy() ? 'Saving…' : (id() ? 'Save changes' : 'Register API') }}</button><a class="btn ghost" routerLink="/apis">Cancel</a></div>
  </form>}`
})
export class ApiFormPage implements OnInit {
  id = input<string>(); methods = METHODS;
  private fb = inject(FormBuilder); private api = inject(Api); private router = inject(Router); private ui = inject(Ui);
  loading = signal(false); busy = signal(false); serverError = signal('');
  f = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]], description: [''], version: ['v1', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]{1,32}$/)]], environment: ['DEV' as 'DEV' | 'STAGING' | 'PROD'],
    gatewayPath: ['', [Validators.required, Validators.pattern(/^\/[a-z0-9][a-z0-9_-]*(\/[a-z0-9][a-z0-9_-]*){0,5}$/)]], upstreamUrl: ['', [Validators.required]],
    methods: [['GET'] as string[], [Validators.required, Validators.minLength(1)]], authPolicy: ['API_KEY' as 'API_KEY' | 'JWT' | 'NONE'],
    rateLimitEnabled: [true], rateLimitRequests: [60, [Validators.min(1), Validators.max(100000)]], rateLimitWindowSeconds: [60, [Validators.min(1), Validators.max(86400)]], rateLimitBurst: [10, [Validators.min(0)]],
    connectTimeoutMs: [2000, [Validators.min(100), Validators.max(10000)]], responseTimeoutMs: [5000, [Validators.min(100), Validators.max(60000)]], maxRequestBytes: [1048576, [Validators.min(0), Validators.max(52428800)]],
    headers: [''], cors: [''],
  });
  ngOnInit() {
    const id = this.id(); if (!id) return; this.loading.set(true);
    this.api.api(+id).subscribe({ next: a => { this.f.patchValue({ ...a, headers: a.allowedHeaders.join(', '), cors: a.corsOrigins.join(', ') }); this.loading.set(false); },
      error: e => { this.ui.error(errorMessage(e)); this.router.navigate(['/apis']); } });
  }
  bad(c: 'name' | 'version' | 'gatewayPath' | 'upstreamUrl') { const x = this.f.controls[c]; return x.invalid && (x.touched || x.dirty); }
  has(m: string) { return this.f.value.methods!.includes(m); }
  toggleMethod(m: string, on: boolean) { const cur = new Set(this.f.value.methods); on ? cur.add(m) : cur.delete(m); this.f.controls.methods.setValue([...cur]); this.f.controls.methods.markAsTouched(); }
  save() {
    this.f.markAllAsTouched(); this.serverError.set(''); if (this.f.invalid) return;
    const v = this.f.getRawValue();
    const body = { ...v, allowedHeaders: csv(v.headers), corsOrigins: csv(v.cors) } as unknown as ApiRequest & { headers?: string; cors?: string };
    delete body.headers; delete body.cors; this.busy.set(true);
    const call = this.id() ? this.api.updateApi(+this.id()!, body) : this.api.createApi(body);
    call.subscribe({ next: a => { this.ui.toast(this.id() ? 'API updated.' : 'API registered (inactive). Activate it to start routing.'); this.router.navigate(['/apis', a.id]); }, error: e => { this.busy.set(false); this.serverError.set(errorMessage(e)); } });
  }
}
