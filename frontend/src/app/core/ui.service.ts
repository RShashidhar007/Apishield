import { Injectable, signal } from '@angular/core';

export interface Toast { id: number; kind: 'ok' | 'bad' | 'info'; text: string; }
export interface ConfirmReq { title: string; body: string; confirmLabel: string; danger: boolean; resolve: (v: boolean) => void; }

@Injectable({ providedIn: 'root' })
export class Ui {
  readonly toasts = signal<Toast[]>([]); readonly confirmReq = signal<ConfirmReq | null>(null); private n = 0;
  toast(text: string, kind: Toast['kind'] = 'ok') {
    const id = ++this.n; this.toasts.update(t => [...t, { id, kind, text }]);
    setTimeout(() => this.dismiss(id), kind === 'bad' ? 8000 : 4500);
  }
  error(text: string) { this.toast(text, 'bad'); }
  dismiss(id: number) { this.toasts.update(t => t.filter(x => x.id !== id)); }
  confirm(title: string, body: string, confirmLabel = 'Confirm', danger = false): Promise<boolean> {
    return new Promise(resolve => this.confirmReq.set({ title, body, confirmLabel, danger, resolve: v => { this.confirmReq.set(null); resolve(v); } }));
  }
}
