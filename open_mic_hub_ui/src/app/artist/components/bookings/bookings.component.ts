import { Component, OnInit } from '@angular/core';
import { ArtistService } from '../../artist.service';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-bookings',
  standalone: false,
  templateUrl: './bookings.component.html',
})
export class BookingsComponent implements OnInit {
  bookings: any[] = [];
  isLoading = true;
  error: string | null = null;

  /** Booking currently being approved or declined, so its row can be disabled. */
  busyId: number | null = null;

  constructor(private service: ArtistService, private toast: ToastrService) {}

  ngOnInit(): void {
    this.fetchBookings();
  }

  get pendingCount(): number {
    return this.bookings.filter(b => b.bookingStatus === 'PENDING').length;
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

  approveBooking(bookingId: number): void {
    this.busyId = bookingId;
    this.service.approvedBookings(bookingId).subscribe({
      next: () => {
        this.busyId = null;
        // Previously this fired a toast *and* a native alert() for the same
        // event, and the error branch was empty — a failed approval looked
        // exactly like nothing happening.
        this.toast.success('Booking approved');
        this.fetchBookings();
      },
      error: () => {
        this.busyId = null;
        this.toast.error('Failed to approve booking');
      }
    });
  }

  rejectBooking(bookingId: number): void {
    this.busyId = bookingId;
    this.service.rejectedBookings(bookingId).subscribe({
      next: () => {
        this.busyId = null;
        this.toast.success('Booking declined');
        this.fetchBookings();
      },
      error: () => {
        this.busyId = null;
        this.toast.error('Failed to decline booking');
      }
    });
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
