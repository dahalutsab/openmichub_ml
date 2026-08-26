import { Component, OnInit, computed, signal } from '@angular/core';
import { AnalyticsService, ArtistOverview, RANGES } from '../../../shared/analytics.service';
import { AVATAR_FALLBACK } from '../../../shared/avatar';

/**
 * The artist's own board.
 *
 * Laid out around a single hero figure — earnings — rather than a uniform grid
 * of tiles. An artist opens this to answer one question first, and the rest of
 * the screen supports that answer.
 */
@Component({
  selector: 'app-artist-dashboard',
  standalone: false,
  templateUrl: './artist-dashboard.component.html',
})
export class ArtistDashboardComponent implements OnInit {
  readonly ranges = RANGES;
  readonly fallbackAvatar = AVATAR_FALLBACK;

  readonly loading = signal(true);
  readonly error = signal('');
  readonly days = signal(30);
  readonly data = signal<ArtistOverview | null>(null);

  /** Spendable balance: the wallet less anything reserved against a payout. */
  readonly available = computed(() => {
    const d = this.data();
    if (!d) return 0;
    return Math.max(d.balance - d.heldFunds, 0);
  });

  readonly firstName = computed(() => {
    const full = this.data()?.fullName ?? '';
    return full.split(' ')[0] || 'there';
  });

  constructor(private analytics: AnalyticsService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');

    this.analytics.artistOverview(this.days()).subscribe({
      next: overview => {
        this.data.set(overview);
        this.loading.set(false);
      },
      error: err => {
        console.error('Failed to load artist analytics', err);
        this.error.set('Could not load your analytics. Please try again.');
        this.loading.set(false);
      },
    });
  }

  setRange(days: number): void {
    if (days === this.days()) {
      return;
    }
    this.days.set(days);
    this.load();
  }

  statusClass(status: string | null): string {
    switch ((status ?? '').toUpperCase()) {
      case 'CONFIRMED':
      case 'COMPLETED':
        return 'omh-status-positive';
      case 'PENDING':
        return 'omh-status-pending';
      case 'CANCELLED':
      case 'DECLINED':
      case 'NO_SHOW':
        return 'omh-status-critical';
      default:
        return 'omh-status-neutral';
    }
  }

  /** "18:00:00" from the API is not something to show a person. */
  shortTime(time: string | null): string {
    if (!time) return '';
    const [hourStr, minute] = time.split(':');
    const hour = parseInt(hourStr, 10);
    if (Number.isNaN(hour)) return time;
    const suffix = hour >= 12 ? 'PM' : 'AM';
    const display = hour % 12 === 0 ? 12 : hour % 12;
    return `${display}:${minute ?? '00'} ${suffix}`;
  }

  /** Rating out of five, as five glyph states. */
  get stars(): number[] {
    return [1, 2, 3, 4, 5];
  }

  starClass(position: number): string {
    const rating = this.data()?.rating ?? 0;
    if (rating >= position) return 'bi-star-fill';
    if (rating >= position - 0.5) return 'bi-star-half';
    return 'bi-star';
  }
}
