import { ReviewDialogComponent } from './review-dialog.component';
import { ReviewListComponent } from './review-list.component';
import { StarRatingComponent } from './star-rating.component';

export * from './review.service';
export { ReviewDialogComponent, ReviewListComponent, StarRatingComponent };

/** Imported as a set, the same way the chart components are. */
export const OMH_REVIEWS = [
  ReviewDialogComponent,
  ReviewListComponent,
  StarRatingComponent,
] as const;
