import { Component } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Post } from '../model/post.model';
import { PostsService } from '../service/posts.service';
import { ToastService } from '../../../../auth/toastr.service';

@Component({
  selector: 'app-create-post',
  standalone: false,
  templateUrl: './create-post.component.html',
})
export class CreatePostComponent {

  postForm: FormGroup;
  selectedFiles: File[] = [];
  loading = false;
  imagePreviews: string[] = [];

  constructor(
    private fb: FormBuilder,
    private postService: PostsService,
    private router: Router,
    private toastr: ToastService
  ) {
    this.postForm = this.fb.group({
      title: ['', [Validators.required, Validators.minLength(3)]],
      content: ['', [Validators.required, Validators.minLength(10)]]
    });
  }

  onFileSelect(event: any): void {
    const files: FileList = event.target.files;
    this.selectedFiles = [];
    this.imagePreviews = [];

    const images = Array.from(files).filter(file => file.type.startsWith('image/'));
    this.selectedFiles = images;

    // Each preview is written to the slot matching its file. Pushing on
    // FileReader completion instead meant previews landed in whatever order
    // the reads finished, so removing one could drop a different image.
    this.imagePreviews = new Array(images.length).fill('');

    images.forEach((file, index) => {
      const reader = new FileReader();
      reader.onload = (e: any) => {
        this.imagePreviews[index] = e.target.result;
      };
      reader.readAsDataURL(file);
    });
  }

  removeImage(index: number): void {
    this.selectedFiles.splice(index, 1);
    this.imagePreviews.splice(index, 1);
  }

  onSubmit(): void {
    if (this.postForm.invalid) {
      this.postForm.markAllAsTouched();
      return;
    }

    this.loading = true;
    const post: Post = this.postForm.value;

    this.postService.createPost(post, this.selectedFiles).subscribe({
      next: () => {
        this.toastr.showSuccess('Post created successfully');
        this.router.navigate(['artist/posts']);
      },
      error: (error) => {
        console.error('Error creating post:', error);
        this.toastr.showError('Could not create the post. Please try again.');
        this.loading = false;
      }
    });
  }

  cancel(): void {
    this.router.navigate(['artist/posts']);
  }

  get title() { return this.postForm.get('title'); }
  get content() { return this.postForm.get('content'); }
}
