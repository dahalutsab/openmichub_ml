import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PostsResponse, LikeToggleResponse } from '../models/post.model';

@Injectable({
  providedIn: 'root'
})
export class ArtistFeedService {
  private baseUrl = 'http://localhost:8181/api/v1';

  constructor(private http: HttpClient) {}

  getPosts(page: number = 0, size: number = 10): Observable<PostsResponse> {
    return this.http.get<PostsResponse>(`${this.baseUrl}/posts?page=${page}&size=${size}`);
  }

  toggleLike(postId: number): Observable<LikeToggleResponse> {
    return this.http.put<LikeToggleResponse>(`${this.baseUrl}/posts/toggle-like/${postId}`, {});
  }
}
