import { Component, OnInit } from '@angular/core';
import { ArtistService } from '../../artist.service';
import { ToastrService } from 'ngx-toastr';


@Component({
  selector: 'app-bookings',
  standalone:false,
  templateUrl: './bookings.component.html',
  styleUrls: ['./bookings.component.scss']
})
export class BookingsComponent implements OnInit {
  bookings: any[] = [];
  isLoading = true;
  error: string | null = null;

  constructor(private service: ArtistService, private toast: ToastrService) {}

  ngOnInit(): void {
    this.fetchBookings();
  }

  fetchBookings(): void {
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
  this.service.approvedBookings(bookingId).subscribe({
    next: () => {
   
      this.fetchBookings();
         this.toast.success('Booking approved successfully');
             alert('Booking approve successfully')
    },
    error: () => {
      
    }
  });
}

rejectBooking(bookingId: number): void {
  this.service.rejectedBookings(bookingId).subscribe({
    next: () => {
  
      this.toast.success('Booking rejected successfully');
      this.fetchBookings();
    },
    error: () => {
      this.toast.error('Failed to reject booking');
    }
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

  // updateStatus(bookingId: number, newStatus: 'APPROVED' | 'REJECTED') {
  //   this.service.updateBookingStatus(bookingId, newStatus).subscribe({
  //     next: () => {
  //       this.toastr.success(`Booking ${newStatus.toLowerCase()} successfully`);
  //       this.fetchBookings(); // reload the list
  //     },
  //     error: () => {
  //       this.toastr.error('Failed to update status');
  //     }
  //   });
  }

