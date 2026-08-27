import { Component, OnInit } from '@angular/core';
import { UserService } from '../../user.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { Router } from '@angular/router';
import { AVATAR_FALLBACK } from '../../../shared/avatar';
import { apiMessage } from '../../../shared/api-error';

@Component({
  selector: 'app-list-artist',
  standalone: false,
  templateUrl: './list-artist.component.html',
})
export class ListArtistComponent implements OnInit {
  readonly fallbackAvatar = AVATAR_FALLBACK;

  artists: any[] = [];
  selectedArtist: any = null;
  availabilityDays: any[] = [];
  bookingForm!: FormGroup;

  loading = true;
  error = '';
  booking = false;

  // Both of these were Bootstrap modals opened with `new bootstrap.Modal(...)`.
  // That bundle is no longer loaded, so they are plain component state.
  availabilityOpen = false;
  bookingOpen = false;

  /** Stops the date field offering yesterday. */
  readonly today = new Date().toISOString().split('T')[0];

  constructor(
    private service: UserService,
    private fb: FormBuilder,
    private toast: ToastrService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.initBookingForm();
    this.loadArtists();
  }

  loadArtists(): void {
    this.loading = true;
    this.error = '';
    this.service.getAllArtists().subscribe({
      next: res => {
        this.artists = res?.data?.content || [];
        this.loading = false;
      },
      error: err => {
        // The original swallowed this branch entirely, so a failed load looked
        // exactly like an empty roster.
        console.error('Failed to load artists', err);
        this.error = 'Could not load artists.';
        this.loading = false;
      },
    });
  }

  initBookingForm(): void {
    this.bookingForm = this.fb.group({
      artistId: [null, Validators.required],
      venue: ['', Validators.required],
      eventDate: ['', Validators.required],
      startTime: ['', Validators.required],
      endTime: ['', Validators.required],
      eventType: ['', Validators.required],
    });
  }

  invalid(control: string): boolean {
    const field = this.bookingForm.get(control);
    return !!field && field.invalid && field.touched;
  }

  genresOf(artist: any): string[] {
    const genres = artist?.genre ?? artist?.genres ?? [];
    return genres.map((g: any) => g?.name ?? g).filter(Boolean).slice(0, 4);
  }

  onImageError(artist: any): void {
    artist.profilePictureUrl = null;
  }

  hireArtist(artist: any): void {
    this.service.getArtistAvailability(artist.stageName).subscribe({
      next: res => {
        this.selectedArtist = { ...artist, artistId: res?.data?.artistId ?? artist.artistId };
        this.availabilityDays = res?.data?.availabilities ?? [];
        this.availabilityOpen = true;
      },
      error: err => {
        console.error('Failed to load availability', err);
        // No published schedule is not a dead end — open the sheet so the
        // booker can still send a request with their own times.
        this.selectedArtist = artist;
        this.availabilityDays = [];
        this.availabilityOpen = true;
      },
    });
  }

  closeAvailability(): void {
    this.availabilityOpen = false;
  }

  closeBooking(): void {
    this.bookingOpen = false;
  }

  bookSlot(day: string, startTime: string, endTime: string): void {
    this.bookingForm.patchValue({
      artistId: this.selectedArtist?.artistId ?? this.selectedArtist?.id,
      startTime: this.toInputTime(startTime),
      endTime: this.toInputTime(endTime),
    });
    this.availabilityOpen = false;
    this.bookingOpen = true;
  }

  /** `<input type="time">` wants HH:mm; the API sends HH:mm:ss. */
  private toInputTime(time: string): string {
    if (!time) return '';
    const [hour, minute] = time.split(':');
    return hour && minute ? `${hour}:${minute}` : '';
  }

  bookNow(): void {
    if (this.bookingForm.invalid) {
      this.bookingForm.markAllAsTouched();
      return;
    }
    if (this.booking) {
      return;
    }
    this.booking = true;

    const to12Hour = (time: string) => {
      const [hourStr, minute] = time.split(':');
      let hour = parseInt(hourStr, 10);
      const ampm = hour >= 12 ? 'PM' : 'AM';
      hour = hour % 12 || 12;
      return `${hour.toString().padStart(2, '0')}:${minute} ${ampm}`;
    };

    const payload = {
      ...this.bookingForm.value,
      startTime: to12Hour(this.bookingForm.value.startTime),
      endTime: to12Hour(this.bookingForm.value.endTime),
    };

    this.service.bookArtist(payload).subscribe({
      next: (paymentUrl: string) => {
        this.booking = false;
        this.bookingOpen = false;
        this.toast.success('Booking request sent.');

        if (paymentUrl && paymentUrl.startsWith('http')) {
          window.location.href = paymentUrl;
          return;
        }
        this.router.navigate(['/user/bookings']);
      },
      error: error => {
        this.booking = false;
        console.error('Booking failed:', error);
        this.toast.error(apiMessage(error, 'Booking failed. Please try again later.'));
      },
    });
  }

  pretty(value: string | undefined): string {
    return (value || '')
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, c => c.toUpperCase());
  }

  shortTime(time: string | null | undefined): string {
    if (!time) return '';
    const [hourStr, minute] = time.split(':');
    const hour = parseInt(hourStr, 10);
    if (Number.isNaN(hour)) return time;
    const suffix = hour >= 12 ? 'PM' : 'AM';
    const display = hour % 12 === 0 ? 12 : hour % 12;
    return `${display}:${minute ?? '00'} ${suffix}`;
  }
}
