import { Component } from '@angular/core';

@Component({
  selector: 'app-user-base',
  standalone: false,
  templateUrl: './user-base.component.html'
})
export class UserBaseComponent {
    currentYear = new Date().getFullYear();

}
