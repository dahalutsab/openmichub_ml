import { Component, OnInit } from '@angular/core';
import { ArtistService } from '../../artist.service';

interface TransactionResponse {
  transactionId: number;
  artist: {
    // Add artist properties as needed
    name?: string;
    id?: number;
  };
  bookingId: number;
  amount: number;
  transactionType: string;
  transactionPurpose: string;
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
  styleUrl: './coin-transaction.component.scss'
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
  
  Math = Math;

  constructor(private artistService: ArtistService) {} // Inject your service here

  ngOnInit() {
    this.loadTransactions();
  }

  loadTransactions() {
    this.loading = true;
    this.error = null;
    
    this.artistService.getAllArtistCoinTransactions(this.selectedType, this.selectedPurpose)
      .subscribe({
        next: (response: any) => {
          const data: PaginatedResponse = response.data;
          this.transactions = data.content;
          this.totalElements = data.totalElements;
          this.totalPages = data.totalPages;
          this.currentPage = data.number;
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

  getVisiblePages(): number[] {
    const visiblePages: number[] = [];
    const maxVisible = 5;
    
    let start = Math.max(0, this.currentPage - Math.floor(maxVisible / 2));
    let end = Math.min(this.totalPages, start + maxVisible);
    
    if (end - start < maxVisible) {
      start = Math.max(0, end - maxVisible);
    }
    
    for (let i = start; i < end; i++) {
      visiblePages.push(i);
    }
    
    return visiblePages;
  }
}