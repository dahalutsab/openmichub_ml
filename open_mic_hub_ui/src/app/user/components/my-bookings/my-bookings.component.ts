import { Component, OnInit } from '@angular/core';
import { UserService } from '../../user.service';


@Component({
  selector: 'app-my-bookings',
  standalone:false,
  templateUrl: './my-bookings.component.html',
  styleUrls: ['./my-bookings.component.scss']
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
}
