import { Component, OnInit } from '@angular/core';
import { UserService } from '../../user.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { Router } from '@angular/router';

declare var bootstrap: any;

@Component({
  selector: 'app-list-artist',
  standalone:false,
  templateUrl: './list-artist.component.html',
  styleUrls: ['./list-artist.component.scss']
})
export class ListArtistComponent implements OnInit {
  artists: any[] = [];
  selectedArtist: any = null;
  selectedAvailability: any = null;
  bookingForm!: FormGroup;

  constructor(private service: UserService, private fb: FormBuilder, private toast:ToastrService, private route:Router) {}

  ngOnInit(): void {
    this.loadArtists();
    this.initBookingForm();
  }

  loadArtists() {
    this.service.getAllArtists().subscribe({
      next: (res) => {
        this.artists = res?.data?.content || [];
      },

    });
  }

  initBookingForm() {
    this.bookingForm = this.fb.group({
      artistId: [null, Validators.required],
      venue: ['', Validators.required],
      eventDate: ['', Validators.required],
      startTime: ['', Validators.required],
      endTime: ['', Validators.required],
      eventType: ['', Validators.required],
  
    });
  }

  hireArtist(artist: any) {
  this.service.getArtistAvailability(artist.stageName).subscribe({
    next: (res) => {
      this.selectedArtist = {
        ...artist,
        artistId: res.data.artistId // ✅ make sure artistId is set
      };
      this.selectedAvailability = res.data;

      const modal = new bootstrap.Modal(document.getElementById('hireModal'));
      modal.show();
    },
  
  });
}


  bookSlot(day: string, startTime: string, endTime: string) {


  this.bookingForm.patchValue({
    artistId: this.selectedArtist.artistId,
    startTime,
    endTime
  });

  const formModal = new bootstrap.Modal(document.getElementById('bookingModal'));
  formModal.show();
}


  bookNow() {
  const formatTimeTo12Hour = (time: string) => {
    const [hourStr, minute] = time.split(':');
    let hour = parseInt(hourStr, 10);
    const ampm = hour >= 12 ? 'PM' : 'AM';
    hour = hour % 12 || 12;
    return `${hour.toString().padStart(2, '0')}:${minute} ${ampm}`;
  };

  const payload = {
    ...this.bookingForm.value,
    startTime: formatTimeTo12Hour(this.bookingForm.value.startTime),
    endTime: formatTimeTo12Hour(this.bookingForm.value.endTime)
  };

  this.service.bookArtist(payload).subscribe(
    (paymentUrl: string) => {
      this.toast.success('Booking successful.');

      // ✅ Close the modal after success
      const bookingModalEl = document.getElementById('bookingModal');
      if (bookingModalEl) {
        const bookingModal = bootstrap.Modal.getInstance(bookingModalEl);
        if (bookingModal) {
          bookingModal.hide();
        }
      }

      // ✅ Navigate to bookings or redirect to payment
      setTimeout(() => {
        this.route.navigate(['/user/bookings']);
            this.loadArtists();

        if (paymentUrl && paymentUrl.startsWith('http')) {
          window.location.href = paymentUrl;
        }
      }, 300); // slight delay for smooth transition
    },
    (error) => {
      this.toast.error('Booking failed. Please try again later.');
      console.error('Booking failed:', error);
    }
  );
}






}
