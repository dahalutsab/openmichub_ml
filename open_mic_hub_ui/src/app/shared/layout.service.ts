import { Injectable, signal } from '@angular/core';

/**
 * Drawer state for the dashboard shell.
 *
 * The sidebar and the topbar toggle live in sibling components, so the open
 * state cannot sit in either of them. It used to be poked into the DOM
 * directly — `document.body.classList.toggle('sidebar-toggled')` — against
 * markup SB Admin 2 supplied and this app never had, which is why the toggle
 * button did nothing at all on small screens.
 */
@Injectable({ providedIn: 'root' })
export class LayoutService {
  readonly sidebarOpen = signal(false);

  toggleSidebar(): void {
    this.sidebarOpen.update(open => !open);
  }

  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }
}
