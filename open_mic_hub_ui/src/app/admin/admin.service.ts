import { Injectable } from '@angular/core';
import { environment } from '../environment/environment';
import {HttpClient, HttpParams} from '@angular/common/http';
import { Role, ADMIN_ROLES, BOOKER_ROLES } from '../shared/role';
import {Observable} from 'rxjs';
import {UserResponse} from './components/users-list/users-list.component';

export interface NavigationItem {
  id: string;
  title: string;
  icon: string;
  route?: string;
  children?: NavigationItem[];
  expanded?: boolean;
  role?: string[];
}

@Injectable({
  providedIn: 'root'
})
export class AdminService {

  readonly apiUrl = environment.baseUrl;
  readonly baseUrl = environment.baseUrl + '/auth';

  constructor(private http: HttpClient) { }

  private navigationItems: NavigationItem[] = [

    // ---------- User Role ----------
    {
      id: 'Artist Feeds',
      title: 'Artist Feeds',
      icon: 'bi bi-house',
      route: '/user/feeds',
      role: BOOKER_ROLES
    },
    {
      id: 'browse-artists',
      title: 'Browse Artists',
      icon: 'bi bi-music-note-list',
      route: '/user/artists',
      role: BOOKER_ROLES
    },
    // {
    //   id: 'my-bookings',
    //   title: 'My Bookings',
    //   icon: 'bi bi-journal-bookmark',
    //   route: '/user/bookings',
    //   role: BOOKER_ROLES
    // },
     {
      id: 'bookings-details',
      title: 'Booking Details',
      icon: 'bi bi-journal-bookmark',
      route: '/user/book-details',
      role: BOOKER_ROLES
    },

    {
      id: 'payment-history',
      title: 'Payment History',
      icon: 'bi bi-clock-history',
      route: '/user/payment-history',
      role: BOOKER_ROLES
    },

    // ---------- Admin Role ----------
    {
      id: 'user-management',
      title: 'User Management',
      icon: 'bi bi-people',
      route: '/admin/users',
      role: ADMIN_ROLES
    },
    {
      id: 'coin-transactions',
      title: 'All Coin Transactions',
      icon: 'bi bi-arrow-left-right',
      route: '/admin/transactions',
      role: ADMIN_ROLES
    },
    {
      id: 'admin-payments',
      title: 'Payment Records',
      icon: 'bi bi-journal-text',
      route: '/admin/payments',
      role: ADMIN_ROLES
    },

    // ---------- Artist Role ----------
    {
      id: 'artist-calender',
      title: 'Calendar',
      icon: 'bi bi-person-lines-fill',
      route: '/artist/calender',
      role: [Role.ARTIST]
    },
    {
      id: 'manage-posts',
      title: 'Manage Posts',
      icon: 'bi bi-images',
      route: '/artist/posts',
      role: [Role.ARTIST]
    },
    {
      id: 'artist-bookings',
      title: 'My Bookings',
      icon: 'bi bi-calendar-check',
      route: '/artist/bookings',
      role: [Role.ARTIST]
    },
    {
      id: 'artist-wallet',
      title: 'Wallet',
      icon: 'bi bi-wallet2',
      route: '/artist/wallet',
      role: [Role.ARTIST]
    },
    {
      id: 'artist-profile',
      title: 'Profile',
      icon: 'bi bi-person-circle',
      route: '/artist/profile',
      role: [Role.ARTIST]
    },
    {
      id: 'artist-transactions',
      title: 'Coin Transactions',
      icon: 'bi bi-currency-exchange',
      route: '/artist/coin-transactions',
      role: [Role.ARTIST]
    },
    {
      id: 'artist-payments',
      title: 'Payment Records',
      icon: 'bi bi-receipt-cutoff',
      route: '/artist/payment-records',
      role: [Role.ARTIST]
    }
  ];

  getNavigationItems(userRoles: string[]): NavigationItem[] {
    return this.navigationItems
      .filter(item => item.role?.some(r => userRoles.includes(r)) ?? false)
      .map(item => {
        if (item.children) {
          item.children = item.children.filter(child =>
            child.role?.some(r => userRoles.includes(r)) ?? false
          );
        }
        return item;
      });
  }

  getAllUsers(userRole?: string, page: number = 0, size: number = 6): Observable<UserResponse> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size);
    if (userRole) {
      params = params.set('userRole', userRole);
    }
    return this.http.get<UserResponse>(`${this.apiUrl}/user/all`, { params });
  }

  confirmPaymentViaPost(params: any) {
    const httpParams = new HttpParams()
      .set('pidx', params['pidx'])
      .set('status', params['status'])
      .set('amount', params['amount'])
      .set('totalAmount', params['total_amount']);

    return this.http.post(`${this.apiUrl}/artist/withdraw/callback`, null, {
      params: httpParams,
      responseType: 'text'
    });
  }
}
