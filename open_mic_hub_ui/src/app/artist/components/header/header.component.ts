import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ArtistService } from '../../artist.service';
import { LayoutService } from '../../../shared/layout.service';

@Component({
  selector: 'app-header',
  standalone: false,
  templateUrl: './header.component.html',
})
export class HeaderComponent implements OnInit {
  menuOpen = false;
  userBalance: string = '0.00';

  userProfile = {
    name: 'Artist',
    avatar: 'assets/profile.jpg',
    status: 'Active'
  };

  constructor(
    private router: Router,
    private artistService: ArtistService,
    public layout: LayoutService
  ) { }

  ngOnInit() {
    this.fetchArtistProfile();
    this.fetchVirtualCoin();
  }

  fetchArtistProfile(): void {
    this.artistService.getArtist().subscribe({
      next: (response: any) => {
        if (response?.data) {
          this.userProfile.name = response.data.fullName || 'Artist';
          this.userProfile.avatar = response.data.profilePicture || 'assets/profile.jpg';
          this.userProfile.status = response.data.status || 'Active';
        }
      },
      error: (error: any) => console.error('Error fetching artist profile:', error)
    });
  }

  fetchVirtualCoin(): void {
    this.artistService.getVirtualCoin().subscribe({
      next: (response: any) => {
        if (response?.data) {
          this.userBalance = response.data.balance.toFixed(2);
        }
      },
      error: (error: any) => console.error('Error fetching virtual coin:', error)
    });
  }

  viewProfile(): void {
    this.menuOpen = false;
    this.router.navigate(['artist/profile']);
  }

  goToWallet(): void {
    this.menuOpen = false;
    this.router.navigate(['artist/wallet']);
  }

  logout(): void {
    this.menuOpen = false;
    localStorage.clear();
    sessionStorage.clear();
    this.router.navigate(['/']);
  }
}
