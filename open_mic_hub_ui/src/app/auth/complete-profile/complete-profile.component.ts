import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../auth.service';
import { Role } from '../../shared/role';

interface SubGenre { id: number; name: string; }
interface Genre { id: number; name: string; categories: SubGenre[]; }

/**
 * The one question a social sign-in cannot answer for itself.
 *
 * Signing up with a password means choosing a form, and the artist form collects a stage name,
 * a bio and genres on the way through. A provider hands over a name, an address and a picture and
 * nothing else, so this is asked once, on the first sign-in, and never again.
 */
@Component({
  selector: 'app-complete-profile',
  standalone: false,
  templateUrl: './complete-profile.component.html',
})
export class CompleteProfileComponent implements OnInit {
  performing: boolean | null = null;
  isSaving = false;

  stageName = '';
  bio = '';
  location = '';
  hourlyRate: number | null = null;

  genres: Genre[] = [];
  selectedSubGenres = new Set<number>();

  constructor(
    private authService: AuthService,
    private router: Router,
    private toast: ToastrService,
  ) {}

  ngOnInit(): void {
    // Someone who has already answered has no business here — send them on rather than letting
    // them submit into a conflict.
    this.authService.profileCompletionStatus().subscribe({
      next: (response: any) => {
        if (response?.data?.onboardingRequired === false) {
          this.router.navigate(['/']);
        }
      },
      error: () => this.router.navigate(['/auth/login']),
    });

    this.authService.genres().subscribe({
      next: (response: any) => (this.genres = response?.data ?? []),
      error: () => (this.genres = []),
    });
  }

  choose(performing: boolean): void {
    this.performing = performing;
  }

  toggleSubGenre(id: number): void {
    if (this.selectedSubGenres.has(id)) {
      this.selectedSubGenres.delete(id);
    } else {
      this.selectedSubGenres.add(id);
    }
  }

  get canSubmit(): boolean {
    if (this.performing === null || this.isSaving) return false;
    if (!this.performing) return true;
    return this.stageName.trim().length > 0 && this.selectedSubGenres.size > 0;
  }

  submit(): void {
    if (!this.canSubmit) return;
    this.isSaving = true;

    // Sub-genres are sent grouped under their parent, which is the shape the artist registration
    // form already uses.
    const genres = this.genres
      .map(genre => ({
        genreId: genre.id,
        subGenreIds: (genre.categories ?? [])
          .map(sub => sub.id)
          .filter(id => this.selectedSubGenres.has(id)),
      }))
      .filter(entry => entry.subGenreIds.length > 0);

    this.authService
      .completeProfile({
        performing: this.performing,
        stageName: this.performing ? this.stageName.trim() : null,
        bio: this.performing ? this.bio.trim() : null,
        location: this.location.trim() || null,
        hourlyRate: this.performing ? this.hourlyRate : null,
        genres: this.performing ? genres : null,
      })
      .subscribe({
        next: (response: any) => {
          const roles: string[] = response?.data?.roles ?? [];
          localStorage.setItem('urole', JSON.stringify(roles));
          this.isSaving = false;
          this.redirectUser(roles);
        },
        error: (error) => {
          this.isSaving = false;
          this.toast.error(error?.error?.message ?? 'Could not save that. Please try again.');
        },
      });
  }

  private redirectUser(roles: string[]): void {
    if (roles.includes(Role.ARTIST)) {
      this.router.navigate(['/artist/']);
    } else if (roles.includes(Role.ORGANIZER) || roles.includes(Role.USER)) {
      this.router.navigate(['/user']);
    } else {
      this.router.navigate(['/']);
    }
  }
}
