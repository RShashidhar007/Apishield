import { Component, Input } from '@angular/core';
import { fmtNum } from '../core/format';

export interface Series { name: string; color: string; values: number[]; fill?: boolean; }

@Component({
  selector: 'app-line-chart',
  template: `
    @if (!labels.length || !series.length) { <div class="empty" role="status">No data in this range yet.</div> }
    @else {
      <svg [attr.viewBox]="'0 0 600 ' + (height + 28)" role="img" [attr.aria-label]="ariaLabel" preserveAspectRatio="none" style="width:100%">
        @for (g of grid; track g) { <line x1="40" x2="596" [attr.y1]="y(g)" [attr.y2]="y(g)" stroke="var(--line)" /><text x="36" [attr.y]="y(g)+4" text-anchor="end" font-size="10" fill="var(--muted)">{{ fmt(g) }}</text> }
        @for (s of series; track s.name) {
          @if (s.fill) { <path [attr.d]="area(s)" [attr.fill]="s.color" fill-opacity=".16" /> }
          <path [attr.d]="line(s)" fill="none" [attr.stroke]="s.color" stroke-width="2.2" stroke-linejoin="round" stroke-linecap="round" class="draw" />
          @for (v of s.values; track $index) { @if (s.values.length <= 60) { <circle [attr.cx]="x($index)" [attr.cy]="y(v)" r="6" fill="transparent"><title>{{ s.name }} · {{ labels[$index] }} · {{ fmt(v) }}</title></circle> } }
        }
        <text x="40" [attr.y]="height + 20" font-size="10" fill="var(--muted)">{{ labels[0] }}</text>
        <text x="596" [attr.y]="height + 20" font-size="10" fill="var(--muted)" text-anchor="end">{{ labels[labels.length - 1] }}</text>
      </svg>
      <div class="row wrap legend">@for (s of series; track s.name) { <span class="row"><i class="sw" [style.background]="s.color"></i>{{ s.name }}</span> }</div>
    }`,
  styles: [`.legend{gap:16px;font-size:.8rem;color:var(--muted);margin-top:4px}.sw{width:14px;height:4px;border-radius:2px;display:inline-block}
    .draw{stroke-dasharray:2000;stroke-dashoffset:2000;animation:d 900ms var(--ease) forwards}@keyframes d{to{stroke-dashoffset:0}}`]
})
export class LineChartComponent {
  @Input() series: Series[] = []; @Input() labels: string[] = []; @Input() height = 170; @Input() ariaLabel = 'Line chart'; @Input() unit = '';
  get max() { const m = Math.max(1, ...this.series.flatMap(s => s.values)); return m; }
  get grid() { return [0, this.max / 2, this.max]; }
  x(i: number) { const n = Math.max(1, this.labels.length - 1); return 44 + (i / n) * 548; }
  y(v: number) { return 8 + (1 - v / this.max) * (this.height - 16); }
  line(s: Series) { return s.values.map((v, i) => (i ? 'L' : 'M') + this.x(i).toFixed(1) + ' ' + this.y(v).toFixed(1)).join(' '); }
  area(s: Series) { return this.line(s) + ` L${this.x(s.values.length - 1)} ${this.y(0)} L${this.x(0)} ${this.y(0)} Z`; }
  fmt(v: number) { return fmtNum(Math.round(v * 10) / 10) + this.unit; }
}

@Component({
  selector: 'app-pulse-bars',
  template: `
    @if (!total.length) { <div class="empty" role="status">No traffic recorded in this range.</div> }
    @else {
      <svg viewBox="0 0 600 150" preserveAspectRatio="none" style="width:100%" role="img" aria-label="Request volume over time. Successful requests in teal, failed requests in coral.">
        @for (t of total; track $index) {
          <rect [attr.x]="44 + $index * step" [attr.width]="bw" [attr.y]="130 - h(t)" [attr.height]="h(t) - h(errors[$index])" fill="var(--teal)"><title>{{ labels[$index] }} · {{ t }} requests · {{ errors[$index] }} failed</title></rect>
          <rect [attr.x]="44 + $index * step" [attr.width]="bw" [attr.y]="130 - h(errors[$index])" [attr.height]="h(errors[$index])" fill="var(--coral)"/>
        }
        <text x="40" y="14" font-size="10" fill="var(--muted)" text-anchor="end">{{ peak }}</text><text x="40" y="132" font-size="10" fill="var(--muted)" text-anchor="end">0</text>
      </svg>
      <div class="row wrap" style="gap:16px;font-size:.8rem;color:var(--muted)"><span><i class="sw" style="background:var(--teal)"></i> succeeded</span><span><i class="sw" style="background:var(--coral)"></i> failed (≥400)</span></div>
    }`,
  styles: [`.sw{width:10px;height:10px;border-radius:2px;display:inline-block;margin-right:4px}`]
})
export class PulseBarsComponent {
  @Input() total: number[] = []; @Input() errors: number[] = []; @Input() labels: string[] = [];
  get peak() { return Math.max(1, ...this.total); }
  get step() { return 548 / Math.max(1, this.total.length); } get bw() { return Math.max(1, this.step * 0.78); }
  h(v: number) { return (v / this.peak) * 116; }
}

