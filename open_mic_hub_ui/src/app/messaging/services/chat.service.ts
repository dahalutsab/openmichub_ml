import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {environment} from '../../environment/environment';

interface User {
  fullName: string;
  email: string;
  profile: string;
  lastMessage: string;
  lastMessageTime: string | null;
}

interface Message {
  id: string;
  senderId: string;
  receiverId: string;
  content: string;
  timestamp: string;
  isRead: boolean;
}

interface ApiResponse {
  timestamp: string;
  message: string;
  data: {
    content: User[];
    pageable: {
      pageNumber: number;
      pageSize: number;
      sort: {
        empty: boolean;
        sorted: boolean;
        unsorted: boolean;
      };
      offset: number;
      paged: boolean;
      unpaged: boolean;
    };
    last: boolean;
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
    sort: {
      empty: boolean;
      sorted: boolean;
      unsorted: boolean;
    };
    first: boolean;
    numberOfElements: number;
    empty: boolean;
  };
  status: string;
}

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private apiUrl = environment.baseUrl + '/messages';

  constructor(private http: HttpClient) { }

  async getChatUsers(): Promise<ApiResponse> {
    const response = await this.http.get<ApiResponse>(`${this.apiUrl}`).toPromise();
    if (!response) throw new Error('No response from server');
    return response;
  }

  async getAllUsers(): Promise<ApiResponse> {
    const response = await this.http.get<ApiResponse>(`${this.apiUrl}/contacts`).toPromise();
    if (!response) throw new Error('No response from server');
    return response;
  }

  async getMessages(userEmail: string): Promise<Message[]> {
    const response = await this.http.get<any>(`${this.apiUrl}/${userEmail}`).toPromise();
    if (!response || !response.data) throw new Error('No response from server');
    return response.data.map((msg: any) => ({
      id: '', // or generate a unique id if needed
      senderId: msg.senderEmail,
      receiverId: msg.recipientEmail,
      content: msg.content,
      timestamp: msg.timestamp,
      isRead: false // or set based on your logic
    }));
  }

  async sendMessage(message: Partial<Message>): Promise<Message> {
    const response = await this.http.post<Message>(`${this.apiUrl}/messages`, message).toPromise();
    if (!response) throw new Error('No response from server');
    return response;
  }
}
