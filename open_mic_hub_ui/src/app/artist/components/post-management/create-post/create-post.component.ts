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
  styleUrl: './create-post.component.scss'
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

  removeImage(index: number): void {
    this.selectedFiles.splice(index, 1);
    this.imagePreviews.splice(index, 1);
  }

  onSubmit(): void {
    if (this.postForm.valid) {
      this.loading = true;
      const post: Post = this.postForm.value;

      this.postService.createPost(post, this.selectedFiles).subscribe({
        next: (response) => {
          console.log('Post created successfully:', response);
          this.toastr.showSuccess('Post created successfully');
          this.router.navigate(['artist/posts']);
        },
        error: (error) => {
          console.error('Error creating post:', error);
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


