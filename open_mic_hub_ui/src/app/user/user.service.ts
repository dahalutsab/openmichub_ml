import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private apiUrl = 'http://localhost:8181/api/v1';

  constructor(private http: HttpClient) {}

  getLoginUser(): Observable<any> {
    return this.http.get(`${this.apiUrl}/user`);
  }
  getUserById(userId: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/user/${userId}`);
  }

  getAllArtists(page: number = 0, size: number = 12): Observable<any> {
    return this.http.get(`${this.apiUrl}/public/artists?page=${page}&size=${size}`);
  }

getArtistAvailability(stageName: string) {
  return this.http.get<any>(`${this.apiUrl}/artist/calendar/${stageName}`);
}

getAllUserBookings(): Observable<any> {
  return this.http.get(`${this.apiUrl}/artist/getAllBooking/user`);
}





bookArtist(payload: any): Observable<string> {
  return this.http.post(this.apiUrl + '/artist/booking', payload, { responseType: 'text' });
}

confirmPaymentViaGet(params: any): Observable<any> {
  const queryString = new URLSearchParams(params).toString();
  return this.http.get(`${this.apiUrl}/artist/callback?${queryString}`);
}


 getAllBookings(page = 0, size = 90): Observable<any> {
    return this.http.get(`${this.apiUrl}/artist/getAllBookings/users?page=${page}&size=${size}`);
  }


  // user.service.ts
confirmPaymentViaPost(params: any) {
  const httpParams = new HttpParams()
    .set('pidx', params['pidx'])
    .set('status', params['status'])
    .set('amount', params['amount'])
    .set('totalAmount', params['total_amount']);

  return this.http.post(`${this.apiUrl}/payments/callback`, null, {
    params: httpParams,
    responseType: 'text'
  });
}


}
