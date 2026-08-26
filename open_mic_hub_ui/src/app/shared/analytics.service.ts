import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../environment/environment';
import { HeatCell, Metric, SeriesPoint, Slice } from './charts';

/** Mirrors AnalyticsDtos.BookingRow on the service. */
export interface BookingRow {
  id: number;
  eventDate: string;
  startTime: string | null;
  endTime: string | null;
  venue: string | null;
  eventType: string | null;
  status: string | null;
  totalAmount: number;
  artistStageName: string | null;
  bookerName: string | null;
}

export interface TopArtist {
  artistId: number | null;
  stageName: string | null;
  fullName: string | null;
  profileImage: string | null;
  bookings: number;
  earnings: number;
  rating: number | null;
}

export interface AdminOverview {
  windowDays: number;
  revenue: Metric;
  bookings: Metric;
  newUsers: Metric;
  platformFees: Metric;
  pendingPayouts: number;
  totalUsers: number;
  totalArtists: number;
  totalBookers: number;
  revenueSeries: SeriesPoint[];
  bookingsSeries: SeriesPoint[];
  bookingsByStatus: Slice[];
  paymentsByMethod: Slice[];
  transactionsByType: Slice[];
  activityHeatmap: HeatCell[];
  topArtists: TopArtist[];
  recentBookings: BookingRow[];
}

export interface ArtistOverview {
  windowDays: number;
  stageName: string | null;
  fullName: string | null;
  profileImage: string | null;
  rating: number | null;
  balance: number;
  heldFunds: number;
  earnings: Metric;
  bookings: Metric;
  lifetimeEarnings: number;
  pendingRequests: number;
  acceptanceRate: number;
  earningsSeries: SeriesPoint[];
  bookingsSeries: SeriesPoint[];
  bookingsByStatus: Slice[];
  earningsByPurpose: Slice[];
  demandHeatmap: HeatCell[];
  upcomingBookings: BookingRow[];
}

export interface BookerOverview {
  windowDays: number;
  fullName: string | null;
  spend: Metric;
  bookings: Metric;
  lifetimeSpend: number;
  upcomingCount: number;
  pendingCount: number;
  spendSeries: SeriesPoint[];
  bookingsSeries: SeriesPoint[];
  bookingsByStatus: Slice[];
  spendByEventType: Slice[];
  paymentsByMethod: Slice[];
  favouriteArtists: TopArtist[];
  upcomingBookings: BookingRow[];
  recentBookings: BookingRow[];
}

/** Windows offered by the range picker on every board. */
export const RANGES = [
  { days: 7, label: '7D' },
  { days: 30, label: '30D' },
  { days: 90, label: '90D' },
  { days: 365, label: '1Y' },
] as const;

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly baseUrl = `${environment.baseUrl}/analytics`;

  constructor(private http: HttpClient) {}

  adminOverview(days: number): Observable<AdminOverview> {
    return this.get<AdminOverview>('/admin/overview', days);
  }

  artistOverview(days: number): Observable<ArtistOverview> {
    return this.get<ArtistOverview>('/artist/overview', days);
  }

  bookerOverview(days: number): Observable<BookerOverview> {
    return this.get<BookerOverview>('/booker/overview', days);
  }

  /** Unwraps the GlobalApiResponse envelope every endpoint here is wrapped in. */
  private get<T>(path: string, days: number): Observable<T> {
    return this.http
      .get<{ data: T }>(`${this.baseUrl}${path}`, {
        params: new HttpParams().set('days', days),
      })
      .pipe(map(response => response.data));
  }
}
