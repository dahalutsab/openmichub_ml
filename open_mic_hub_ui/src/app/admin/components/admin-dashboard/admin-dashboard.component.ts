import { Component, OnInit } from '@angular/core';
import { AdminService } from '../../admin.service';
import { TransactionService, TransactionResponse } from '../coin-transaction/transaction.service';
import { PaymentService, Payment } from '../payment-records/payment.service';

/**
 * Admin overview.
 *
 * This route was a bare `<h1>Admin Dashboard</h1>` — the CLI stub it was
 * generated as. Everything here is assembled from endpoints the app already
 * calls elsewhere; no new API surface is assumed.
 */
@Component({
  selector: 'app-admin-dashboard',
  standalone: false,
  templateUrl: './admin-dashboard.component.html',
})
export class AdminDashboardComponent implements OnInit {
  loading = true;

  totalUsers = 0;
  totalArtists = 0;
  totalTransactions = 0;
  totalPayments = 0;

  recentTransactions: TransactionResponse[] = [];
  recentPayments: Payment[] = [];

  constructor(
    private adminService: AdminService,
    private transactionService: TransactionService,
    private paymentService: PaymentService
  ) {}

  ngOnInit(): void {
    // A page size of 1 is enough: only the totalElements count is read from
    // these two, and pulling every user just to count them is wasteful.
    this.adminService.getAllUsers(undefined, 0, 1).subscribe({
      next: res => (this.totalUsers = res.data.totalElements),
      error: () => {},
    });

    this.adminService.getAllUsers('ARTIST', 0, 1).subscribe({
      next: res => (this.totalArtists = res.data.totalElements),
      error: () => {},
    });

    this.transactionService.getAllTransactions(0, 6).subscribe({
      next: res => {
        this.totalTransactions = res.data.totalElements;
        this.recentTransactions = res.data.content ?? [];
        this.loading = false;
      },
      error: () => (this.loading = false),
    });

    this.paymentService.getPayments(0, 6).subscribe({
      next: res => {
        this.totalPayments = res.data.totalElements;
        this.recentPayments = res.data.content ?? [];
      },
      error: () => {},
    });
  }

  statusClass(status: string | undefined): string {
    switch ((status ?? '').toUpperCase()) {
      case 'COMPLETE':
      case 'COMPLETED':
      case 'SUCCESS':
        return 'omh-status-positive';
      case 'PENDING':
      case 'INITIATED':
        return 'omh-status-pending';
      case 'FAILED':
      case 'REFUNDED':
      case 'CANCELED':
      case 'CANCELLED':
        return 'omh-status-critical';
      default:
        return 'omh-status-neutral';
    }
  }
}
