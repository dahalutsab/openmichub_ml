import { Component, OnInit } from '@angular/core';
import { ArtistService } from '../../artist.service';

interface PaymentResponse {
  paymentTime: string;
  receivedAmount: number;
  paymentMethod: string;
  paymentStatus: string;
  productCode: string;
  booking: {
    // Add booking properties as needed
    id?: number;
    eventName?: string;
    venue?: string;
  };
}

interface PaginatedResponse {
  content: PaymentResponse[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

@Component({
  selector: 'app-payment-record',
  standalone: false,
  templateUrl: './payment-record.component.html',
  styleUrl: './payment-record.component.scss'
})
export class PaymentRecordComponent implements OnInit {
  payments: PaymentResponse[] = [];
  currentPage = 0;
  pageSize = 10;
  totalElements = 0;
  totalPages = 0;
  loading = false;
  error: string | null = null;
  
  selectedStatus = 'ALL';
  selectedMethod = 'ALL';
  
  Math = Math;

  constructor(private artistService: ArtistService) {} // Inject your service here

  ngOnInit() {
    this.loadPayments();
  }

  loadPayments() {
    this.loading = true;
    this.error = null;
    
    this.artistService.getAllArtistPaymentRecords()
      .subscribe({
        next: (response: any) => {
          const data: PaginatedResponse = response.data;
          this.payments = data.content;
          this.totalElements = data.totalElements;
          this.totalPages = data.totalPages;
          this.currentPage = data.number;
          this.loading = false;
        },
        error: (error: any) => {
          this.error = 'Failed to load payment records. Please try again.';
          this.loading = false;
          console.error('Error loading payment records:', error);
        }
      });
  }

  onFilterChange() {
    this.currentPage = 0;
    this.loadPayments();
  }

  resetFilters() {
    this.selectedStatus = 'ALL';
    this.selectedMethod = 'ALL';
    this.currentPage = 0;
    this.loadPayments();
  }

  goToPage(page: number) {
    if (page >= 0 && page < this.totalPages && page !== this.currentPage) {
      this.currentPage = page;
      this.loadPayments();
    }
  }

  onPageSizeChange() {
    this.currentPage = 0;
    this.loadPayments();
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

  formatTime(timeString: string): string {
    if (!timeString) return 'N/A';
    
    try {
      // Assuming the time comes in HH:mm:ss format
      const [hours, minutes] = timeString.split(':');
      const hour = parseInt(hours, 10);
      const minute = parseInt(minutes, 10);
      
      const ampm = hour >= 12 ? 'PM' : 'AM';
      const displayHour = hour % 12 || 12;
      
      return `${displayHour}:${minute.toString().padStart(2, '0')} ${ampm}`;
    } catch (error) {
      return timeString;
    }
  }

  formatPaymentMethod(method: string): string {
    if (!method) return 'N/A';
    
    return method.replace(/_/g, ' ')
                 .toLowerCase()
                 .replace(/\b\w/g, l => l.toUpperCase());
  }
}