@Component({
  selector: 'app-bar-list',
  template: `@if (!items.length) { <div class="empty" role="status">Nothing to show yet.</div> } @else {
    <ul class="bl">@for (i of items; track i.label) { <li><span class="n">{{ i.label }}</span><span class="b"><span [style.width.%]="(i.count / max) * 100" [style.background]="color(i.label)"></span></span><span class="c">{{ fmt(i.count) }}</span></li> }</ul> }`,
  styles: [`.bl{list-style:none;margin:0;padding:0;display:grid;gap:8px}.bl li{display:grid;grid-template-columns:90px 1fr 56px;gap:10px;align-items:center;font-size:.88rem}
    .b{background:var(--surface-2);height:10px;border-radius:5px;overflow:hidden}.b span{display:block;height:100%;border-radius:5px;transition:width var(--t-slow) var(--ease)}.c{text-align:right;font-variant-numeric:tabular-nums}`]
})
export class BarListComponent {
  @Input() items: { label: string; count: number }[] = []; @Input() colors: Record<string, string> = {};
  private palette = ['var(--teal)', 'var(--cobalt)', 'var(--amber)', 'var(--rose)', 'var(--lavender)', 'var(--lime)'];
  get max() { return Math.max(1, ...this.items.map(i => i.count)); }
  color(l: string) { if (this.colors[l]) return this.colors[l]; if (/^\d{3}$/.test(l)) return l[0] === '2' ? 'var(--lime)' : l[0] === '3' ? 'var(--teal)' : l[0] === '4' ? 'var(--amber)' : 'var(--coral)'; return this.palette[this.items.findIndex(i => i.label === l) % this.palette.length]; }
  fmt = fmtNum;
}

@Component({
  selector: 'app-gauge',
  template: `
    <svg viewBox="0 0 200 120" role="img" [attr.aria-label]="label + ': ' + (limit ? used + ' of ' + limit : 'no limit configured')" style="width:100%;max-width:260px">
      <path d="M20 100 A80 80 0 0 1 180 100" fill="none" stroke="var(--surface-2)" stroke-width="16" stroke-linecap="round"/>
      @if (limit) { <path d="M20 100 A80 80 0 0 1 180 100" fill="none" [attr.stroke]="color" stroke-width="16" stroke-linecap="round" pathLength="100" [attr.stroke-dasharray]="pct + ' 100'" style="transition:stroke-dasharray var(--t-slow) var(--ease)"/> }
      <text x="100" y="88" text-anchor="middle" font-size="26" font-family="var(--font-display)" fill="var(--text)">{{ limit ? pct.toFixed(0) + '%' : '—' }}</text>
      <text x="100" y="108" text-anchor="middle" font-size="10" fill="var(--muted)">{{ limit ? used + ' / ' + limit + ' per ' + window + 's' : 'no limit configured' }}</text>
    </svg>`
})
export class GaugeComponent {
  @Input() used = 0; @Input() limit?: number | null; @Input() window = 60; @Input() label = 'Rate-limit usage';
  get pct() { return this.limit ? Math.min(100, (this.used / this.limit) * 100) : 0; }
  get color() { return this.pct >= 90 ? 'var(--coral)' : this.pct >= 70 ? 'var(--amber)' : 'var(--teal)'; }
}

@Component({
  selector: 'app-donut',
  template: `@if (!total) { <div class="empty" role="status">No data yet.</div> } @else {
    <div class="row wrap"><svg viewBox="0 0 120 120" width="130" height="130" role="img" aria-label="Distribution chart">
      @for (s of segs; track s.label) { <circle cx="60" cy="60" r="40" fill="none" [attr.stroke]="s.color" stroke-width="18" pathLength="100" [attr.stroke-dasharray]="s.len + ' ' + (100 - s.len)" [attr.stroke-dashoffset]="-s.off" transform="rotate(-90 60 60)"><title>{{ s.label }}: {{ s.count }}</title></circle> }
      <text x="60" y="64" text-anchor="middle" font-size="16" font-family="var(--font-display)" fill="var(--text)">{{ fmt(total) }}</text></svg>
      <ul class="lg">@for (s of segs; track s.label) { <li><i [style.background]="s.color"></i>{{ s.label }} <b>{{ fmt(s.count) }}</b></li> }</ul></div> }`,
  styles: [`.lg{list-style:none;margin:0;padding:0;font-size:.85rem;display:grid;gap:4px}.lg i{display:inline-block;width:10px;height:10px;border-radius:50%;margin-right:6px}.lg b{margin-left:6px}`]
})
export class DonutComponent {
  @Input() items: { label: string; count: number }[] = []; fmt = fmtNum;
  private pal = ['var(--teal)', 'var(--amber)', 'var(--coral)', 'var(--cobalt)', 'var(--lavender)', 'var(--rose)'];
  get total() { return this.items.reduce((a, b) => a + b.count, 0); }
  get segs() { let off = 0; return this.items.map((i, k) => { const len = (i.count / this.total) * 100; const s = { ...i, len, off, color: this.pal[k % this.pal.length] }; off += len; return s; }); }
}
