import { Component, OnInit } from '@angular/core';
import { UserService } from '../../../user/user.service';

@Component({
  selector: 'app-admin-profile',
  standalone: false,
  templateUrl: './profile.component.html',
})
export class ProfileComponent implements OnInit {
  loading = true;
  error = '';
  user: any = null;

  constructor(private userService: UserService) {}

  ngOnInit(): void {
    this.userService.getLoginUser().subscribe({
      next: (res: any) => {
        this.user = res?.data ?? null;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load your profile.';
        this.loading = false;
      },
    });
  }

  getInitials(fullName: string): string {
    return (fullName || '')
      .split(' ')
      .map(name => name.charAt(0))
      .join('')
      .toUpperCase()
      .substring(0, 2);
  }
}
