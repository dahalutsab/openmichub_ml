import { ChangeDetectionStrategy, Component, Input, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Review, ReviewService, RatingSummary } from './review.service';
import { StarRatingComponent } from './star-rating.component';
import { AVATAR_FALLBACK } from '../avatar';

/**
 * Reviews for one artist: the summary, the distribution, then the reviews.
 *
 * Fetches its own data so any screen can drop it in with just an artist id.
 */
@Component({
  selector: 'omh-review-list',
  standalone: true,
  imports: [CommonModule, RouterModule, StarRatingComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center justify-center gap-3 py-10" *ngIf="loading()">
      <span class="omh-spinner-md"></span>
      <span class="text-base text-muted">Loading reviews…</span>
    </div>

    <ng-container *ngIf="!loading()">
      <ng-container *ngIf="reviews().length; else empty">

        <!-- Summary -->
        <div class="mb-5 flex flex-wrap items-center gap-6 border-b border-line pb-5">
          <div class="text-center">
            <div class="omh-metric text-4xl">{{ summary().average | number:'1.1-1' }}</div>
            <omh-star-rating [value]="summary().average" [size]="14" />
            <p class="mt-1 text-xs text-muted">
              {{ summary().count }} {{ summary().count === 1 ? 'review' : 'reviews' }}
            </p>
          </div>

          <!-- Distribution. Bars are relative to the most common rating, not to
               the total, so the shape is readable even at low volume. -->
          <div class="min-w-[180px] flex-1">
            <div class="flex items-center gap-2" *ngFor="let star of [5,4,3,2,1]">
              <span class="w-3 text-right text-xs text-muted">{{ star }}</span>
              <i class="bi bi-star-fill text-[9px] text-stage"></i>
              <div class="h-1.5 flex-1 overflow-hidden rounded-pill bg-surface-2">
                <div class="h-full rounded-pill bg-stage" [style.width.%]="barWidth(star)"></div>
              </div>
              <span class="w-6 text-right text-xs tabular-nums text-muted">
                {{ summary().distribution[star - 1] }}
              </span>
            </div>
          </div>
        </div>

        <!-- Reviews -->
        <ul class="flex flex-col gap-4">
          <li *ngFor="let review of reviews()" class="flex gap-3">
            <img [src]="review.reviewerImage || fallbackAvatar"
                 alt=""
                 class="omh-avatar-md mt-0.5">

            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                <span class="font-display text-sm font-bold text-ink">
                  {{ review.reviewerName || 'A booker' }}
                </span>
                <omh-star-rating [value]="review.rating" [size]="12" />
                <span class="text-xs text-muted">{{ review.createdAt | date:'mediumDate' }}</span>
              </div>

              <span class="omh-chip mt-1.5" *ngIf="review.eventType">{{ review.eventType }}</span>

              <p class="mt-1.5 whitespace-pre-wrap text-base leading-relaxed text-ink-soft"
                 *ngIf="review.comment">{{ review.comment }}</p>
            </div>
          </li>
        </ul>

        <button type="button"
                class="omh-btn-secondary mt-4 w-full"
                *ngIf="hasMore()"
                [disabled]="loadingMore()"
                (click)="loadMore()">
          <span class="omh-spinner-sm" *ngIf="loadingMore()"></span>
          {{ loadingMore() ? 'Loading…' : 'Show more reviews' }}
        </button>
      </ng-container>

      <ng-template #empty>
        <div class="omh-empty border-0 py-10">
          <i class="bi bi-chat-quote omh-empty-icon"></i>
          <p class="omh-empty-title">{{ emptyTitle }}</p>
          <p class="omh-empty-text">{{ emptyText }}</p>
        </div>
      </ng-template>
    </ng-container>
  `,
})
export class ReviewListComponent {
  /** Whose reviews to show. Omit with `source="mine"` or `source="about-me"`. */
  @Input() artistId: number | null = null;

  /** artist = reviews of an artist; mine = ones I wrote; about-me = ones about me. */
  @Input() source: 'artist' | 'mine' | 'about-me' = 'artist';

  @Input() emptyTitle = 'No reviews yet';
  @Input() emptyText = 'Reviews appear here once a booking has been played and rated.';

  readonly fallbackAvatar = AVATAR_FALLBACK;

  readonly reviews = signal<Review[]>([]);
  readonly loading = signal(true);
  readonly loadingMore = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);

  readonly summary = computed<RatingSummary>(() => this.service.summarise(this.reviews()));
  readonly hasMore = computed(() => this.page() + 1 < this.totalPages());

  constructor(private service: ReviewService) {}

  ngOnInit(): void {
    this.fetch(0);
  }

  /** Re-reads from the first page. Called by hosts after a review is posted. */
  refresh(): void {
    this.page.set(0);
    this.fetch(0);
  }

  loadMore(): void {
    if (this.hasMore() && !this.loadingMore()) {
      this.loadingMore.set(true);
      this.fetch(this.page() + 1, true);
    }
  }

  /** Longest bar is the most common rating, so shape stays legible at low counts. */
  barWidth(star: number): number {
    const distribution = this.summary().distribution;
    const peak = Math.max(...distribution, 1);
    return Math.round((distribution[star - 1] / peak) * 100);
  }

  private fetch(page: number, append = false): void {
    const request$ =
      this.source === 'mine' ? this.service.mine(page)
      : this.source === 'about-me' ? this.service.aboutMe(page)
      : this.service.forArtist(this.artistId ?? 0, page);

    request$.subscribe({
      next: result => {
        this.reviews.update(current =>
          append ? [...current, ...(result.content ?? [])] : (result.content ?? []));
        this.page.set(result.number ?? page);
        this.totalPages.set(result.totalPages ?? 0);
        this.loading.set(false);
        this.loadingMore.set(false);
      },
      error: err => {
        console.error('Failed to load reviews', err);
        this.loading.set(false);
        this.loadingMore.set(false);
      },
    });
  }
}
