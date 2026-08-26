import { Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { Subscription } from 'rxjs';
import {ChatBotService, ChatMessage} from '../service/chat-bot.service';

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  template: `
    <!-- Chat Toggle Button -->
    <div class="chat-toggle-container" *ngIf="!isChatOpen">
      <button
        class="chat-toggle-btn"
        (click)="toggleChat()"
        title="Open Chat">
        <i class="bi bi-chat-dots"></i>
      </button>
    </div>

    <!-- Chat Interface -->
    <div class="chat-container" *ngIf="isChatOpen">
      <!-- Chat Header -->
      <div class="chat-header">
        <div class="chat-title">
          <i class="bi bi-robot me-2"></i>
          OpenMicHub Smart Assistant
        </div>
        <div class="chat-actions">
          <button
            class="btn-icon"
            (click)="clearChat()"
            title="Clear Chat">
            <i class="bi bi-trash"></i>
          </button>
          <button
            class="btn-icon"
            (click)="toggleChat()"
            title="Close Chat">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>
      </div>

      <!-- Chat Messages -->
      <div class="chat-messages" #chatMessages>
        <div
          *ngFor="let message of messages"
          class="message-wrapper"
          [ngClass]="{'user-message': message.isUser, 'bot-message': !message.isUser}">

          <div class="message-content">
            <div class="message-avatar">
              <i class="bi" [ngClass]="message.isUser ? 'bi-person-fill' : 'bi-robot'"></i>
            </div>

            <div class="message-bubble">
              <div *ngIf="message.isLoading" class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
              <div
                *ngIf="!message.isLoading"
                class="message-text"
                [innerHTML]="formatMarkdown(message.content)">
              </div>
              <div class="message-time">
                {{ formatTime(message.timestamp) }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Chat Input -->
      <div class="chat-input-container">
        <div class="input-group">
          <input
            type="text"
            class="form-control chat-input"
            [(ngModel)]="currentMessage"
            (keyup.enter)="sendMessage()"
            [disabled]="isLoading"
            placeholder="Type your message...">
          <button
            class="btn btn-primary send-btn"
            (click)="sendMessage()"
            [disabled]="isLoading || !currentMessage.trim()">
            <i class="bi bi-send" *ngIf="!isLoading"></i>
            <i class="bi bi-arrow-clockwise spin" *ngIf="isLoading"></i>
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    /* Chat Toggle Button */
    .chat-toggle-container {
      position: fixed;
      bottom: 20px;
      right: 20px;
      z-index: 1000;
    }

    .chat-toggle-btn {
      width: 60px;
      height: 60px;
      border-radius: 50%;
      background: linear-gradient(135deg, rgb(var(--omh-accent)), rgb(var(--omh-accent) / 0.78));
      border: none;
      color: white;
      font-size: 24px;
      cursor: pointer;
      box-shadow: 0 4px 12px rgb(var(--omh-accent) / 0.3);
      transition: all 0.3s ease;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .chat-toggle-btn:hover {
      transform: scale(1.05);
      box-shadow: 0 6px 16px rgb(var(--omh-accent) / 0.4);
    }

    /* Chat Container */
    .chat-container {
      position: fixed;
      bottom: 20px;
      right: 20px;
      width: 400px;
      height: 600px;
      background: rgb(var(--omh-surface));
      border-radius: 16px;
      box-shadow: var(--omh-shadow-lift);
      display: flex;
      flex-direction: column;
      z-index: 1000;
      border: 1px solid rgb(var(--omh-line));
    }

    /* Chat Header */
    .chat-header {
      background: linear-gradient(135deg, rgb(var(--omh-accent)), rgb(var(--omh-accent) / 0.78));
      color: white;
      padding: 16px 20px;
      border-radius: 16px 16px 0 0;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .chat-title {
      font-weight: 600;
      font-size: 16px;
      display: flex;
      align-items: center;
    }

    .chat-actions {
      display: flex;
      gap: 8px;
    }

    .btn-icon {
      background: rgba(255, 255, 255, 0.2);
      border: none;
      color: white;
      width: 32px;
      height: 32px;
      border-radius: 8px;
      cursor: pointer;
      transition: background 0.2s;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .btn-icon:hover {
      background: rgba(255, 255, 255, 0.3);
    }

    /* Chat Messages */
    .chat-messages {
      flex: 1;
      overflow-y: auto;
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .message-wrapper {
      display: flex;
    }

    .message-wrapper.user-message {
      justify-content: flex-end;
    }

    .message-wrapper.bot-message {
      justify-content: flex-start;
    }

    .message-content {
      display: flex;
      align-items: flex-end;
      gap: 8px;
      max-width: 80%;
    }

    .user-message .message-content {
      flex-direction: row-reverse;
    }

    .message-avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      flex-shrink: 0;
    }

    .user-message .message-avatar {
      background: rgb(var(--omh-accent));
      color: white;
    }

    .bot-message .message-avatar {
      background: rgb(var(--omh-surface-2));
      color: rgb(var(--omh-muted));
      border: 1px solid rgb(var(--omh-line));
    }

    .message-bubble {
      padding: 12px 16px;
      border-radius: 18px;
      position: relative;
    }

    .user-message .message-bubble {
      background: rgb(var(--omh-accent));
      color: white;
      border-bottom-right-radius: 6px;
    }

    .bot-message .message-bubble {
      background: rgb(var(--omh-surface-2));
      color: rgb(var(--omh-ink));
      border: 1px solid rgb(var(--omh-line));
      border-bottom-left-radius: 6px;
    }

    .message-text {
      line-height: 1.4;
      word-wrap: break-word;
    }

    .message-time {
      font-size: 11px;
      opacity: 0.7;
      margin-top: 4px;
    }

    /* Typing Indicator */
    .typing-indicator {
      display: flex;
      gap: 4px;
      padding: 8px 0;
    }

    .typing-indicator span {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: rgb(var(--omh-muted));
      animation: typing 1.4s infinite ease-in-out;
    }

    .typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
    .typing-indicator span:nth-child(2) { animation-delay: -0.16s; }

    @keyframes typing {
      0%, 80%, 100% { transform: scale(0.8); opacity: 0.5; }
      40% { transform: scale(1); opacity: 1; }
    }

    /* Chat Input */
    .chat-input-container {
      padding: 16px 20px;
      border-top: 1px solid rgb(var(--omh-line));
      border-radius: 0 0 16px 16px;
      background: rgb(var(--omh-surface));
    }

    .input-group {
      display: flex;
      gap: 8px;
    }

    .chat-input {
      flex: 1;
      border: 1px solid rgb(var(--omh-line));
      border-radius: 20px;
      padding: 10px 16px;
      outline: none;
      font-size: 14px;
    }

    .chat-input:focus {
      border-color: rgb(var(--omh-accent));
      box-shadow: 0 0 0 0.2rem rgba(0, 123, 255, 0.25);
    }

    .send-btn {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      border: none;
      background: rgb(var(--omh-accent));
      color: white;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: background 0.2s;
    }

    .send-btn:hover:not(:disabled) {
      background: rgb(var(--omh-accent) / 0.82);
    }

    .send-btn:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .spin {
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    /* Scrollbar Styling */
    .chat-messages::-webkit-scrollbar {
      width: 6px;
    }

    .chat-messages::-webkit-scrollbar-track {
      background: rgb(var(--omh-surface-2));
      border-radius: 3px;
    }

    .chat-messages::-webkit-scrollbar-thumb {
      background: rgb(var(--omh-line));
      border-radius: 3px;
    }

    .chat-messages::-webkit-scrollbar-thumb:hover {
      background: rgb(var(--omh-muted));
    }

    /* Markdown Styling */
    .message-text :global(ul) {
      margin: 8px 0;
      padding-left: 20px;
    }

    .message-text :global(li) {
      margin: 4px 0;
    }

    .message-text :global(strong) {
      font-weight: 600;
    }

    .message-text :global(em) {
      font-style: italic;
    }

    .message-text :global(p) {
      margin: 8px 0;
    }

    .message-text :global(code) {
      background: rgba(0, 0, 0, 0.1);
      padding: 2px 4px;
      border-radius: 4px;
      font-family: 'Courier New', monospace;
      font-size: 0.9em;
    }

    /* Responsive Design */
    @media (max-width: 480px) {
      .chat-container {
        width: calc(100vw - 40px);
        height: calc(100vh - 40px);
        bottom: 20px;
        right: 20px;
      }
    }
  `]
})
export class ChatBotComponent implements OnInit, OnDestroy, AfterViewChecked {
  @ViewChild('chatMessages') private chatMessagesElement!: ElementRef;

