import {
  Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewChecked, ChangeDetectorRef
} from '@angular/core';
import { ChatService } from '../services/chat.service';
import { NgForOf, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RxStompService } from '@stomp/ng2-stompjs';
import { Message as StompMessage } from '@stomp/stompjs';
import { Subscription } from 'rxjs';

interface User {
  fullName: string;
  email: string;
  profile: string;
  lastMessage: string;
  lastMessageTime: string | null;
}

interface Message {
  id?: string;
  senderId: string;
  receiverId: string | null;
  content: string;
  timestamp: string;
  isRead: boolean;
}

interface ChatResponse {
  senderEmail: string;
  recipientEmail: string | null;
  content: string;
  timestamp: string;
}

@Component({
  selector: 'app-chat',
  templateUrl: './chat.component.html',
  standalone: true,
  imports: [NgIf, NgForOf, FormsModule],
  styleUrls: ['./chat.component.scss']
})
export class ChatComponent implements OnInit, OnDestroy, AfterViewChecked {
  activeTab: 'chats' | 'users' = 'chats';
  chatUsers: User[] = [];
  allUsers: User[] = [];
  selectedUser: User | null = null;
  messages: Message[] = [];
  newMessage: string = '';
  loading = false;
  loadingMessages = false;
  currentUserEmail: string = '';

  private subscriptions: Subscription[] = [];
  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;

