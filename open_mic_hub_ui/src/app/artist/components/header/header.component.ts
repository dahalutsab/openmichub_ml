import {Component, OnInit} from '@angular/core';
import { Router } from '@angular/router';
import {ArtistService} from '../../artist.service';

@Component({
  selector: 'app-header',
  standalone: false,
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss'
})
export class HeaderComponent implements OnInit {
  userBalance: string = '0.00';

  userProfile = {
    name: 'Artist',
    avatar: 'assets/profile.jpg',
    status: 'Active'
  };

  constructor(
    private router: Router,
    private artistService: ArtistService
  ) { }

  ngOnInit() {
    this.fetchArtistProfile()
    this.fetchVirtualCoin()
  }

  fetchArtistProfile(): void {
    this.artistService.getArtist().subscribe(
      (response: any) => {

        if (response) {

          console.log('Artist profile fetched successfully:', response.data);
          this.userProfile.name = response.data.fullName || 'Artist';
          this.userProfile.avatar = response.data.profilePicture || 'assets/profile.jpg';
          this.userProfile.status = response.data.status || 'Active';
        } else {
          console.warn('No user data found');
        }
      },
      (error: any) => {
        console.error('Error fetching artist profile:', error);
      }
    );
  }

  fetchVirtualCoin(): void {
    this.artistService.getVirtualCoin().subscribe(
      (response: any) => {
        if (response && response.data) {
          this.userBalance = response.data.balance.toFixed(2);
        } else {
          console.warn('No virtual coin data found');
        }
      },
      (error: any) => {
        console.error('Error fetching virtual coin:', error);
      }
    );
  }

  toggleSidebar(): void {
    document.body.classList.toggle('sidebar-toggled');
    document.querySelector('.sidebar')?.classList.toggle('toggled');
  }

  viewProfile(): void {
    console.log('Viewing profile');
    // Navigate to profile page or show profile modal
    this.router.navigate(['artist/profile']);
  }

  navigateTo(route: string): void {
    console.log(`Navigating to ${route}`);

    switch(route) {
      case 'statement':
        this.router.navigate(['/statement']);
        break;
      case 'limits':
        this.router.navigate(['/limits']);
        break;
      case 'activities':
        this.router.navigate(['/activities']);
        break;
      case 'bank-account':
        this.router.navigate(['/bank-account']);
        break;
      case 'linked-bank':
        this.router.navigate(['/linked-bank']);
        break;
      default:
        console.warn(`Unknown route: ${route}`);
    }
  }

  addMoney(): void {
    console.log('Add money clicked');
    // Navigate to add money page or show add money modal
    this.router.navigate(['/add-money']);
  }

  logout(): void {

    // Clear user session data
    localStorage.clear();
    sessionStorage.clear();

    // Navigate to login page
    this.router.navigate(['/']);
  }
}
