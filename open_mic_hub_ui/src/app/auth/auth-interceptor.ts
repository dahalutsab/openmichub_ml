import { Injectable } from '@angular/core';
import {
  HttpEvent,
  HttpInterceptor,
  HttpHandler,
  HttpRequest,
  HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { Router } from '@angular/router';
import { AdminService } from '../admin/admin.service';



@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  constructor(private service: AdminService, private router: Router) {}

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = localStorage.getItem('authToken');
    
    if (token) {
      request = this.addToken(request, token);
    }

    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        // An expired or rejected token means the session is over: clear it and send the user to
        // sign in. This used to route to the registration page, which is not where someone whose
        // session lapsed needs to end up.
        if (error.status === 401) {
          localStorage.removeItem('authToken');
          localStorage.removeItem('urole');
          this.router.navigate(['/auth/login'], {
            queryParams: { returnUrl: this.router.url }
          });
        }
        return throwError(() => error);
      })
    );
  }

  private addToken(request: HttpRequest<any>, token: string) {

    return request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
        
      }
    });
  }
}
