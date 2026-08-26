import { Component, Inject, PLATFORM_ID } from '@angular/core';

import { isPlatformBrowser } from '@angular/common';
import { AdminService, NavigationItem } from '../../admin/admin.service';


@Component({
  selector: 'app-sidebar',
  standalone: false,
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent {


  navigationItems: NavigationItem[] = [];

  constructor(
    private servive:AdminService ,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      const roles = JSON.parse(localStorage.getItem('urole') || '[]');
      this.navigationItems = this.servive.getNavigationItems(roles);
    } else {
      this.navigationItems = []; 
    }
  }
  
  toggleExpanded(item: NavigationItem): void {
    item.expanded = !item.expanded;
  }
}

