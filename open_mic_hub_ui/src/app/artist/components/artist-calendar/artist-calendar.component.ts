import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormArray, Validators, AbstractControl, ValidatorFn } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { HttpClient } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';

interface TimeSlot {
  id?: number;
  startTime: string;
  endTime: string;
}

interface Availability {
  id?: number;
  dayOfWeek: string;
  availabilityTimes: TimeSlot[];
}

interface AvailabilityResponse {
  timestamp: string;
  message: string;
  data: Availability[];
  status: string;
}

@Component({
  selector: 'app-artist-calendar',
  standalone: false,
  templateUrl: './artist-calendar.component.html',
  styleUrls: ['./artist-calendar.component.scss']
})
export class ArtistCalendarComponent implements OnInit {
  daysOfWeek = [
    { key: 'SUNDAY', label: 'Sunday', icon: 'wb_sunny' },
    { key: 'MONDAY', label: 'Monday', icon: 'work' },
    { key: 'TUESDAY', label: 'Tuesday', icon: 'work' },
    { key: 'WEDNESDAY', label: 'Wednesday', icon: 'work' },
    { key: 'THURSDAY', label: 'Thursday', icon: 'work' },
    { key: 'FRIDAY', label: 'Friday', icon: 'work' },
    { key: 'SATURDAY', label: 'Saturday', icon: 'weekend' }
  ];

  selectedTabIndex = 0;
  calendarForm: FormGroup;
  savedSlots: TimeSlot[] = [];
  isLoading = false;
  availabilityData: { [key: string]: TimeSlot[] } = {};
  timeFormat: '12h' | '24h' = '12h';
  timeOptions: string[] = [];

