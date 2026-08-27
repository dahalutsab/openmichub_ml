import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { UserService } from '../../user/user.service';

/**
 * Requesting a booking: pick a slot, fill in the event, send.
 *
 * Extracted from the browse-artists screen, which was the only place a booking
 * could be started — the artist profile's own button just navigated back to the
 * list. One implementation means the two cannot drift, and the profile can
 * actually do the thing its button claims.
 *
 * Availability is a convenience, not a gate: an artist who has published no
 * schedule can still be asked, with times the booker supplies.
 */
@Component({
  selector: 'omh-booking-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="omh-modal-backdrop" (click)="close()">
      <div class="omh-modal max-w-lg animate-slide-up" (click)="$event.stopPropagation()"
           role="dialog" aria-modal="true" aria-labelledby="booking-dialog-title">

        <div class="omh-modal-head">
          <div>
            <h2 class="omh-modal-title" id="booking-dialog-title">
              {{ step() === 'slots' ? 'When can they play?' : 'Request a booking' }}
            </h2>
            <p class="mt-0.5 text-sm text-muted">{{ artistName }}</p>
          </div>
          <button type="button" class="omh-btn-icon" aria-label="Close" (click)="close()">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <!-- Step 1: published availability -->
        <ng-container *ngIf="step() === 'slots'">
          <div class="omh-modal-body max-h-[55vh] overflow-y-auto">
            <div class="flex items-center justify-center gap-3 py-8" *ngIf="loadingSlots()">
              <span class="omh-spinner-md"></span>
              <span class="text-base text-muted">Checking availability…</span>
            </div>

            <ng-container *ngIf="!loadingSlots()">
              <div class="flex flex-col gap-4" *ngIf="days().length; else noSlots">
                <div *ngFor="let day of days()">
                  <p class="omh-label">{{ pretty(day.dayOfWeek) }}</p>
                  <div class="flex flex-col gap-2">
                    <button *ngFor="let slot of day.availabilityTimes"
                            type="button"
                            class="flex items-center justify-between rounded-card border border-line px-3 py-2.5 text-left cursor-pointer hover:border-accent"
                            (click)="chooseSlot(slot)">
                      <span class="text-base text-ink">
                        {{ shortTime(slot.startTime) }} – {{ shortTime(slot.endTime) }}
                      </span>
                      <span class="omh-chip">Choose</span>
                    </button>
                  </div>
                </div>
              </div>

              <ng-template #noSlots>
                <div class="omh-empty border-0 py-8">
                  <i class="bi bi-calendar-x omh-empty-icon"></i>
                  <p class="omh-empty-title">No published schedule</p>
                  <p class="omh-empty-text">
                    You can still ask, with the times that suit your event.
                  </p>
                </div>
              </ng-template>
            </ng-container>
          </div>

          <div class="omh-modal-foot">
            <button type="button" class="omh-btn-secondary" (click)="close()">Cancel</button>
            <button type="button" class="omh-btn-primary" (click)="step.set('form')">
              Pick my own times
            </button>
          </div>
        </ng-container>

        <!-- Step 2: the request -->
        <form *ngIf="step() === 'form'" [formGroup]="form" (ngSubmit)="submit()">
          <div class="omh-modal-body">
            <div class="grid gap-4">
              <div class="omh-field">
                <label for="bd-venue" class="omh-label">Venue <span class="text-hot">*</span></label>
                <input id="bd-venue" type="text" class="omh-input" formControlName="venue"
                       placeholder="Where is the event?">
                <p class="omh-error" *ngIf="invalid('venue')">A venue is required.</p>
              </div>

              <div class="omh-field">
                <label for="bd-date" class="omh-label">Event date <span class="text-hot">*</span></label>
                <input id="bd-date" type="date" class="omh-input" formControlName="eventDate" [min]="today">
                <p class="omh-error" *ngIf="invalid('eventDate')">Pick a date.</p>
              </div>

              <div class="grid gap-4 sm:grid-cols-2">
                <div class="omh-field">
                  <label for="bd-start" class="omh-label">Start <span class="text-hot">*</span></label>
                  <input id="bd-start" type="time" class="omh-input" formControlName="startTime">
                  <p class="omh-error" *ngIf="invalid('startTime')">Pick a start time.</p>
                </div>
                <div class="omh-field">
                  <label for="bd-end" class="omh-label">End <span class="text-hot">*</span></label>
                  <input id="bd-end" type="time" class="omh-input" formControlName="endTime">
                  <p class="omh-error" *ngIf="invalid('endTime')">Pick an end time.</p>
                </div>
              </div>

              <div class="omh-field">
                <label for="bd-type" class="omh-label">Event type <span class="text-hot">*</span></label>
                <input id="bd-type" type="text" class="omh-input" formControlName="eventType"
                       placeholder="Concert, comedy night, private party…">
                <p class="omh-error" *ngIf="invalid('eventType')">Tell the artist what the event is.</p>
              </div>
            </div>

            <div class="omh-alert-info mt-4 text-sm">
              <i class="bi bi-info-circle mt-0.5"></i>
              <span>The artist reviews your request before anything is charged.</span>
            </div>
          </div>

          <div class="omh-modal-foot">
            <button type="button" class="omh-btn-secondary"
                    (click)="days().length ? step.set('slots') : close()">
              {{ days().length ? 'Back' : 'Cancel' }}
            </button>
            <button type="submit" class="omh-btn-primary" [disabled]="sending()">
              <span class="omh-spinner-sm" *ngIf="sending()"></span>
              {{ sending() ? 'Sending…' : 'Send request' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
})
export class BookingDialogComponent implements OnInit {
  @Input({ required: true }) artistId!: number;
  @Input() artistName: string | null = null;
  /** Needed because availability is keyed by stage name on the API. */
  @Input() stageName: string | null = null;

  @Output() booked = new EventEmitter<void>();
  @Output() dismissed = new EventEmitter<void>();

  readonly step = signal<'slots' | 'form'>('slots');
  readonly days = signal<any[]>([]);
  readonly loadingSlots = signal(true);
  readonly sending = signal(false);

  readonly today = new Date().toISOString().split('T')[0];

  form!: FormGroup;

  constructor(
    private fb: FormBuilder,
    private service: UserService,
    private toast: ToastrService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      artistId: [this.artistId, Validators.required],
      venue: ['', Validators.required],
      eventDate: ['', Validators.required],
      startTime: ['', Validators.required],
      endTime: ['', Validators.required],
      eventType: ['', Validators.required],
    });

    if (!this.stageName) {
      this.loadingSlots.set(false);
      this.step.set('form');
      return;
    }

    this.service.getArtistAvailability(this.stageName).subscribe({
      next: res => {
        this.days.set(res?.data?.availabilities ?? []);
        this.loadingSlots.set(false);
        if (!this.days().length) {
          this.step.set('form');
        }
      },
      error: () => {
        this.days.set([]);
        this.loadingSlots.set(false);
        this.step.set('form');
      },
    });
  }

  invalid(control: string): boolean {
    const field = this.form.get(control);
    return !!field && field.invalid && field.touched;
  }

  chooseSlot(slot: any): void {
    this.form.patchValue({
      startTime: this.toInputTime(slot.startTime),
      endTime: this.toInputTime(slot.endTime),
    });
    this.step.set('form');
  }

  close(): void {
    if (!this.sending()) {
      this.dismissed.emit();
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.sending()) {
      return;
    }
    this.sending.set(true);

    const payload = {
      ...this.form.value,
      startTime: this.to12Hour(this.form.value.startTime),
      endTime: this.to12Hour(this.form.value.endTime),
    };

    this.service.bookArtist(payload).subscribe({
      next: (paymentUrl: string) => {
        this.sending.set(false);
        this.toast.success('Booking request sent.');
        this.booked.emit();

        if (paymentUrl && paymentUrl.startsWith('http')) {
          window.location.href = paymentUrl;
          return;
        }
        this.router.navigate(['/user/bookings']);
      },
      error: err => {
        this.sending.set(false);
        const detail = err?.error?.message || err?.error?.error;
        this.toast.error(detail || 'Booking failed. Please try again.');
        console.error('Booking failed', err);
      },
    });
  }

  /** `<input type="time">` wants HH:mm; the API sends HH:mm:ss. */
  private toInputTime(time: string): string {
    if (!time) return '';
    const [hour, minute] = time.split(':');
    return hour && minute ? `${hour}:${minute}` : '';
  }

  /** The booking API expects a 12-hour clock. */
  private to12Hour(time: string): string {
    const [hourStr, minute] = time.split(':');
    let hour = parseInt(hourStr, 10);
    const suffix = hour >= 12 ? 'PM' : 'AM';
    hour = hour % 12 || 12;
    return `${hour.toString().padStart(2, '0')}:${minute} ${suffix}`;
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
