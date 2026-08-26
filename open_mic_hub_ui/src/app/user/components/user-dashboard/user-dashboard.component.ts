import { Component } from '@angular/core';

@Component({
  selector: 'app-user-dashboard',
  standalone: false,
  templateUrl: './user-dashboard.component.html',
  styleUrl: './user-dashboard.component.scss'
})
export class UserDashboardComponent {
   // Sample data for charts and cards
  cardData = [
    { title: 'Earnings (Monthly)', value: '$40,000', icon: 'bi bi-calendar', color: 'primary' },
    { title: 'Earnings (Annual)', value: '$215,000', icon: 'bi bi-earnings', color: 'success' },
    { title: 'Tasks', value: '50%', icon: 'bi bi-clipboard', color: 'info' },
    { title: 'Pending Requests', value: '18', icon: 'bi bi-chat', color: 'warning' }
  ];

  constructor() { }

  ngOnInit(): void {
    // Initialize charts and other dashboard elements
  }


}
