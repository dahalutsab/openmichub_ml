import { Component, OnInit } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { Post } from '../model/post.model';
import { PostsService } from '../service/posts.service';
import { ToastService } from '../../../../auth/toastr.service';

@Component({
  selector: 'app-edit-post',
  standalone: false,
  templateUrl: './edit-post.component.html',
  styleUrl: './edit-post.component.scss'
})
export class EditPostComponent implements OnInit {
  postForm: FormGroup;
  postId: number;
  selectedFiles: File[] = [];
  loading = false;
  loadingPost = false;
  imagePreviews: string[] = [];
  existingImages: string[] = [];

  constructor(
    private fb: FormBuilder,
    private postService: PostsService,
    private router: Router,
    private route: ActivatedRoute,
    private toastr: ToastService
  ) {
    this.postId = +this.route.snapshot.params['id'];
    this.postForm = this.fb.group({
      title: ['', [Validators.required, Validators.minLength(3)]],
      content: ['', [Validators.required, Validators.minLength(10)]]
    });
  }

  ngOnInit(): void {
    this.loadPost();
  }

  loadPost(): void {
    this.loadingPost = true;
    this.postService.getPostById(this.postId).subscribe({
      next: (response) => {
        const post = response.data;
        this.postForm.patchValue({
          title: post.title,
          content: post.content
        });
        this.existingImages = post.images || [];
        this.loadingPost = false;
      },
      error: (error) => {
        console.error('Error loading post:', error);
        this.loadingPost = false;
        this.router.navigate(['artist/posts']);
      }
    });
  }

  onFileSelect(event: any): void {
    const files: FileList = event.target.files;
    this.selectedFiles = [];
    this.imagePreviews = [];

    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      if (file.type.startsWith('image/')) {
        this.selectedFiles.push(file);
        
        // Create image preview
        const reader = new FileReader();
        reader.onload = (e: any) => {
          this.imagePreviews.push(e.target.result);
        };
        reader.readAsDataURL(file);
      }
    }
  }

  removeNewImage(index: number): void {
    this.selectedFiles.splice(index, 1);
    this.imagePreviews.splice(index, 1);
  }

  onSubmit(): void {
    if (this.postForm.valid) {
      this.loading = true;
      const post: Post = this.postForm.value;

      this.postService.updatePost(this.postId, post, this.selectedFiles).subscribe({
        next: (response) => {
          console.log('Post updated successfully:', response);
          this.toastr.showSuccess('Post updated successfully');
          this.router.navigate(['artist/posts']);
        },
        error: (error) => {
          console.error('Error updating post:', error);
          this.toastr.showError('Failed to update post');
          this.loading = false;
        }
      });
    } else {
      this.markFormGroupTouched();
    }
  }

  private markFormGroupTouched(): void {
    Object.keys(this.postForm.controls).forEach(key => {
      const control = this.postForm.get(key);
      control?.markAsTouched();
    });
  }

  cancel(): void {
    this.router.navigate(['artist/posts']);
  }

  get title() { return this.postForm.get('title'); }
  get content() { return this.postForm.get('content'); }
}