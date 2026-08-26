import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { UserService } from '../../user.service';
import { LayoutService } from '../../../shared/layout.service';
import { ThemeService } from '../../../shared/theme.service';

@Component({
  selector: 'app-header',
  standalone: false,
  templateUrl: './header.component.html',
})
export class HeaderComponent implements OnInit {
  menuOpen = false;
  query = '';
  loggedInUserId: number | null = null;

  userProfile = {
    name: 'User',
    avatar: 'assets/profile.jpg',
    status: ''
  };

  constructor(
    private router: Router,
    private service: UserService,
    public layout: LayoutService,
    public theme: ThemeService
  ) { }

  ngOnInit(): void {
    this.fetchUserProfile();
  }

  fetchUserProfile(): void {
    this.service.getLoginUser().subscribe({
      next: (response: any) => {
        if (response?.data) {
          this.loggedInUserId = response.data.id;
          this.userProfile.name = response.data.fullName || 'User';
          this.userProfile.avatar = response.data.profilePicture || 'assets/profile.jpg';
          this.userProfile.status = response.data.status || 'Active';
        }
      },
      error: (err) => console.error('Error fetching profile:', err)
    });
  }

  /** Hands the term to discovery, which is the app's real search surface. */
  search(): void {
    const term = this.query.trim();
    if (!term) {
      return;
    }
    this.router.navigate(['/discover'], { queryParams: { q: term } });
  }

  logout(): void {
    this.menuOpen = false;
    localStorage.clear();
    this.router.navigate(['/']);
  }
}
