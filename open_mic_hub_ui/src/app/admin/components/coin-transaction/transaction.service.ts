import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {environment} from '../../../environment/environment';

// Interfaces
export interface ArtistResponse {
  artistId: number;
  artistStageName: string;
  email: string;
  virtualCoinBalance: number;
}

export interface TransactionResponse {
  transactionId: number;
  artist?: ArtistResponse;
  bookingId: number;
  amount: number;
  transactionType: string;
  transactionPurpose: string;
  /** PENDING, APPROVED or DECLINED. Absent on responses from an older API. */
  status?: string;
  createdDate?: string;
}

export interface Sort {
  empty: boolean;
  sorted: boolean;
  unsorted: boolean;
}

export interface Pageable {
  pageNumber: number;
  pageSize: number;
  sort: Sort;
  offset: number;
  paged: boolean;
  unpaged: boolean;
}

export interface TransactionPageData {
  content: TransactionResponse[];
  pageable: Pageable;
  last: boolean;
  totalElements: number;
  totalPages: number;
  first: boolean;
  size: number;
  number: number;
  sort: Sort;
  numberOfElements: number;
  empty: boolean;
}

export interface TransactionApiResponse {
  timestamp: string;
  message: string;
  data: TransactionPageData;
  status: string;
}

@Injectable({
  providedIn: 'root'
})
export class TransactionService {
  private apiUrl = `${environment.baseUrl}/transactions`;

  constructor(private http: HttpClient) { }

  /**
   * Get all transactions with pagination
   * @param page Page number (0-based)
   * @param size Page size
   * @param sort Sort parameter (optional)
   */
  getAllTransactions(page: number = 0, size: number = 20, sort?: string): Observable<TransactionApiResponse> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (sort) {
      params = params.set('sort', sort);
    }

    return this.http.get<TransactionApiResponse>(this.apiUrl, { params });
  }

  /**
   * Get transaction by ID
   * @param transactionId Transaction ID
   */
  getTransactionById(transactionId: number): Observable<TransactionApiResponse> {
    return this.http.get<TransactionApiResponse>(`${this.apiUrl}/${transactionId}`);
  }

  /**
   * Get transactions by artist ID
   * @param artistId Artist ID
   * @param page Page number
   * @param size Page size
   */
  getTransactionsByArtist(artistId: number, page: number = 0, size: number = 20): Observable<TransactionApiResponse> {
    const params = new HttpParams()
      .set('artistId', artistId.toString())
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<TransactionApiResponse>(`${this.apiUrl}/artist`, { params });
  }

  /**
   * Get transactions by booking ID
   * @param bookingId Booking ID
   * @param page Page number
   * @param size Page size
   */
  getTransactionsByBooking(bookingId: number, page: number = 0, size: number = 20): Observable<TransactionApiResponse> {
    const params = new HttpParams()
      .set('bookingId', bookingId.toString())
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<TransactionApiResponse>(`${this.apiUrl}/booking`, { params });
  }

  /**
   * Get transactions by type
   * @param transactionType Transaction type
   * @param page Page number
   * @param size Page size
   */
  getTransactionsByType(transactionType: string, page: number = 0, size: number = 20): Observable<TransactionApiResponse> {
    const params = new HttpParams()
      .set('type', transactionType)
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<TransactionApiResponse>(`${this.apiUrl}/type`, { params });
  }

  /**
   * Get transactions by purpose
   * @param transactionPurpose Transaction purpose
   * @param page Page number
   * @param size Page size
   */
  getTransactionsByPurpose(transactionPurpose: string, page: number = 0, size: number = 20): Observable<TransactionApiResponse> {
    const params = new HttpParams()
      .set('purpose', transactionPurpose)
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<TransactionApiResponse>(`${this.apiUrl}/purpose`, { params });
  }

  /**
   * Withdrawal requests awaiting a decision.
   *
   * <p>Its own endpoint rather than the ledger filtered in the browser. Withdrawals are a sliver of
   * a ledger that runs to thousands of rows, and the page that used to filter client-side loaded
   * only the first thousand of them — which never contained a single withdrawal, so the queue read
   * as empty no matter how many artists were waiting.
   */
  getWithdrawalRequests(page: number = 0, size: number = 20,
                        status: string = 'PENDING'): Observable<TransactionApiResponse> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('status', status);

    return this.http.get<TransactionApiResponse>(`${this.apiUrl}/withdrawals`, { params });
  }

  /** Refuses a request and returns the held funds to the artist. */
  declineWithdrawal(transactionId: number): Observable<unknown> {
    return this.http.post(`${this.apiUrl}/withdrawals/${transactionId}/decline`, {});
  }

  processWithdrawal(transactionId: number): Observable<string> {
    return this.http.post(
      environment.baseUrl + `/artist/withdraw`,
      { transactionId },
      { responseType: 'text' }
    );
  }

}
