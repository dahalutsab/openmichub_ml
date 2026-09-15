import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../environment/environment';
import { rotateVisitorId } from '../shared/visitor';

/** One ranked artist, as returned by /api/v1/discover. */
export interface ArtistHit {
  artistId: number;
  /** Public URL segment. Prefer it over the id when linking to the profile. */
  slug?: string;
  stageName: string;
  fullName?: string;
  bio?: string;
  city?: string;
  hourlyRate: number;
  rating: number;
  completedBookings: number;
  subGenres: string[];
  parentGenres: string[];
  profileImage?: string;
  /** Ranking score. Comparable within one result list, not across searches. */
  score: number;
  /** Cosine similarity to the query text; absent when there was no text query. */
  similarity?: number;
  /** Whether this position took the visitor's own history into account, signed in or not. */
  personalized?: boolean;
  /**
   * Why this act was raised — "you have booked them before", "you keep coming back to Jazz",
   * or for anyone, "in demand this month". Never invented; often empty.
   */
  reasons?: string[];
}

export interface DiscoveryResult {
  query?: string;
  total: number;
  /** Which ranker produced the ordering — surfaced so the UI can be honest about it. */
  strategy: string;
  /** Whether this list was shaped by the viewer's own searches, views and bookings. */
  personalized?: boolean;
  results: ArtistHit[];
  /** Identifies this served list, so a click can say which list and position it came from. */
  requestId?: string;
}

export interface DiscoveryFilters {
  city?: string | null;
  eventType?: string | null;
  budgetPerHour?: number | null;
  genre?: string | null;
  limit?: number;
}

/** Shape of the API envelope every endpoint wraps its payload in. */
interface ApiEnvelope<T> {
  timestamp: string;
  message: string;
  data: T;
  status: string;
}

/**
 * The API falls back to an unranked listing when the ML service is unreachable,
 * and that response carries `artists` rather than `results`. Both are normalised
 * here so components only ever deal with one shape.
 */
interface FallbackPayload {
  strategy: string;
  artists?: unknown[];
}

@Injectable({ providedIn: 'root' })
export class DiscoveryService {
  private readonly base = `${environment.baseUrl}/discover`;

  constructor(private readonly http: HttpClient) {}

  search(query: string, filters: DiscoveryFilters = {}): Observable<DiscoveryResult> {
    let params = new HttpParams().set('q', query);
    params = this.applyFilters(params, filters);

    return this.http
      .get<ApiEnvelope<DiscoveryResult | FallbackPayload>>(`${this.base}/search`, { params })
      .pipe(map(response => this.normalise(response.data, query)));
  }

  recommendations(filters: DiscoveryFilters = {}): Observable<DiscoveryResult> {
    const params = this.applyFilters(new HttpParams(), filters);

    return this.http
      .get<ApiEnvelope<DiscoveryResult | FallbackPayload>>(`${this.base}/recommendations`, { params })
      .pipe(map(response => this.normalise(response.data)));
  }

  /**
   * Tells discovery which artist was chosen from a list, and from where in it.
   *
   * Fire-and-forget: it must never delay opening the profile, and a lost click costs one signal.
   * `position` is the artist's place in the list as the server ordered it, not after a client-side
   * sort - a click on the cheapest act is not evidence about the first-ranked one.
   */
  recordClick(requestId: string | undefined, artistId: number, position: number): void {
    if (!requestId || position < 0) return;
    this.http
      .post(`${this.base}/clicks`, { requestId, artistId, position })
      .subscribe({ error: () => undefined });
  }

  /**
   * Moves what this browser did while signed out onto the account that just signed in, then
   * starts a fresh visitor id. Called once, straight after a successful sign-in; resolves either
   * way, because failing to carry history over is no reason to hold up the sign-in.
   */
  claimVisitorHistory(): Promise<void> {
    return new Promise(resolve => {
      this.http.post(`${this.base}/visitor/claim`, {}).subscribe({
        next: () => {
          rotateVisitorId();
          resolve();
        },
        error: () => resolve(),
      });
    });
  }

  private applyFilters(params: HttpParams, filters: DiscoveryFilters): HttpParams {
    if (filters.city) params = params.set('city', filters.city);
    if (filters.eventType) params = params.set('eventType', filters.eventType);
    if (filters.budgetPerHour) params = params.set('budgetPerHour', filters.budgetPerHour);
    if (filters.genre) params = params.set('genre', filters.genre);
    params = params.set('limit', filters.limit ?? 24);
    return params;
  }

  private normalise(data: DiscoveryResult | FallbackPayload, query?: string): DiscoveryResult {
    const ranked = data as DiscoveryResult;
    if (Array.isArray(ranked?.results)) {
      return { ...ranked, query: ranked.query ?? query };
    }

    // Unranked fallback: keep the shape, and let `strategy` tell the UI.
    const fallback = data as FallbackPayload;
    return {
      query,
      strategy: fallback?.strategy ?? 'unranked listing',
      personalized: false,
      total: fallback?.artists?.length ?? 0,
      results: [],
    };
  }
}
