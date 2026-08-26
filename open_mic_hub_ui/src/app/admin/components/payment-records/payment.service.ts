import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {environment} from '../../../environment/environment';

export interface Payment {
  paymentTime: string;
  receivedAmount: number;
  paymentMethod: string;
  paymentStatus: string;
  productCode: string;
  booking: any;
}

export interface PaymentApiResponse {
  timestamp: string;
  message: string;
  data: {
    content: Payment[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
  };
  status: string;
}

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private apiUrl = environment.baseUrl

  constructor(private http: HttpClient) {}

  getPayments(page = 0, size = 10, status?: string, method?: string): Observable<PaymentApiResponse> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size);
    if (status) params = params.set('status', status);
    if (method) params = params.set('method', method);
    return this.http.get<PaymentApiResponse>(this.apiUrl+`/payments`, { params });
  }
}