  constructor(
    private chatService: ChatService,
    private rxStompService: RxStompService,
    private cdr: ChangeDetectorRef
  ) {
    const token = localStorage.getItem('authToken') || '';
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        this.currentUserEmail = payload.sub || payload.email || '';
      } catch (e) {
        console.warn('Invalid JWT token');
      }
    }
  }

  ngOnInit(): void {
    this.loadChatUsers();
    this.loadAllUsers();
    this.setupWebSocketSubscriptions();
  }

  ngAfterViewChecked(): void {
    this.scrollToBottom();
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }

  scrollToBottom(): void {
    if (this.messagesContainer) {
      const container = this.messagesContainer.nativeElement;
      container.scrollTop = container.scrollHeight;
    }
  }

  setupWebSocketSubscriptions(): void {
    if (!this.rxStompService.active) {
      this.rxStompService.activate();
    }

    const privateSub = this.rxStompService.watch('/user/queue/private')
      .subscribe((msg: StompMessage) => {
        console.log('Private message received:', msg);
        const chatResponse: ChatResponse = JSON.parse(msg.body);
        this.handleIncomingMessage(chatResponse);
        this.cdr.detectChanges();
      });

    const publicSub = this.rxStompService.watch('/topic/public')
      .subscribe((msg: StompMessage) => {
        console.log('Public message received:', msg);
        const chatResponse: ChatResponse = JSON.parse(msg.body);
        this.handleIncomingMessage(chatResponse);
        this.cdr.detectChanges();
      });

    this.subscriptions.push(privateSub, publicSub);
  }

  private handleIncomingMessage(chat: ChatResponse): void {
    const incoming: Message = {
      senderId: chat.senderEmail,
      receiverId: chat.recipientEmail,
      content: chat.content,
      timestamp: chat.timestamp,
      isRead: chat.senderEmail === this.currentUserEmail
    };

    const isFromSelectedUser = this.selectedUser?.email === chat.senderEmail;
    const isToSelectedUser = this.selectedUser?.email === chat.recipientEmail;

    // ✅ Real-time push into current messages if chatting with that user
    if (this.selectedUser && (isFromSelectedUser || isToSelectedUser)) {
      this.messages = [...this.messages, incoming];
      this.scrollToBottom();
      this.cdr.detectChanges();
    }

    // ✅ Update chat list previews
    const otherUserEmail = this.currentUserEmail === chat.senderEmail ? chat.recipientEmail : chat.senderEmail;
    if (otherUserEmail) {
      const userIndex = this.chatUsers.findIndex(u => u.email === otherUserEmail);
      if (userIndex !== -1) {
        this.chatUsers[userIndex].lastMessage = incoming.content;
        this.chatUsers[userIndex].lastMessageTime = incoming.timestamp;
        this.chatUsers = [...this.chatUsers];
      } else {
        this.chatService.getAllUsers().then(response => {
          const foundUser = response.data.content.find(u => u.email === otherUserEmail);
          if (foundUser) {
            this.chatUsers = [...this.chatUsers, {
              ...foundUser,
              lastMessage: incoming.content,
              lastMessageTime: incoming.timestamp
            }];
          }
          this.cdr.detectChanges();
        });
      }
    }
  }


  async loadChatUsers(): Promise<void> {
    this.loading = true;
    try {
      const response = await this.chatService.getChatUsers();
      this.chatUsers = response.data.content;
    } catch (e) {
      console.error('Chat users load failed');
    } finally {
      this.loading = false;
    }
  }

  async loadAllUsers(): Promise<void> {
    this.loading = true;
    try {
      const response = await this.chatService.getAllUsers();
      this.allUsers = response.data.content;
    } catch (e) {
      console.error('All users load failed');
    } finally {
      this.loading = false;
    }
  }

  async selectUser(user: User): Promise<void> {
    this.selectedUser = user;
    this.loadingMessages = true;
    try {
      const response = await this.chatService.getMessages(user.email);
      this.messages = response.map((msg: any) => ({
        senderId: msg.senderId,
        receiverId: msg.receiverId,
        content: msg.content,
        timestamp: msg.timestamp,
        isRead: msg.senderId !== this.currentUserEmail
      }));
    } catch (e) {
      console.error('Message history load failed');
      this.messages = [];
    } finally {
      this.loadingMessages = false;
      this.scrollToBottom();
      this.cdr.detectChanges();
    }
  }

  async sendMessage(): Promise<void> {
    if (!this.newMessage.trim() || !this.selectedUser) return;

    const msgPayload = {
      content: this.newMessage.trim(),
      recipientEmail: this.selectedUser.email
    };

    this.rxStompService.publish({
      destination: '/app/chat.sendPrivateMessage',
      body: JSON.stringify(msgPayload)
    });

    const outgoing: Message = {
      senderId: this.currentUserEmail,
      receiverId: this.selectedUser.email,
      content: this.newMessage,
      timestamp: new Date().toISOString(),
      isRead: true
    };

    this.messages.push(outgoing);
    this.newMessage = '';

    const index = this.chatUsers.findIndex(u => u.email === this.selectedUser?.email);
    if (index !== -1) {
      this.chatUsers[index].lastMessage = outgoing.content;
      this.chatUsers[index].lastMessageTime = outgoing.timestamp;
    } else {
      this.chatUsers.push({
        ...this.selectedUser,
        lastMessage: outgoing.content,
        lastMessageTime: outgoing.timestamp
      });
    }

    this.scrollToBottom();
    this.cdr.detectChanges();
  }

  switchTab(tab: 'chats' | 'users'): void {
    this.activeTab = tab;
    this.selectedUser = null;
    this.messages = [];
  }

  getCurrentUsersList(): User[] {
    return this.activeTab === 'chats' ? this.chatUsers : this.allUsers;
  }

  getDefaultAvatar(name: string | undefined): string {
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(name ?? '')}&background=random`;
  }

  formatTime(timestamp: string | null): string {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    return diff < 86400000
      ? date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      : date.toLocaleDateString();
  }

  isMessageFromCurrentUser(message: Message): boolean {
    return message.senderId === this.currentUserEmail;
  }

  onImageError(event: Event, fullName: string | undefined): void {
    const img = event.target as HTMLImageElement;
    if (img) {
      img.src = this.getDefaultAvatar(fullName);
    }
  }

  onKeyPress(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }
}
