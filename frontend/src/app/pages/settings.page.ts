import { Component, OnInit, inject, signal } from '@angular/core';
import { KeyValuePipe } from '@angular/common';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { SystemInfo } from '../core/models';

@Component({
  selector: 'app-settings', imports: [KeyValuePipe],
  template: `
  <header class="head"><p class="eyebrow">Settings</p><h1>Account &amp; environment</h1></header>
  <div class="grid">
    <section class="panel"><h2>Your account</h2><dl><dt>Name</dt><dd>{{ auth.user()?.displayName }}</dd><dt>Email</dt><dd>{{ auth.user()?.email }}</dd><dt>Roles</dt><dd>{{ auth.user()?.roles?.join(', ') }}</dd></dl>
      <button class="btn danger" (click)="auth.logout()">Sign out</button><p class="hint" style="margin-top:12px">Sessions use a 15-minute access token and a rotating refresh token that lives only for this browser tab.</p></section>
    @if (auth.canManage) { <section class="panel"><h2>Security policy</h2>
      @if (info(); as i) { <dl><dt>Token issuer</dt><dd>{{ i.jwtIssuer }}</dd><dt>Access token</dt><dd>{{ i.accessTokenSeconds / 60 }} min</dd><dt>Refresh token</dt><dd>{{ i.refreshTokenSeconds / 86400 }} days (rotating)</dd>
        <dt>Login lockout</dt><dd>{{ i.maxFailedLogins }} failures → {{ i.lockMinutes }} min</dd><dt>Upstream allow-list</dt><dd class="mono">{{ i.allowedUpstreamHosts.join(', ') }}</dd><dt>Private upstreams</dt><dd>{{ i.allowPrivateUpstreams ? 'allowed for allow-listed hosts' : 'blocked' }}</dd></dl> }
      @else if (error()) { <div class="banner bad" role="alert">Unavailable.</div> } @else { <div class="skeleton" style="height:120px"></div> }</section>
      <section class="panel"><h2>Detection thresholds</h2>@if (info(); as i) { <dl>@for (e of i.detection | keyvalue; track e.key) { <dt>{{ e.key }}</dt><dd>{{ e.value }}</dd> }</dl><p class="hint">Configured by environment/application properties on the backend; changes require a restart.</p> }</section>
      <section class="panel"><h2>Retention</h2>@if (info(); as i) { <dl>@for (e of i.retention | keyvalue; track e.key) { <dt>{{ e.key }}</dt><dd>{{ e.value }} days</dd> }<dt>auditLogs</dt><dd>kept until archived by an operator</dd></dl> }</section> }
  </div>`,
  styles: [`.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:16px}dl{display:grid;grid-template-columns:150px 1fr;gap:6px 12px}dt{color:var(--muted)}dd{margin:0;word-break:break-word}`]
})
export class SettingsPage implements OnInit {
  auth = inject(AuthService); private api = inject(Api); info = signal<SystemInfo | null>(null); error = signal(false);
  ngOnInit() { if (this.auth.canManage) this.api.systemInfo().subscribe({ next: i => this.info.set(i), error: () => this.error.set(true) }); }
}
