import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Slice, categorical, compact, round, statusColor } from './chart.types';

interface Arc {
  d: string;
  color: string;
  slice: Slice;
  percent: number;
}

/**
 * Donut with a total in the middle.
 *
 * A donut rather than a pie: the hole gives the total somewhere to live, and
 * comparing arc lengths on a ring is easier than comparing wedge areas. It is
 * still only honest for a handful of parts, so anything past the fifth is
 * folded into "Other" rather than drawn as an unreadable sliver.
 */
@Component({
  selector: 'omh-donut-chart',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-wrap items-center gap-4">
      <div class="relative shrink-0" [style.width.px]="size()" [style.height.px]="size()">
        <svg [attr.viewBox]="'0 0 ' + size() + ' ' + size()"
             [style.width.px]="size()"
             [style.height.px]="size()"
             role="img"
             [attr.aria-label]="ariaLabel()"
             (mouseleave)="active.set(null)">

          <!-- Track, so an empty or partial ring still reads as a ring. -->
          <circle [attr.cx]="center()" [attr.cy]="center()" [attr.r]="radius()"
                  fill="none"
                  stroke="rgb(var(--omh-surface-2))"
                  [attr.stroke-width]="thickness()" />

          <!-- A slice covering the whole ring is drawn as a circle. An SVG arc
               whose start and end are the same point has zero length and paints
               nothing, so a single-category donut would otherwise render as an
               empty track. -->
          <circle *ngIf="fullRing() as ring"
                  [attr.cx]="center()" [attr.cy]="center()" [attr.r]="radius()"
                  fill="none"
                  [attr.stroke]="ring.color"
                  [attr.stroke-width]="active() === 0 ? thickness() + 4 : thickness()"
                  class="cursor-pointer transition-[stroke-width] duration-150"
                  (mouseenter)="active.set(0)" />

          <path *ngFor="let arc of arcs(); let i = index"
                [attr.d]="arc.d"
                fill="none"
                [attr.stroke]="arc.color"
                [attr.stroke-width]="active() === i ? thickness() + 4 : thickness()"
                stroke-linecap="butt"
                class="cursor-pointer transition-[stroke-width] duration-150"
                (mouseenter)="active.set(i)" />
        </svg>

        <!-- Centre readout. Shows the hovered slice when there is one, so the
             hole is doing work rather than just being a hole. -->
        <div class="pointer-events-none absolute inset-0 flex flex-col items-center justify-center text-center">
          <span class="omh-metric text-xl">{{ centerValue() }}</span>
          <span class="mt-0.5 max-w-[80%] truncate text-micro uppercase tracking-[0.1em] text-muted">
            {{ centerLabel() }}
          </span>
        </div>
      </div>

      <ul class="flex min-w-0 flex-1 flex-col gap-1.5">
        <li *ngFor="let arc of arcs(); let i = index"
            class="flex items-center gap-2 text-sm cursor-pointer"
            (mouseenter)="active.set(i)"
            (mouseleave)="active.set(null)">
          <span class="omh-swatch" [style.background]="arc.color"></span>
          <span class="min-w-0 flex-1 truncate text-ink-soft">{{ pretty(arc.slice.label) }}</span>
          <span class="font-display font-semibold text-ink tabular-nums">{{ arc.slice.count }}</span>
          <span class="w-10 text-right text-xs text-muted tabular-nums">{{ arc.percent }}%</span>
        </li>
      </ul>
    </div>
  `,
})
export class DonutChartComponent {
  readonly data = input<Slice[]>([]);
  readonly size = input(150);
  readonly thickness = input(18);
  readonly totalLabel = input('Total');
  /** Colour slices by their status name rather than by position. */
  readonly byStatus = input(false);
  /** Slices past this are folded into "Other". */
  readonly maxSlices = input(5);

  readonly active = signal<number | null>(null);

  readonly center = computed(() => this.size() / 2);
  readonly radius = computed(() => (this.size() - this.thickness()) / 2);

  /** Top slices, with the tail collapsed so no arc is too thin to see. */
  readonly grouped = computed<Slice[]>(() => {
    const slices = [...this.data()]
      .filter(s => s.count > 0)
      .sort((a, b) => b.count - a.count);

    const limit = this.maxSlices();
    if (slices.length <= limit) return slices;

    const head = slices.slice(0, limit - 1);
    const tail = slices.slice(limit - 1);
    head.push({
      label: 'Other',
      count: tail.reduce((sum, s) => sum + s.count, 0),
      amount: tail.reduce((sum, s) => sum + s.amount, 0),
    });
    return head;
  });

  readonly total = computed(() =>
    this.grouped().reduce((sum, s) => sum + s.count, 0));

  /**
   * The single slice that owns the entire ring, if there is one — drawn as a
   * circle rather than an arc. Null whenever the ring is genuinely divided.
   */
  readonly fullRing = computed(() => {
    const slices = this.grouped();
    if (slices.length !== 1 || this.total() === 0) return null;
    return {
      color: this.byStatus() ? statusColor(slices[0].label) : categorical(0),
      slice: slices[0],
    };
  });

  readonly arcs = computed<Arc[]>(() => {
    const slices = this.grouped();
    const total = this.total();
    if (!slices.length || total === 0) return [];

    // Already painted as a circle.
    if (slices.length === 1) {
      return [{
        d: '',
        color: this.byStatus() ? statusColor(slices[0].label) : categorical(0),
        slice: slices[0],
        percent: 100,
      }];
    }

    const cx = this.center();
    const cy = this.center();
    const r = this.radius();

    // A small gap between arcs so adjacent slices of similar colour stay
    // distinct. Skipped when there is only one slice — a ring with a notch
    // taken out of it looks like a rendering fault.
    const gap = slices.length > 1 ? 0.03 : 0;

    let angle = -Math.PI / 2; // start at twelve o'clock
    return slices.map((slice, i) => {
      const portion = (slice.count / total) * Math.PI * 2;
      const start = angle + gap / 2;
      const end = angle + portion - gap / 2;
      angle += portion;

      return {
        d: this.arcPath(cx, cy, r, start, Math.max(end, start + 0.001)),
        color: this.byStatus() ? statusColor(slice.label) : categorical(i),
        slice,
        percent: Math.round((slice.count / total) * 100),
      };
    });
  });

  readonly centerValue = computed(() => {
    const index = this.active();
    if (index !== null) {
      const arc = this.arcs()[index];
      if (arc) return String(arc.slice.count);
    }
    return compact(this.total());
  });

  readonly centerLabel = computed(() => {
    const index = this.active();
    if (index !== null) {
      const arc = this.arcs()[index];
      if (arc) return this.pretty(arc.slice.label);
    }
    return this.totalLabel();
  });

  readonly ariaLabel = computed(() => {
    const slices = this.grouped();
    if (!slices.length) return 'No data';
    return slices.map(s => `${this.pretty(s.label)}: ${s.count}`).join(', ');
  });

  /** SCREAMING_SNAKE enum names are not display copy. */
  pretty(label: string): string {
    return (label || '')
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, c => c.toUpperCase());
  }

  private arcPath(cx: number, cy: number, r: number, start: number, end: number): string {
    const x1 = cx + r * Math.cos(start);
    const y1 = cy + r * Math.sin(start);
    const x2 = cx + r * Math.cos(end);
    const y2 = cy + r * Math.sin(end);
    const largeArc = end - start > Math.PI ? 1 : 0;
    return `M ${round(x1)} ${round(y1)} A ${round(r)} ${round(r)} 0 ${largeArc} 1 ${round(x2)} ${round(y2)}`;
  }
}
