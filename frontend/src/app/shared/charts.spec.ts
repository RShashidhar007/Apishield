import { TestBed } from '@angular/core/testing';
import { GaugeComponent, LineChartComponent, PulseBarsComponent } from './charts';

describe('chart components', () => {
  it('line chart shows an honest empty state instead of fabricating data', () => {
    const f = TestBed.createComponent(LineChartComponent); f.detectChanges(); expect(f.nativeElement.textContent).toContain('No data in this range yet.');
  });
  it('line chart renders one path per series from supplied values only', () => {
    const f = TestBed.createComponent(LineChartComponent); f.componentInstance.labels = ['a', 'b', 'c']; f.componentInstance.series = [{ name: 's', color: 'red', values: [1, 5, 2] }]; f.detectChanges();
    expect(f.nativeElement.querySelectorAll('path.draw').length).toBe(1);
  });
  it('pulse bars shows an empty state with no traffic', () => { const f = TestBed.createComponent(PulseBarsComponent); f.detectChanges(); expect(f.nativeElement.textContent).toContain('No traffic recorded'); });
  it('gauge caps at 100% and warns by colour and text', () => {
    const f = TestBed.createComponent(GaugeComponent); f.componentInstance.used = 250; f.componentInstance.limit = 100; f.detectChanges();
    expect(f.componentInstance.pct).toBe(100); expect(f.nativeElement.textContent).toContain('100%'); expect(f.componentInstance.color).toBe('var(--coral)');
  });
  it('gauge handles APIs without a limit', () => { const f = TestBed.createComponent(GaugeComponent); f.detectChanges(); expect(f.nativeElement.textContent).toContain('no limit configured'); });
});
