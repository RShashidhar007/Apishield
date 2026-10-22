export const fmtNum = (n: number | undefined | null) => n == null ? '—' : n >= 10000 ? (n / 1000).toFixed(1) + 'k' : n.toLocaleString();
export const fmtMs = (n: number | undefined | null) => n == null ? '—' : n >= 1000 ? (n / 1000).toFixed(2) + ' s' : Math.round(n) + ' ms';
export const fmtPct = (n: number) => (n * 100).toFixed(n > 0 && n < 0.01 ? 2 : 1) + '%';
export const fmtTime = (s?: string | null) => s ? new Date(s).toLocaleString() : '—';
export const ago = (s?: string | null) => {
  if (!s) return 'never'; const d = (Date.now() - new Date(s).getTime()) / 1000;
  if (d < 60) return 'just now'; if (d < 3600) return Math.floor(d / 60) + ' min ago'; if (d < 86400) return Math.floor(d / 3600) + ' h ago'; return Math.floor(d / 86400) + ' d ago';
};
export type Health = 'HEALTHY' | 'DEGRADED' | 'FAILING' | 'NO_TRAFFIC';
export const healthOf = (total: number, errors: number): Health => total === 0 ? 'NO_TRAFFIC' : errors / total < 0.05 ? 'HEALTHY' : errors / total < 0.25 ? 'DEGRADED' : 'FAILING';
export const healthColor = (h: Health) => ({ HEALTHY: 'var(--lime)', DEGRADED: 'var(--amber)', FAILING: 'var(--coral)', NO_TRAFFIC: 'var(--muted)' })[h];
export const rangeFrom = (hours: number) => new Date(Date.now() - hours * 3600_000).toISOString();
