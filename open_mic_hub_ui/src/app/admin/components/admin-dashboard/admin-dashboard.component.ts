import { Component, OnInit, signal } from '@angular/core';
import { AdminOverview, AnalyticsService, RANGES } from '../../../shared/analytics.service';
import { Slice, statusColor } from '../../../shared/charts';

/**
 * Platform analytics board.
 *
 * One request per range change: the API returns everything the screen draws,
 * so changing the window is a single round trip rather than eight.
 */
@Component({
  selector: 'app-admin-dashboard',
  standalone: false,
  templateUrl: './admin-dashboard.component.html',
})
export class AdminDashboardComponent implements OnInit {
  readonly ranges = RANGES;

  readonly loading = signal(true);
  readonly error = signal('');
  readonly days = signal(30);
  readonly data = signal<AdminOverview | null>(null);

  constructor(private analytics: AnalyticsService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');

    this.analytics.adminOverview(this.days()).subscribe({
      next: overview => {
        this.data.set(overview);
        this.loading.set(false);
      },
      error: err => {
        console.error('Failed to load platform analytics', err);
        this.error.set('Could not load analytics. Please try again.');
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

  /** Colour for a status slice, shared with the donut so legend and ring agree. */
  sliceColor(slice: Slice): string {
    return statusColor(slice.label);
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

  pretty(label: string | null): string {
    return (label || '')
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, c => c.toUpperCase());
  }

  /** Share of the leaderboard's top earner, for the inline bar in each row. */
  earningsShare(earnings: number): number {
    const top = this.data()?.topArtists?.[0]?.earnings ?? 0;
    return top <= 0 ? 0 : Math.round((earnings / top) * 100);
  }

  initials(name: string | null): string {
    return (name || '?')
      .split(' ')
      .map(part => part.charAt(0))
      .join('')
      .toUpperCase()
      .slice(0, 2);
  }
}
