import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api, errorMessage } from '../core/api.service';
import { ApiDef } from '../core/models';
import { AuthService } from '../core/auth.service';
import { fmtMs } from '../core/format';
import { PillComponent, statusKind } from '../shared/ui';

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
/** Tiny, safe highlighter: input is HTML-escaped first, then tokens are wrapped. Angular's sanitiser still runs on [innerHTML]. */
export function highlight(code: string): string {
  return esc(code).replace(/(&quot;|"|')(?:(?!\1).)*\1/g, m => `<span class="s">${m}</span>`)
    .replace(/\b(curl|fetch|await|const|import|requests|print|headers|method)\b/g, '<span class="k">$1</span>').replace(/(^|\s)(-[XHd])\b/g, '$1<span class="f">$2</span>');
}

const ERRORS: [string, string, string][] = [
  ['401', 'INVALID_CREDENTIALS', 'Credential missing, invalid, revoked or expired.'], ['403', 'FORBIDDEN', 'Key not permitted for this API or lacks the READ/WRITE scope.'],
  ['404', 'NOT_FOUND', 'No active API is configured for this path.'], ['405', 'METHOD_NOT_ALLOWED', 'Method is not enabled for this API (see the Allow header).'],
  ['413', 'PAYLOAD_TOO_LARGE', 'Request body exceeds the API limit.'], ['429', 'RATE_LIMITED', 'Rate limit exceeded; honour Retry-After.'],
  ['502', 'UPSTREAM_UNAVAILABLE', 'Upstream could not be reached.'], ['503', 'RATE_LIMIT_UNAVAILABLE', 'Limiter unavailable — the gateway fails closed.'], ['504', 'UPSTREAM_TIMEOUT', 'Upstream exceeded the response timeout.'],
];

@Component({
  selector: 'app-portal', imports: [FormsModule, RouterLink, PillComponent],
  template: `
  <header class="head"><p class="eyebrow">Developer portal</p><h1>Build against the gateway</h1><p class="muted">Every call below goes through the real gateway with the same authentication, limits and destination policy as production traffic.</p></header>
  <div class="layout">
    <nav class="panel list" aria-label="API catalog"><h2>Catalog</h2>
      @if (loading()) { <div class="skeleton" style="height:120px"></div> } @else if (error()) { <div class="banner bad" role="alert">{{ error() }}</div> }
      @else if (!apis().length) { <div class="empty">No APIs are published yet.</div> }
      @for (a of apis(); track a.id) { <button class="item" [class.sel]="cur()?.id === a.id" (click)="choose(a)"><strong>{{ a.name }}</strong><span class="muted">{{ a.version }} · {{ a.environment }}</span></button> }</nav>

    @if (cur(); as a) { <div class="stack">
      <section class="panel"><div class="row wrap"><h2 style="margin:0">{{ a.name }} <span class="muted">{{ a.version }}</span></h2><app-pill kind="tech" [text]="a.environment"/></div>
        <p>{{ a.description || 'No description provided.' }}</p>
        <div class="grid-2"><div><span class="eyebrow">Base URL</span><p class="mono">{{ origin }}{{ a.publicPath }}</p></div><div><span class="eyebrow">Methods</span><p>{{ a.methods.join(', ') }}</p></div>
          <div><span class="eyebrow">Authentication</span><p>{{ authText(a) }}</p></div><div><span class="eyebrow">Rate limit</span><p>{{ a.rateLimitEnabled ? a.rateLimitRequests + ' requests / ' + a.rateLimitWindowSeconds + ' s (+' + a.rateLimitBurst + ' burst)' : 'none' }}</p></div></div>
        <p class="hint">Max body {{ a.maxRequestBytes }} bytes · response timeout {{ a.responseTimeoutMs }} ms. Need a key? <a routerLink="/keys">Open the key vault</a>.</p></section>

      <section class="panel"><div class="row"><h2 style="margin:0">Examples</h2>
        <div class="tabs right" role="tablist">@for (t of langs; track t) { <button role="tab" [attr.aria-selected]="lang() === t" (click)="lang.set(t)">{{ t }}</button> }</div></div>
        <pre class="code" tabindex="0" [innerHTML]="html()"></pre></section>

      <section class="panel"><h2>Try it</h2>
        <div class="grid-2"><div class="field"><label for="m">Method</label><select id="m" [(ngModel)]="method">@for (m of testMethods(); track m) { <option>{{ m }}</option> }</select></div>
          <div class="field"><label for="sp">Path after {{ a.publicPath }}</label><input id="sp" type="text" [(ngModel)]="suffix" placeholder="/products"></div></div>
        @if (a.authPolicy === 'API_KEY') { <div class="field"><label for="ak">API key</label><input id="ak" type="password" [(ngModel)]="key" autocomplete="off" placeholder="ask_…"><span class="hint">Held in memory only; never stored or logged by the portal.</span></div> }
        @if (a.authPolicy === 'JWT') { <p class="hint">This API expects a JWT; your current session token is sent automatically.</p> }
        @if (bodyAllowed()) { <div class="field"><label for="bd">JSON body</label><textarea id="bd" rows="4" [(ngModel)]="body"></textarea></div> }
        <button class="btn primary" (click)="send(a)" [disabled]="sending()">{{ sending() ? 'Sending…' : 'Send request' }}</button>
        @if (res(); as r) { <div class="result" role="status"><div class="row"><app-pill [kind]="sk(r.status)" [text]="'HTTP ' + r.status"/><span class="hint">{{ ms(r.ms) }}</span></div>
          <dl>@for (h of r.headers; track h[0]) { <dt>{{ h[0] }}</dt><dd class="mono">{{ h[1] }}</dd> }</dl><pre class="code">{{ r.body }}</pre></div> }
      </section>

      <section class="panel"><h2>Error codes</h2><div class="table-wrap"><table><thead><tr><th>HTTP</th><th>code</th><th>Meaning</th></tr></thead><tbody>@for (e of errors; track e[1]) { <tr><td>{{ e[0] }}</td><td class="mono">{{ e[1] }}</td><td>{{ e[2] }}</td></tr> }</tbody></table></div>
        <p class="hint">Error bodies: <code>{{ '{' }} "code", "message", "correlationId" {{ '}' }}</code>. Quote the correlation ID (also in <code>X-Request-Id</code>) when contacting support.</p></section></div> }
    @else if (!loading()) { <div class="panel empty"><h3>Select an API</h3>Choose an API from the catalog to see its documentation.</div> }
  </div>`,
  styles: [`.head{margin-bottom:var(--s5)}h1{margin-bottom:4px}.layout{display:grid;grid-template-columns:260px 1fr;gap:16px;align-items:start}.list{position:sticky;top:16px;display:grid;gap:8px}
    .item{all:unset;box-sizing:border-box;display:grid;gap:2px;padding:10px 12px;border-radius:10px;border:1px solid var(--line);cursor:pointer;background:var(--surface-2)}.item:hover{border-color:var(--accent)}.item.sel{border-color:var(--coral);box-shadow:inset 3px 0 0 var(--coral)}.item:focus-visible{outline:3px solid var(--focus)}
    .tabs{display:flex;gap:4px}.tabs button{background:none;border:1px solid var(--line);border-radius:999px;padding:4px 12px;font:inherit;cursor:pointer;color:var(--text)}.tabs button[aria-selected=true]{background:var(--obsidian);color:var(--ivory)}
    .code{background:#14161d;color:#e8e4d9;border-radius:10px;padding:14px;overflow:auto;margin:12px 0 0;font-size:.82rem;max-height:340px}.code ::ng-deep .s{color:#C8F169}.code ::ng-deep .k{color:#FFB547}.code ::ng-deep .f{color:#35D0BA}
    .result{margin-top:16px;border-top:1px solid var(--line);padding-top:12px}dl{display:grid;grid-template-columns:200px 1fr;gap:4px 12px;font-size:.85rem}dd{margin:0;word-break:break-all}@media(max-width:900px){.layout{grid-template-columns:1fr}.list{position:static}}`]
})
export class PortalPage implements OnInit {
  private api = inject(Api); private http = inject(HttpClient); private auth = inject(AuthService);
  apis = signal<ApiDef[]>([]); cur = signal<ApiDef | null>(null); loading = signal(true); error = signal(''); sending = signal(false);
  res = signal<{ status: number; ms: number; headers: [string, string][]; body: string } | null>(null);
  langs = ['curl', 'JavaScript', 'Python']; lang = signal('curl'); errors = ERRORS; origin = location.origin;
  method = 'GET'; suffix = ''; key = ''; body = '{}'; sk = statusKind; ms = fmtMs;
  testMethods = computed(() => (this.cur()?.methods ?? []).filter(m => m !== 'OPTIONS'));
  bodyAllowed() { return ['POST', 'PUT', 'PATCH'].includes(this.method); }
  html = computed(() => { const a = this.cur(); return a ? highlight(this.example(a, this.lang())) : ''; });
  ngOnInit() { this.api.apis({ status: 'ACTIVE' }).subscribe({ next: p => { this.apis.set(p.items); this.loading.set(false); if (p.items.length) this.choose(p.items[0]); }, error: e => { this.error.set(errorMessage(e)); this.loading.set(false); } }); }
  choose(a: ApiDef) { this.cur.set(a); this.method = a.methods.find(m => m !== 'OPTIONS') ?? 'GET'; this.res.set(null); }
  authText(a: ApiDef) { return a.authPolicy === 'API_KEY' ? 'API key in the X-API-Key header' : a.authPolicy === 'JWT' ? 'Bearer JWT in the Authorization header' : 'None (public)'; }
  example(a: ApiDef, lang: string) {
    const url = `${this.origin}${a.publicPath}/`, m = a.methods[0] ?? 'GET', h = a.authPolicy === 'API_KEY' ? ['X-API-Key', 'YOUR_API_KEY'] : a.authPolicy === 'JWT' ? ['Authorization', 'Bearer YOUR_JWT'] : null;
    if (lang === 'curl') return `curl -X ${m} "${url}" ${h ? `\\\n  -H "${h[0]}: ${h[1]}"` : ''}`.trimEnd();
    if (lang === 'JavaScript') return `const res = await fetch("${url}", {\n  method: "${m}",${h ? `\n  headers: { "${h[0]}": "${h[1]}" }` : ''}\n});\nconsole.log(res.status, await res.json());`;
    return `import requests\n\nres = requests.request("${m}", "${url}"${h ? `,\n  headers={"${h[0]}": "${h[1]}"}` : ''})\nprint(res.status_code, res.json())`;
  }
  send(a: ApiDef) {
    if (a.authPolicy === 'API_KEY' && !this.key) { this.res.set({ status: 0, ms: 0, headers: [], body: 'Enter an API key first.' }); return; }
    let headers = new HttpHeaders({ Accept: 'application/json' });
    if (a.authPolicy === 'API_KEY') headers = headers.set('X-API-Key', this.key);
    if (a.authPolicy === 'JWT' && this.auth.accessToken()) headers = headers.set('Authorization', `Bearer ${this.auth.accessToken()}`);
    const hasBody = this.bodyAllowed(); if (hasBody) headers = headers.set('Content-Type', 'application/json');
    const path = (this.suffix.startsWith('/') || !this.suffix ? this.suffix : '/' + this.suffix);
    const t0 = performance.now(); this.sending.set(true);
    this.http.request(this.method, `${a.publicPath}${path}`.replace('//', '/').replace(/^\/gateway\/?/, '/gateway/'), { headers, body: hasBody ? this.body : undefined, observe: 'response', responseType: 'text' }).subscribe({
      next: r => this.show(r.status, t0, r.headers, r.body ?? ''), error: e => this.show(e.status ?? 0, t0, e.headers, typeof e.error === 'string' ? e.error : errorMessage(e)) });
  }
  private show(status: number, t0: number, h: HttpHeaders | null, body: string) {
    const keep = ['content-type', 'x-request-id', 'x-ratelimit-limit', 'x-ratelimit-remaining', 'retry-after', 'allow'];
    const hs: [string, string][] = h ? keep.filter(k => h.has(k)).map(k => [k, h.get(k)!]) : [];
    this.res.set({ status, ms: performance.now() - t0, headers: hs, body: body.length > 4000 ? body.slice(0, 4000) + '\n… truncated' : body }); this.sending.set(false);
  }
}
