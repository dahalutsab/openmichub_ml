import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { SeriesPoint, compact, niceCeiling, round, smoothPath } from './chart.types';

interface Plotted {
  x: number;
  y: number;
  y2: number;
  point: SeriesPoint;
}

/**
 * Two-series area chart with a hover readout.
 *
 * Drawn in a fixed 1000×H viewBox with `preserveAspectRatio="none"` on nothing —
 * the SVG scales as a whole via `width: 100%`, so one geometry works at every
 * container width without a resize observer. Text is placed in the same
 * coordinate space and scales with it, which is why the type sizes here look
 * large: at the rendered width they land at ~11px.
 */
@Component({
  selector: 'omh-area-chart',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- A series of all zeros is not a chart. Drawing it produces a flat line
         against an invented 0-to-1 axis, which reads as a broken widget rather
         than as an empty period. -->
    <div class="flex flex-col items-center justify-center text-center"
         [style.height.px]="height()"
         *ngIf="isEmpty(); else chart">
      <i class="bi bi-graph-up mb-2 text-xl text-muted/40"></i>
      <p class="text-sm text-muted">{{ emptyText() }}</p>
    </div>

    <ng-template #chart>
    <div class="relative w-full" (mouseleave)="active.set(null)">
      <svg [attr.viewBox]="'0 0 1000 ' + height()"
           class="w-full overflow-visible"
           [style.height.px]="height()"
           role="img"
           [attr.aria-label]="ariaLabel()">

        <defs>
          <linearGradient [attr.id]="gradientId" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" [attr.stop-color]="primaryColor()" stop-opacity="0.28" />
            <stop offset="100%" [attr.stop-color]="primaryColor()" stop-opacity="0" />
          </linearGradient>
        </defs>

        <!-- Gridlines. Drawn behind everything and kept very low contrast: they
             are a reading aid, not part of the data. -->
        <g>
          <line *ngFor="let tick of ticks()"
                x1="0" [attr.y1]="tick.y" x2="1000" [attr.y2]="tick.y"
                stroke="rgb(var(--omh-line))" stroke-width="1" />
          <text *ngFor="let tick of ticks()"
                x="0" [attr.y]="tick.y - 6"
                fill="rgb(var(--omh-muted))" font-size="20" font-family="Inter, sans-serif">
            {{ tick.label }}
          </text>
        </g>

        <!-- Secondary series first, so the primary reads on top of it. -->
        <path *ngIf="showSecondary()"
              [attr.d]="secondaryPath()"
              fill="none"
              [attr.stroke]="secondaryColor()"
              stroke-width="2"
              stroke-dasharray="5 5"
              stroke-linecap="round" />

        <path [attr.d]="areaPath()" [attr.fill]="'url(#' + gradientId + ')'" />
        <path [attr.d]="linePath()"
              fill="none"
              [attr.stroke]="primaryColor()"
              stroke-width="2.5"
              stroke-linecap="round"
              stroke-linejoin="round" />

        <!-- Hover marker -->
        <g *ngIf="activePoint() as p">
          <line [attr.x1]="p.x" y1="0" [attr.x2]="p.x" [attr.y2]="plotHeight()"
                stroke="rgb(var(--omh-muted))" stroke-width="1" stroke-dasharray="3 3" />
          <circle [attr.cx]="p.x" [attr.cy]="p.y" r="5"
                  [attr.fill]="primaryColor()"
                  stroke="rgb(var(--omh-surface))" stroke-width="3" />
        </g>

        <!-- One transparent band per point. Hit areas are full-height columns so
             the chart responds anywhere in the plot, not only on the line. -->
        <rect *ngFor="let p of plotted(); let i = index"
              [attr.x]="bandX(i)" y="0" [attr.width]="bandWidth()" [attr.height]="plotHeight()"
              fill="transparent"
              (mouseenter)="active.set(i)" />
      </svg>

      <!-- Axis labels sit outside the SVG so they use real page type rather than
           scaled SVG text, which goes blurry at small container widths. -->
      <div class="mt-1.5 flex justify-between text-micro text-muted" *ngIf="showAxis()">
        <span *ngFor="let label of axisLabels()">{{ label }}</span>
      </div>

      <div *ngIf="activePoint() as p"
           class="omh-tooltip"
           [style.left.%]="tooltipLeft()"
           [style.transform]="tooltipTransform()"
           style="top: 0">
        <div class="omh-tooltip-label">{{ formatFull(p.point) }}</div>
        <div class="flex items-center gap-1.5">
          <span class="omh-swatch" [style.background]="primaryColor()"></span>
          <span class="font-semibold">{{ prefix() }}{{ p.point.primary | number:'1.0-2' }}</span>
          <span class="text-muted">{{ primaryLabel() }}</span>
        </div>
        <div class="mt-0.5 flex items-center gap-1.5" *ngIf="showSecondary()">
          <span class="omh-swatch" [style.background]="secondaryColor()"></span>
          <span class="font-semibold">{{ prefix() }}{{ p.point.secondary | number:'1.0-2' }}</span>
          <span class="text-muted">{{ secondaryLabel() }}</span>
        </div>
      </div>
    </div>
    </ng-template>
  `,
})
export class AreaChartComponent {
  readonly data = input<SeriesPoint[]>([]);
  readonly height = input(180);
  readonly primaryColor = input('rgb(var(--omh-accent))');
  readonly secondaryColor = input('rgb(var(--omh-muted))');
  readonly primaryLabel = input('settled');
  readonly secondaryLabel = input('requested');
  readonly showSecondary = input(true);
  readonly showAxis = input(true);
  readonly prefix = input('');
  readonly emptyText = input('No activity in this period');

  readonly active = signal<number | null>(null);

  /** True when there is nothing to plot: no points, or every value is zero. */
  isEmpty(): boolean {
    const points = this.data();
    if (!points.length) return true;
    return points.every(p => !p.primary && !p.secondary);
  }

  /** Unique per instance so two charts on a page cannot share a gradient. */
  readonly gradientId = `omh-area-${Math.random().toString(36).slice(2, 9)}`;

  /** Room reserved under the plot for nothing — labels live outside the SVG. */
  private readonly padTop = 14;

  readonly plotHeight = computed(() => this.height());

  readonly max = computed(() => {
    const points = this.data();
    if (!points.length) return 1;
    const values = points.flatMap(p =>
      this.showSecondary() ? [p.primary, p.secondary] : [p.primary]);
    return niceCeiling(Math.max(...values, 0));
  });

  readonly plotted = computed<Plotted[]>(() => {
    const points = this.data();
    if (!points.length) return [];
    const max = this.max();
    const h = this.plotHeight();
    const usable = h - this.padTop;
    const step = points.length === 1 ? 0 : 1000 / (points.length - 1);

    return points.map((point, i) => ({
      x: round(points.length === 1 ? 500 : i * step),
      y: round(this.padTop + usable - (point.primary / max) * usable),
      y2: round(this.padTop + usable - (point.secondary / max) * usable),
      point,
    }));
  });

  readonly linePath = computed(() => smoothPath(this.plotted()));

  readonly secondaryPath = computed(() =>
    smoothPath(this.plotted().map(p => ({ x: p.x, y: p.y2 }))));

  /** The line, closed down to the baseline to make a fill. */
  readonly areaPath = computed(() => {
    const points = this.plotted();
    if (!points.length) return '';
    const h = this.plotHeight();
    const first = points[0];
    const last = points[points.length - 1];
    return `${this.linePath()} L ${last.x} ${h} L ${first.x} ${h} Z`;
  });

  readonly ticks = computed(() => {
    const max = this.max();
    const h = this.plotHeight();
    const usable = h - this.padTop;
    return [0, 0.5, 1].map(fraction => ({
      y: round(this.padTop + usable - fraction * usable),
      label: compact(max * fraction),
    }));
  });

  readonly bandWidth = computed(() => {
    const count = this.data().length;
    return count <= 1 ? 1000 : 1000 / count;
  });

  readonly activePoint = computed(() => {
    const index = this.active();
    if (index === null) return null;
    return this.plotted()[index] ?? null;
  });

  readonly ariaLabel = computed(() => {
    const points = this.data();
    if (!points.length) return 'No data';
    const total = points.reduce((sum, p) => sum + p.primary, 0);
    return `Series of ${points.length} points, total ${compact(total)}`;
  });

  /** First, middle and last labels only — a tick per bucket is unreadable. */
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

  bandX(index: number): number {
    return round(index * this.bandWidth() - this.bandWidth() / 2);
  }

  tooltipLeft(): number {
    const index = this.active();
    const count = this.data().length;
    if (index === null || count === 0) return 0;
    return count === 1 ? 50 : (index / (count - 1)) * 100;
  }

  /** Keeps the tooltip inside the tile at both ends of the series. */
  tooltipTransform(): string {
    const left = this.tooltipLeft();
    if (left < 15) return 'translate(0, -100%)';
    if (left > 85) return 'translate(-100%, -100%)';
    return 'translate(-50%, -100%)';
  }

  formatShort(point: SeriesPoint): string {
    const date = new Date(point.date);
    if (Number.isNaN(date.getTime())) return point.period;
    // A month bucket's label carries no day, so show month and year.
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
