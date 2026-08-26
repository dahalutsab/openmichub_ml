import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth.service';
import { ToastrService } from 'ngx-toastr';

interface Genre {
  id: number;
  name: string;
  description: string;
  slug: string;
  categories: SubGenre[];
}

interface SubGenre {
  id: number;
  name: string;
  description: string;
}

@Component({
  selector: 'app-register',
  standalone:false,
  templateUrl: './register.component.html',
})
export class RegisterComponent implements OnInit {
  registerForm: FormGroup;
  selectedFile: File | null = null;
  profileImage: string | ArrayBuffer | null = null;
  isSubmitting = false;
  submitted = false;
  errorMessage = '';
  activeRole: 'user' | 'artist' = 'user';
  genres: Genre[] = [];
  selectedGenres: { genreId: number; subGenreIds: number[] }[] = [];

  constructor(
    private router: Router,
    private fb: FormBuilder,
    private authService: AuthService,
    private http: HttpClient,
    private toast: ToastrService
  ) {
    this.registerForm = this.fb.group({
      fullName: ['', [Validators.required, Validators.minLength(3)]],
      userEmail: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      phoneNumber: ['', [Validators.required, Validators.pattern('^[0-9]{7,15}$')]],
      location: ['', Validators.required],
      genres: [[]],
      bio: [''],
      stageName: [''],
      role: ['user', Validators.required]
    });
  }

  ngOnInit() {
    this.fetchGenres();
  }

  fetchGenres() {
    this.http.get<{ data: Genre[] }>('http://localhost:8181/api/v1/genre').subscribe({
      next: (response) => {
        this.genres = response.data;
      },
      error: (error) => {
        this.errorMessage = 'Failed to load genres. Please try again.';
        console.error('Error fetching genres:', error);
      }
    });
  }

