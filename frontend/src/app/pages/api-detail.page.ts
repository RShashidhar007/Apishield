import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, errorMessage } from '../core/api.service';
import { ApiDef, LogView, Quota, Summary, TimePoint } from '../core/models';
import { fmtMs, fmtNum, fmtPct, fmtTime, rangeFrom } from '../core/format';
import { Ui } from '../core/ui.service';
import { GaugeComponent, LineChartComponent } from '../shared/charts';
import { RequestInspectorComponent } from '../shared/request-inspector.component';
import { PillComponent, statusKind } from '../shared/ui';

@Component({
  selector: 'app-api-detail', imports: [RouterLink, PillComponent, GaugeComponent, LineChartComponent, RequestInspectorComponent],
  template: `
  <p><a routerLink="/apis">← API registry</a></p>
  @if (a(); as a) {
    <header class="head"><div><p class="eyebrow">{{ a.environment }} · {{ a.version }}</p><h1>{{ a.name }}</h1><p class="muted">{{ a.description || 'No description.' }}</p></div>
      <div class="row wrap"><app-pill [kind]="a.status === 'ACTIVE' ? 'ok' : 'warn'" [text]="a.status"/>
        <button class="btn" (click)="toggle(a)" [disabled]="a.status === 'ARCHIVED'">{{ a.status === 'ACTIVE' ? 'Deactivate' : 'Activate' }}</button><a class="btn" [routerLink]="['/apis', a.id, 'edit']">Edit</a></div></header>

    <section class="panel"><h2>Upstream routing</h2>
      <svg viewBox="0 0 760 130" role="img" aria-label="Request path: client to gateway checks to upstream" style="width:100%">
        <g font-size="12" fill="var(--text)"><rect x="6" y="40" width="120" height="48" rx="10" fill="var(--surface-2)" stroke="var(--line)"/><text x="66" y="68" text-anchor="middle">Client</text>
          <rect x="190" y="20" width="300" height="88" rx="14" fill="var(--surface-2)" stroke="var(--teal)" stroke-width="2"/><text x="340" y="44" text-anchor="middle" font-weight="700">APIShield gateway</text>
          <text x="340" y="64" text-anchor="middle" fill="var(--muted)">{{ a.authPolicy.replace('_', ' ') }} · {{ a.methods.join(' ') }}</text><text x="340" y="84" text-anchor="middle" fill="var(--muted)">{{ a.rateLimitEnabled ? a.rateLimitRequests + ' req / ' + a.rateLimitWindowSeconds + ' s' : 'no rate limit' }} · ≤ {{ a.maxRequestBytes }} B</text>
          <rect x="560" y="40" width="190" height="48" rx="10" fill="var(--surface-2)" stroke="var(--lime)"/><text x="655" y="62" text-anchor="middle" font-size="11">{{ a.upstreamUrl }}</text><text x="655" y="78" text-anchor="middle" fill="var(--muted)" font-size="10">timeout {{ a.responseTimeoutMs }} ms</text></g>
        <g stroke="var(--cobalt)" stroke-width="2" fill="none"><path d="M126 64 H190"/><path d="M490 64 H560"/></g>
        <text x="158" y="56" text-anchor="middle" font-size="9" fill="var(--muted)">{{ a.publicPath }}</text></svg></section>

    <div class="grid" style="margin-top:16px">
      <section class="panel"><h2>Configuration</h2><dl>
        <dt>Public path</dt><dd class="mono">{{ a.publicPath }}</dd><dt>Methods</dt><dd>{{ a.methods.join(', ') }}</dd><dt>Authentication</dt><dd>{{ a.authPolicy }}</dd>
        <dt>Timeouts</dt><dd>connect {{ a.connectTimeoutMs }} ms · response {{ a.responseTimeoutMs }} ms</dd><dt>Max body</dt><dd>{{ a.maxRequestBytes }} bytes</dd>
        <dt>Extra headers</dt><dd>{{ a.allowedHeaders.join(', ') || 'default set only' }}</dd><dt>CORS origins</dt><dd>{{ a.corsOrigins.join(', ') || 'none' }}</dd>
        <dt>Created</dt><dd>{{ time(a.createdAt) }}</dd><dt>Updated</dt><dd>{{ time(a.updatedAt) }}</dd></dl></section>
      <section class="panel" style="text-align:center"><h2>Rate-limit meter</h2>
        @if (quota(); as q) { <app-gauge [used]="q.used" [limit]="q.limit" [window]="q.windowSeconds"/><p class="hint">Requests accepted in the current window, from persisted traffic (a few seconds of ingest lag). The authoritative counter lives in Redis.</p> }
        @else { <div class="empty">Quota unavailable.</div> }</section>
    </div>

    <section class="panel" style="margin-top:16px"><div class="panel-head"><h2>Traffic · last 24 h</h2>
      @if (sum(); as s) { <span class="hint">{{ n(s.total) }} requests · {{ pct(s.errorRate) }} errors · p95 {{ ms(s.p95LatencyMs) }}</span> }</div>
      <app-line-chart [series]="[{name:'requests', color:'var(--teal)', values: totals(), fill:true},{name:'errors', color:'var(--coral)', values: errs()}]" [labels]="labels()" ariaLabel="Requests and errors over time"/></section>

    <div class="split" style="margin-top:16px"><section class="panel"><h2>Recent requests</h2>
      @if (!logs().length) { <div class="empty" role="status">No requests recorded for this API yet.</div> } @else {
      <div class="table-wrap"><table><thead><tr><th>Time</th><th>Method</th><th>Path</th><th>Status</th><th>Latency</th><th>Outcome</th></tr></thead><tbody>
        @for (l of logs(); track l.id) { <tr class="clickable" tabindex="0" (click)="sel.set(l)" (keydown.enter)="sel.set(l)"><td>{{ time(l.ts) }}</td><td>{{ l.method }}</td><td class="mono">{{ l.path }}</td><td><app-pill [kind]="sk(l.status)" [text]="'' + l.status"/></td><td>{{ ms(l.latencyMs) }}</td><td>{{ l.outcome }}</td></tr> }</tbody></table></div> }</section>
      <app-request-inspector [log]="sel()" (close)="sel.set(null)"/></div>
  } @else if (loading()) { <div class="skeleton" style="height:240px"></div> }
  @else if (error()) { <div class="banner bad" role="alert">{{ error() }}</div> }`,
  styles: [`.head{display:flex;justify-content:space-between;gap:16px;flex-wrap:wrap;align-items:flex-start}.grid{display:grid;grid-template-columns:2fr 1fr;gap:16px}
    dl{display:grid;grid-template-columns:140px 1fr;gap:8px 12px;margin:0}dt{color:var(--muted)}dd{margin:0;word-break:break-word}.split{display:grid;grid-template-columns:1fr;gap:16px}
    @media(min-width:1200px){.split:has(aside){grid-template-columns:2fr 1fr}}@media(max-width:800px){.grid{grid-template-columns:1fr}}`]
})
export class ApiDetailPage implements OnInit {
  id = input.required<string>();
  private api = inject(Api); private ui = inject(Ui);
  a = signal<ApiDef | null>(null); loading = signal(true); error = signal(''); quota = signal<Quota | null>(null); sum = signal<Summary | null>(null);
  series = signal<TimePoint[]>([]); logs = signal<LogView[]>([]); sel = signal<LogView | null>(null);
  totals = computed(() => this.series().map(p => p.total)); errs = computed(() => this.series().map(p => p.errors));
  labels = computed(() => this.series().map(p => new Date(p.ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })));
  time = fmtTime; ms = fmtMs; n = fmtNum; pct = fmtPct; sk = statusKind;
  ngOnInit() { this.load(); }
  load() {
    const id = +this.id(); this.loading.set(true);
    this.api.api(id).subscribe({ next: a => { this.a.set(a); this.loading.set(false); }, error: e => { this.error.set(errorMessage(e)); this.loading.set(false); } });
    this.api.quota(id).subscribe({ next: q => this.quota.set(q), error: () => {} });
    this.api.summary(rangeFrom(24), undefined, id).subscribe({ next: s => this.sum.set(s), error: () => {} });
    this.api.timeseries(rangeFrom(24), undefined, id).subscribe({ next: t => this.series.set(t), error: () => {} });
    this.api.logs({ apiId: id, from: rangeFrom(24), size: 15 }).subscribe({ next: p => this.logs.set(p.items), error: () => {} });
  }
  toggle(a: ApiDef) { this.api.setApiStatus(a.id, a.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE').subscribe({ next: x => { this.a.set(x); this.ui.toast('Status updated.'); }, error: e => this.ui.error(errorMessage(e)) }); }
}
