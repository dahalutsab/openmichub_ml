import { Component } from '@angular/core';

@Component({
  selector: 'app-admin-base',
  standalone: false,
  templateUrl: './admin-base.component.html'
})
export class AdminBaseComponent {

  currentYear = new Date().getFullYear();

}
