import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, errorMessage } from '../core/api.service';
import { ApiDef, ApiStat, Bucket, LogView, Page, Summary, TimePoint } from '../core/models';
import { fmtMs, fmtNum, fmtPct, fmtTime, rangeFrom } from '../core/format';
import { BarListComponent, DonutComponent, LineChartComponent, PulseBarsComponent } from '../shared/charts';
import { RequestInspectorComponent } from '../shared/request-inspector.component';
import { PillComponent, statusKind } from '../shared/ui';

@Component({
  selector: 'app-analytics', imports: [FormsModule, LineChartComponent, PulseBarsComponent, BarListComponent, DonutComponent, RequestInspectorComponent, PillComponent],
  template: `
  <header class="head"><div><p class="eyebrow">Analytics</p><h1>What actually crossed the gateway</h1></div>
    <div class="row wrap"><select aria-label="Time range" [(ngModel)]="hours" (change)="load()"><option [ngValue]="1">Last hour</option><option [ngValue]="6">Last 6 hours</option><option [ngValue]="24">Last 24 hours</option><option [ngValue]="168">Last 7 days</option></select>
      <select aria-label="API filter" [(ngModel)]="apiId" (change)="load()"><option [ngValue]="null">All APIs</option>@for (a of apis(); track a.id) { <option [ngValue]="a.id">{{ a.name }} {{ a.version }}</option> }</select>
      <button class="btn" (click)="load()" [disabled]="loading()">↻ Refresh</button></div></header>
  @if (error()) { <div class="banner bad" role="alert">{{ error() }}</div> }
  @if (sum(); as s) { <div class="kpis">
    <div class="panel kpi"><span class="v">{{ n(s.total) }}</span><span class="l">requests</span></div><div class="panel kpi"><span class="v">{{ pct(s.errorRate) }}</span><span class="l">error rate</span></div>
    <div class="panel kpi"><span class="v">{{ ms(s.avgLatencyMs) }}</span><span class="l">avg latency</span></div><div class="panel kpi"><span class="v">{{ ms(s.p95LatencyMs) }}</span><span class="l">p95 latency</span></div>
    <div class="panel kpi"><span class="v">{{ ms(s.avgUpstreamLatencyMs) }}</span><span class="l">avg upstream</span></div><div class="panel kpi"><span class="v">{{ n(s.rateLimited) }}</span><span class="l">rate-limited</span></div>
    <div class="panel kpi"><span class="v">{{ n(s.authFailures) }}</span><span class="l">auth failures</span></div><div class="panel kpi"><span class="v">{{ n(s.policyRejections) }}</span><span class="l">policy rejections</span></div></div> }
  @else if (loading()) { <div class="skeleton" style="height:90px"></div> }

  <div class="grid">
    <section class="panel wide"><h2>Traffic over time</h2><app-pulse-bars [total]="totals()" [errors]="errs()" [labels]="labels()"/></section>
    <section class="panel"><h2>Status codes</h2><app-donut [items]="statuses()"/></section>
    <section class="panel wide"><h2>Latency trend</h2><app-line-chart [series]="lat()" [labels]="labels()" unit=" ms" ariaLabel="Latency over time"/></section>
    <section class="panel"><h2>HTTP methods</h2><app-bar-list [items]="methods()"/></section>
    <section class="panel wide"><h2>API comparison</h2>
      @if (!stats().length) { <div class="empty">No traffic to compare yet.</div> } @else {
      <div class="table-wrap"><table><thead><tr><th>API</th><th>Requests</th><th>Errors</th><th>Error rate</th><th>Avg latency</th><th>Last seen</th></tr></thead><tbody>
        @for (s of stats(); track s.apiId ?? -1) { <tr><td>{{ s.name ? s.name + ' ' + s.version : 'Unmatched routes' }}</td><td>{{ n(s.total) }}</td><td>{{ n(s.errors) }}</td><td>{{ pct(s.total ? s.errors / s.total : 0) }}</td><td>{{ ms(s.avgLatencyMs) }}</td><td>{{ time(s.lastSeen) }}</td></tr> }</tbody></table></div> }</section>
  </div>

  <section class="panel" style="margin-top:16px"><div class="panel-head"><h2>Request log</h2>
    <select class="right" aria-label="Outcome filter" [(ngModel)]="outcome" (change)="page = 0; loadLogs()"><option value="">All outcomes</option><option>OK</option><option>AUTH_FAILED</option><option>FORBIDDEN</option><option>RATE_LIMITED</option><option>METHOD_NOT_ALLOWED</option><option>PAYLOAD_TOO_LARGE</option><option>UPSTREAM_TIMEOUT</option><option>UPSTREAM_UNAVAILABLE</option><option>DESTINATION_BLOCKED</option><option>NOT_FOUND</option></select></div>
    @if (correlationId()) { <div class="banner">Filtered by correlation ID <code>{{ correlationId() }}</code></div> }
    <div class="split">
      <div>@if (!logs()?.items?.length) { <div class="empty" role="status">No requests match.</div> } @else {
        <div class="table-wrap"><table><thead><tr><th>Time</th><th>Method</th><th>Path</th><th>Status</th><th>Latency</th><th>Outcome</th></tr></thead><tbody>
          @for (l of logs()!.items; track l.id) { <tr class="clickable" tabindex="0" (click)="sel.set(l)" (keydown.enter)="sel.set(l)"><td>{{ time(l.ts) }}</td><td>{{ l.method }}</td><td class="mono">{{ l.path }}</td><td><app-pill [kind]="sk(l.status)" [text]="'' + l.status"/></td><td>{{ ms(l.latencyMs) }}</td><td>{{ l.outcome }}</td></tr> }</tbody></table></div>
        <div class="row" style="margin-top:12px"><button class="btn sm" [disabled]="page === 0" (click)="page = page - 1; loadLogs()">← Prev</button><span class="hint">Page {{ page + 1 }} of {{ logs()!.totalPages || 1 }} · {{ logs()!.totalItems }} requests</span><button class="btn sm" [disabled]="page + 1 >= logs()!.totalPages" (click)="page = page + 1; loadLogs()">Next →</button></div> }</div>
      <app-request-inspector [log]="sel()" (close)="sel.set(null)"/></div></section>`,
  styles: [`.head{display:flex;justify-content:space-between;align-items:flex-end;gap:16px;flex-wrap:wrap;margin-bottom:var(--s5)}h1{margin:0}select{width:auto}
    .kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin-bottom:16px}.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}.wide{grid-column:span 2}
    .split{display:grid;gap:16px;grid-template-columns:1fr}@media(min-width:1200px){.split:has(aside){grid-template-columns:2fr 1fr}}@media(max-width:900px){.grid{grid-template-columns:1fr}.wide{grid-column:auto}}`]
})
export class AnalyticsPage implements OnInit {
  correlationId = input<string>();
  private api = inject(Api);
  hours = 24; apiId: number | null = null; outcome = ''; page = 0;
  apis = signal<ApiDef[]>([]); sum = signal<Summary | null>(null); series = signal<TimePoint[]>([]); statuses = signal<Bucket[]>([]); methods = signal<Bucket[]>([]); stats = signal<ApiStat[]>([]);
  logs = signal<Page<LogView> | null>(null); sel = signal<LogView | null>(null); loading = signal(true); error = signal('');
  totals = computed(() => this.series().map(p => p.total)); errs = computed(() => this.series().map(p => p.errors));
  labels = computed(() => this.series().map(p => new Date(p.ts).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })));
  lat = computed(() => [{ name: 'avg', color: 'var(--teal)', values: this.series().map(p => Math.round(p.avgLatencyMs)), fill: true }, { name: 'max', color: 'var(--amber)', values: this.series().map(p => p.maxLatencyMs) }]);
  n = fmtNum; ms = fmtMs; pct = fmtPct; time = fmtTime; sk = statusKind;
  ngOnInit() { if (this.correlationId()) this.hours = 168; this.api.apis().subscribe({ next: p => this.apis.set(p.items), error: () => {} }); this.load(); }
  load() {
    this.loading.set(true); this.error.set(''); const from = rangeFrom(this.hours), id = this.apiId ?? undefined; const fail = (e: unknown) => this.error.set(errorMessage(e));
    this.api.summary(from, undefined, id).subscribe({ next: s => { this.sum.set(s); this.loading.set(false); }, error: e => { fail(e); this.loading.set(false); } });
    this.api.timeseries(from, undefined, id).subscribe({ next: t => this.series.set(t), error: fail });
    this.api.statusCodes(from, undefined, id).subscribe({ next: b => this.statuses.set(b) });
    this.api.methods(from, undefined, id).subscribe({ next: b => this.methods.set(b) });
    this.api.byApi(from).subscribe({ next: s => this.stats.set(s) });
    this.page = 0; this.loadLogs();
  }
  loadLogs() {
    this.api.logs({ from: rangeFrom(this.hours), apiId: this.apiId ?? undefined, outcome: this.outcome, correlationId: this.correlationId(), page: this.page, size: 15 }).subscribe({ next: p => this.logs.set(p), error: e => this.error.set(errorMessage(e)) });
  }
}
