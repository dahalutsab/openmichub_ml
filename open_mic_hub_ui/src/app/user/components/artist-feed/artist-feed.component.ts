import { Component, HostListener, OnInit } from '@angular/core';
import { Post } from '../models/post.model';
import { ArtistFeedService } from '../service/artist-feed.service';
import { AVATAR_FALLBACK } from '../../../shared/avatar';

/**
 * The booker's feed of artist posts.
 *
 * Previously this reached for two globals that were never guaranteed to be
 * there: Bootstrap's `Tooltip` (whose bundle is no longer loaded at all) and
 * AOS, which was never a dependency of this project — `initializeAOS` checked
 * `window.AOS` and silently did nothing, on every load, forever. Both are gone;
 * the lightbox and the entry animation are the component's own.
 */
@Component({
  selector: 'app-artist-feed',
  standalone: false,
  templateUrl: './artist-feed.component.html',
})
export class ArtistFeedComponent implements OnInit {
  readonly fallbackAvatar = AVATAR_FALLBACK;

  posts: Post[] = [];
  loading = false;
  error: string | null = null;
  currentPage = 0;
  pageSize = 10;
  totalPages = 0;
  totalElements = 0;
  hasMore = true;

  /** Open post in the lightbox, and which of its images is showing. */
  lightboxPost: Post | null = null;
  lightboxIndex = 0;

  constructor(private artistFeedsService: ArtistFeedService) {}

  ngOnInit(): void {
    this.loadPosts();
  }

  loadPosts(page: number = 0): void {
    this.loading = true;
    this.error = null;

    this.artistFeedsService.getPosts(page, this.pageSize).subscribe({
      next: (response) => {
        const content = response.data?.content ?? [];
        this.posts = page === 0 ? content : [...this.posts, ...content];

        this.currentPage = response.data?.number ?? 0;
        this.totalPages = response.data?.totalPages ?? 0;
        this.totalElements = response.data?.totalElements ?? 0;
        this.hasMore = !(response.data?.last ?? true);
        this.loading = false;
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

  // ---- lightbox ----------------------------------------------------------

  openImage(post: Post, index: number): void {
    if (!post.images?.length) {
      return;
    }
    this.lightboxPost = post;
    this.lightboxIndex = index;
  }

  closeImage(): void {
    this.lightboxPost = null;
  }

  /** Wraps at both ends, so the arrows never dead-end. */
  stepImage(delta: number): void {
    if (!this.lightboxPost?.images?.length) {
      return;
    }
    const count = this.lightboxPost.images.length;
    this.lightboxIndex = (this.lightboxIndex + delta + count) % count;
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (!this.lightboxPost) {
      return;
    }
    if (event.key === 'Escape') this.closeImage();
    if (event.key === 'ArrowRight') this.stepImage(1);
    if (event.key === 'ArrowLeft') this.stepImage(-1);
  }

  // ---- helpers -----------------------------------------------------------

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    if (Number.isNaN(date.getTime())) return '';

    const diffInMs = Date.now() - date.getTime();
    const diffInMins = Math.floor(diffInMs / (1000 * 60));
    const diffInHours = Math.floor(diffInMs / (1000 * 60 * 60));
    const diffInDays = Math.floor(diffInMs / (1000 * 60 * 60 * 24));

    if (diffInMins < 1) return 'Just now';
    if (diffInMins < 60) return `${diffInMins}m ago`;
    if (diffInHours < 24) return `${diffInHours}h ago`;
    if (diffInDays < 7) return `${diffInDays}d ago`;

    return date.toLocaleDateString();
  }

  /**
   * Falls back to the shared avatar. The old handler pointed at
   * `assets/images/placeholder.jpg`, which does not exist in this project — so
   * a broken image was replaced by a second broken image.
   */
  onImageError(event: any): void {
    event.target.src = AVATAR_FALLBACK;
  }

  refresh(): void {
    this.currentPage = 0;
    this.posts = [];
    this.hasMore = true;
    this.loadPosts();
  }

  trackByPostId(index: number, post: any): any {
    return post && post.id ? post.id : index;
  }
}
