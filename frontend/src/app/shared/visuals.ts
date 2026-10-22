import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ApiDef, SecEvent, Severity } from '../core/models';
import { Health, healthColor } from '../core/format';

/** API Network Map: gateway hub + one node per REAL registered API. Edge motion only for APIs with real recent traffic. */
@Component({
  selector: 'app-network-map',
  template: `
    @if (!apis.length) { <div class="empty" role="status"><h3>No APIs registered</h3>Register an API to see it appear on the map.</div> }
    @else {
      <svg viewBox="0 0 620 340" role="group" aria-label="API network map" style="width:100%">
        @for (n of nodes; track n.api.id) { <line [attr.x1]="310" [attr.y1]="170" [attr.x2]="n.x" [attr.y2]="n.y" [attr.stroke]="n.color" stroke-width="2" [class.flow]="n.health !== 'NO_TRAFFIC'" stroke-dasharray="6 6" opacity=".8"/> }
        <g><circle cx="310" cy="170" r="38" fill="var(--surface-2)" stroke="var(--teal)" stroke-width="3"/><path d="M310 148 L328 156 V172 C328 184 320 190 310 194 C300 190 292 184 292 172 V156 Z" fill="none" stroke="var(--lime)" stroke-width="2.5"/>
          <text x="310" y="222" text-anchor="middle" font-size="12" font-weight="700" fill="var(--text)">APIShield gateway</text></g>
        @for (n of nodes; track n.api.id) {
          <g class="node" tabindex="0" role="link" [attr.aria-label]="n.api.name + ' ' + n.api.version + ', ' + label(n.health) + '. Open details'" (click)="open(n.api.id)" (keydown.enter)="open(n.api.id)">
            <rect [attr.x]="n.x - 62" [attr.y]="n.y - 20" width="124" height="40" rx="10" fill="var(--surface)" [attr.stroke]="n.color" stroke-width="2"/>
            <text [attr.x]="n.x" [attr.y]="n.y - 3" text-anchor="middle" font-size="11" font-weight="700" fill="var(--text)">{{ short(n.api.name) }} {{ n.api.version }}</text>
            <text [attr.x]="n.x" [attr.y]="n.y + 12" text-anchor="middle" font-size="9.5" [attr.fill]="n.color">{{ glyph(n.health) }} {{ label(n.health) }}</text>
          </g>
        }
      </svg>
      <p class="hint">Edges animate only while the API has handled real requests in the last 15 minutes. Click a node for details.</p>
    }`,
  styles: [`.node{cursor:pointer;transition:transform var(--t-med) var(--ease);transform-box:fill-box;transform-origin:center}.node:hover,.node:focus-visible{transform:scale(1.06)}
    .flow{animation:flow 1.2s linear infinite}@keyframes flow{to{stroke-dashoffset:-24}}`]
})
export class NetworkMapComponent {
  @Input() apis: ApiDef[] = []; @Input() health: Record<number, Health> = {};
  private router = inject(Router);
  get nodes() {
    const n = this.apis.length;
    return this.apis.map((api, i) => { const a = (i / n) * Math.PI * 2 - Math.PI / 2, h = this.health[api.id] ?? 'NO_TRAFFIC';
      return { api, health: h, color: healthColor(h), x: 310 + Math.cos(a) * 230, y: 170 + Math.sin(a) * 130 }; });
  }
  short(s: string) { return s.length > 16 ? s.slice(0, 15) + '…' : s; }
  label(h: Health) { return ({ HEALTHY: 'Healthy', DEGRADED: 'Degraded', FAILING: 'Failing', NO_TRAFFIC: 'No recent traffic' })[h]; }
  glyph(h: Health) { return ({ HEALTHY: '✓', DEGRADED: '▲', FAILING: '✕', NO_TRAFFIC: '○' })[h]; }
  open(id: number) { this.router.navigate(['/apis', id]); }
}

/** Threat Radar: unacknowledged persisted security events only. Ring = severity, angle = event id (stable), shape = severity (not colour alone). */
@Component({
  selector: 'app-threat-radar',
  template: `
    <svg viewBox="0 0 260 260" role="group" aria-label="Threat radar of unacknowledged security events" style="width:100%;max-width:340px">
      @for (r of rings; track r.sev) { <circle cx="130" cy="130" [attr.r]="r.r" fill="none" stroke="var(--line)" stroke-dasharray="3 4"/><text x="130" [attr.y]="130 - r.r + 10" text-anchor="middle" font-size="8" fill="var(--muted)">{{ r.sev }}</text> }
      <line x1="130" y1="10" x2="130" y2="250" stroke="var(--line)"/><line x1="10" y1="130" x2="250" y2="130" stroke="var(--line)"/>
      @for (e of events; track e.id) {
        <g tabindex="0" role="button" style="cursor:pointer" [attr.aria-label]="e.severity + ' ' + e.type" (click)="select.emit(e)" (keydown.enter)="select.emit(e)" [attr.transform]="pos(e)">
          @switch (e.severity) {
            @case ('CRITICAL') { <path d="M0 -8 L8 0 L0 8 L-8 0 Z" fill="var(--coral)"/> }
            @case ('HIGH') { <rect x="-6" y="-6" width="12" height="12" fill="var(--amber)"/> }
            @case ('MEDIUM') { <path d="M0 -8 L8 6 L-8 6 Z" fill="var(--rose)"/> }
            @default { <circle r="6" fill="var(--teal)"/> }
          }<title>{{ e.severity }} · {{ e.type }} · {{ e.description }}</title>
        </g>
      }
      <circle cx="130" cy="130" r="4" fill="var(--lime)"/>
    </svg>
    @if (!events.length) { <p class="hint" role="status">No unacknowledged security events.</p> }`
})
export class ThreatRadarComponent {
  @Input() events: SecEvent[] = []; @Output() select = new EventEmitter<SecEvent>();
  rings: { sev: Severity; r: number }[] = [{ sev: 'CRITICAL', r: 30 }, { sev: 'HIGH', r: 62 }, { sev: 'MEDIUM', r: 94 }, { sev: 'LOW', r: 120 }];
  pos(e: SecEvent) { const r = this.rings.find(x => x.sev === e.severity)!.r, a = ((e.id * 137.508) % 360) * Math.PI / 180; return `translate(${130 + Math.cos(a) * r} ${130 + Math.sin(a) * r})`; }
}
