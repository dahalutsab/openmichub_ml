import { Component, OnInit } from '@angular/core';
import { PaymentService, Payment } from './payment.service';

@Component({
  selector: 'app-payment-records',
  standalone: false,
  templateUrl: './payment-records.component.html',
})
export class PaymentRecordsComponent implements OnInit {
  payments: Payment[] = [];
  loading = false;
  error = '';
  page = 0;
  size = 10;
  totalPages = 0;
  totalElements = 0;

  // Filters
  status = '';
  method = '';

  paymentStatuses = ['PENDING', 'COMPLETED', 'COMPLETE', 'FAILED', 'CANCELLED', 'ABORTED'];
  paymentMethods = ['CASH', 'CARD', 'ONLINE', 'OTHER'];

  constructor(private paymentService: PaymentService) {}

  ngOnInit() {
    this.fetchPayments();
  }

  fetchPayments() {
    this.loading = true;
    this.error = '';
    this.paymentService.getPayments(this.page, this.size, this.status, this.method)
      .subscribe({
        next: res => {
          this.payments = res.data.content ?? [];
          this.totalPages = res.data.totalPages;
          this.totalElements = res.data.totalElements;
          this.loading = false;
        },
        error: () => {
          this.error = 'Failed to load payments';
          this.loading = false;
        }
      });
  }

  onFilterChange() {
    this.page = 0;
    this.fetchPayments();
  }

  clearFilters() {
    this.status = '';
    this.method = '';
    this.onFilterChange();
  }

  goToPage(page: number) {
    if (page < 0 || page >= this.totalPages || page === this.page) {
      return;
    }
    this.page = page;
    this.fetchPayments();
  }

  /**
   * Page buttons. Built here rather than with `[].constructor(totalPages)` in
   * the template, which allocated a fresh array on every change-detection pass.
   */
  get pageNumbers(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }

  statusClass(status: string | undefined): string {
    switch ((status ?? '').toUpperCase()) {
      case 'COMPLETE':
      case 'COMPLETED':
        return 'omh-status-positive';
      case 'PENDING':
        return 'omh-status-pending';
      case 'FAILED':
      case 'CANCELLED':
      case 'ABORTED':
        return 'omh-status-critical';
      default:
        return 'omh-status-neutral';
    }
  }

  /**
   * `paymentTime` arrives as a bare LocalTime ("18:30:00"), so it cannot go
   * through the `date` pipe — that throws InvalidPipeArgument.
   */
  formatTime(time: string | null | undefined): string {
    if (!time) return '';
    const [hourStr, minute] = time.split(':');
    const hour = parseInt(hourStr, 10);
    if (Number.isNaN(hour)) return time;
    const suffix = hour >= 12 ? 'PM' : 'AM';
    const display = hour % 12 === 0 ? 12 : hour % 12;
    return `${display}:${minute ?? '00'} ${suffix}`;
  }
}
