import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { LayoutService } from '../../../shared/layout.service';
import { ThemeService } from '../../../shared/theme.service';

@Component({
  selector: 'app-header',
  standalone: false,
  templateUrl: './header.component.html',
})
export class HeaderComponent {
  menuOpen = false;
  query = '';

  constructor(
    private router: Router,
    public layout: LayoutService,
    public theme: ThemeService
  ) { }

  /**
   * There is no search endpoint spanning users, bookings and payments, so this
   * routes to the users list pre-filtered rather than pretending to be global.
   */
  search(): void {
    const term = this.query.trim();
    if (!term) {
      return;
    }
    this.router.navigate(['/admin/users'], { queryParams: { q: term } });
  }

  logout(): void {
    this.menuOpen = false;
    localStorage.clear();
    this.router.navigate(['/']);
  }

  viewProfile(): void {
    this.menuOpen = false;
    this.router.navigate(['admin/profile']);
  }
}
