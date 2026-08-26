import { Component, Inject, OnInit, PLATFORM_ID } from '@angular/core';

import { isPlatformBrowser } from '@angular/common';
import { AdminService, NavigationItem } from '../../admin/admin.service';
import { LayoutService } from '../layout.service';

@Component({
  selector: 'app-sidebar',
  standalone: false,
  templateUrl: './sidebar.component.html',
})
export class SidebarComponent implements OnInit {

  navigationItems: NavigationItem[] = [];

  constructor(
    private service: AdminService,
    public layout: LayoutService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      const roles = JSON.parse(localStorage.getItem('urole') || '[]');
      this.navigationItems = this.service.getNavigationItems(roles);
    } else {
      this.navigationItems = [];
    }
  }

  toggleExpanded(item: NavigationItem): void {
    item.expanded = !item.expanded;
  }
}
