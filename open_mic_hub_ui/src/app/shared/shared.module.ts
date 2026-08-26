import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SidebarComponent } from './sidebar/sidebar.component';
import { ClickOutsideDirective } from './click-outside.directive';
import { RouterModule } from '@angular/router';

@NgModule({
  declarations: [SidebarComponent, ClickOutsideDirective],

  imports: [
    CommonModule,
    RouterModule
  ],
  exports: [SidebarComponent, ClickOutsideDirective, RouterModule]
})
export class SharedModule { }
