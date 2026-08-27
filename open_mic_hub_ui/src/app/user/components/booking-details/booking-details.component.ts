import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { UserService } from '../../user.service';
import { ToastrService } from 'ngx-toastr';
import { apiMessage } from '../../../shared/api-error';
import { environment } from '../../../environment/environment';

@Component({
  selector: 'app-booking-details',
  standalone: false,
  templateUrl: './booking-details.component.html',
})
export class BookingDetailsComponent implements OnInit {
  bookings: any[] = [];
  isLoading = true;
  error: string | null = null;
  selectedBookingId: number | null = null;
  paying = false;

  constructor(
    private service: UserService,
    private http: HttpClient,
    private toast: ToastrService
  ) {}

  ngOnInit(): void {
    this.fetchBookings();
  }

  fetchBookings(): void {
    this.isLoading = true;
    this.error = null;
    this.service.getAllUserBookings().subscribe({
      next: (res: any) => {
        this.bookings = res?.data?.content || [];
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error fetching bookings:', err);
        this.error = 'Failed to load bookings.';
        this.isLoading = false;
      },
    });
  }

  statusClass(status: string): string {
    switch ((status || '').toUpperCase()) {
      case 'CONFIRMED':
      case 'COMPLETED':
        return 'omh-status-positive';
      case 'PENDING':
        return 'omh-status-pending';
      case 'REJECTED':
      case 'CANCELLED':
      case 'DECLINED':
        return 'omh-status-critical';
      default:
        return 'omh-status-neutral';
    }
  }

  formatTime(time: string): string {
    if (!time) return '';
    const [hour, minute] = time.split(':');
    const h = parseInt(hour, 10);
    if (Number.isNaN(h)) return time;
    const ampm = h >= 12 ? 'PM' : 'AM';
    const formattedHour = h % 12 === 0 ? 12 : h % 12;
    return `${formattedHour}:${minute} ${ampm}`;
  }

  togglePaymentOptions(bookingId: number): void {
    this.selectedBookingId = this.selectedBookingId === bookingId ? null : bookingId;
  }

  /**
   * Hands off to the gateway. The endpoint replies with a redirect URL as plain
   * text.
   *
   * The base URL was hard-coded to localhost:8181 here, so this was the one
   * call in the screen that could not follow the environment.
   */
  processPayment(bookingId: number, paymentType: 'partial' | 'full'): void {
    if (this.paying) {
      return;
    }
    this.paying = true;

    const params = new HttpParams()
      .set('bookingId', bookingId)
      .set('paymentType', paymentType);

    this.http
      .post(`${environment.baseUrl}/payments/booking`, null, {
        params,
        responseType: 'text',
      })
      .subscribe({
        next: (paymentUrl: string) => {
          this.selectedBookingId = null;
          if (paymentUrl?.trim()) {
            // Same tab, not window.open: this runs in an HTTP callback rather
            // than a click handler, so the popup blocker ate the new window and
            // checkout appeared to do nothing. The gateway's return_url brings
            // the booker back to /user/artist/payment-callback either way.
            // `paying` stays true so the button cannot be pressed twice while
            // the browser navigates away.
            window.location.href = paymentUrl.trim();
          } else {
            this.paying = false;
            this.toast.error('No payment link came back from the server.');
          }
        },
        error: (err) => {
          this.paying = false;
          console.error('Payment failed:', err);
          // The server explains itself; repeating "please try again" over the
          // top of "the gateway is not configured" helps nobody.
          this.toast.error(apiMessage(err, 'Payment could not be started. Please try again.'));
        },
      });
  }
}
