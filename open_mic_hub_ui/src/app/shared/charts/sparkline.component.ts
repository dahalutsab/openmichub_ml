import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SeriesPoint, round, smoothPath } from './chart.types';

/**
 * Trend line for a metric tile — no axes, no labels, no interaction.
 *
 * A sparkline answers one question: which way is this going. Adding gridlines
 * or ticks at this size buys nothing and costs the shape its legibility, so
 * the exact figures stay in the tile's own text.
 */
@Component({
  selector: 'omh-sparkline',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg [attr.viewBox]="'0 0 ' + width + ' ' + height()"
         class="w-full"
         [style.height.px]="height()"
         preserveAspectRatio="none"
         aria-hidden="true">
      <defs>
        <linearGradient [attr.id]="gradientId" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" [attr.stop-color]="color()" stop-opacity="0.3" />
          <stop offset="100%" [attr.stop-color]="color()" stop-opacity="0" />
        </linearGradient>
      </defs>

      <path *ngIf="filled()" [attr.d]="areaPath()" [attr.fill]="'url(#' + gradientId + ')'" />
      <path [attr.d]="linePath()"
            fill="none"
            [attr.stroke]="color()"
            [attr.stroke-width]="strokeWidth()"
            stroke-linecap="round"
            stroke-linejoin="round"
            vector-effect="non-scaling-stroke" />
    </svg>
  `,
})
export class SparklineComponent {
  readonly data = input<SeriesPoint[]>([]);
  readonly height = input(40);
  readonly color = input('rgb(var(--omh-accent))');
  readonly filled = input(true);
  readonly strokeWidth = input(2);

  /** Fixed internal width; the SVG stretches to its container. */
  readonly width = 100;

  readonly gradientId = `omh-spark-${Math.random().toString(36).slice(2, 9)}`;

  private readonly pad = 3;

  readonly points = computed(() => {
    const series = this.data();
    if (!series.length) return [];

    const values = series.map(p => p.primary);
    const min = Math.min(...values);
    const max = Math.max(...values);
    // A flat series has no range to scale into; centre it rather than dividing
    // by zero and collapsing every point onto the top edge.
    const span = max - min || 1;

    const h = this.height();
    const usable = h - this.pad * 2;
    const step = series.length === 1 ? 0 : this.width / (series.length - 1);

    return series.map((point, i) => ({
      x: round(series.length === 1 ? this.width / 2 : i * step),
      y: round(this.pad + usable - ((point.primary - min) / span) * usable),
    }));
  });

  readonly linePath = computed(() => smoothPath(this.points()));

  readonly areaPath = computed(() => {
    const pts = this.points();
    if (!pts.length) return '';
    const h = this.height();
    return `${this.linePath()} L ${pts[pts.length - 1].x} ${h} L ${pts[0].x} ${h} Z`;
  });
}
