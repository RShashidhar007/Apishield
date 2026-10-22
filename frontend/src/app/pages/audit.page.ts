import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, errorMessage } from '../core/api.service';
import { AuditEntry, Page } from '../core/models';
import { fmtTime } from '../core/format';
import { PillComponent } from '../shared/ui';

@Component({
  selector: 'app-audit', imports: [FormsModule, PillComponent],
  template: `
  <header class="head"><p class="eyebrow">Administration</p><h1>Audit log</h1><p class="muted">Append-only record of sensitive actions. Secrets, tokens and request bodies are never written here.</p></header>
  <div class="panel"><div class="row wrap" style="margin-bottom:12px">
    <input type="search" aria-label="Action prefix" placeholder="Action (e.g. API_KEY)" [(ngModel)]="action"><input type="search" aria-label="Actor" placeholder="Actor email" [(ngModel)]="actor">
    <select aria-label="Outcome" [(ngModel)]="outcome"><option value="">Any outcome</option><option>SUCCESS</option><option>FAILURE</option><option>DENIED</option></select><button class="btn" (click)="search()">Search</button></div>
    @if (loading()) { <div class="skeleton" style="height:160px"></div> } @else if (error()) { <div class="banner bad" role="alert">{{ error() }}</div> }
    @else if (!data()?.items?.length) { <div class="empty" role="status">No audit entries match.</div> }
    @else { <div class="table-wrap"><table><thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Resource</th><th>Outcome</th><th>Detail</th><th>Correlation</th></tr></thead><tbody>
      @for (a of data()!.items; track a.id) { <tr><td>{{ time(a.ts) }}</td><td>{{ a.actorEmail || '—' }}</td><td class="mono">{{ a.action }}</td><td>{{ a.resourceType }} {{ a.resourceId }}</td>
        <td><app-pill [kind]="a.outcome === 'SUCCESS' ? 'ok' : a.outcome === 'DENIED' ? 'warn' : 'bad'" [text]="a.outcome"/></td><td>{{ a.detail }}</td><td class="mono">{{ a.correlationId?.slice(0, 8) }}</td></tr> }</tbody></table></div>
      <div class="row" style="margin-top:12px"><button class="btn sm" [disabled]="page === 0" (click)="page = page - 1; load()">← Prev</button><span class="hint">Page {{ page + 1 }} of {{ data()!.totalPages || 1 }} · {{ data()!.totalItems }} entries</span><button class="btn sm" [disabled]="page + 1 >= data()!.totalPages" (click)="page = page + 1; load()">Next →</button></div> }
  </div>`,
  styles: [`input[type=search],select{width:auto;min-width:200px}`]
})
export class AuditPage implements OnInit {
  private api = inject(Api); action = ''; actor = ''; outcome = ''; page = 0; data = signal<Page<AuditEntry> | null>(null); loading = signal(true); error = signal(''); time = fmtTime;
  ngOnInit() { this.load(); } search() { this.page = 0; this.load(); }
  load() { this.loading.set(true); this.api.audit({ action: this.action, actor: this.actor, outcome: this.outcome, page: this.page, size: 20 }).subscribe({ next: d => { this.data.set(d); this.loading.set(false); }, error: e => { this.error.set(errorMessage(e)); this.loading.set(false); } }); }
}
