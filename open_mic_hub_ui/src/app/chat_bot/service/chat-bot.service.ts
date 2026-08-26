import {Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';
import {environment} from '../../environment/environment';

export interface ChatMessage {
  id: string;
  content: string;
  isUser: boolean;
  timestamp: Date;
  isLoading?: boolean;
}

export interface ChatResponse {
  timestamp: string;
  message: string;
  data: {
    message: string;
    results: any;
  };
  status: string;
}

@Injectable({
  providedIn: 'root'
})
export class ChatBotService {
  private readonly API_BASE_URL = environment.baseUrl;
  private messagesSubject = new BehaviorSubject<ChatMessage[]>([]);
  private isChatOpenSubject = new BehaviorSubject<boolean>(false);

  public messages$ = this.messagesSubject.asObservable();
  public isChatOpen$ = this.isChatOpenSubject.asObservable();

  constructor(private http: HttpClient) {
    // Initialize with welcome message
    this.addMessage('Hello! I\'m your AI assistant. How can I help you today?', false);
  }

  sendMessage(userMessage: string): Observable<ChatMessage> {
    // Add user message
    const userChatMessage = this.addMessage(userMessage, true);

    // Add loading message
    const loadingMessage = this.addMessage('', false, true);

    // Prepare API request
    const params = new HttpParams().set('request', userMessage);

    return this.http.get<ChatResponse>(`${this.API_BASE_URL}/chat`, { params })
      .pipe(
        map(response => {
          // Remove loading message
          this.removeMessage(loadingMessage.id);

          // Add bot response
          return this.addMessage(response.data.message, false);
        }),
        catchError(error => {
          // Remove loading message
          this.removeMessage(loadingMessage.id);

          // Add error message
          const errorMessage = this.addMessage(
            'Sorry, I encountered an error while processing your request. Please try again.',
            false
          );
          return of(errorMessage);
        })
      );
  }

  private addMessage(content: string, isUser: boolean, isLoading: boolean = false): ChatMessage {
    const message: ChatMessage = {
      id: this.generateId(),
      content,
      isUser,
      timestamp: new Date(),
      isLoading
    };

    const currentMessages = this.messagesSubject.value;
    this.messagesSubject.next([...currentMessages, message]);

    return message;
  }

  private removeMessage(messageId: string): void {
    const currentMessages = this.messagesSubject.value;
    const filteredMessages = currentMessages.filter(msg => msg.id !== messageId);
    this.messagesSubject.next(filteredMessages);
  }

  toggleChat(): void {
    this.isChatOpenSubject.next(!this.isChatOpenSubject.value);
  }

  openChat(): void {
    this.isChatOpenSubject.next(true);
  }

  closeChat(): void {
    this.isChatOpenSubject.next(false);
  }

  clearMessages(): void {
    this.messagesSubject.next([]);
    this.addMessage('Chat cleared. How can I help you?', false);
  }

  private generateId(): string {
    return Math.random().toString(36).substr(2, 9);
  }
}
