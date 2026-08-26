import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {AdminService} from '../../admin.service';

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
  styleUrls: ['./users-list.component.scss']
})
export class UsersListComponent implements OnInit {
  users: User[] = [];
  filteredUsers: User[] = [];
  paginatedUsers: User[] = [];
  selectedRole: string = '';

  // Pagination
  currentPage: number = 0;
  pageSize: number = 6;
  totalPages: number = 0;
  totalUsers: number = 0;

  loading = false;

  constructor(private adminService: AdminService) {}


  ngOnInit() {
    this.fetchUsers();
  }

  fetchUsers() {
    this.loading = true;
    this.adminService.getAllUsers(this.selectedRole, this.currentPage, this.pageSize)
      .subscribe({
        next: (response) => {
          this.users = response.data.content;
          this.totalUsers = response.data.totalElements;
          this.totalPages = response.data.totalPages;
          this.filteredUsers = this.users;
          this.paginatedUsers = this.users; // Already paginated from backend
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        }
      });
  }


  onRoleFilterChange() {
    this.currentPage = 0;
    this.applyFilters();
  }

  applyFilters() {
    if (this.selectedRole) {
      this.filteredUsers = this.users.filter(user =>
        user.roles.some(role => role.role === this.selectedRole)
      );
    } else {
      this.filteredUsers = [...this.users];
    }

    this.updatePagination();
  }

  updatePagination() {
    this.totalPages = Math.ceil(this.filteredUsers.length / this.pageSize);
    const startIndex = this.currentPage * this.pageSize;
    const endIndex = startIndex + this.pageSize;
    this.paginatedUsers = this.filteredUsers.slice(startIndex, endIndex);
  }

  goToPage(page: number) {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.updatePagination();
    }
  }

  getVisiblePages(): number[] {
    const pages: number[] = [];
    const maxVisible = 5;
    let start = Math.max(0, this.currentPage - Math.floor(maxVisible / 2));
    let end = Math.min(this.totalPages - 1, start + maxVisible - 1);

    if (end - start + 1 < maxVisible) {
      start = Math.max(0, end - maxVisible + 1);
    }

    for (let i = start; i <= end; i++) {
      pages.push(i);
    }

    return pages;
  }

  get startIndex(): number {
    return this.currentPage * this.pageSize + 1;
  }

  get endIndex(): number {
    return Math.min((this.currentPage + 1) * this.pageSize, this.filteredUsers.length);
  }

  getInitials(fullName: string): string {
    return fullName
      .split(' ')
      .map(name => name.charAt(0))
      .join('')
      .toUpperCase()
      .substring(0, 2);
  }

  onImageError(event: any) {
    event.target.style.display = 'none';
    event.target.parentElement.querySelector('.avatar-placeholder').style.display = 'flex';
  }
}
