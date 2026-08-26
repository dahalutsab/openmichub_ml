import { Injectable } from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import { Observable } from 'rxjs';

export interface GenreResponse {
  timestamp: string;
  message: string;
  data: Genre[];
  status: string;
}

interface Category {
  id: number;
  name: string;
  description: string;
}

export interface Genre {
  id: number;
  name: string;
  description: string;
  slug: string;
  categories: Category[];
}

interface Artist {
  fullName: string;
  bio: string;
  profilePictureUrl: string;
  stageName: string;
  genre: Genre[];
}

interface ArtistResponse {
  timestamp: string;
  message: string;
  data: {
    content: Artist[];
    pageable: {
      pageNumber: number;
      pageSize: number;
      sort: {
        empty: boolean;
        unsorted: boolean;
        sorted: boolean;
      };
      offset: number;
      unpaged: boolean;
      paged: boolean;
    };
    last: boolean;
    totalPages: number;
    totalElements: number;
    first: boolean;
    size: number;
    number: number;
    sort: {
      empty: boolean;
      unsorted: boolean;
      sorted: boolean;
    };
    numberOfElements: number;
    empty: boolean;
  };
  status: string;
}

interface CountResponse {
  timestamp: string;
  message: string;
  data: {
    users: number;
    artists: number;
    bookings: number;
  };
  status: string;
}

@Injectable({
  providedIn: 'root'
})
export class LandingService {
  private apiUrl = 'http://localhost:8181/api/v1/public';

  constructor(private http: HttpClient) {}

  getArtists(page: number, size: number, searchTerm?: string, genreName?: string): Observable<ArtistResponse> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size);

    if (searchTerm) {
      params = params.set('searchTerm', searchTerm);
    }
    if (genreName) {
      params = params.set('genreName', genreName);
    }

    return this.http.get<ArtistResponse>(`${this.apiUrl}/artists`, { params });
  }

  // Add method to fetch genres
  getGenres(): Observable<GenreResponse> {
    return this.http.get<GenreResponse>(`${this.apiUrl}/genres`);
  }

  // Fetch count data
  getCounts(): Observable<CountResponse> {
    return this.http.get<CountResponse>(`${this.apiUrl}/count`);
  }
}
