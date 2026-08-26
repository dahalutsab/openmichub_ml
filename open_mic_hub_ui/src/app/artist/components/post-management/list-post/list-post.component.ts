import { Component, OnInit } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { Post } from '../model/post.model';
import { PostsService } from '../service/posts.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-list-post',
  standalone: false,
  templateUrl: './list-post.component.html',
  styleUrl: './list-post.component.scss'
})
export class ListPostComponent implements OnInit {
  posts: Post[] = [];
  currentPage = 0;
  pageSize = 10;
  totalPages = 0;
  totalElements = 0;
  loading = false;

  constructor(
    private postService: PostsService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadPosts();
  }

  loadPosts(): void {
    this.loading = true;
    this.postService.getPosts(this.currentPage, this.pageSize).subscribe({
      next: (response) => {
        this.posts = response.data.content;
        this.totalPages = response.data.totalPages;
        this.totalElements = response.data.totalElements;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading posts:', error);
        this.loading = false;
      }
    });
  }

  onPageChange(page: number): void {
    this.currentPage = page;
    this.loadPosts();
  }

  viewPost(id: number): void {
    this.router.navigate(['artist/posts/view', id]);
  }

  editPost(id: number): void {
    this.router.navigate(['artist/posts/update', id]);
  }

  deletePost(id: number): void {
    if (confirm('Are you sure you want to delete this post?')) {
      this.postService.deletePost(id).subscribe({
        next: () => {
          this.loadPosts(); // Reload posts after deletion
        },
        error: (error) => {
          console.error('Error deleting post:', error);
        }
      });
    }
  }

  createPost(): void {
    this.router.navigate(['/artist/posts/create']);
  }
}