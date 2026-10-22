import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api, errorMessage } from '../core/api.service';
import { Page, SecEvent } from '../core/models';
import { ago, fmtTime } from '../core/format';
import { Ui } from '../core/ui.service';
import { PillComponent, sevKind } from '../shared/ui';
import { ThreatRadarComponent } from '../shared/visuals';

@Component({
  selector: 'app-security', imports: [FormsModule, RouterLink, PillComponent, ThreatRadarComponent],
  template: `
  <header class="head"><div><p class="eyebrow">Security center</p><h1>Incidents, derived from real events</h1></div></header>
  <p class="muted">Detections are explainable threshold rules over persisted gateway traffic — no machine learning is claimed. Thresholds are shown in Settings.</p>
  <div class="layout">
    <section class="panel">
      <div class="row wrap" style="margin-bottom:12px"><select aria-label="Severity" [(ngModel)]="severity" (change)="reset()"><option value="">All severities</option><option>CRITICAL</option><option>HIGH</option><option>MEDIUM</option><option>LOW</option></select>
        <select aria-label="Acknowledgement" [(ngModel)]="ack" (change)="reset()"><option value="">Open &amp; acknowledged</option><option value="false">Open only</option><option value="true">Acknowledged only</option></select>
        <button class="btn" (click)="load()">↻ Refresh</button></div>
      @if (loading()) { <div class="skeleton" style="height:160px"></div> }
      @else if (error()) { <div class="banner bad" role="alert">{{ error() }}</div> }
      @else if (!data()?.items?.length) { <div class="empty" role="status"><h3>No security events</h3>Events appear here when thresholds are crossed by real traffic.</div> }
      @else { <ul class="feed">@for (e of data()!.items; track e.id) { <li><button class="item" [class.sel]="sel()?.id === e.id" (click)="sel.set(e)">
        <app-pill [kind]="sk(e.severity)" [text]="e.severity"/><span class="t"><strong>{{ e.type.replaceAll('_', ' ') }}</strong><span class="hint">{{ e.description }}</span></span><span class="hint">{{ ago(e.createdAt) }}</span>
        @if (e.acknowledged) { <app-pill kind="ok" text="Acknowledged"/> }</button></li> }</ul>
        <div class="row"><button class="btn sm" [disabled]="page === 0" (click)="page = page - 1; load()">← Prev</button><span class="hint">Page {{ page + 1 }} of {{ data()!.totalPages || 1 }}</span><button class="btn sm" [disabled]="page + 1 >= data()!.totalPages" (click)="page = page + 1; load()">Next →</button></div> }
    </section>
    <aside class="stack">
      <section class="panel"><h2>Threat radar</h2><app-threat-radar [events]="open()" (select)="sel.set($event)"/></section>
      @if (sel(); as e) { <section class="panel" aria-label="Event detail"><div class="panel-head"><h2>Event #{{ e.id }}</h2><app-pill class="right" [kind]="sk(e.severity)" [text]="e.severity"/></div>
        <p><strong>{{ e.type.replaceAll('_', ' ') }}</strong><br>{{ e.description }}</p>
        <dl><dt>Detected</dt><dd>{{ time(e.createdAt) }}</dd><dt>Client</dt><dd class="mono">{{ e.clientIp || '—' }}</dd><dt>API id</dt><dd>@if (e.apiId) { <a [routerLink]="['/apis', e.apiId]">#{{ e.apiId }}</a> } @else { — }</dd>
          <dt>Occurrences</dt><dd>{{ e.occurrences }}</dd><dt>Correlation</dt><dd class="mono">@if (e.correlationId) { <a routerLink="/analytics" [queryParams]="{ correlationId: e.correlationId }">{{ e.correlationId }}</a> } @else { — }</dd></dl>
        @if (e.acknowledged) { <div class="banner"><strong>Acknowledged</strong> {{ time(e.acknowledgedAt) }}<br>{{ e.note || 'No investigation note.' }}</div> }
        @else { <div class="field"><label for="note">Investigation note</label><textarea id="note" rows="3" [(ngModel)]="note" maxlength="1000"></textarea></div><button class="btn primary" (click)="acknowledge(e)" [disabled]="busy()">Acknowledge</button> }
      </section> }
    </aside></div>`,
  styles: [`.head{margin-bottom:8px}h1{margin:0}.layout{display:grid;grid-template-columns:3fr 2fr;gap:16px}select{width:auto}.feed{list-style:none;margin:0 0 12px;padding:0;display:grid;gap:8px}
    .item{all:unset;box-sizing:border-box;width:100%;display:grid;grid-template-columns:auto 1fr auto auto;gap:12px;align-items:center;padding:12px;border-radius:10px;border:1px solid var(--line);cursor:pointer;background:var(--surface-2);transition:transform var(--t-fast)}
    .item:hover{transform:translateX(3px)}.item:focus-visible{outline:3px solid var(--focus)}.item.sel{border-color:var(--coral);box-shadow:inset 3px 0 0 var(--coral)}.t{display:grid}
    dl{display:grid;grid-template-columns:100px 1fr;gap:6px 10px}dt{color:var(--muted)}dd{margin:0;word-break:break-all}@media(max-width:1000px){.layout{grid-template-columns:1fr}}`]
})
export class SecurityPage implements OnInit {
  private api = inject(Api); private ui = inject(Ui);
  severity = ''; ack = ''; page = 0; note = ''; data = signal<Page<SecEvent> | null>(null); open = signal<SecEvent[]>([]); sel = signal<SecEvent | null>(null);
  loading = signal(true); error = signal(''); busy = signal(false); sk = sevKind; ago = ago; time = fmtTime;
  ngOnInit() { this.load(); }
  reset() { this.page = 0; this.load(); }
  load() {
    this.loading.set(true); this.error.set('');
    this.api.events({ severity: this.severity, acknowledged: this.ack === '' ? undefined : this.ack === 'true', page: this.page, size: 10 }).subscribe({ next: p => { this.data.set(p); this.loading.set(false); }, error: e => { this.error.set(errorMessage(e)); this.loading.set(false); } });
    this.api.events({ acknowledged: false, size: 50 }).subscribe({ next: p => this.open.set(p.items), error: () => {} });
  }
  acknowledge(e: SecEvent) {
    this.busy.set(true);
    this.api.acknowledge(e.id, this.note).subscribe({ next: u => { this.busy.set(false); this.note = ''; this.sel.set(u); this.ui.toast('Event acknowledged and audited.'); this.load(); }, error: x => { this.busy.set(false); this.ui.error(errorMessage(x)); } });
  }
}
