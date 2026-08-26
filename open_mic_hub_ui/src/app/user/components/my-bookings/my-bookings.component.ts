import { Component, OnInit } from '@angular/core';
import { UserService } from '../../user.service';


@Component({
  selector: 'app-my-bookings',
  standalone:false,
  templateUrl: './my-bookings.component.html',
})
export class MyBookingsComponent implements OnInit {
  bookings: any[] = [];
  isLoading = true;
  error: string | null = null;

  constructor(private service: UserService) {}

  ngOnInit(): void {
    this.fetchBookings();
  }

  fetchBookings(): void {
    this.isLoading = true;
    this.error = null;
    this.service.getAllBookings().subscribe({
      next: (res: any) => {
        this.bookings = res?.data?.content || []; 
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error fetching bookings:', err);
        this.error = 'Failed to load bookings.';
        this.isLoading = false;
      }
    });
  }

  statusClass(status: string): string {
    switch ((status || '').toUpperCase()) {
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

  /** The API sends "18:00:00"; that is not something to show a person. */
  shortTime(time: string | null | undefined): string {
    if (!time) return '';
    const [hourStr, minute] = time.split(':');
    const hour = parseInt(hourStr, 10);
    if (Number.isNaN(hour)) return time;
    const suffix = hour >= 12 ? 'PM' : 'AM';
    const display = hour % 12 === 0 ? 12 : hour % 12;
    return `${display}:${minute ?? '00'} ${suffix}`;
  }

  pretty(value: string | undefined): string {
    return (value || '')
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, c => c.toUpperCase());
  }
}
