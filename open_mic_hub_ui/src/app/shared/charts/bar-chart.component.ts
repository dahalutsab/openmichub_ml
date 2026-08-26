import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { SeriesPoint, compact, niceCeiling, round } from './chart.types';

/**
 * Column chart for bucketed volume.
 *
 * Bars rather than a line whenever the buckets are countable and discrete —
 * a line implies a value existed between Tuesday and Wednesday, which for a
 * count of bookings it did not.
 */
@Component({
  selector: 'omh-bar-chart',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- All-zero buckets are an empty period, not a chart. -->
    <div class="flex flex-col items-center justify-center text-center"
         [style.height.px]="height()"
         *ngIf="isEmpty(); else chart">
      <i class="bi bi-bar-chart mb-2 text-xl text-muted/40"></i>
      <p class="text-sm text-muted">{{ emptyText() }}</p>
    </div>

    <ng-template #chart>
    <div class="relative w-full" (mouseleave)="active.set(null)">
      <svg [attr.viewBox]="'0 0 1000 ' + height()"
           class="w-full"
           [style.height.px]="height()"
           role="img"
           [attr.aria-label]="ariaLabel()">

        <g>
          <line *ngFor="let tick of ticks()"
                x1="0" [attr.y1]="tick.y" x2="1000" [attr.y2]="tick.y"
                stroke="rgb(var(--omh-line))" stroke-width="1" />
        </g>

        <g *ngFor="let bar of bars(); let i = index"
           (mouseenter)="active.set(i)">
          <!-- Full-height hit area, so thin bars are still easy to hover. -->
          <rect [attr.x]="bar.x" y="0" [attr.width]="bar.slot" [attr.height]="height()"
                fill="transparent" />
          <rect [attr.x]="bar.bx" [attr.y]="bar.y"
                [attr.width]="bar.w" [attr.height]="bar.h"
                [attr.fill]="active() === i ? activeColor() : color()"
                [attr.rx]="radius()" />
        </g>
      </svg>

      <div class="mt-1.5 flex justify-between text-micro text-muted" *ngIf="showAxis()">
        <span *ngFor="let label of axisLabels()">{{ label }}</span>
      </div>

      <div *ngIf="activeBar() as bar"
           class="omh-tooltip"
           [style.left.%]="tooltipLeft()"
           [style.transform]="tooltipTransform()"
           style="top: 0">
        <div class="omh-tooltip-label">{{ formatFull(bar.point) }}</div>
        <div class="font-semibold">
          {{ prefix() }}{{ bar.point.primary | number:'1.0-2' }}
          <span class="font-normal text-muted">{{ valueLabel() }}</span>
        </div>
      </div>
    </div>
    </ng-template>
  `,
})
export class BarChartComponent {
  readonly data = input<SeriesPoint[]>([]);
  readonly height = input(160);
  readonly color = input('rgb(var(--omh-accent) / 0.55)');
  readonly activeColor = input('rgb(var(--omh-accent))');
  readonly valueLabel = input('bookings');
  readonly showAxis = input(true);
  readonly prefix = input('');
  readonly emptyText = input('No activity in this period');

  readonly active = signal<number | null>(null);

  /** True when there is nothing to plot: no buckets, or every bucket is zero. */
  isEmpty(): boolean {
    const points = this.data();
    if (!points.length) return true;
    return points.every(p => !p.primary);
  }

  private readonly padTop = 10;

  readonly max = computed(() => {
    const points = this.data();
    if (!points.length) return 1;
    return niceCeiling(Math.max(...points.map(p => p.primary), 0));
  });

  readonly bars = computed(() => {
    const points = this.data();
    if (!points.length) return [];
    const max = this.max();
    const h = this.height();
    const usable = h - this.padTop;
    const slot = 1000 / points.length;

    // Gap scales with how many bars there are: at 90 buckets a fixed gap eats
    // the bar entirely, and at 6 buckets a proportional one looks gappy.
    const gap = Math.min(slot * 0.3, 8);
    const w = Math.max(slot - gap, 1);

    return points.map((point, i) => {
      const barHeight = max === 0 ? 0 : (point.primary / max) * usable;
      return {
        x: round(i * slot),
        slot: round(slot),
        bx: round(i * slot + gap / 2),
        w: round(w),
        // A zero-height rect renders nothing; a 2px stub shows the bucket exists.
        h: round(Math.max(barHeight, point.primary > 0 ? 2 : 0)),
        y: round(this.padTop + usable - Math.max(barHeight, point.primary > 0 ? 2 : 0)),
        point,
      };
    });
  });

  readonly radius = computed(() => {
    const points = this.data();
    if (!points.length) return 2;
    // Rounded corners disappear below a few px of width and just cost DOM.
    return 1000 / points.length > 12 ? 3 : 1;
  });

  readonly ticks = computed(() => {
    const max = this.max();
    const h = this.height();
    const usable = h - this.padTop;
    return [0, 0.5, 1].map(fraction => ({
      y: round(this.padTop + usable - fraction * usable),
      label: compact(max * fraction),
    }));
  });

  readonly activeBar = computed(() => {
    const index = this.active();
    if (index === null) return null;
    return this.bars()[index] ?? null;
  });

  readonly ariaLabel = computed(() => {
    const points = this.data();
    if (!points.length) return 'No data';
    const total = points.reduce((sum, p) => sum + p.primary, 0);
    return `${points.length} buckets, total ${compact(total)}`;
  });

  readonly axisLabels = computed(() => {
    const points = this.data();
    if (points.length === 0) return [];
    if (points.length <= 3) return points.map(p => this.formatShort(p));
    return [
      this.formatShort(points[0]),
      this.formatShort(points[Math.floor(points.length / 2)]),
      this.formatShort(points[points.length - 1]),
    ];
  });

  tooltipLeft(): number {
    const index = this.active();
    const count = this.data().length;
    if (index === null || count === 0) return 0;
    return ((index + 0.5) / count) * 100;
  }

  tooltipTransform(): string {
    const left = this.tooltipLeft();
    if (left < 15) return 'translate(0, -100%)';
    if (left > 85) return 'translate(-100%, -100%)';
    return 'translate(-50%, -100%)';
  }

  formatShort(point: SeriesPoint): string {
    const date = new Date(point.date);
    if (Number.isNaN(date.getTime())) return point.period;
    return point.period.length === 7
      ? date.toLocaleDateString(undefined, { month: 'short', year: '2-digit' })
      : date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }

  formatFull(point: SeriesPoint): string {
    const date = new Date(point.date);
    if (Number.isNaN(date.getTime())) return point.period;
    return point.period.length === 7
      ? date.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
      : date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
  }
}
