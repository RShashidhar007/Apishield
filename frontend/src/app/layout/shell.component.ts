import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';

interface NavItem { path: string; label: string; icon: string; show: () => boolean; badge?: boolean; }

@Component({
  selector: 'app-shell', imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <a class="skip" href="#main">Skip to content</a>
    <div class="app" [class.theme-light]="light()" [class.open]="open()">
      <header class="top">
        <button class="btn ghost menu" (click)="open.set(!open())" [attr.aria-expanded]="open()" aria-controls="nav" aria-label="Toggle navigation">☰</button>
        <span class="brand-sm">APIShield</span>
      </header>
      <nav id="nav" class="side" aria-label="Main">
        <div class="brand"><svg viewBox="0 0 32 32" width="30" height="30" aria-hidden="true"><path d="M16 2 L28 7 V16 C28 23 22.5 28 16 30 C9.5 28 4 23 4 16 V7 Z" fill="none" stroke="#35D0BA" stroke-width="2.4"/><circle cx="16" cy="15" r="4" fill="#FF5C5C"/></svg><span>APIShield</span></div>
        <ul>@for (n of items(); track n.path) { <li><a [routerLink]="n.path" routerLinkActive="active" (click)="open.set(false)"><span class="ic" aria-hidden="true">{{ n.icon }}</span>{{ n.label }}
          @if (n.badge && unack() > 0) { <span class="badge" [attr.aria-label]="unack() + ' unacknowledged security events'">{{ unack() }}</span> }</a></li> }</ul>
        <div class="me"><div class="who"><strong>{{ auth.user()?.displayName }}</strong><span class="muted">{{ roles() }}</span></div><button class="btn ghost sm" (click)="auth.logout()">Sign out</button></div>
      </nav>
      <main id="main" tabindex="-1"><router-outlet /></main>
    </div>`,
  styles: [`
    .app{display:grid;grid-template-columns:248px 1fr;min-height:100vh;background:var(--bg);color:var(--text)}
    .side{background:var(--obsidian);color:var(--ivory);padding:20px 14px;display:flex;flex-direction:column;gap:18px;position:sticky;top:0;height:100vh;border-right:1px solid rgba(244,240,232,.1)}
    .brand{display:flex;align-items:center;gap:10px;font-family:var(--font-display);font-size:1.35rem;padding:4px 8px}
    ul{list-style:none;margin:0;padding:0;display:grid;gap:2px;flex:1;align-content:start}
    a{display:flex;align-items:center;gap:10px;padding:9px 12px;border-radius:10px;color:#cfcabd;text-decoration:none;font-weight:500;transition:background var(--t-fast),transform var(--t-fast)}
    a:hover{background:rgba(244,240,232,.07);transform:translateX(2px)}a.active{background:rgba(53,208,186,.14);color:#fff;box-shadow:inset 3px 0 0 var(--lime)}
    .ic{width:20px;text-align:center;color:var(--teal)}.badge{margin-left:auto;background:var(--coral);color:#1a0505;border-radius:999px;font-size:.72rem;font-weight:800;padding:1px 8px}
    .me{border-top:1px solid rgba(244,240,232,.12);padding-top:12px;display:grid;gap:8px;font-size:.85rem}.who{display:grid}.me .btn{color:var(--ivory)}
    main{padding:clamp(16px,3vw,36px);min-width:0;outline:none}.top{display:none}.skip{position:absolute;left:-999px}.skip:focus{left:8px;top:8px;z-index:200;background:var(--amber);color:#000;padding:8px 12px;border-radius:6px}
    @media (max-width:900px){.app{grid-template-columns:1fr}.top{display:flex;align-items:center;gap:8px;padding:8px 12px;background:var(--obsidian);color:var(--ivory);position:sticky;top:0;z-index:20}.brand-sm{font-family:var(--font-display);font-size:1.2rem}
      .top .btn{color:var(--ivory)}.side{position:fixed;z-index:30;inset:0 auto 0 0;width:260px;transform:translateX(-102%);transition:transform var(--t-med) var(--ease)}.open .side{transform:none}}`]
})
export class ShellComponent implements OnInit, OnDestroy {
  auth = inject(AuthService); private api = inject(Api); private router = inject(Router);
  open = signal(false); unack = signal(0); light = signal(this.router.url.startsWith('/portal'));
  private timer?: ReturnType<typeof setInterval>;
  private all: NavItem[] = [
    { path: '/dashboard', label: 'Dashboard', icon: '◈', show: () => this.auth.canManage },
    { path: '/apis', label: 'API registry', icon: '⌁', show: () => this.auth.canManage },
    { path: '/analytics', label: 'Analytics', icon: '∿', show: () => this.auth.canManage },
    { path: '/security', label: 'Security center', icon: '⛨', show: () => this.auth.canManage, badge: true },
    { path: '/keys', label: 'API key vault', icon: '⚿', show: () => true },
    { path: '/portal', label: 'Developer portal', icon: '❖', show: () => true },
    { path: '/users', label: 'Users', icon: '☺', show: () => this.auth.hasRole('ADMIN') },
    { path: '/audit', label: 'Audit log', icon: '☰', show: () => this.auth.hasRole('ADMIN') },
    { path: '/settings', label: 'Settings', icon: '⚙', show: () => true },
  ];
  items = computed(() => this.all.filter(i => i.show()));
  roles = computed(() => (this.auth.user()?.roles ?? []).map(r => r.replace('_', ' ').toLowerCase()).join(', '));

  ngOnInit() {
    this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => this.light.set(this.router.url.startsWith('/portal')));
    if (this.auth.canManage) { this.poll(); this.timer = setInterval(() => this.poll(), 30000); }
  }
  private poll() { this.api.unacknowledged().subscribe({ next: c => this.unack.set(c.unacknowledged), error: () => {} }); }
  ngOnDestroy() { clearInterval(this.timer); }
}
