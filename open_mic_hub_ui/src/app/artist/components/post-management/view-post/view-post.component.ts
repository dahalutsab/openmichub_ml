import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { Post } from '../model/post.model';
import { PostsService } from '../service/posts.service';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-view-post',
  standalone: false,
  templateUrl: './view-post.component.html',
})
export class ViewPostComponent implements OnInit {
  post: Post | null = null;
  postId: number;
  loading = false;

  /** Lightbox source, or null when closed. */
  activeImage: string | null = null;

  confirmingDelete = false;
  deleting = false;

  constructor(
    private postService: PostsService,
    private router: Router,
    private route: ActivatedRoute,
    private toast: ToastrService
  ) {
    this.postId = +this.route.snapshot.params['id'];
  }

  ngOnInit(): void {
    this.loadPost();
  }

  loadPost(): void {
    this.loading = true;
    this.postService.getPostById(this.postId).subscribe({
      next: (response) => {
        this.post = response.data;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading post:', error);
        this.loading = false;
        this.post = null;
      }
    });
  }

  openImage(image: string): void {
    this.activeImage = image;
  }

  editPost(): void {
    this.router.navigate(['artist/posts/update', this.postId]);
  }

  askDelete(): void {
    this.confirmingDelete = true;
  }

  deletePost(): void {
    if (this.deleting) {
      return;
    }
    this.deleting = true;
    this.postService.deletePost(this.postId).subscribe({
      next: () => {
        this.deleting = false;
        this.confirmingDelete = false;
        this.toast.success('Post deleted');
        this.router.navigate(['artist/posts']);
      },
      error: (error) => {
        this.deleting = false;
        this.confirmingDelete = false;
        this.toast.error('Could not delete the post');
        console.error('Error deleting post:', error);
      }
    });
  }

  goBack(): void {
    this.router.navigate(['artist/posts']);
  }

  likePost(): void {
    if (!this.post) {
      return;
    }
    this.postService.likePost(this.postId).subscribe({
      next: (response) => {
        if (this.post) {
          this.post.likesCount = response.data.likesCount;
        }
      },
      error: (error) => {
        console.error('Error liking post:', error);
      }
    });
  }
}
