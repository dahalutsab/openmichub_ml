import { Component } from '@angular/core';

@Component({
  selector: 'app-user-base',
  standalone: false,
  templateUrl: './user-base.component.html',
  styleUrl: './user-base.component.scss'
})
export class UserBaseComponent {
    currentYear = new Date().getFullYear();

}