  onFileChange(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      if (!file.type.startsWith('image/')) {
        this.errorMessage = 'Please select an image file';
        this.selectedFile = null;
        this.profileImage = null;
        return;
      }
      if (file.size > 5 * 1024 * 1024) {
        this.errorMessage = 'File size should be less than 5MB';
        this.selectedFile = null;
        this.profileImage = null;
        return;
      }
      this.selectedFile = file;
      this.errorMessage = '';

      const reader = new FileReader();
      reader.onload = () => {
        this.profileImage = reader.result;
      };
      reader.readAsDataURL(file);
    }
  }

  removeImage() {
    this.selectedFile = null;
    this.profileImage = null;
    const fileInput = document.getElementById('profileImage') as HTMLInputElement;
    if (fileInput) {
      fileInput.value = '';
    }
  }

  setRole(role: 'user' | 'artist') {
    this.activeRole = role;
    this.registerForm.patchValue({ role });

    this.selectedGenres = [];
    this.registerForm.get('genres')?.setValue([]);

    this.registerForm.get('genres')?.clearValidators();
    this.registerForm.get('bio')?.clearValidators();
    this.registerForm.get('stageName')?.clearValidators();

    if (role === 'artist') {
      this.registerForm.get('genres')?.setValidators([Validators.required]);
      this.registerForm.get('bio')?.setValidators([Validators.required, Validators.minLength(10)]);
      this.registerForm.get('stageName')?.setValidators([Validators.required, Validators.minLength(3)]);
    }

    this.registerForm.get('genres')?.updateValueAndValidity();
    this.registerForm.get('bio')?.updateValueAndValidity();
    this.registerForm.get('stageName')?.updateValueAndValidity();
  }

  onGenreCheckboxChange(genreId: number, subGenreId: number, event: Event) {
    const isChecked = (event.target as HTMLInputElement).checked;

    if (isChecked) {
      const existingGenre = this.selectedGenres.find(g => g.genreId === genreId);
      if (existingGenre) {
        if (!existingGenre.subGenreIds.includes(subGenreId)) {
          existingGenre.subGenreIds.push(subGenreId);
        }
      } else {
        this.selectedGenres.push({ genreId, subGenreIds: [subGenreId] });
      }
    } else {
      const genreIndex = this.selectedGenres.findIndex(g => g.genreId === genreId);
      if (genreIndex !== -1) {
        const subGenreIndex = this.selectedGenres[genreIndex].subGenreIds.indexOf(subGenreId);
        if (subGenreIndex !== -1) {
          this.selectedGenres[genreIndex].subGenreIds.splice(subGenreIndex, 1);
          if (this.selectedGenres[genreIndex].subGenreIds.length === 0) {
            this.selectedGenres.splice(genreIndex, 1);
          }
        }
      }
    }

    this.registerForm.get('genres')?.setValue(this.selectedGenres);
  }

  isSubGenreSelected(genreId: number, subGenreId: number): boolean {
    const genre = this.selectedGenres.find(g => g.genreId === genreId);
    return genre ? genre.subGenreIds.includes(subGenreId) : false;
  }

  getSelectedGenresText(): string {
    if (this.selectedGenres.length === 0) {
      return 'No genres selected';
    }
    const genreNames: string[] = [];
    this.selectedGenres.forEach(selectedGenre => {
      const genre = this.genres.find(g => g.id === selectedGenre.genreId);
      if (genre) {
        const subGenreNames = selectedGenre.subGenreIds.map(subId => {
          const subGenre = genre.categories.find(c => c.id === subId);
          return subGenre ? subGenre.name : '';
        }).filter(name => name);
        if (subGenreNames.length > 0) {
          genreNames.push(`${genre.name}: ${subGenreNames.join(', ')}`);
        }
      }
    });
    return genreNames.join(' | ');
  }

  onSubmit() {
    this.submitted = true;
    this.errorMessage = '';

    if (!this.selectedFile) {
      this.errorMessage = 'Please upload a profile picture';
      return;
    }

    if (this.activeRole === 'artist' && this.selectedGenres.length === 0) {
      this.errorMessage = 'Please select at least one genre';
      return;
    }

    if (this.registerForm.invalid) {
      return;
    }

    this.isSubmitting = true;

    const formData = new FormData();
    formData.append('fullName', this.registerForm.get('fullName')?.value);
    formData.append('userEmail', this.registerForm.get('userEmail')?.value);
    formData.append('password', this.registerForm.get('password')?.value);
    formData.append('phoneNumber', this.registerForm.get('phoneNumber')?.value);
    formData.append('location', this.registerForm.get('location')?.value);
    formData.append('profileImage', this.selectedFile!, this.selectedFile!.name);

    if (this.activeRole === 'artist') {
      this.selectedGenres.forEach((genre, index) => {
        formData.append(`genres[${index}].genreId`, genre.genreId.toString());
        genre.subGenreIds.forEach((subGenreId, subIndex) => {
          formData.append(`genres[${index}].subGenreIds[${subIndex}]`, subGenreId.toString());
        });
      });
      formData.append('bio', this.registerForm.get('bio')?.value || '');
      formData.append('stageName', this.registerForm.get('stageName')?.value || '');

      this.authService.registerArtist(formData).subscribe({
        next: () => {
          this.isSubmitting = false;
          this.toast.success('Registration successful');
          this.router.navigate(['/auth/verify-email'], { queryParams: { email: this.registerForm.value.userEmail } });
        },
        error: (err) => {
          this.isSubmitting = false;
          this.toast.error(err.error?.message || 'Registration failed. Please try again.');
        }
      });
    } else {
      this.authService.registerUser(formData).subscribe({
        next: () => {
          this.isSubmitting = false;
          this.toast.success('Registration successful');
            this.router.navigate(['/auth/verify-email'], { queryParams: { email: this.registerForm.value.userEmail } });

        },
        error: (err) => {
          this.isSubmitting = false;
          this.toast.error(err.error?.message || 'Registration failed. Please try again.');
        }
      });
    }
  }
}