  quickTimeSlots = [
    { label: 'Morning', start: '09:00', end: '12:00' },
    { label: 'Afternoon', start: '13:00', end: '17:00' },
    { label: 'Evening', start: '18:00', end: '22:00' },
    { label: 'Full Day', start: '09:00', end: '17:00' },
    { label: 'Early Morning', start: '06:00', end: '09:00' },
    { label: 'Late Evening', start: '20:00', end: '23:00' }
  ];

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private toastr: ToastrService,
    private dialog: MatDialog
  ) {
    this.calendarForm = this.fb.group({
      timeSlots: this.fb.array([], [this.timeSlotsValidator()])
    });
    this.timeRangeValidator = this.timeRangeValidator.bind(this);
  }

  ngOnInit() {
    this.timeOptions = this.generateTimeOptions();
    this.loadExistingAvailability();
    this.addSlot();
  }

  toggleTimeFormat() {
    this.timeFormat = this.timeFormat === '12h' ? '24h' : '12h';
    this.timeOptions = this.generateTimeOptions();
  }

  generateTimeOptions(): string[] {
    const options: string[] = [];
    for (let hour = 0; hour < 24; hour++) {
      for (let minute = 0; minute < 60; minute += 30) {
        const time24 = `${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}`;
        const time12 = this.convertTo12HourFormat(hour, minute);
        options.push(this.timeFormat === '12h' ? time12 : time24);
      }
    }
    return options;
  }

  convertTo12HourFormat(hour: number, minute: number): string {
    const period = hour >= 12 ? 'PM' : 'AM';
    const hour12 = hour % 12 === 0 ? 12 : hour % 12;
    return `${hour12.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} ${period}`;
  }

  normalizeTo24Hour(time: string): string {
    const minutes = this.timeToMinutes(time);
    const hour = Math.floor(minutes / 60).toString().padStart(2, '0');
    const min = (minutes % 60).toString().padStart(2, '0');
    return `${hour}:${min}`;
  }

  get selectedDay() {
    return this.daysOfWeek[this.selectedTabIndex];
  }

  get timeSlots(): FormArray {
    return this.calendarForm.get('timeSlots') as FormArray;
  }

  addSlot() {
    const group = this.fb.group({
      startTime: ['', Validators.required],
      endTime: ['', Validators.required]
    }, { validators: this.timeRangeValidator });
    this.timeSlots.push(group);
  }

  removeSlot(index: number) {
    if (this.timeSlots.length > 1) {
      this.timeSlots.removeAt(index);
    } else {
      this.toastr.warning('At least one time slot is required');
    }
  }

  copyFromPreviousDay(): void {
    const previousDayIndex = (this.selectedTabIndex - 1 + this.daysOfWeek.length) % this.daysOfWeek.length;
    const previousDayKey = this.daysOfWeek[previousDayIndex].key;
    const previousSlots = this.availabilityData[previousDayKey] || [];

    if (previousSlots.length === 0) {
      this.toastr.warning('No slots found for previous day');
      return;
    }

    this.timeSlots.clear();
    previousSlots.forEach(slot => {
      const group = this.fb.group({
        startTime: [this.formatTimeForInput(slot.startTime), Validators.required],
        endTime: [this.formatTimeForInput(slot.endTime), Validators.required]
      }, { validators: this.timeRangeValidator });
      this.timeSlots.push(group);
    });
  }

  clearAllSlots(): void {
    this.timeSlots.clear();
    this.addSlot();
  }

  addQuickSlot(slot: { start: string; end: string; label: string }): void {
    const group = this.fb.group({
      startTime: [slot.start, Validators.required],
      endTime: [slot.end, Validators.required]
    }, { validators: this.timeRangeValidator });
    this.timeSlots.push(group);
  }

  timeToMinutes(time: string): number {
    const ampmMatch = time.match(/(AM|PM)$/i);
    if (ampmMatch) {
      const [base, period] = [time.slice(0, -2).trim(), ampmMatch[1].toUpperCase()];
      let [hour, minute] = base.split(':').map(Number);
      if (period === 'PM' && hour < 12) hour += 12;
      if (period === 'AM' && hour === 12) hour = 0;
      return hour * 60 + minute;
    }
    const [hour, minute] = time.split(':').map(Number);
    return hour * 60 + minute;
  }

  format(time: string): string {
    const [hour, minute] = time.split(':').map(Number);
    const period = hour >= 12 ? 'PM' : 'AM';
    const displayHour = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour;
    return this.timeFormat === '12h'
      ? `${displayHour}:${minute.toString().padStart(2, '0')} ${period}`
      : time;
  }

  getEndTimeOptions(startTime: string): string[] {
    if (!startTime) return this.timeOptions;
    const startMinutes = this.timeToMinutes(startTime);
    return this.timeOptions.filter(time => {
      const endMinutes = this.timeToMinutes(time);
      return endMinutes > startMinutes;
    });
  }

  loadExistingAvailability() {
    this.isLoading = true;
    this.http.get<AvailabilityResponse>('http://localhost:8181/api/v1/artist/availability').subscribe({
      next: (response) => {
        this.availabilityData = {};
        if (response.status === 'OK' && Array.isArray(response.data)) {
          response.data.forEach(availability => {
            if (availability.dayOfWeek && Array.isArray(availability.availabilityTimes)) {
              this.availabilityData[availability.dayOfWeek] = availability.availabilityTimes.map(slot => ({
                id: slot.id,
                startTime: slot.startTime,
                endTime: slot.endTime
              }));
            }
          });
        }
        this.loadSlotsForDay();
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error loading availability:', err);
        this.toastr.error('Failed to load availability');
        this.isLoading = false;
        this.loadSlotsForDay();
      }
    });
  }

  loadSlotsForDay() {
    const dayKey = this.selectedDay.key;
    this.savedSlots = this.availabilityData[dayKey] || [];
    this.timeSlots.clear();
    if (this.savedSlots.length > 0) {
      this.savedSlots.forEach(slot => {
        const group = this.fb.group({
          startTime: [this.formatTimeForInput(slot.startTime), Validators.required],
          endTime: [this.formatTimeForInput(slot.endTime), Validators.required]
        }, { validators: this.timeRangeValidator });
        this.timeSlots.push(group);
      });
    } else {
      this.addSlot();
    }
  }

  formatTimeForInput(time: string): string {
    return this.timeFormat === '12h' ? this.format(time) : time;
  }

  timeRangeValidator(group: FormGroup) {
    const start = group.get('startTime')?.value;
    const end = group.get('endTime')?.value;
    if (start && end) {
      const startMinutes = this.timeToMinutes(start);
      const endMinutes = this.timeToMinutes(end);
      if (startMinutes >= endMinutes) {
        return { invalidTimeRange: true };
      }
    }
    return null;
  }

  timeSlotsValidator(): ValidatorFn {
    return (control: AbstractControl): { [key: string]: any } | null => {
      const array = control as FormArray;
      const slots = array.controls.map(ctrl => ({
        start: this.timeToMinutes(ctrl.get('startTime')?.value || '00:00'),
        end: this.timeToMinutes(ctrl.get('endTime')?.value || '00:00')
      }));
      for (let i = 0; i < slots.length; i++) {
        for (let j = i + 1; j < slots.length; j++) {
          if (slots[i].start < slots[j].end && slots[j].start < slots[i].end) {
            return { overlappingSlots: true };
          }
        }
      }
      return null;
    };
  }

  saveAvailability() {
    if (this.calendarForm.invalid) {
      this.toastr.error('Please fix the errors in the form');
      this.calendarForm.markAllAsTouched();
      return;
    }

    this.isLoading = true;

    const payload: Availability = {
      dayOfWeek: this.selectedDay.key,
      availabilityTimes: this.timeSlots.controls.map(ctrl => ({
        startTime: this.normalizeTo24HourWithSeconds(ctrl.get('startTime')?.value),
        endTime: this.normalizeTo24HourWithSeconds(ctrl.get('endTime')?.value)
      }))
    };

    this.http.post('http://localhost:8181/api/v1/artist/availability', payload).subscribe({
      next: () => {
        this.toastr.success('Availability saved successfully!');
        this.availabilityData[this.selectedDay.key] = payload.availabilityTimes;
        this.savedSlots = payload.availabilityTimes;
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error saving availability:', err);
        this.toastr.error('Failed to save availability');
        this.isLoading = false;
      }
    });
  }

  normalizeTo24HourWithSeconds(time: string): string {
    const minutes = this.timeToMinutes(time);
    const hour = Math.floor(minutes / 60).toString().padStart(2, '0');
    const min = (minutes % 60).toString().padStart(2, '0');
    return `${hour}:${min}:00`;
  }

  getTotalHours(): string {
    let totalMinutes = 0;
    this.timeSlots.controls.forEach(ctrl => {
      const start = ctrl.get('startTime')?.value;
      const end = ctrl.get('endTime')?.value;
      if (start && end) {
        const startMinutes = this.timeToMinutes(start);
        const endMinutes = this.timeToMinutes(end);
        if (endMinutes > startMinutes) {
          totalMinutes += endMinutes - startMinutes;
        }
      }
    });
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return `${hours}h ${minutes}m`;
  }

  hasAvailabilityForDay(dayKey: string): boolean {
    return this.availabilityData[dayKey] && this.availabilityData[dayKey].length > 0;
  }

  onDayTabChange(index: number): void {
    this.selectedTabIndex = index;
    this.loadSlotsForDay();
  }

  getSlotDurationFromObject(slot: TimeSlot): string {
    const start = slot.startTime;
    const end = slot.endTime;

    if (typeof start === 'string' && typeof end === 'string') {
      const startMins = this.timeToMinutes(start);
      const endMins = this.timeToMinutes(end);
      const diff = endMins - startMins;
      const hours = Math.floor(diff / 60);
      const minutes = diff % 60;
      return `${hours}h ${minutes}m`;
    }
    return '';
  }
}