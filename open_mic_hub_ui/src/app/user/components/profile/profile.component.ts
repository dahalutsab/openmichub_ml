import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { UserService } from '../../user.service';
import { AVATAR_FALLBACK } from '../../../shared/avatar';

@Component({
  selector: 'app-profile',
  standalone:false,
  templateUrl: './profile.component.html',
})
export class ProfileComponent implements OnInit {
  userId!: number;
  userData: any;
  isLoading = true;

  readonly fallbackAvatar = AVATAR_FALLBACK;

  constructor(
    private route: ActivatedRoute,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    const param = this.route.snapshot.paramMap.get('id');
    if (param) {
      this.userId = +param;
      this.getUserProfile();
    } else {
      console.error('User ID not found in route');
      this.isLoading = false;
    }
  }

  getUserProfile(): void {
    this.userService.getUserById(this.userId).subscribe({
      next: (res) => {
        this.userData = res.data;
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to fetch user by ID:', err);
        this.isLoading = false;
      }
    });
  }
}
