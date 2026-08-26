import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SparklineComponent } from './sparkline.component';
import { Metric, SeriesPoint, compact } from './chart.types';

/**
 * Headline figure, its period-over-period delta, and an optional trend line.
 *
 * Every dashboard opens with a row of these. Keeping them one component is what
 * stops the type sizes and the delta styling drifting between the three boards.
 */
@Component({
  selector: 'omh-metric-tile',
  standalone: true,
  imports: [CommonModule, SparklineComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="omh-tile">
      <div class="omh-tile-head">
        <span class="omh-tile-title">
          <i class="bi" [ngClass]="icon()" *ngIf="icon()"></i>
          {{ label() }}
        </span>

        <span *ngIf="metric() as m" [ngClass]="deltaClass(m)">
          <i class="bi text-[10px]" [ngClass]="deltaIcon(m)"></i>
          {{ absChange(m) }}%
        </span>
      </div>

      <div class="flex items-end justify-between gap-3">
        <div class="min-w-0">
          <div class="omh-metric text-2xl">
            <span class="text-muted" *ngIf="prefix()">{{ prefix() }}</span>{{ display() }}
          </div>
          <p class="mt-0.5 truncate text-xs text-muted">{{ caption() }}</p>
        </div>

        <div class="w-24 shrink-0" *ngIf="hasTrend()">
          <omh-sparkline [data]="trend()" [color]="trendColor()" [height]="34" />
        </div>
      </div>
    </div>
  `,
})
export class MetricTileComponent {
  readonly label = input('');
  readonly caption = input('');
  readonly icon = input('');
  readonly prefix = input('');
  readonly metric = input<Metric | null>(null);
  readonly trend = input<SeriesPoint[]>([]);
  /** Overrides the metric's own value, for figures with no comparison. */
  readonly value = input<number | null>(null);
  /** Large counts read better abbreviated; money usually does not. */
  readonly abbreviate = input(true);

  /**
   * A trend line is only worth drawing when something actually moved. An
   * all-zero series renders as a flat rule, which reads as a divider rather
   * than as data.
   */
  hasTrend(): boolean {
    const points = this.trend();
    return points.length > 1 && points.some(p => p.primary !== 0);
  }

  readonly display = computed(() => {
    const raw = this.value() ?? this.metric()?.value ?? 0;
    if (this.abbreviate()) return compact(raw);
    return raw.toLocaleString(undefined, { maximumFractionDigits: 2 });
  });

  /** The trend line follows the delta's direction, so tile and line agree. */
  readonly trendColor = computed(() => {
    const change = this.metric()?.changePercent ?? 0;
    if (change > 0) return 'rgb(var(--omh-positive))';
    if (change < 0) return 'rgb(var(--omh-critical))';
    return 'rgb(var(--omh-muted))';
  });

  deltaClass(metric: Metric): string {
    if (metric.changePercent > 0) return 'omh-delta-up';
    if (metric.changePercent < 0) return 'omh-delta-down';
    return 'omh-delta-flat';
  }

  deltaIcon(metric: Metric): string {
    if (metric.changePercent > 0) return 'bi-arrow-up-right';
    if (metric.changePercent < 0) return 'bi-arrow-down-right';
    return 'bi-dash';
  }

  /** The arrow carries the sign, so repeating it in the number is noise. */
  absChange(metric: Metric): string {
    return Math.abs(metric.changePercent).toFixed(1);
  }
}
