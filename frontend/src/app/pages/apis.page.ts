import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api, errorMessage } from '../core/api.service';
import { ApiDef, ApiStat } from '../core/models';
import { healthOf, rangeFrom } from '../core/format';
import { Ui } from '../core/ui.service';
import { PillComponent } from '../shared/ui';

@Component({
  selector: 'app-apis', imports: [FormsModule, RouterLink, PillComponent],
  template: `
  <header class="head"><div><p class="eyebrow">Registry</p><h1>APIs behind the shield</h1></div><a class="btn primary" routerLink="/apis/new">+ Register API</a></header>
  <div class="panel">
    <div class="row wrap filters">
      <input type="search" placeholder="Search name or path" aria-label="Search APIs" [(ngModel)]="q" (keyup.enter)="load()">
      <select aria-label="Filter by status" [(ngModel)]="status" (change)="load()"><option value="">Active &amp; inactive</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option><option value="ARCHIVED">Archived</option></select>
      <select aria-label="Filter by environment" [(ngModel)]="env" (change)="load()"><option value="">All environments</option><option>DEV</option><option>STAGING</option><option>PROD</option></select>
      <button class="btn" (click)="load()">Search</button>
    </div>
    @if (loading()) { <div class="stack">@for (i of [1,2,3]; track i) { <div class="skeleton" style="height:44px"></div> }</div> }
    @else if (error()) { <div class="banner bad" role="alert">{{ error() }} <button class="btn sm" (click)="load()">Retry</button></div> }
    @else if (!items().length) { <div class="empty"><h3>No APIs found</h3><p>Register an upstream service to route it through the gateway.</p><a class="btn primary" routerLink="/apis/new">Register an API</a></div> }
    @else {
      <div class="table-wrap"><table><caption class="sr-only">Registered APIs</caption><thead><tr><th>API</th><th>Public path</th><th>Env</th><th>Auth</th><th>Rate limit</th><th>Health (15 min)</th><th>Status</th><th><span class="sr-only">Actions</span></th></tr></thead>
        <tbody>@for (a of items(); track a.id) { <tr class="clickable" (click)="open(a.id)">
          <td><a [routerLink]="['/apis', a.id]" (click)="$event.stopPropagation()"><strong>{{ a.name }}</strong></a> <span class="muted">{{ a.version }}</span></td>
          <td class="mono">{{ a.publicPath }}</td><td><app-pill kind="tech" [text]="a.environment"/></td>
          <td><app-pill [kind]="a.authPolicy === 'NONE' ? 'warn' : 'info'" [text]="a.authPolicy.replace('_', ' ')"/></td>
          <td>{{ a.rateLimitEnabled ? a.rateLimitRequests + ' / ' + a.rateLimitWindowSeconds + 's' : 'off' }}</td>
          <td>@switch (health(a.id)) { @case ('HEALTHY') { <app-pill kind="ok" text="Healthy"/> } @case ('DEGRADED') { <app-pill kind="warn" text="Degraded"/> } @case ('FAILING') { <app-pill kind="bad" text="Failing"/> } @default { <app-pill kind="neutral" text="No traffic"/> } }</td>
          <td><app-pill [kind]="a.status === 'ACTIVE' ? 'ok' : a.status === 'INACTIVE' ? 'warn' : 'neutral'" [text]="a.status"/></td>
          <td class="row" (click)="$event.stopPropagation()">
            @if (a.status !== 'ARCHIVED') { <button class="btn sm" (click)="toggle(a)">{{ a.status === 'ACTIVE' ? 'Deactivate' : 'Activate' }}</button>
              <a class="btn sm" [routerLink]="['/apis', a.id, 'edit']">Edit</a><button class="btn sm danger" (click)="archive(a)">Archive</button> }</td></tr> }</tbody></table></div>
    }
  </div>`,
  styles: [`.head{display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:var(--s5);gap:12px;flex-wrap:wrap}h1{margin:0}.filters{margin-bottom:var(--s4)}.filters input{flex:1;min-width:200px}.filters select{width:auto}`]
})
export class ApisPage implements OnInit {
  private api = inject(Api); private ui = inject(Ui); private router = inject(Router);
  items = signal<ApiDef[]>([]); stats = signal<ApiStat[]>([]); loading = signal(true); error = signal('');
  q = ''; status = ''; env = '';
  ngOnInit() { this.load(); }
  health(id: number) { const s = this.stats().find(x => x.apiId === id); return healthOf(s?.total ?? 0, s?.errors ?? 0); }
  open(id: number) { this.router.navigate(['/apis', id]); }
  load() {
    this.loading.set(true); this.error.set('');
    this.api.apis({ q: this.q, status: this.status, environment: this.env }).subscribe({ next: p => { this.items.set(p.items); this.loading.set(false); }, error: e => { this.error.set(errorMessage(e)); this.loading.set(false); } });
    this.api.byApi(rangeFrom(0.25)).subscribe({ next: s => this.stats.set(s), error: () => {} });
  }
  toggle(a: ApiDef) {
    const to = a.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    this.api.setApiStatus(a.id, to).subscribe({ next: () => { this.ui.toast(`${a.name} is now ${to.toLowerCase()}. The gateway picks this up within seconds.`); this.load(); }, error: e => this.ui.error(errorMessage(e)) });
  }
  async archive(a: ApiDef) {
    if (!(await this.ui.confirm('Archive API?', `${a.name} ${a.version} will stop routing traffic and its path ${a.publicPath} will be freed. History is retained.`, 'Archive', true))) return;
    this.api.archiveApi(a.id).subscribe({ next: () => { this.ui.toast('API archived.'); this.load(); }, error: e => this.ui.error(errorMessage(e)) });
  }
}
