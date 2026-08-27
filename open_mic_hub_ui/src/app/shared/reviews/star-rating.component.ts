import { ChangeDetectionStrategy, Component, computed, input, model, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Five stars, either shown or set.
 *
 * One component for both because the two must not drift: a 4.2 rendered as four
 * and a half stars in a list, then as four when you go to edit it, reads as the
 * app losing your input.
 *
 * Read-only mode renders spans; interactive mode renders real radio inputs, so
 * the control is reachable by keyboard and announces itself as a rating rather
 * than as five unlabelled buttons.
 */
@Component({
  selector: 'omh-star-rating',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- Display -->
    <span class="inline-flex items-center gap-0.5" *ngIf="!interactive(); else input"
          [attr.aria-label]="value() + ' out of 5'">
      <i *ngFor="let star of stars"
         class="bi text-stage"
         [ngClass]="displayClass(star)"
         [style.font-size.px]="size()"></i>
      <span class="ml-1.5 text-xs text-muted" *ngIf="showValue()">
        {{ value() | number:'1.1-1' }}
      </span>
    </span>

    <ng-template #input>
      <fieldset class="m-0 flex items-center gap-1 border-0 p-0"
                (mouseleave)="hover.set(0)">
        <legend class="sr-only">Rating out of 5</legend>

        <label *ngFor="let star of stars"
               class="cursor-pointer leading-none"
               [attr.title]="star + ' star' + (star === 1 ? '' : 's')">
          <input type="radio"
                 class="sr-only"
                 name="rating"
                 [value]="star"
                 [checked]="value() === star"
                 (change)="pick(star)">
          <i class="bi transition-transform duration-100"
             [ngClass]="interactiveClass(star)"
             [style.font-size.px]="size()"
             (mouseenter)="hover.set(star)"></i>
        </label>

        <span class="ml-2 text-sm text-muted">{{ caption() }}</span>
      </fieldset>
    </ng-template>
  `,
})
export class StarRatingComponent {
  readonly value = model(0);
  readonly interactive = input(false);
  readonly size = input(14);
  readonly showValue = input(false);

  /** Star currently under the cursor, so the row previews before committing. */
  readonly hover = signal(0);

  readonly stars = [1, 2, 3, 4, 5];

  private readonly WORDS = ['', 'Poor', 'Fair', 'Good', 'Great', 'Excellent'];

  readonly caption = computed(() => {
    const shown = this.hover() || this.value();
    return shown ? this.WORDS[shown] : 'Tap to rate';
  });

  /** Halves are only meaningful for an average, never for a value someone set. */
  displayClass(star: number): string {
    const value = this.value();
    if (value >= star) return 'bi-star-fill';
    if (value >= star - 0.5) return 'bi-star-half';
    return 'bi-star opacity-40';
  }

  interactiveClass(star: number): string {
    const shown = this.hover() || this.value();
    return shown >= star
      ? 'bi-star-fill text-stage scale-110'
      : 'bi-star text-muted/50';
  }

  pick(star: number): void {
    this.value.set(star);
  }
}
