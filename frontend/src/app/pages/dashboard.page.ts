import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { Api } from '../core/api.service';
import { ApiDef, ApiStat, SecEvent, Summary, TimePoint } from '../core/models';
import { Health, ago, fmtMs, fmtNum, fmtPct, healthColor, healthOf, rangeFrom } from '../core/format';
import { LineChartComponent, PulseBarsComponent } from '../shared/charts';
import { ShieldSceneComponent } from '../shared/shield-scene.component';
import { PillComponent, TiltDirective, sevKind } from '../shared/ui';
import { NetworkMapComponent, ThreatRadarComponent } from '../shared/visuals';

@Component({
  selector: 'app-dashboard',
  imports: [ShieldSceneComponent, LineChartComponent, PulseBarsComponent, NetworkMapComponent, ThreatRadarComponent, PillComponent, TiltDirective, RouterLink],
  template: `
  <header class="head"><div><p class="eyebrow">Command centre</p><h1>Traffic &amp; threats, as they happened.</h1></div>
    <div class="row"><label class="row hint"><input type="checkbox" [checked]="auto()" (change)="toggleAuto($any($event.target).checked)"> Auto-refresh (30 s)</label>
      <button class="btn" (click)="load()" [disabled]="loading()">{{ loading() ? 'Refreshing…' : '↻ Refresh' }}</button></div></header>

  <div class="grid">
    <section class="panel hero" style="grid-area:hero" aria-label="Security core">
      <app-shield-scene [nodes]="apis().length" [requestsPerMinute]="rpm()"/>
      <div class="hero-meta"><span class="eyebrow">Security core</span>
        <span>{{ apis().length }} registered API{{ apis().length === 1 ? '' : 's' }} · {{ rpm() > 0 ? rpm() + ' req/min over the last hour' : 'no requests in the last hour' }}</span></div>
    </section>

    <section class="kpis" style="grid-area:kpi" aria-label="Last 24 hours">
      @if (summary(); as s) {
        <div class="panel kpi" appTilt><span class="v">{{ n(s.total) }}</span><span class="l">requests · 24 h</span></div>
        <div class="panel kpi" appTilt><span class="v" [style.color]="s.errorRate > 0.05 ? 'var(--coral)' : 'var(--lime)'">{{ pct(s.errorRate) }}</span><span class="l">error rate ({{ n(s.errors) }} failed)</span></div>
        <div class="panel kpi" appTilt><span class="v">{{ ms(s.p95LatencyMs) }}</span><span class="l">p95 latency · avg {{ ms(s.avgLatencyMs) }}</span></div>
        <div class="panel kpi" appTilt><span class="v" [style.color]="s.rateLimited ? 'var(--amber)' : 'inherit'">{{ n(s.rateLimited) }}</span><span class="l">rate-limited · {{ n(s.authFailures) }} auth failures</span></div>
      } @else if (loading()) { @for (i of [1,2,3,4]; track i) { <div class="panel"><div class="skeleton" style="height:48px"></div></div> } }
      @else { <div class="panel banner bad" role="alert" style="grid-column:1/-1">Analytics are unavailable right now. {{ err() }}</div> }
    </section>

    <section class="panel" style="grid-area:pulse"><div class="panel-head"><h2>Traffic pulse</h2><span class="hint">last 24 h · real gateway traffic</span></div>
      @if (loading() && !series().length) { <div class="skeleton" style="height:150px"></div> } @else { <app-pulse-bars [total]="totals()" [errors]="errs()" [labels]="labels()"/> }</section>

    <section class="panel" style="grid-area:lat"><div class="panel-head"><h2>Latency landscape</h2><span class="hint">avg &amp; max, ms</span></div>
      <app-line-chart [series]="latSeries()" [labels]="labels()" ariaLabel="Average and maximum latency over time" unit=" ms"/></section>

    <section class="panel" style="grid-area:map"><div class="panel-head"><h2>API network map</h2></div><app-network-map [apis]="apis()" [health]="healthMap()"/></section>

    <section class="panel" style="grid-area:radar"><div class="panel-head"><h2>Threat radar</h2><a class="right hint" routerLink="/security">Open security center →</a></div>
      <app-threat-radar [events]="openEvents()" (select)="go()"/></section>

    <section class="panel" style="grid-area:health"><div class="panel-head"><h2>Service health</h2><span class="hint">last 15 min</span></div>
      @if (!apis().length) { <div class="empty">No APIs registered yet. <a routerLink="/apis/new">Register your first API</a>.</div> }
      <div class="tiles">@for (a of apis(); track a.id) { <a class="tile" appTilt [appTilt]="8" [routerLink]="['/apis', a.id]" [style.borderColor]="color(a.id)">
        <strong>{{ a.name }} <span class="muted">{{ a.version }}</span></strong>
        <app-pill [kind]="pk(a.id)" [text]="label(a.id)"/>
        <span class="hint">{{ stat(a.id)?.total ?? 0 }} req · avg {{ ms(stat(a.id)?.avgLatencyMs ?? null) }}</span>
        <span class="hint">{{ a.status === 'ACTIVE' ? 'Active' : 'Inactive' }} · {{ a.environment }}</span></a> }</div></section>

    <section class="panel" style="grid-area:feed"><div class="panel-head"><h2>Recent security events</h2></div>
      @if (!events().length) { <div class="empty" role="status">No security events recorded.</div> }
      <ul class="feed">@for (e of events(); track e.id) { <li><app-pill [kind]="sk(e.severity)" [text]="e.severity"/><div><strong>{{ e.type.replaceAll('_', ' ') }}</strong><br><span class="hint">{{ e.description }}</span></div><span class="hint">{{ ago(e.createdAt) }}</span></li> }</ul></section>
  </div>`,
  styles: [`
    .head{display:flex;justify-content:space-between;align-items:flex-end;flex-wrap:wrap;gap:16px;margin-bottom:var(--s5)}h1{max-width:20ch;margin:0}
    .grid{display:grid;gap:var(--s4);grid-template-columns:repeat(12,1fr);grid-template-areas:"hero hero hero hero hero hero hero kpi kpi kpi kpi kpi" "pulse pulse pulse pulse pulse pulse pulse lat lat lat lat lat" "map map map map map map map radar radar radar radar radar" "health health health health health health health feed feed feed feed feed"}
    .hero{padding:0;overflow:hidden;position:relative;min-height:360px;background:radial-gradient(circle at 50% 40%,#232838,var(--surface) 70%)}.hero app-shield-scene{height:360px}
    .hero-meta{position:absolute;left:20px;bottom:14px;display:grid;font-size:.85rem;color:var(--muted)}
    .kpis{display:grid;grid-template-columns:1fr 1fr;gap:var(--s4);align-content:stretch}.kpi{align-content:center}
    .tiles{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:12px}.tile{display:grid;gap:6px;padding:14px;border:2px solid var(--line);border-radius:var(--r2);background:var(--surface-2);color:var(--text);text-decoration:none}
    .feed{list-style:none;margin:0;padding:0;display:grid;gap:12px}.feed li{display:grid;grid-template-columns:auto 1fr auto;gap:10px;align-items:start}
    @media (max-width:1100px){.grid{grid-template-columns:1fr;grid-template-areas:"hero" "kpi" "pulse" "lat" "map" "radar" "health" "feed"}}`]
})
export class DashboardPage implements OnInit, OnDestroy {
  private api = inject(Api); private router = inject(Router);
  apis = signal<ApiDef[]>([]); summary = signal<Summary | null>(null); series = signal<TimePoint[]>([]); stats = signal<ApiStat[]>([]); events = signal<SecEvent[]>([]); openEvents = signal<SecEvent[]>([]);
  rpm = signal(0); loading = signal(true); err = signal(''); auto = signal(false); private timer?: ReturnType<typeof setInterval>;
  totals = computed(() => this.series().map(p => p.total)); errs = computed(() => this.series().map(p => p.errors));
  labels = computed(() => this.series().map(p => new Date(p.ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })));
  latSeries = computed(() => [{ name: 'avg latency', color: 'var(--teal)', values: this.series().map(p => Math.round(p.avgLatencyMs)), fill: true }, { name: 'max latency', color: 'var(--amber)', values: this.series().map(p => p.maxLatencyMs) }]);
  healthMap = computed(() => { const m: Record<number, Health> = {}; for (const a of this.apis()) { const s = this.stat(a.id); m[a.id] = healthOf(s?.total ?? 0, s?.errors ?? 0); } return m; });
  n = fmtNum; ms = fmtMs; pct = fmtPct; ago = ago; sk = sevKind;
  stat(id: number) { return this.stats().find(s => s.apiId === id); }
  h(id: number) { return this.healthMap()[id] ?? 'NO_TRAFFIC'; } color(id: number) { return healthColor(this.h(id)); }
  pk(id: number) { return ({ HEALTHY: 'ok', DEGRADED: 'warn', FAILING: 'bad', NO_TRAFFIC: 'neutral' } as const)[this.h(id)]; }
  label(id: number) { return ({ HEALTHY: 'Healthy', DEGRADED: 'Degraded', FAILING: 'Failing', NO_TRAFFIC: 'No recent traffic' })[this.h(id)]; }

  ngOnInit() { this.load(); }
  ngOnDestroy() { clearInterval(this.timer); }
  toggleAuto(on: boolean) { this.auto.set(on); clearInterval(this.timer); if (on) this.timer = setInterval(() => this.load(), 30000); }
  go() { this.router.navigate(['/security']); }

  load() {
    this.loading.set(true); this.err.set(''); let pending = 7;
    const done = () => { if (--pending === 0) this.loading.set(false); };
    const run = <T>(o: Observable<T>, set: (v: T) => void) => o.subscribe({ next: set, error: e => { if (e.status !== 403) this.err.set('Some data could not be loaded.'); done(); }, complete: done });
    run(this.api.apis({ status: 'ACTIVE' }), p => this.apis.set(p.items));
    run(this.api.summary(rangeFrom(24)), s => this.summary.set(s));
    run(this.api.timeseries(rangeFrom(24)), t => this.series.set(t));
    run(this.api.byApi(rangeFrom(0.25)), s => this.stats.set(s));
    run(this.api.summary(rangeFrom(1)), s => this.rpm.set(Math.round(s.total / 60)));
    run(this.api.events({ size: 6 }), p => this.events.set(p.items));
    run(this.api.events({ acknowledged: false, size: 50 }), p => this.openEvents.set(p.items));
  }
}
