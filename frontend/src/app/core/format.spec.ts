import { ago, fmtMs, fmtNum, fmtPct, healthOf } from './format';

describe('format helpers', () => {
  it('formats numbers, durations and percentages', () => {
    expect(fmtNum(950)).toBe('950'); expect(fmtNum(12500)).toBe('12.5k'); expect(fmtNum(null)).toBe('—');
    expect(fmtMs(240)).toBe('240 ms'); expect(fmtMs(1500)).toBe('1.50 s'); expect(fmtPct(0.0123)).toBe('1.2%');
  });
  it('never reports healthy for an API without traffic', () => { expect(healthOf(0, 0)).toBe('NO_TRAFFIC'); });
  it('classifies health by error ratio', () => { expect(healthOf(100, 1)).toBe('HEALTHY'); expect(healthOf(100, 10)).toBe('DEGRADED'); expect(healthOf(100, 60)).toBe('FAILING'); });
  it('renders relative time', () => { expect(ago(new Date().toISOString())).toBe('just now'); expect(ago(null)).toBe('never'); });
});
