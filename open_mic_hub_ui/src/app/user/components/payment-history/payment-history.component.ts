import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environment/environment';

interface PaymentRow {
  paymentTime: string;
  receivedAmount: number;
  totalAmount: number;
  paymentMethod: string;
  paymentStatus: string;
  productCode: string;
  booking?: {
    id?: number;
    venue?: string;
    eventType?: string;
    eventDate?: string;
  };
}

/**
 * A booker's own payment history.
 *
 * This route is in the sidebar but the component was still the CLI stub
 * (`<p>payment-history works!</p>`). It reads the same paged endpoint the
 * artist's records screen uses; the API scopes it to the caller.
 */
@Component({
  selector: 'app-payment-history',
  standalone: false,
  templateUrl: './payment-history.component.html',
})
export class PaymentHistoryComponent implements OnInit {
  payments: PaymentRow[] = [];
  loading = false;
  error = '';

  page = 0;
  size = 10;
  totalPages = 0;
  totalElements = 0;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetch();
  }

  fetch(): void {
    this.loading = true;
    this.error = '';

    const params = new HttpParams().set('page', this.page).set('size', this.size);

    this.http
      .get<any>(`${environment.baseUrl}/payments/user`, { params })
      .subscribe({
        next: res => {
          this.payments = res?.data?.content ?? [];
          this.totalPages = res?.data?.totalPages ?? 0;
          this.totalElements = res?.data?.totalElements ?? 0;
          this.loading = false;
        },
        error: err => {
          console.error('Failed to load payment history', err);
          this.error = 'Could not load your payments.';
          this.loading = false;
        },
      });
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages || page === this.page) {
      return;
    }
    this.page = page;
    this.fetch();
  }

  get pageNumbers(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }

  get totalPaid(): number {
    return this.payments
      .filter(p => this.isSettled(p.paymentStatus))
      .reduce((sum, p) => sum + (p.receivedAmount ?? 0), 0);
  }

  isSettled(status: string): boolean {
    return ['COMPLETE', 'COMPLETED', 'SUCCESS'].includes((status || '').toUpperCase());
  }

  statusClass(status: string): string {
    switch ((status || '').toUpperCase()) {
      case 'COMPLETE':
      case 'COMPLETED':
      case 'SUCCESS':
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

  pretty(value: string | undefined): string {
    return (value || '')
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, c => c.toUpperCase());
  }
}
