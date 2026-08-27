import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../environment/environment';

/** Mirrors ReviewResponse on the service. */
export interface Review {
  reviewId: number;
  rating: number;
  comment: string | null;
  createdAt: string;

  bookingId: number | null;
  eventType: string | null;

  reviewerId: number | null;
  reviewerName: string | null;
  reviewerImage: string | null;

  artistId: number | null;
  artistStageName: string | null;
}

export interface ReviewPage {
  content: Review[];
  totalElements: number;
  totalPages: number;
  number: number;
}

/** What the artist profile shows above the list. */
export interface RatingSummary {
  average: number;
  count: number;
  /** Index 0 = one star … index 4 = five stars. */
  distribution: number[];
}

@Injectable({ providedIn: 'root' })
export class ReviewService {
  private readonly baseUrl = `${environment.baseUrl}/reviews`;

  constructor(private http: HttpClient) {}

  /**
   * Reviews for one artist. Public — no token needed, so this also works on the
   * artist profile before a visitor has signed in.
   */
  forArtist(artistId: number, page = 0, size = 10): Observable<ReviewPage> {
    return this.get(`/artist/${artistId}`, page, size);
  }

  /** Reviews written by the signed-in user. */
  mine(page = 0, size = 20): Observable<ReviewPage> {
    return this.get('/me', page, size);
  }

  /** Reviews left for the signed-in artist. */
  aboutMe(page = 0, size = 20): Observable<ReviewPage> {
    return this.get('/me/artist', page, size);
  }

  create(bookingId: number, rating: number, comment: string): Observable<Review> {
    return this.http
      .post<{ data: Review }>(this.baseUrl, { bookingId, rating, comment })
      .pipe(map(r => r.data));
  }

  update(reviewId: number, bookingId: number, rating: number, comment: string): Observable<Review> {
    return this.http
      .put<{ data: Review }>(`${this.baseUrl}/${reviewId}`, { bookingId, rating, comment })
      .pipe(map(r => r.data));
  }

  remove(reviewId: number): Observable<unknown> {
    return this.http.delete(`${this.baseUrl}/${reviewId}`);
  }

  /**
   * Average and star distribution over a set of reviews.
   *
   * Computed client-side from the page already fetched rather than as another
   * request: the API has no summary endpoint, and adding one to serve a number
   * the caller can already add up would be a round trip for nothing.
   */
  summarise(reviews: Review[]): RatingSummary {
    if (!reviews.length) {
      return { average: 0, count: 0, distribution: [0, 0, 0, 0, 0] };
    }

    const distribution = [0, 0, 0, 0, 0];
    let total = 0;
    for (const review of reviews) {
      const stars = Math.min(Math.max(Math.round(review.rating), 1), 5);
      distribution[stars - 1]++;
      total += review.rating;
    }

    return {
      average: Math.round((total / reviews.length) * 10) / 10,
      count: reviews.length,
      distribution,
    };
  }

  private get(path: string, page: number, size: number): Observable<ReviewPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http
      .get<{ data: ReviewPage }>(`${this.baseUrl}${path}`, { params })
      .pipe(
        map(r => r.data ?? { content: [], totalElements: 0, totalPages: 0, number: 0 })
      );
  }
}
