import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { LayoutService } from '../../../shared/layout.service';

@Component({
  selector: 'app-header',
  standalone: false,
  templateUrl: './header.component.html',
})
export class HeaderComponent {
  menuOpen = false;

  constructor(
    private router: Router,
    public layout: LayoutService
  ) { }

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
