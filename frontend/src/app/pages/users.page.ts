import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Page, Role, User } from '../core/models';
import { ago } from '../core/format';
import { Ui } from '../core/ui.service';
import { PillComponent } from '../shared/ui';

const ALL: Role[] = ['ADMIN', 'API_MANAGER', 'DEVELOPER'];
@Component({
  selector: 'app-users', imports: [FormsModule, PillComponent],
  template: `
  <header class="head"><div><p class="eyebrow">Administration</p><h1>Users &amp; roles</h1></div><div class="row"><input type="search" aria-label="Search users" placeholder="Search email or name" [(ngModel)]="q" (keyup.enter)="reload()"><button class="btn" (click)="reload()">Search</button></div></header>
  <div class="panel">
    <p class="hint"><strong>Permission matrix.</strong> ADMIN: users, roles, audit, everything below. API_MANAGER: register/configure APIs, view analytics &amp; security events. DEVELOPER: browse active APIs, manage own keys.</p>
    @if (loading()) { <div class="skeleton" style="height:140px"></div> } @else if (error()) { <div class="banner bad" role="alert">{{ error() }}</div> }
    @else if (!data()?.items?.length) { <div class="empty">No users match.</div> }
    @else { <div class="table-wrap"><table><thead><tr><th>User</th><th>Roles</th><th>Status</th><th>Last sign-in</th><th><span class="sr-only">Actions</span></th></tr></thead><tbody>
      @for (u of data()!.items; track u.id) { <tr><td><strong>{{ u.displayName }}</strong><br><span class="muted">{{ u.email }}</span></td>
        <td>@if (editing()?.id === u.id) { <div class="checks">@for (r of roles; track r) { <label><input type="checkbox" [checked]="draft.has(r)" (change)="toggle(r, $any($event.target).checked)"> {{ r }}</label> }</div> } @else { @for (r of u.roles; track r) { <app-pill kind="tech" [text]="r"/> } }</td>
        <td>@if (editing()?.id === u.id) { <label class="row"><input type="checkbox" [(ngModel)]="active"> Active</label> } @else { <app-pill [kind]="u.active ? 'ok' : 'bad'" [text]="u.active ? 'Active' : 'Deactivated'"/> }</td><td>{{ u.lastLoginAt ? ago(u.lastLoginAt) : 'never' }}</td>
        <td class="row">@if (editing()?.id === u.id) { <button class="btn sm primary" (click)="save(u)">Save</button><button class="btn sm ghost" (click)="editing.set(null)">Cancel</button> } @else { <button class="btn sm" (click)="edit(u)">Edit</button> }</td></tr> }</tbody></table></div>
      <div class="row" style="margin-top:12px"><button class="btn sm" [disabled]="page === 0" (click)="page = page - 1; load()">← Prev</button><span class="hint">Page {{ page + 1 }} of {{ data()!.totalPages || 1 }}</span><button class="btn sm" [disabled]="page + 1 >= data()!.totalPages" (click)="page = page + 1; load()">Next →</button></div> }
  </div>`,
  styles: [`.head{display:flex;justify-content:space-between;align-items:flex-end;gap:16px;flex-wrap:wrap;margin-bottom:var(--s5)}h1{margin:0}input[type=search]{width:260px}`]
})
export class UsersPage implements OnInit {
  private api = inject(Api); private ui = inject(Ui); me = inject(AuthService);
  roles = ALL; q = ''; page = 0; data = signal<Page<User> | null>(null); loading = signal(true); error = signal(''); editing = signal<User | null>(null); draft = new Set<string>(); active = true; ago = ago;
  ngOnInit() { this.load(); } reload() { this.page = 0; this.load(); }
  load() { this.loading.set(true); this.api.users(this.q, this.page).subscribe({ next: d => { this.data.set(d); this.loading.set(false); }, error: e => { this.error.set(errorMessage(e)); this.loading.set(false); } }); }
  edit(u: User) { this.editing.set(u); this.draft = new Set(u.roles); this.active = u.active; }
  toggle(r: string, on: boolean) { on ? this.draft.add(r) : this.draft.delete(r); }
  save(u: User) {
    if (!this.draft.size) return this.ui.error('A user needs at least one role.');
    this.api.updateUser(u.id, [...this.draft], this.active).subscribe({ next: () => { this.ui.toast('User updated and audited.'); this.editing.set(null); this.load(); }, error: e => this.ui.error(errorMessage(e)) });
  }
}
