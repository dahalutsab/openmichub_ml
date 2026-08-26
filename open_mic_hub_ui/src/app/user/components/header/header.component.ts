import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { UserService } from '../../user.service';

@Component({
  selector: 'app-header',
  standalone: false,
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss'
})
export class HeaderComponent {
  
  constructor(
    private router: Router,
    private service:UserService
  ) { }

  alerts = [
    {
      date: 'December 12, 2023',
      message: 'A new monthly report is ready to download!',
      icon: 'fas fa-file-alt',
      iconBg: 'bg-primary'
    },
    {
      date: 'December 7, 2023',
      message: 'New user registration detected!',
      icon: 'fas fa-user',
      iconBg: 'bg-success'
    },
    {
      date: 'December 2, 2023',
      message: 'Security alert: Unusual login detected',
      icon: 'fas fa-exclamation-triangle',
      iconBg: 'bg-warning'
    }
  ];

  messages = [
    {
      sender: 'Emily Fowler',
      avatar: 'img/undraw_profile_1.svg',
      message: 'Hi there! I am wondering if you can help me with a problem I\'ve been having.',
      time: '58m',
      status: 'bg-success'
    },
    {
      sender: 'Jae Chun',
      avatar: 'img/undraw_profile_2.svg',
      message: 'I need the latest reports ASAP!',
      time: '1d',
      status: 'bg-warning'
    },
    {
      sender: 'Morgan Alvarez',
      avatar: 'img/undraw_profile_3.svg',
      message: 'The project is ready for final review.',
      time: '2d',
      status: 'bg-success'
    }
  ];

  userProfile = {
  name: 'User',
  avatar: 'assets/profile.jpg', // default image
  status: ''
};


  ngOnInit(): void {
    this.fetchArtistProfile();
  }

  fetchArtistProfile(): void {
    this.service.getLoginUser().subscribe({
      next: (response: any) => {
        if (response && response.data) {
          this.loggedInUserId = response.data.id; // ✅ correct field
          this.userProfile.name = response.data.fullName || 'Artist';
          this.userProfile.avatar = response.data.profilePicture || 'assets/profile.jpg';
          this.userProfile.status = response.data.status || 'Active';
        }
      },
      error: (err) => {
        console.error('Error fetching profile:', err);
      }
    });
  }
  toggleSidebar(): void {
    document.body.classList.toggle('sidebar-toggled');
    document.querySelector('.sidebar')?.classList.toggle('toggled');
  }

loggedInUserId: number | null = null;




goToProfile() {
  if (this.loggedInUserId) {
    this.router.navigate(['/user/profile', this.loggedInUserId]);
  }
}

  logout(): void {
    console.log('Owner logged out');
    localStorage.clear();
    this.router.navigate(['../']);
  }

}
