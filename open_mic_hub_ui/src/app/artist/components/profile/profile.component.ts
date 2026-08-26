import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { environment } from '../../../environment/environment';
import { AVATAR_FALLBACK } from '../../../shared/avatar';

interface UserRole {
  role: string;
  description: string;
}

interface UserData {
  id: number;
  fullName: string;
  userEmail: string;
  profilePicture: string;
  userRole: UserRole[];
  phoneNumber: string;
  location: string;
  otpExpiryTime: string | null;
}

interface ApiResponse {
  timestamp: string;
  message: string;
  data: UserData;
  status: string;
}

@Component({
  selector: 'app-profile',
  standalone: false,
  templateUrl: './profile.component.html',
})
export class ProfileComponent implements OnInit {
  user: UserData | null = null;
  loading = false;
  error: string | null = null;

  readonly fallbackAvatar = AVATAR_FALLBACK;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchUserProfile();
  }

  fetchUserProfile(): void {
    this.loading = true;
    this.error = null;

    this.http.get<ApiResponse>(`${environment.baseUrl}/user`)
      .subscribe({
        next: (response) => {
          if (response.status === 'OK' && response.data) {
            this.user = response.data;
          } else {
            this.error = 'Failed to load user profile';
          }
          this.loading = false;
        },
        error: (err) => {
          this.error = 'Error loading profile: ' + (err.message || 'Unknown error');
          this.loading = false;
        }
      });
  }

  onImageError(event: any): void {
    event.target.src = AVATAR_FALLBACK;
  }
}
