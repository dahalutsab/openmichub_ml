import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { UserService } from '../../user.service';

@Component({
  selector: 'app-booking-details',
  standalone:false,
  templateUrl: './booking-details.component.html',
  styleUrls: ['./booking-details.component.scss'],
})
export class BookingDetailsComponent implements OnInit {
  bookings: any[] = [];
  isLoading = true;
  error: string | null = null;
  selectedBookingId: number | null = null;

  constructor(
    private service: UserService,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.fetchBookings();
  }

  fetchBookings(): void {
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

  formatTime(time: string): string {
    if (!time) return '';
    const [hour, minute] = time.split(':');
    const h = parseInt(hour, 10);
    const ampm = h >= 12 ? 'PM' : 'AM';
    const formattedHour = h % 12 === 0 ? 12 : h % 12;
    return `${formattedHour}:${minute} ${ampm}`;
  }

  togglePaymentOptions(bookingId: number): void {
    this.selectedBookingId = this.selectedBookingId === bookingId ? null : bookingId;
  }

  processPayment(bookingId: number, paymentType: 'partial' | 'full'): void {
    const url = `http://localhost:8181/api/v1/payments/booking?bookingId=${bookingId}&paymentType=${paymentType}`;

    // Assuming backend returns plain URL string in response
    this.http.post(url, null, { responseType: 'text' }).subscribe({
      next: (paymentUrl: string) => {
        if (paymentUrl) {
          window.open(paymentUrl, '_blank'); // open the Khalti payment URL in a new tab
        } else {
          alert('Payment URL not received from server.');
        }
        this.selectedBookingId = null;
      },
      error: (err) => {
        console.error('Payment failed:', err);
        alert('Payment failed. Please try again.');
      }
    });
  }
}
