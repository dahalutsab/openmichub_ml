import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { environment } from '../environment/environment';
import { Observable } from 'rxjs';


@Injectable({
  providedIn: 'root'
})
export class ArtistService {
  private baseUrl = environment.baseUrl;

  constructor(private http: HttpClient) {}

  getAvailability(day: string) {
    return this.http.get<any>(`${this.baseUrl}/artist/calendar/${day}`);
  }

  getVirtualCoin() {
    return this.http.get<any>(`${this.baseUrl}/virtual-coins/get-logged-in-artist`);
  }


 getAllBookings(page = 0, size = 22): Observable<any> {
    return this.http.get(`${this.baseUrl}/artist/getBookings?page=${page}&size=${size}`);
  }

approvedBookings(bookingId: number): Observable<any> {
  return this.http.put(`${this.baseUrl}/artist/approve?bookingId=${bookingId}`, null);
}

rejectedBookings(bookingId: number): Observable<any> {
  return this.http.put(`${this.baseUrl}/artist/decline?bookingId=${bookingId}`, null);
}



  saveAvailability(payload: any) {
    return this.http.post(`${this.baseUrl}/artist/availability`, payload);
  }

  getArtist() {
    return this.http.get<any>(`${this.baseUrl}/user`);
  }

  // Page and size were pinned to 0/10 here, so the paging controls on the
  // transactions screen moved a page number that never reached the server.
  getAllArtistCoinTransactions(type: string, purpose: string, page = 0, size = 10) {
    return this.http.get<any>(
      `${this.baseUrl}/transactions/artist?page=${page}&size=${size}` +
      `&transactionType=${type}&transactionPurpose=${purpose}`
    );
  }

  getAllArtistPaymentRecords(page = 0, size = 10) {
    return this.http.get<any>(`${this.baseUrl}/payments/user?page=${page}&size=${size}`);
  }
}
