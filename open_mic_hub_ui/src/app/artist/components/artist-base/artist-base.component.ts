import { Component } from '@angular/core';

@Component({
  selector: 'app-artist-base',
  standalone: false,
  templateUrl: './artist-base.component.html'
})
export class ArtistBaseComponent {
    currentYear = new Date().getFullYear();

}
