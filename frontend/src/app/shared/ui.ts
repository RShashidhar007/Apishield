import { Component, Directive, ElementRef, HostListener, Input, inject, numberAttribute } from '@angular/core';
import { Ui } from '../core/ui.service';

export type PillKind = 'ok' | 'bad' | 'warn' | 'info' | 'neutral' | 'tech';
const GLYPH: Record<PillKind, string> = { ok: '✓', bad: '✕', warn: '▲', info: '●', neutral: '○', tech: '◆' };

@Component({ selector: 'app-pill', template: `<span class="pill" [class]="kind"><span aria-hidden="true">{{ g }}</span>{{ text }}</span>` })
export class PillComponent { @Input() kind: PillKind = 'neutral'; @Input() text = ''; get g() { return GLYPH[this.kind]; } }

export const sevKind = (s: string): PillKind => s === 'CRITICAL' ? 'bad' : s === 'HIGH' ? 'warn' : s === 'MEDIUM' ? 'info' : 'neutral';
export const statusKind = (s: number): PillKind => s < 300 ? 'ok' : s < 400 ? 'info' : s < 500 ? 'warn' : 'bad';

@Component({
  selector: 'app-toast-host',
  template: `<div class="toasts" aria-live="polite" role="status">@for (t of ui.toasts(); track t.id) {
    <div class="toast" [class]="t.kind"><span>{{ t.text }}</span><button class="btn ghost sm" (click)="ui.dismiss(t.id)" aria-label="Dismiss notification">✕</button></div> }</div>`,
  styles: [`.toasts{position:fixed;right:16px;bottom:16px;display:grid;gap:8px;z-index:100;max-width:min(420px,calc(100vw - 32px))}
    .toast{display:flex;gap:12px;align-items:center;justify-content:space-between;padding:12px 14px;border-radius:10px;background:var(--surface);border:1px solid var(--line);border-left:4px solid var(--teal);box-shadow:var(--shadow);animation:in var(--t-med) var(--ease)}
    .toast.ok{border-left-color:var(--lime)}.toast.bad{border-left-color:var(--coral)}@keyframes in{from{transform:translateY(8px);opacity:0}}`]
})
export class ToastHostComponent { ui = inject(Ui); }

@Component({
  selector: 'app-confirm-host',
  template: `@if (ui.confirmReq(); as c) {
    <div class="scrim" (click)="c.resolve(false)"></div>
    <div class="dlg panel" role="alertdialog" aria-modal="true" aria-labelledby="cft" aria-describedby="cfb" (keydown.escape)="c.resolve(false)">
      <h2 id="cft">{{ c.title }}</h2><p id="cfb">{{ c.body }}</p>
      <div class="row"><button class="btn ghost" (click)="c.resolve(false)">Cancel</button><button #ok class="btn" [class.danger]="c.danger" [class.primary]="!c.danger" (click)="c.resolve(true)" autofocus>{{ c.confirmLabel }}</button></div>
    </div> }`,
  styles: [`.scrim{position:fixed;inset:0;background:rgba(0,0,0,.55);z-index:90}.dlg{position:fixed;z-index:91;left:50%;top:50%;transform:translate(-50%,-50%);width:min(440px,calc(100vw - 32px))}.dlg .row{justify-content:flex-end}`]
})
export class ConfirmHostComponent { ui = inject(Ui); }

/** Subtle 3D tilt on hover; disabled for reduced-motion and coarse pointers. */
@Directive({ selector: '[appTilt]' })
export class TiltDirective {
  private el = inject(ElementRef<HTMLElement>); private on = !window.matchMedia('(prefers-reduced-motion: reduce)').matches && window.matchMedia('(pointer: fine)').matches;
  @Input({ transform: (v: unknown) => numberAttribute(v, 5) }) appTilt = 5;
  @HostListener('pointermove', ['$event']) move(e: PointerEvent) {
    if (!this.on) return; const r = this.el.nativeElement.getBoundingClientRect(); const x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
    this.el.nativeElement.style.transform = `perspective(900px) rotateX(${(-y * this.appTilt).toFixed(2)}deg) rotateY(${(x * this.appTilt).toFixed(2)}deg) translateZ(0)`;
  }
  @HostListener('pointerleave') leave() { this.el.nativeElement.style.transform = ''; }
}
