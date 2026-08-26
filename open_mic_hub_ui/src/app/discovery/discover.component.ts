import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { ArtistCardComponent } from './components/artist-card.component';
import { ArtistHit, DiscoveryService } from './discovery.service';

type ViewState = 'idle' | 'loading' | 'results' | 'empty' | 'error';

@Component({
  selector: 'omh-discover',
  standalone: true,
  imports: [CommonModule, FormsModule, ArtistCardComponent],
  templateUrl: './discover.component.html',
})
export class DiscoverComponent implements OnInit {
  private readonly discovery = inject(DiscoveryService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  query = '';
  city: string | null = null;
  eventType: string | null = null;
  genre: string | null = null;
  budgetPerHour: number | null = null;

  state: ViewState = 'idle';
  results: ArtistHit[] = [];
  total = 0;
  strategy = '';
  errorMessage = '';

  /**
   * Shown before the first search. Written the way an organizer would actually
   * describe a booking, because the point of the feature is that they no longer
   * have to translate that into filters.
   */
  readonly examples = [
    'mellow acoustic set for a restaurant opening',
    'high energy band to headline an outdoor festival',
    'jazz trio for a corporate dinner',
    'traditional folk music for a wedding',
    'dj for a late night club event',
  ];

  readonly cities = [
    'Kathmandu', 'Lalitpur', 'Bhaktapur', 'Pokhara', 'Chitwan',
    'Butwal', 'Nepalgunj', 'Biratnagar', 'Dharan', 'Janakpur',
  ];

  readonly eventTypes = [
    'Wedding', 'Corporate', 'Festival', 'Birthday',
    'Club Night', 'Open Mic', 'Charity Gala', 'Restaurant',
  ];

  /** Placeholder cards while a search is in flight. */
  readonly skeletons = Array.from({ length: 6 });

  ngOnInit(): void {
    // Read state back out of the URL, so a result page can be shared, bookmarked
    // and reached with the back button rather than resetting to blank.
    const params = this.route.snapshot.queryParamMap;
    this.query = params.get('q') ?? '';
    this.city = params.get('city');
    this.eventType = params.get('eventType');
    this.genre = params.get('genre');
    const budget = params.get('budget');
    this.budgetPerHour = budget ? Number(budget) : null;

    if (this.query.trim() || this.hasFilters) {
      this.run();
    }
  }

  get hasFilters(): boolean {
    return Boolean(this.city || this.eventType || this.genre || this.budgetPerHour);
  }

  get activeFilterCount(): number {
    return [this.city, this.eventType, this.genre, this.budgetPerHour].filter(Boolean).length;
  }

  /** True when results came from the trained model rather than the fallback. */
  get isRanked(): boolean {
    return this.strategy?.toLowerCase().includes('lambdarank') ?? false;
  }

  useExample(example: string): void {
    this.query = example;
    this.submit();
  }

  submit(): void {
    this.syncUrl();
    this.run();
  }

  clearFilters(): void {
    this.city = null;
    this.eventType = null;
    this.genre = null;
    this.budgetPerHour = null;
    this.submit();
  }

  reset(): void {
    this.query = '';
    this.clearFilters();
    this.state = 'idle';
    this.results = [];
    this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  trackByArtist(_index: number, artist: ArtistHit): number {
    return artist.artistId;
  }

  private syncUrl(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: this.query.trim() || null,
        city: this.city || null,
        eventType: this.eventType || null,
        genre: this.genre || null,
        budget: this.budgetPerHour || null,
      },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private run(): void {
    const trimmed = this.query.trim();
    if (!trimmed && !this.hasFilters) {
      this.state = 'idle';
      return;
    }

    this.state = 'loading';
    this.errorMessage = '';

    const filters = {
      city: this.city,
      eventType: this.eventType,
      genre: this.genre,
      budgetPerHour: this.budgetPerHour,
      limit: 24,
    };

    // With no words to match on there is nothing to retrieve against, so this
    // falls through to ranking the catalogue by the filters alone.
    const request$ = trimmed
      ? this.discovery.search(trimmed, filters)
      : this.discovery.recommendations(filters);

    request$
      .pipe(finalize(() => {
        if (this.state === 'loading') this.state = 'empty';
      }))
      .subscribe({
        next: result => {
          this.results = result.results ?? [];
          this.total = result.total ?? this.results.length;
          this.strategy = result.strategy ?? '';
          this.state = this.results.length ? 'results' : 'empty';
        },
        error: error => {
          this.errorMessage =
            error?.error?.message ?? 'We could not reach the search service. Please try again.';
          this.state = 'error';
        },
      });
  }
}
