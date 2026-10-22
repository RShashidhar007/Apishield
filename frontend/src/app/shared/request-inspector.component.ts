import { Component, EventEmitter, Input, Output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { LogView } from '../core/models';
import { fmtMs } from '../core/format';
import { PillComponent, statusKind } from './ui';

/** Request Inspector: everything the gateway persisted about one request. Headers and bodies are deliberately NOT stored. */
@Component({
  selector: 'app-request-inspector', imports: [PillComponent, DatePipe],
  template: `@if (log) {
    <aside class="panel insp" aria-label="Request details">
      <div class="panel-head"><h2>Request {{ log.method }}</h2><button class="btn ghost sm right" (click)="close.emit()" aria-label="Close request details">✕</button></div>
      <app-pill [kind]="kind(log.status)" [text]="'HTTP ' + log.status"/> <app-pill kind="tech" [text]="log.outcome"/>
      <dl>
        <dt>Time</dt><dd>{{ log.ts | date:'medium' }}</dd>
        <dt>Path</dt><dd class="mono">{{ log.path }}</dd>
        <dt>Correlation ID</dt><dd class="mono">{{ log.correlationId }}</dd>
        <dt>Total latency</dt><dd>{{ ms(log.latencyMs) }}</dd>
        <dt>Upstream latency</dt><dd>{{ log.upstreamLatencyMs == null ? 'not reached' : ms(log.upstreamLatencyMs) }}</dd>
        <dt>Request size</dt><dd>{{ log.requestBytes }} bytes</dd>
        <dt>Client</dt><dd class="mono">{{ log.clientIp || '—' }}</dd>
        <dt>Credential</dt><dd>{{ log.keyId ? 'API key #' + log.keyId : 'none / JWT' }}</dd>
        @if (log.reason) { <dt>Reason</dt><dd class="mono">{{ log.reason }}</dd> }
      </dl>
      <p class="hint">Request/response headers and bodies are never persisted. The same correlation ID is returned to the client in <code>X-Request-Id</code>.</p>
    </aside> }`,
  styles: [`.insp{position:sticky;top:16px}dl{display:grid;grid-template-columns:130px 1fr;gap:6px 12px;margin:16px 0 8px}dt{color:var(--muted);font-size:.82rem}dd{margin:0;word-break:break-all}`]
})
export class RequestInspectorComponent {
  @Input() log: LogView | null = null; @Output() close = new EventEmitter<void>();
  kind = statusKind; ms = fmtMs;
}
