import { Component, OnInit, computed, signal } from '@angular/core';
import { AnalyticsService, BookerOverview, RANGES } from '../../../shared/analytics.service';
import { AVATAR_FALLBACK } from '../../../shared/avatar';

/**
 * The board for whoever is doing the hiring — an organizer or an audience
 * account. Framed around spend and what is coming up, rather than earnings.
 */
@Component({
  selector: 'app-user-dashboard',
  standalone: false,
  templateUrl: './user-dashboard.component.html',
})
export class UserDashboardComponent implements OnInit {
  readonly ranges = RANGES;
  readonly fallbackAvatar = AVATAR_FALLBACK;

  readonly loading = signal(true);
  readonly error = signal('');
  readonly days = signal(30);
  readonly data = signal<BookerOverview | null>(null);

  readonly firstName = computed(() => {
    const full = this.data()?.fullName ?? '';
    return full.split(' ')[0] || 'there';
  });

  /** The very next gig, pulled out so it can be given its own card. */
  readonly nextBooking = computed(() => this.data()?.upcomingBookings?.[0] ?? null);

  constructor(private analytics: AnalyticsService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');

    this.analytics.bookerOverview(this.days()).subscribe({
      next: overview => {
        this.data.set(overview);
        this.loading.set(false);
      },
      error: err => {
        console.error('Failed to load booking analytics', err);
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

  shortTime(time: string | null): string {
    if (!time) return '';
    const [hourStr, minute] = time.split(':');
    const hour = parseInt(hourStr, 10);
    if (Number.isNaN(hour)) return time;
    const suffix = hour >= 12 ? 'PM' : 'AM';
    const display = hour % 12 === 0 ? 12 : hour % 12;
    return `${display}:${minute ?? '00'} ${suffix}`;
  }

  /** Days until an event, for the countdown on the next-gig card. */
  daysUntil(date: string | null): number {
    if (!date) return 0;
    const target = new Date(date);
    if (Number.isNaN(target.getTime())) return 0;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    target.setHours(0, 0, 0, 0);
    return Math.max(Math.round((target.getTime() - today.getTime()) / 86_400_000), 0);
  }

  countdownLabel(date: string | null): string {
    const days = this.daysUntil(date);
    if (days === 0) return 'Today';
    if (days === 1) return 'Tomorrow';
    return `In ${days} days`;
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