  messages: ChatMessage[] = [];
  currentMessage: string = '';
  isChatOpen: boolean = false;
  isLoading: boolean = false;

  private subscriptions: Subscription = new Subscription();
  private shouldScrollToBottom: boolean = false;

  constructor(private chatBotService: ChatBotService) {}

  ngOnInit(): void {
    // Subscribe to messages
    this.subscriptions.add(
      this.chatBotService.messages$.subscribe(messages => {
        this.messages = messages;
        this.shouldScrollToBottom = true;
      })
    );

    // Subscribe to chat open state
    this.subscriptions.add(
      this.chatBotService.isChatOpen$.subscribe(isOpen => {
        this.isChatOpen = isOpen;
        if (isOpen) {
          this.shouldScrollToBottom = true;
        }
      })
    );
  }

  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  toggleChat(): void {
    this.chatBotService.toggleChat();
  }

  sendMessage(): void {
    if (!this.currentMessage.trim() || this.isLoading) {
      return;
    }

    const message = this.currentMessage.trim();
    this.currentMessage = '';
    this.isLoading = true;

    this.subscriptions.add(
      this.chatBotService.sendMessage(message).subscribe({
        next: (response) => {
          this.isLoading = false;
        },
        error: (error) => {
          this.isLoading = false;
          console.error('Chat error:', error);
        }
      })
    );
  }

  clearChat(): void {
    this.chatBotService.clearMessages();
  }

  formatTime(date: Date): string {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  formatMarkdown(text: string): string {
    if (!text) return '';

    // Convert markdown to HTML
    let formatted = text
      // Bold
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      // Italic
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      // Code
      .replace(/`(.*?)`/g, '<code>$1</code>')
      // Line breaks
      .replace(/\n\n/g, '</p><p>')
      .replace(/\n/g, '<br>')
      // Lists
      .replace(/^- (.*$)/gm, '<li>$1</li>')
      .replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>')
      // Remove empty paragraphs
      .replace(/<p><\/p>/g, '');

    // Wrap in paragraph if not already wrapped
    if (!formatted.includes('<p>') && !formatted.includes('<ul>')) {
      formatted = `<p>${formatted}</p>`;
    } else if (formatted.includes('<p>')) {
      formatted = `<p>${formatted}</p>`;
    }

    return formatted;
  }

  private scrollToBottom(): void {
    if (this.chatMessagesElement) {
      try {
        const element = this.chatMessagesElement.nativeElement;
        element.scrollTop = element.scrollHeight;
      } catch (err) {
        console.warn('Could not scroll to bottom:', err);
      }
    }
  }
}
