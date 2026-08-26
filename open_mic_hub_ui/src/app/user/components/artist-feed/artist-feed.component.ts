import { Component, OnInit, AfterViewInit, OnDestroy } from '@angular/core';
import { Post } from '../models/post.model';
import { ArtistFeedService } from '../service/artist-feed.service';

declare var bootstrap: any;

@Component({
  selector: 'app-artist-feed',
  standalone: false,
  templateUrl: './artist-feed.component.html',
  styleUrl: './artist-feed.component.scss'
})
export class ArtistFeedComponent implements OnInit, AfterViewInit, OnDestroy {
  posts: Post[] = [];
  loading = false;
  error: string | null = null;
  currentPage = 0;
  pageSize = 10;
  totalPages = 0;
  totalElements = 0;
  hasMore = true;

  constructor(private artistFeedsService: ArtistFeedService) {}

  ngOnInit(): void {
    this.loadPosts();
  }

  ngAfterViewInit(): void {
    this.initializeTooltips();
    this.initializeAOS();
  }

  ngOnDestroy(): void {
    this.disposeTooltips();
  }

  loadPosts(page: number = 0): void {
    this.loading = true;
    this.error = null;

    this.artistFeedsService.getPosts(page, this.pageSize).subscribe({
      next: (response) => {
        if (page === 0) {
          this.posts = response.data.content;
        } else {
          this.posts = [...this.posts, ...response.data.content];
        }
        
        this.currentPage = response.data.number;
        this.totalPages = response.data.totalPages;
        this.totalElements = response.data.totalElements;
        this.hasMore = !response.data.last;
        this.loading = false;
        
        // Reinitialize tooltips after new content is loaded
        setTimeout(() => this.initializeTooltips(), 100);
      },
      error: (error) => {
        this.error = 'Failed to load posts. Please try again.';
        this.loading = false;
        console.error('Error loading posts:', error);
      }
    });
  }

  loadMore(): void {
    if (this.hasMore && !this.loading) {
      this.loadPosts(this.currentPage + 1);
    }
  }

  toggleLike(post: Post): void {
    this.artistFeedsService.toggleLike(post.id).subscribe({
      next: (response) => {
        const index = this.posts.findIndex(p => p.id === post.id);
        if (index !== -1) {
          this.posts[index] = response.data;
        }
      },
      error: (error) => {
        console.error('Error toggling like:', error);
      }
    });
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    const now = new Date();
    const diffInMs = now.getTime() - date.getTime();
    const diffInMins = Math.floor(diffInMs / (1000 * 60));
    const diffInHours = Math.floor(diffInMs / (1000 * 60 * 60));
    const diffInDays = Math.floor(diffInMs / (1000 * 60 * 60 * 24));

    if (diffInMins < 1) return 'Just now';
    if (diffInMins < 60) return `${diffInMins}m ago`;
    if (diffInHours < 24) return `${diffInHours}h ago`;
    if (diffInDays < 7) return `${diffInDays}d ago`;
    
    return date.toLocaleDateString();
  }

  onImageError(event: any): void {
    event.target.src = 'assets/images/placeholder.jpg';
  }

  refresh(): void {
    this.currentPage = 0;
    this.posts = [];
    this.hasMore = true;
    this.loadPosts();
    // Reinitialize tooltips after content refresh
    setTimeout(() => this.initializeTooltips(), 200);
  }

  trackByPostId(index: number, post: any): any {
    return post && post.id ? post.id : index;
  }

  private initializeTooltips(): void {
    if (typeof bootstrap !== 'undefined') {
      setTimeout(() => {
        const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
        tooltipTriggerList.map(function (tooltipTriggerEl) {
          return new bootstrap.Tooltip(tooltipTriggerEl);
        });
      }, 100);
    }
  }

  private disposeTooltips(): void {
    if (typeof bootstrap !== 'undefined') {
      const tooltips = document.querySelectorAll('[data-bs-toggle="tooltip"]');
      tooltips.forEach(tooltip => {
        const instance = bootstrap.Tooltip.getInstance(tooltip);
        if (instance) {
          instance.dispose();
        }
      });
    }
  }

  private initializeAOS(): void {
    // Initialize AOS (Animate On Scroll) if available
    if (typeof window !== 'undefined' && (window as any).AOS) {
      (window as any).AOS.init({
        duration: 600,
        easing: 'ease-out',
        once: true,
        offset: 50
      });
    }
  }
}
