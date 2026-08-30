import { Component, OnInit } from '@angular/core';
import { ArtistService } from '../../artist.service';
import { visiblePages } from '../../../shared/pagination';

interface TransactionResponse {
  transactionId: number;
  artist: {
    name?: string;
    id?: number;
  };
  bookingId: number;
  amount: number;
  transactionType: string;
  transactionPurpose: string;
  /** PENDING, APPROVED or DECLINED — which is how an artist learns a payout went through. */
  status?: string;
  createdDate?: string;
}

interface PaginatedResponse {
  content: TransactionResponse[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

@Component({
  selector: 'app-coin-transaction',
  standalone: false,
  templateUrl: './coin-transaction.component.html',
})
export class CoinTransactionComponent implements OnInit {
  transactions: TransactionResponse[] = [];
  currentPage = 0;
  pageSize = 10;
  totalElements = 0;
  totalPages = 0;
  loading = false;
  error: string | null = null;

  selectedType = 'ALL';
  selectedPurpose = 'ALL';

  constructor(private artistService: ArtistService) {}

  ngOnInit() {
    this.loadTransactions();
  }

  loadTransactions() {
    this.loading = true;
    this.error = null;

    this.artistService
      .getAllArtistCoinTransactions(this.selectedType, this.selectedPurpose, this.currentPage, this.pageSize)
      .subscribe({
        next: (response: any) => {
          const data: PaginatedResponse = response.data;
          this.transactions = data?.content ?? [];
          this.totalElements = data?.totalElements ?? 0;
          this.totalPages = data?.totalPages ?? 0;
          this.currentPage = data?.number ?? 0;
          this.loading = false;
        },
        error: (error: any) => {
          this.error = 'Failed to load transactions. Please try again.';
          this.loading = false;
          console.error('Error loading transactions:', error);
        }
      });
  }

  onFilterChange() {
    this.currentPage = 0;
    this.loadTransactions();
  }

  resetFilters() {
    this.selectedType = 'ALL';
    this.selectedPurpose = 'ALL';
    this.currentPage = 0;
    this.loadTransactions();
  }

  goToPage(page: number) {
    if (page >= 0 && page < this.totalPages && page !== this.currentPage) {
      this.currentPage = page;
      this.loadTransactions();
    }
  }

  onPageSizeChange() {
    this.currentPage = 0;
    this.loadTransactions();
  }

  get startIndex(): number {
    return this.totalElements === 0 ? 0 : this.currentPage * this.pageSize + 1;
  }

  get endIndex(): number {
    return Math.min((this.currentPage + 1) * this.pageSize, this.totalElements);
  }

  statusClass(status?: string): string {
    switch (status) {
      case 'APPROVED': return 'omh-status-positive';
      case 'PENDING': return 'omh-status-pending';
      case 'DECLINED': return 'omh-status-critical';
      default: return 'omh-status-neutral';
    }
  }

  /**
   * The status in the artist's terms rather than the ledger's.
   *
   * "Approved" is what the record says; "Paid" is what the artist wants to know. A booking credit
   * is approved by definition and saying so on every row would be noise, so those read "Settled".
   */
  statusLabel(transaction: TransactionResponse): string {
    if (transaction.transactionPurpose !== 'WITHDRAWAL_REQUEST') {
      return transaction.status === 'APPROVED' ? 'Settled' : (transaction.status || '—');
    }
    switch (transaction.status) {
      case 'APPROVED': return 'Paid';
      case 'PENDING': return 'Awaiting payout';
      case 'DECLINED': return 'Declined · funds returned';
      default: return '—';
    }
  }

  formatPurpose(purpose: string): string {
    return (purpose || '').replace(/_/g, ' ').toLowerCase()
      .replace(/\b\w/g, l => l.toUpperCase());
  }

  getVisiblePages(): number[] {
    return visiblePages(this.currentPage, this.totalPages);
  }
}
