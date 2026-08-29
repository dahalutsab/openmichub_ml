
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

  /**
   * Which social sign-in providers this backend can actually use.
   *
   * Asked rather than configured here. The credentials live on the backend, and a second switch
   * in the front end only creates ways for the two to disagree — a configured provider with no
   * button, or a button for a provider that was never set up.
   */
  socialProviders(): Observable<any> {
    return this.httpClient.get<any>(`${this.baseUrl}/auth/providers`);
  }

registerUser(formData: FormData): Observable<any> {
    return this.httpClient.post(`${this.baseUrl}/auth/register/user`, formData);
  }

  registerArtist(formData: FormData): Observable<any> {
    return this.httpClient.post(`${this.baseUrl}/auth/register/artist`, formData);
  }
}

 

