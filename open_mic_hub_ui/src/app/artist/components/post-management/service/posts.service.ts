import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PostResponse, Post, PostListResponse } from '../model/post.model';

@Injectable({
  providedIn: 'root'
})
export class PostsService {
  private apiUrl = 'http://localhost:8181/api/v1/posts';

  constructor(private http: HttpClient) {}

  getPosts(page: number = 0, size: number = 10): Observable<PostListResponse> {
    return this.http.get<PostListResponse>(`${this.apiUrl}?page=${page}&size=${size}`);
  }

  getPostById(id: number): Observable<PostResponse> {
    return this.http.get<PostResponse>(`${this.apiUrl}/${id}`);
  }

  createPost(post: Post, images: File[]): Observable<PostResponse> {
    const formData = new FormData();
    formData.append('title', post.title);
    formData.append('content', post.content);
    
    images.forEach(image => {
      formData.append('images', image);
    });

    return this.http.post<PostResponse>(this.apiUrl, formData);
  }

  updatePost(id: number, post: Post, images: File[]): Observable<PostResponse> {
    const formData = new FormData();
    formData.append('title', post.title);
    formData.append('content', post.content);
    
    images.forEach(image => {
      formData.append('images', image);
    });

    return this.http.put<PostResponse>(`${this.apiUrl}/${id}`, formData);
  }

  deletePost(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${id}`);
  }

  likePost(id: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/toggle-like/${id}`, {});
  }
}