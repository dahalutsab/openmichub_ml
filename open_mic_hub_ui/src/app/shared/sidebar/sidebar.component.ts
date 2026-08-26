import { Component, Inject, OnInit, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { AdminService, NavigationItem } from '../../admin/admin.service';
import { LayoutService } from '../layout.service';

/**
 * Dashboard navigation: a permanent icon rail plus a labelled drawer.
 *
 * The rail carries the primary destinations at 68px wide, which is what lets a
 * dense analytics board keep the rest of the screen. The drawer is the same
 * list with labels, opened from the rail's handle or the topbar on small
 * screens, and it overlays rather than pushes so the charts never reflow.
 */
@Component({
  selector: 'app-sidebar',
  standalone: false,
  templateUrl: './sidebar.component.html',
})
export class SidebarComponent implements OnInit {

  navigationItems: NavigationItem[] = [];

  /** Where the role's dashboard lives, for the rail's home button. */
  dashboardRoute = '/';

  constructor(
    private service: AdminService,
    public layout: LayoutService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      this.navigationItems = [];
      return;
    }

    const roles: string[] = JSON.parse(localStorage.getItem('urole') || '[]');
    this.navigationItems = this.service.getNavigationItems(roles);
    this.dashboardRoute = this.resolveDashboard(roles);
  }

  toggleExpanded(item: NavigationItem): void {
    item.expanded = !item.expanded;
  }

  /**
   * The rail shows at most this many destinations; the rest stay in the drawer.
   * Past roughly eight, an icon-only column stops being scannable and the
   * icons start needing to be read rather than recognised.
   */
  get railItems(): NavigationItem[] {
    return this.navigationItems.slice(0, 7);
  }

  get overflowItems(): NavigationItem[] {
    return this.navigationItems.slice(7);
  }

  private resolveDashboard(roles: string[]): string {
    if (roles.includes('SUPER_ADMIN') || roles.includes('ADMIN')) return '/admin/dashboard';
    if (roles.includes('ARTIST')) return '/artist/dashboard';
    return '/user/dashboard';
  }
}
