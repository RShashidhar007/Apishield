import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Api, errorMessage } from '../core/api.service';
import { ApiDef, KeyView, KeyWithSecret } from '../core/models';
import { AuthService } from '../core/auth.service';
import { ago, fmtNum } from '../core/format';
import { Ui } from '../core/ui.service';
import { PillComponent, TiltDirective } from '../shared/ui';

@Component({
  selector: 'app-keys', imports: [ReactiveFormsModule, PillComponent, TiltDirective, DatePipe],
  template: `
  <header class="head"><div><p class="eyebrow">Vault</p><h1>API keys</h1><p class="muted">Keys are shown once, stored only as keyed hashes, and enforced by the gateway.</p></div>
    <div class="row">@if (auth.hasRole('ADMIN')) { <label class="row hint"><input type="checkbox" [checked]="all()" (change)="all.set($any($event.target).checked); load()"> All users' keys</label> }
      <button class="btn primary" (click)="creating.set(!creating())" [attr.aria-expanded]="creating()">{{ creating() ? 'Close' : '+ New key' }}</button></div></header>

  @if (secret(); as s) { <div class="panel reveal" role="alertdialog" aria-labelledby="sk"><h2 id="sk">Copy your new key now</h2>
    <p>This is the only time the full key is displayed. It cannot be recovered — rotate or create a new key if it is lost.</p>
    <div class="row wrap"><code class="secret" tabindex="0">{{ s.rawKey }}</code><button class="btn" (click)="copy(s.rawKey)">Copy</button><button class="btn primary" (click)="secret.set(null)">I have stored it safely</button></div></div> }

  @if (creating()) { <form class="panel" [formGroup]="f" (ngSubmit)="create()" novalidate style="margin-bottom:16px"><h2>Create key</h2>
    @if (formError()) { <div class="banner bad" role="alert">{{ formError() }}</div> }
    <div class="grid-2"><div class="field" [class.invalid]="f.controls.name.invalid && f.controls.name.touched"><label for="kn">Name</label><input id="kn" type="text" formControlName="name">@if (f.controls.name.invalid && f.controls.name.touched) { <span class="error-text">Name is required.</span> }</div>
      <div class="field"><label for="kd">Description</label><input id="kd" type="text" formControlName="description"></div>
      <div class="field"><label for="ke">Expires in (days, max 365)</label><input id="ke" type="number" formControlName="expiresInDays"></div>
      <div class="field"><label for="kq">Quota override (requests per API window, optional)</label><input id="kq" type="number" formControlName="quotaRequests"></div></div>
    <div class="field"><span class="lbl">APIs this key may call</span>
      @if (!apis().length) { <span class="hint">No active APIs are available yet.</span> }
      <div class="checks">@for (a of apis(); track a.id) { <label><input type="checkbox" [checked]="sel.has(a.id)" (change)="pick(a.id, $any($event.target).checked)"> {{ a.name }} {{ a.version }} <span class="muted mono">{{ a.publicPath }}</span></label> }</div></div>
    <div class="field"><span class="lbl">Scopes</span><div class="checks"><label><input type="checkbox" [checked]="scopes.has('READ')" (change)="scope('READ', $any($event.target).checked)"> READ (GET, HEAD)</label><label><input type="checkbox" [checked]="scopes.has('WRITE')" (change)="scope('WRITE', $any($event.target).checked)"> WRITE (POST, PUT, PATCH, DELETE)</label></div></div>
    <button class="btn primary" type="submit" [disabled]="busy()">{{ busy() ? 'Creating…' : 'Create key' }}</button></form> }

  @if (loading()) { <div class="grid">@for (i of [1,2,3]; track i) { <div class="skeleton" style="height:190px"></div> }</div> }
  @else if (error()) { <div class="banner bad" role="alert">{{ error() }}</div> }
  @else if (!keys().length) { <div class="panel empty"><h3>Your vault is empty</h3><p>Create a key to call APIs through the gateway.</p></div> }
  @else { <div class="grid">@for (k of keys(); track k.id) { <article class="panel key" appTilt [class.dead]="k.status !== 'ACTIVE'">
    <div class="row"><strong>{{ k.name }}</strong><app-pill class="right" [kind]="k.status === 'ACTIVE' ? 'ok' : k.status === 'EXPIRED' ? 'warn' : 'bad'" [text]="k.status"/></div>
    <code class="mono">{{ k.maskedKey }}</code>
    <dl><dt>APIs</dt><dd>{{ names(k.apiIds) }}</dd><dt>Scopes</dt><dd>{{ k.scopes.join(', ') }}</dd><dt>Expires</dt><dd>{{ k.expiresAt ? (k.expiresAt | date:'mediumDate') : 'never' }}</dd>
      <dt>Last used</dt><dd>{{ k.lastUsedAt ? ago(k.lastUsedAt) : 'never' }}</dd><dt>Usage 24 h</dt><dd>{{ n(k.requests24h) }} requests</dd>@if (k.quotaRequests) { <dt>Quota</dt><dd>{{ k.quotaRequests }} / window</dd> }</dl>
    @if (k.status === 'ACTIVE') { <div class="row"><button class="btn sm" (click)="rotate(k)">Rotate</button><button class="btn sm danger" (click)="revoke(k)">Revoke</button></div> }</article> }</div> }`,
  styles: [`.head{display:flex;justify-content:space-between;align-items:flex-end;gap:16px;flex-wrap:wrap;margin-bottom:var(--s5)}h1{margin:0}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:16px}
    .key{display:grid;gap:10px;background:linear-gradient(160deg,var(--surface),var(--surface-2))}.key.dead{opacity:.62}dl{display:grid;grid-template-columns:80px 1fr;gap:4px 10px;margin:0;font-size:.88rem}dt{color:var(--muted)}dd{margin:0}
    .reveal{border:2px solid var(--lime);margin-bottom:16px}.secret{background:var(--bg);padding:10px 12px;border-radius:8px;border:1px dashed var(--lime);word-break:break-all;flex:1;min-width:240px;user-select:all}`]
})
export class KeysPage implements OnInit {
  auth = inject(AuthService); private api = inject(Api); private ui = inject(Ui); private fb = inject(FormBuilder);
  keys = signal<KeyView[]>([]); apis = signal<ApiDef[]>([]); loading = signal(true); error = signal(''); all = signal(false); creating = signal(false); busy = signal(false); formError = signal(''); secret = signal<KeyWithSecret | null>(null);
  sel = new Set<number>(); scopes = new Set<string>(['READ']); n = fmtNum; ago = ago;
  f = this.fb.nonNullable.group({ name: ['', Validators.required], description: [''], expiresInDays: [90, [Validators.min(1), Validators.max(365)]], quotaRequests: [null as number | null] });
  ngOnInit() { this.load(); this.api.apis({ status: 'ACTIVE' }).subscribe({ next: p => this.apis.set(p.items) }); }
  load() { this.loading.set(true); this.api.keys(this.all()).subscribe({ next: k => { this.keys.set(k); this.loading.set(false); }, error: e => { this.error.set(errorMessage(e)); this.loading.set(false); } }); }
  names(ids: number[]) { return ids.map(i => this.apis().find(a => a.id === i)?.name ?? '#' + i).join(', '); }
  pick(id: number, on: boolean) { on ? this.sel.add(id) : this.sel.delete(id); }
  scope(s: string, on: boolean) { on ? this.scopes.add(s) : this.scopes.delete(s); }
  create() {
    this.f.markAllAsTouched(); this.formError.set('');
    if (this.f.invalid) return; if (!this.sel.size) return this.formError.set('Select at least one API.'); if (!this.scopes.size) return this.formError.set('Select at least one scope.');
    const v = this.f.getRawValue(); this.busy.set(true);
    this.api.createKey({ name: v.name, description: v.description || undefined, apiIds: [...this.sel], scopes: [...this.scopes], expiresInDays: v.expiresInDays, quotaRequests: v.quotaRequests ?? undefined }).subscribe({
      next: r => { this.busy.set(false); this.secret.set(r); this.creating.set(false); this.sel.clear(); this.f.reset({ expiresInDays: 90 }); this.load(); }, error: e => { this.busy.set(false); this.formError.set(errorMessage(e)); } });
  }
  async revoke(k: KeyView) {
    if (!(await this.ui.confirm('Revoke key?', `${k.name} (${k.maskedKey}) will stop working at the gateway within seconds. This cannot be undone.`, 'Revoke', true))) return;
    this.api.revokeKey(k.id).subscribe({ next: () => { this.ui.toast('Key revoked.'); this.load(); }, error: e => this.ui.error(errorMessage(e)) });
  }
  async rotate(k: KeyView) {
    if (!(await this.ui.confirm('Rotate key?', 'A new key with the same access is issued and the current key is revoked immediately. Update your clients first if they cannot tolerate downtime.', 'Rotate', false))) return;
    this.api.rotateKey(k.id).subscribe({ next: r => { this.secret.set(r); this.load(); }, error: e => this.ui.error(errorMessage(e)) });
  }
  async copy(t: string) { try { await navigator.clipboard.writeText(t); this.ui.toast('Key copied to clipboard.'); } catch { this.ui.error('Copy failed — select the key and copy it manually.'); } }
}
