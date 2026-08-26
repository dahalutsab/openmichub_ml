import { Component, OnInit } from '@angular/core';
import { AdminService } from '../../admin.service';

// Interfaces
interface Role {
  role: string;
  description: string;
}

interface User {
  id: number;
  fullName: string;
  email: string;
  profileImage: string | null;
  roles: Role[];
  active: boolean;
}

interface Pageable {
  pageNumber: number;
  pageSize: number;
  sort: {
    empty: boolean;
    sorted: boolean;
    unsorted: boolean;
  };
  offset: number;
  paged: boolean;
  unpaged: boolean;
}

export interface UserResponse {
  timestamp: string;
  message: string;
  data: {
    content: User[];
    pageable: Pageable;
    last: boolean;
    totalElements: number;
    totalPages: number;
    first: boolean;
    size: number;
    number: number;
    sort: {
      empty: boolean;
      sorted: boolean;
      unsorted: boolean;
    };
    numberOfElements: number;
    empty: boolean;
  };
  status: string;
}

@Component({
  selector: 'app-users-list',
  standalone: false,
  templateUrl: 'users-list.component.html',
})
export class UsersListComponent implements OnInit {
  users: User[] = [];
  selectedRole: string = '';

  // Pagination
  currentPage: number = 0;
  pageSize: number = 10;
  totalPages: number = 0;
  totalUsers: number = 0;

  loading = false;

  constructor(private adminService: AdminService) {}

  ngOnInit() {
    this.fetchUsers();
  }

  /**
   * The server paginates and filters; this only asks it for a page.
   *
   * Previously the component did both again on the client, slicing the six
   * rows the server had already returned — so every page past the first came
   * back empty, and the role filter only searched the page you were looking at.
   */
  fetchUsers() {
    this.loading = true;
    this.adminService
      .getAllUsers(this.selectedRole || undefined, this.currentPage, this.pageSize)
      .subscribe({
        next: response => {
          this.users = response.data.content ?? [];
          this.totalUsers = response.data.totalElements;
          this.totalPages = response.data.totalPages;
          this.loading = false;
        },
        error: () => {
          this.users = [];
          this.loading = false;
        },
      });
  }

  onRoleFilterChange() {
    this.currentPage = 0;
    this.fetchUsers();
  }

  clearFilter() {
    this.selectedRole = '';
    this.onRoleFilterChange();
  }

  goToPage(page: number) {
    if (page >= 0 && page < this.totalPages && page !== this.currentPage) {
      this.currentPage = page;
      this.fetchUsers();
    }
  }

  getVisiblePages(): number[] {
    const pages: number[] = [];
    const maxVisible = 5;
    let start = Math.max(0, this.currentPage - Math.floor(maxVisible / 2));
    const end = Math.min(this.totalPages - 1, start + maxVisible - 1);

    if (end - start + 1 < maxVisible) {
      start = Math.max(0, end - maxVisible + 1);
    }

    for (let i = start; i <= end; i++) {
      pages.push(i);
    }

    return pages;
  }

  get startIndex(): number {
    return this.totalUsers === 0 ? 0 : this.currentPage * this.pageSize + 1;
  }

  get endIndex(): number {
    return Math.min((this.currentPage + 1) * this.pageSize, this.totalUsers);
  }

  getInitials(fullName: string): string {
    return (fullName || '')
      .split(' ')
      .map(name => name.charAt(0))
      .join('')
      .toUpperCase()
      .substring(0, 2);
  }

  roleClass(role: string): string {
    switch (role) {
      case 'ADMIN':
        return 'omh-status-critical';
      case 'ARTIST':
        return 'omh-status-pending';
      default:
        return 'omh-status-brand';
    }
  }

  /**
   * Falls back to initials by clearing the model rather than reaching into the
   * DOM to hide the broken <img> — the template already renders initials when
   * `profileImage` is empty.
   */
  onImageError(user: User) {
    user.profileImage = null;
  }
}
