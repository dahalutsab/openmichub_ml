import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subject, takeUntil, debounceTime, distinctUntilChanged } from 'rxjs';
import {
  TransactionService,
  TransactionResponse,
  TransactionApiResponse,
} from './transaction.service';
import { visiblePages } from '../../../shared/pagination';

@Component({
  selector: 'app-transactions-list',
  standalone: false,
  templateUrl: './transactions-list.component.html',
})
export class TransactionsListComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private artistSearchSubject = new Subject<string>();

  // Data properties
  transactions: TransactionResponse[] = [];
  filteredTransactions: TransactionResponse[] = [];
  paginatedTransactions: TransactionResponse[] = [];

  // Filter properties
  selectedType: string = '';
  selectedPurpose: string = '';
  artistSearchTerm: string = '';
  selectedAmountRange: string = '';

  // Pagination properties
  currentPage: number = 0;
  pageSize: number = 20;
  totalPages: number = 0;
  totalTransactions: number = 0;

  // State properties
  loading: boolean = false;
  error: string = '';

  /** The row awaiting withdrawal confirmation, or null when the modal is shut. */
  pendingWithdrawal: TransactionResponse | null = null;
  processing = false;

  constructor(private transactionService: TransactionService) {
    // Setup artist search with debounce
    this.artistSearchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.onFilterChange();
    });
  }

  ngOnInit() {
    this.loadTransactions();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadTransactions() {
    this.loading = true;
    this.error = '';

    this.transactionService.getAllTransactions(0, 1000) // Load more for client-side filtering
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response: TransactionApiResponse) => {
          this.transactions = response.data.content ?? [];
          this.totalTransactions = response.data.totalElements;
          this.applyFilters();
          this.loading = false;
        },
        error: (error) => {
          this.error = 'Failed to load transactions. Please try again.';
          this.loading = false;
          console.error('Error loading transactions:', error);
        }
      });
  }

  refreshTransactions() {
    this.loadTransactions();
  }

  onFilterChange() {
    this.currentPage = 0;
    this.applyFilters();
  }

  onArtistSearch(event: any) {
    this.artistSearchTerm = event.target.value;
    this.artistSearchSubject.next(this.artistSearchTerm);
  }

  onPageSizeChange() {
    this.currentPage = 0;
    this.updatePagination();
  }

  applyFilters() {
    let filtered = [...this.transactions];

    // Filter by transaction type
    if (this.selectedType) {
      filtered = filtered.filter(t => t.transactionType === this.selectedType);
    }

    // Filter by purpose. The "all purposes" option used to carry the value
    // 'ALL', which is truthy and matched no row's purpose — choosing it emptied
    // the table. It is now an empty string like every other "all" option here.
    if (this.selectedPurpose) {
      filtered = filtered.filter(t => t.transactionPurpose === this.selectedPurpose);
    }

    // Filter by artist name
    if (this.artistSearchTerm) {
      const searchTerm = this.artistSearchTerm.toLowerCase();
      filtered = filtered.filter(t =>
        t.artist?.artistStageName?.toLowerCase().includes(searchTerm) ||
        t.artist?.email?.toLowerCase().includes(searchTerm)
      );
    }

    // Filter by amount range
    if (this.selectedAmountRange) {
      filtered = filtered.filter(t => this.isInAmountRange(t.amount, this.selectedAmountRange));
    }

    this.filteredTransactions = filtered;
    this.updatePagination();
  }

  isInAmountRange(amount: number, range: string): boolean {
    switch (range) {
      case '0-100': return amount >= 0 && amount <= 100;
      case '100-500': return amount > 100 && amount <= 500;
      case '500-1000': return amount > 500 && amount <= 1000;
      case '1000+': return amount > 1000;
      default: return true;
    }
  }

  updatePagination() {
    this.totalPages = Math.ceil(this.filteredTransactions.length / this.pageSize);
    const startIndex = this.currentPage * this.pageSize;
    const endIndex = startIndex + this.pageSize;
    this.paginatedTransactions = this.filteredTransactions.slice(startIndex, endIndex);
  }

  goToPage(page: number) {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.updatePagination();
    }
  }

  getVisiblePages(): number[] {
    return visiblePages(this.currentPage, this.totalPages);
  }

  get startIndex(): number {
    return this.filteredTransactions.length === 0 ? 0 : this.currentPage * this.pageSize + 1;
  }

  get endIndex(): number {
    return Math.min((this.currentPage + 1) * this.pageSize, this.filteredTransactions.length);
  }

  hasActiveFilters(): boolean {
    return !!(this.selectedType || this.selectedPurpose || this.artistSearchTerm || this.selectedAmountRange);
  }

  clearFilters() {
    this.selectedType = '';
    this.selectedPurpose = '';
    this.artistSearchTerm = '';
    this.selectedAmountRange = '';
    this.onFilterChange();
  }

  isPositiveTransaction(type: string): boolean {
    return type === 'CREDIT' || type === 'REFUND';
  }

  typeClass(type: string): string {
    switch (type) {
      case 'CREDIT': return 'omh-status-positive';
      case 'DEBIT': return 'omh-status-critical';
      case 'REFUND': return 'omh-status-pending';
      case 'PAYMENT': return 'omh-status-brand';
      default: return 'omh-status-neutral';
    }
  }

  formatPurpose(purpose: string): string {
    return (purpose || '').replace(/_/g, ' ').toLowerCase()
      .replace(/\b\w/g, l => l.toUpperCase());
  }

  trackByTransactionId(index: number, transaction: TransactionResponse): number {
    return transaction.transactionId;
  }

  askWithdrawal(transaction: TransactionResponse) {
    this.pendingWithdrawal = transaction;
  }

  confirmWithdrawal() {
    const transaction = this.pendingWithdrawal;
    if (!transaction || this.processing) {
      return;
    }

    this.processing = true;
    this.error = '';

    this.transactionService.processWithdrawal(transaction.transactionId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (url: string) => {
          this.processing = false;
          this.pendingWithdrawal = null;
          // The gateway hands back a redirect target.
          setTimeout(() => (window.location.href = url), 0);
        },
        error: (error) => {
          this.processing = false;
          this.pendingWithdrawal = null;
          this.error = 'Failed to process withdrawal. Please try again.';
          console.error('Error processing withdrawal:', error);
        },
      });
  }
}
