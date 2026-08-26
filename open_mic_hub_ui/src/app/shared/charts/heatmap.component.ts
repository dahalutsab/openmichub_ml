import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { DAY_LABELS, HeatCell } from './chart.types';

interface Cell {
  day: number;
  hour: number;
  count: number;
  intensity: number;
}

/**
 * When-do-gigs-happen grid: hours down, days across.
 *
 * The API sends only the cells that have activity, because a full week/hour
 * lattice is 168 mostly-empty rows. This draws the lattice and looks each cell
 * up, so an empty grid still shows its own shape.
 *
 * Hours are trimmed to the range that actually contains bookings — open mic
 * nights cluster in the evening, and drawing midnight to 6am at full height
 * would spend most of the tile on rows that are always empty.
 */
@Component({
  selector: 'omh-heatmap',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="relative">
      <div class="flex gap-1.5">
        <!-- Hour gutter -->
        <div class="flex shrink-0 flex-col gap-1 pt-[18px]">
          <div *ngFor="let hour of hours()"
               class="flex items-center justify-end pr-1 text-micro tabular-nums text-muted"
               [style.height.px]="cellSize()">
            {{ hourLabel(hour) }}
          </div>
        </div>

        <div class="min-w-0 flex-1">
          <!-- Day header -->
          <div class="mb-1 grid gap-1" [style.grid-template-columns]="columns()">
            <div *ngFor="let day of days()"
                 class="text-center text-micro font-semibold text-muted">
              {{ dayLabel(day) }}
            </div>
          </div>

          <!-- Grid -->
          <div class="flex flex-col gap-1">
            <div *ngFor="let hour of hours()"
                 class="grid gap-1"
                 [style.grid-template-columns]="columns()">
              <div *ngFor="let day of days()"
                   class="rounded-[3px] transition-transform duration-100"
                   [style.height.px]="cellSize()"
                   [style.background]="cellColor(cellFor(day, hour))"
                   [class.scale-110]="isActive(day, hour)"
                   [attr.title]="cellTitle(day, hour)"
                   (mouseenter)="active.set(day * 100 + hour)"
                   (mouseleave)="active.set(null)"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- Scale key -->
      <div class="mt-3 flex items-center justify-end gap-1.5 text-micro text-muted">
        <span>Less</span>
        <span *ngFor="let step of [0, 0.25, 0.5, 0.75, 1]"
              class="h-2.5 w-2.5 rounded-[3px]"
              [style.background]="scaleColor(step)"></span>
        <span>More</span>
      </div>
    </div>
  `,
})
export class HeatmapComponent {
  readonly data = input<HeatCell[]>([]);
  readonly cellSize = input(14);

  readonly active = signal<number | null>(null);

  readonly days = computed(() => [1, 2, 3, 4, 5, 6, 7]);

  readonly lookup = computed(() => {
    const map = new Map<number, number>();
    for (const cell of this.data()) {
      map.set(cell.dayOfWeek * 100 + cell.hour, cell.count);
    }
    return map;
  });

  readonly max = computed(() => {
    const counts = this.data().map(c => c.count);
    return counts.length ? Math.max(...counts) : 0;
  });

  /**
   * Hour rows to draw. Falls back to a sensible evening band when there is no
   * data at all, so an empty state still looks like a schedule rather than a
   * single blank row.
   */
  readonly hours = computed(() => {
    const cells = this.data();
    if (!cells.length) return this.range(17, 23);

    const active = cells.map(c => c.hour);
    let min = Math.min(...active);
    let max = Math.max(...active);

    // Always show at least six rows so the grid keeps its proportions, growing
    // outward in both directions. Expanding one way only put a dataset that
    // sits entirely at 6pm at the bottom of five empty afternoon rows.
    let expandDown = false;
    while (max - min < 5) {
      if (expandDown && min > 0) min--;
      else if (!expandDown && max < 23) max++;
      else if (min > 0) min--;
      else if (max < 23) max++;
      else break;
      expandDown = !expandDown;
    }
    return this.range(min, max);
  });

  readonly columns = computed(() => `repeat(${this.days().length}, minmax(0, 1fr))`);

  cellFor(day: number, hour: number): number {
    return this.lookup().get(day * 100 + hour) ?? 0;
  }

  /**
   * Intensity uses a square-root ramp rather than a linear one. Booking counts
   * are heavily skewed — one blockbuster Friday makes every other cell round to
   * the palest step under a linear scale, hiding the pattern the grid exists to
   * show.
   */
  cellColor(count: number): string {
    const max = this.max();
    if (max === 0 || count === 0) {
      return 'rgb(var(--omh-surface-2))';
    }
    const intensity = Math.sqrt(count / max);
    return this.scaleColor(intensity);
  }

  scaleColor(intensity: number): string {
    if (intensity <= 0) return 'rgb(var(--omh-surface-2))';
    // Floor at 0.18 so the lightest occupied cell is still distinguishable
    // from an empty one.
    const alpha = 0.18 + intensity * 0.82;
    return `rgb(var(--omh-accent) / ${alpha.toFixed(2)})`;
  }

  isActive(day: number, hour: number): boolean {
    return this.active() === day * 100 + hour;
  }

  cellTitle(day: number, hour: number): string {
    const count = this.cellFor(day, hour);
    const noun = count === 1 ? 'booking' : 'bookings';
    return `${DAY_LABELS[day]} ${this.hourLabel(hour)} — ${count} ${noun}`;
  }

  dayLabel(day: number): string {
    return DAY_LABELS[day] ?? '';
  }

  hourLabel(hour: number): string {
    if (hour === 0) return '12a';
    if (hour === 12) return '12p';
    return hour < 12 ? `${hour}a` : `${hour - 12}p`;
  }

  private range(from: number, to: number): number[] {
    return Array.from({ length: to - from + 1 }, (_, i) => from + i);
  }
}
