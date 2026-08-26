import { CommonModule } from '@angular/common';
import { Component, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { ArtistCardComponent } from './components/artist-card.component';
import { ArtistHit, DiscoveryService } from './discovery.service';

type ViewState = 'idle' | 'loading' | 'results' | 'empty' | 'error';
type SortKey = 'match' | 'price-asc' | 'price-desc' | 'rating';
type Layout = 'grid' | 'list';

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
  /** Server order, untouched — the model's ranking. */
  private ranked: ArtistHit[] = [];
  results: ArtistHit[] = [];
  total = 0;
  strategy = '';
  errorMessage = '';

  sort: SortKey = 'match';
  layout: Layout = 'grid';

  readonly sortOptions: { key: SortKey; label: string }[] = [
    { key: 'match', label: 'Best match' },
    { key: 'price-asc', label: 'Price ↑' },
    { key: 'price-desc', label: 'Price ↓' },
    { key: 'rating', label: 'Rating' },
  ];

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
  readonly skeletons = Array.from({ length: 8 });

  /**
   * Shown on the idle page, under the search.
   *
   * Landing on a search box above an empty screen tells a first-time visitor
   * nothing about whether the platform has anyone worth booking. A live slice of
   * the roster answers that before they type, and doubles as a worked example of
   * what results look like.
   */
  featured: ArtistHit[] = [];
  featuredLoading = false;

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
    } else {
      this.loadFeatured();
    }
  }

  private loadFeatured(): void {
    this.featuredLoading = true;
    this.discovery.recommendations({ limit: 8 })
      .pipe(finalize(() => (this.featuredLoading = false)))
      .subscribe({
        // A quiet failure is right here: the roster strip is a bonus, and an
        // error banner over an otherwise working search box would be noise.
        next: result => (this.featured = result.results ?? []),
        error: () => (this.featured = []),
      });
  }

  /**
   * Headline figures for the current result set.
   *
   * Cheap to compute and worth showing: a price range and a city count tell an
   * organizer whether to widen the search far faster than scrolling will.
   */
  get stats(): { label: string; value: string }[] {
    if (!this.results.length) return [];

    const rates = this.results.map(a => a.hourlyRate).sort((a, b) => a - b);
    const median = rates[Math.floor(rates.length / 2)];
    const cities = new Set(this.results.map(a => a.city).filter(Boolean));
    const topRated = this.results.filter(a => a.rating >= 4.5).length;

    return [
      { label: 'Acts', value: String(this.total) },
      { label: 'Median rate', value: `Rs ${median.toLocaleString()}` },
      { label: 'Range', value: `${(rates[0] / 1000).toFixed(1)}k–${(rates[rates.length - 1] / 1000).toFixed(1)}k` },
      { label: 'Cities', value: String(cities.size) },
      { label: '4.5★ and up', value: String(topRated) },
    ];
  }

  /** Genre chips built from what actually came back, not a fixed list. */
  get resultGenres(): { name: string; count: number }[] {
    const counts = new Map<string, number>();
    for (const artist of this.results) {
      for (const genre of artist.subGenres ?? []) {
        counts.set(genre, (counts.get(genre) ?? 0) + 1);
      }
    }
    return [...counts.entries()]
      .map(([name, count]) => ({ name, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 8);
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

  setSort(key: SortKey): void {
    this.sort = key;
    this.applySort();
  }

  setLayout(layout: Layout): void {
    this.layout = layout;
  }

  toggleGenre(name: string): void {
    this.genre = this.genre === name ? null : name;
    this.submit();
  }

  /**
   * Reorders in place rather than refetching.
   *
   * 'Best match' restores the server's ordering, which is the model's ranking —
   * so switching away and back is lossless.
   */
  private applySort(): void {
    const byKey: Record<SortKey, (a: ArtistHit, b: ArtistHit) => number> = {
      match: () => 0,
      'price-asc': (a, b) => a.hourlyRate - b.hourlyRate,
      'price-desc': (a, b) => b.hourlyRate - a.hourlyRate,
      rating: (a, b) => b.rating - a.rating || b.completedBookings - a.completedBookings,
    };
    this.results = this.sort === 'match' ? [...this.ranked] : [...this.ranked].sort(byKey[this.sort]);
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
    this.city = null;
    this.eventType = null;
    this.genre = null;
    this.budgetPerHour = null;
    this.state = 'idle';
    this.results = [];
    this.ranked = [];
    this.router.navigate([], { relativeTo: this.route, queryParams: {} });
    if (!this.featured.length) this.loadFeatured();
  }

  /** `/` focuses the search field, the way every search-led product behaves. */
  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    const typingAlready = target && /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName);

    if (event.key === '/' && !typingAlready) {
      event.preventDefault();
      document.getElementById('omh-query')?.focus();
    }
    if (event.key === 'Escape' && typingAlready) {
      (target as HTMLElement).blur();
    }
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
          this.ranked = result.results ?? [];
          this.total = result.total ?? this.ranked.length;
          this.strategy = result.strategy ?? '';
          this.applySort();
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
