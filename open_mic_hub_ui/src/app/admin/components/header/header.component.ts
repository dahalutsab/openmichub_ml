import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-header',
  standalone: false,
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss'
})
export class HeaderComponent {
  constructor(
    private router: Router
  ) { }

  toggleSidebar(): void {
    document.body.classList.toggle('sidebar-toggled');
    document.querySelector('.sidebar')?.classList.toggle('toggled');
  }

  logout(): void {
    console.log('Admin logged out');
    localStorage.clear();
    this.router.navigate(['../']);
  }

  viewProfile(): void {
    console.log('Viewing profile');
    // Navigate to profile page or show profile modal
    this.router.navigate(['admin/profile']);
  }
}
