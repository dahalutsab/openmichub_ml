import { Component, OnInit } from '@angular/core';
import { Post } from '../model/post.model';
import { PostsService } from '../service/posts.service';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-list-post',
  standalone: false,
  templateUrl: './list-post.component.html',
})
export class ListPostComponent implements OnInit {
  posts: Post[] = [];
  currentPage = 0;
  pageSize = 10;
  totalPages = 0;
  totalElements = 0;
  loading = false;

  /** Post awaiting delete confirmation, or null when the modal is shut. */
  pendingDelete: Post | null = null;
  deleting = false;

  constructor(
    private postService: PostsService,
    private router: Router,
    private toast: ToastrService
  ) {}

  ngOnInit(): void {
    this.loadPosts();
  }

  loadPosts(): void {
    this.loading = true;
    this.postService.getPosts(this.currentPage, this.pageSize).subscribe({
      next: (response) => {
        this.posts = response.data?.content ?? [];
        this.totalPages = response.data?.totalPages ?? 0;
        this.totalElements = response.data?.totalElements ?? 0;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading posts:', error);
        this.loading = false;
      }
    });
  }

  onPageChange(page: number): void {
    if (page < 0 || page >= this.totalPages || page === this.currentPage) {
      return;
    }
    this.currentPage = page;
    this.loadPosts();
  }

  get pageNumbers(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }

  viewPost(id: number): void {
    this.router.navigate(['artist/posts/view', id]);
  }

  editPost(id: number): void {
    this.router.navigate(['artist/posts/update', id]);
  }

  askDelete(post: Post): void {
    this.pendingDelete = post;
  }

  /** Replaces window.confirm(), and reports failures instead of only logging. */
  confirmDelete(): void {
    const post = this.pendingDelete;
    if (!post?.id || this.deleting) {
      return;
    }

    this.deleting = true;
    this.postService.deletePost(post.id).subscribe({
      next: () => {
        this.deleting = false;
        this.pendingDelete = null;
        this.toast.success('Post deleted');
        this.loadPosts();
      },
      error: (error) => {
        this.deleting = false;
        this.pendingDelete = null;
        this.toast.error('Could not delete the post');
        console.error('Error deleting post:', error);
      }
    });
  }

  createPost(): void {
    this.router.navigate(['/artist/posts/create']);
  }
}
