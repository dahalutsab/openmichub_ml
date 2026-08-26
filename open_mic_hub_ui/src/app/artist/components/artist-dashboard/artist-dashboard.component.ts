import { Component, OnInit } from '@angular/core';
import { ArtistService } from '../../artist.service';

/**
 * Artist overview.
 *
 * Was a CLI stub (`<p>artist-dashboard works!</p>`). Built from the endpoints
 * the artist area already calls: the wallet, and the bookings list.
 */
@Component({
  selector: 'app-artist-dashboard',
  standalone: false,
  templateUrl: './artist-dashboard.component.html',
})
export class ArtistDashboardComponent implements OnInit {
  loading = true;

  balance = 0;
  artistName = 'there';

  bookings: any[] = [];

  constructor(private artistService: ArtistService) {}

  ngOnInit(): void {
    this.artistService.getVirtualCoin().subscribe({
      next: (res: any) => (this.balance = res?.data?.balance ?? 0),
      error: () => {},
    });

    this.artistService.getArtist().subscribe({
      next: (res: any) => {
        const name = res?.data?.fullName as string | undefined;
        if (name) {
          this.artistName = name.split(' ')[0];
        }
      },
      error: () => {},
    });

    this.artistService.getAllBookings(0, 50).subscribe({
      next: (res: any) => {
        this.bookings = res?.data?.content ?? [];
        this.loading = false;
      },
      error: () => (this.loading = false),
    });
  }

  private countBy(status: string): number {
    return this.bookings.filter(b => b.bookingStatus === status).length;
  }

  get pendingCount(): number { return this.countBy('PENDING'); }
  get approvedCount(): number { return this.countBy('APPROVED'); }
  get rejectedCount(): number { return this.countBy('REJECTED'); }

  /** The five soonest requests still waiting on an answer. */
  get upcoming(): any[] {
    return this.bookings
      .filter(b => b.bookingStatus === 'PENDING')
      .slice(0, 5);
  }

  statusClass(status: string): string {
    switch (status) {
      case 'APPROVED': return 'omh-status-positive';
      case 'PENDING': return 'omh-status-pending';
      case 'REJECTED': return 'omh-status-critical';
      default: return 'omh-status-neutral';
    }
  }

  formatTime(time: string): string {
    if (!time) return '';
    const [hour, minute] = time.split(':');
    const h = parseInt(hour, 10);
    const ampm = h >= 12 ? 'PM' : 'AM';
    const formattedHour = h % 12 === 0 ? 12 : h % 12;
    return `${formattedHour}:${minute} ${ampm}`;
  }
}
