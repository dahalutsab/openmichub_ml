import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { Post } from '../model/post.model';
import { PostsService } from '../service/posts.service';

@Component({
  selector: 'app-view-post',
  standalone: false,
  templateUrl: './view-post.component.html',
  styleUrl: './view-post.component.scss'
})
export class ViewPostComponent implements OnInit {
  post: Post | null = null;
  postId: number;
  loading = false;

  constructor(
    private postService: PostsService,
    private router: Router,
    private route: ActivatedRoute
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
        this.router.navigate(['artist/posts']);
      }
    });
  }

  editPost(): void {
    this.router.navigate(['artist/posts/update', this.postId]);
  }

  deletePost(): void {
    if (confirm('Are you sure you want to delete this post?')) {
      this.postService.deletePost(this.postId).subscribe({
        next: () => {
          this.router.navigate(['artist/posts']);
        },
        error: (error) => {
          console.error('Error deleting post:', error);
        }
      });
    }
  }

  goBack(): void {
    this.router.navigate(['artist/posts']);
  }

  likePost(): void {
    if (this.post) {
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
}