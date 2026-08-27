import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { Review, ReviewService } from './review.service';
import { StarRatingComponent } from './star-rating.component';

/**
 * Leave or edit a review for one booking.
 *
 * Takes a booking rather than an artist: the API derives the artist from the
 * booking so a review cannot be pointed at someone the reviewer never hired,
 * and mirroring that here keeps the UI from offering a shape the server refuses.
 */
@Component({
  selector: 'omh-review-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, StarRatingComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="omh-modal-backdrop" (click)="close()">
      <div class="omh-modal max-w-lg animate-slide-up" (click)="$event.stopPropagation()"
           role="dialog" aria-modal="true" aria-labelledby="review-dialog-title">

        <div class="omh-modal-head">
          <div>
            <h2 class="omh-modal-title" id="review-dialog-title">
              {{ existing ? 'Edit your review' : 'How did it go?' }}
            </h2>
            <p class="mt-0.5 text-sm text-muted" *ngIf="artistName">
              {{ artistName }}<span *ngIf="eventLabel"> · {{ eventLabel }}</span>
            </p>
          </div>
          <button type="button" class="omh-btn-icon" aria-label="Close" (click)="close()">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <div class="omh-modal-body">
          <div class="omh-field">
            <label class="omh-label">Your rating <span class="text-hot">*</span></label>
            <omh-star-rating [(value)]="rating" [interactive]="true" [size]="26" />
            <p class="omh-error" *ngIf="submitted() && rating < 1">Pick a rating from 1 to 5.</p>
          </div>

          <div class="omh-field mt-4">
            <label for="review-comment" class="omh-label">Comment</label>
            <textarea id="review-comment"
                      rows="4"
                      class="omh-textarea"
                      maxlength="2000"
                      [(ngModel)]="comment"
                      placeholder="What stood out? Anything a future booker should know?"></textarea>
            <p class="omh-help mt-1">{{ comment.length }}/2000 · optional</p>
          </div>
        </div>

        <div class="omh-modal-foot">
          <button type="button" class="omh-btn-secondary" (click)="close()">Cancel</button>
          <button type="button" class="omh-btn-primary" [disabled]="saving()" (click)="save()">
            <span class="omh-spinner-sm" *ngIf="saving()"></span>
            {{ saving() ? 'Saving…' : (existing ? 'Save changes' : 'Post review') }}
          </button>
        </div>
      </div>
    </div>
  `,
})
export class ReviewDialogComponent {
  @Input({ required: true }) bookingId!: number;
  @Input() artistName: string | null = null;
  @Input() eventLabel: string | null = null;

  /** Present when editing rather than creating. */
  @Input() existing: Review | null = null;

  @Output() saved = new EventEmitter<Review>();
  @Output() dismissed = new EventEmitter<void>();

  rating = 0;
  comment = '';

  readonly saving = signal(false);
  readonly submitted = signal(false);

  constructor(private reviews: ReviewService, private toast: ToastrService) {}

  ngOnInit(): void {
    if (this.existing) {
      this.rating = this.existing.rating;
      this.comment = this.existing.comment ?? '';
    }
  }

  close(): void {
    if (!this.saving()) {
      this.dismissed.emit();
    }
  }

  save(): void {
    this.submitted.set(true);
    if (this.rating < 1 || this.saving()) {
      return;
    }

    this.saving.set(true);
    const request$ = this.existing
      ? this.reviews.update(this.existing.reviewId, this.bookingId, this.rating, this.comment)
      : this.reviews.create(this.bookingId, this.rating, this.comment);

    request$.subscribe({
      next: review => {
        this.saving.set(false);
        this.toast.success(this.existing ? 'Review updated' : 'Thanks for the review');
        this.saved.emit(review);
      },
      error: err => {
        this.saving.set(false);
        // The API refuses duplicates, non-confirmed bookings and other people's
        // bookings with a specific message; showing it beats a generic failure.
        const detail = err?.error?.message || err?.error?.error;
        this.toast.error(detail || 'Could not save your review.');
        console.error('Review save failed', err);
      },
    });
  }
}
