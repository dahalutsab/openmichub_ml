
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { HttpClient } from '@angular/common/http';
import { environment } from '../environment/environment';



@Injectable({
  providedIn: 'root',
})


export class AuthService {
  private baseUrl: string = environment.baseUrl;

  constructor(private httpClient: HttpClient) { }

  //auth

  login(loginDetail: any): Observable<any> {
    return this.httpClient.post<any>(`${this.baseUrl}/auth/login`, loginDetail);
  }

registerUser(formData: FormData): Observable<any> {
    return this.httpClient.post(`${this.baseUrl}/auth/register/user`, formData);
  }

  registerArtist(formData: FormData): Observable<any> {
    return this.httpClient.post(`${this.baseUrl}/auth/register/artist`, formData);
  }
}

 

