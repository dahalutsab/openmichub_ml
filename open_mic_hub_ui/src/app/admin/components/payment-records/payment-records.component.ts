import { Component, OnInit } from '@angular/core';
import { PaymentService, Payment } from './payment.service';

@Component({
  selector: 'app-payment-records',
  standalone: false,
  templateUrl: './payment-records.component.html',
  styleUrl: './payment-records.component.scss'
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
          this.payments = res.data.content;
          this.totalPages = res.data.totalPages;
          this.totalElements = res.data.totalElements;
          this.loading = false;
        },
        error: err => {
          this.error = 'Failed to load payments';
          this.loading = false;
        }
      });
  }

  onFilterChange() {
    this.page = 0;
    this.fetchPayments();
  }

  goToPage(page: number) {
    this.page = page;
    this.fetchPayments();
  }
}
