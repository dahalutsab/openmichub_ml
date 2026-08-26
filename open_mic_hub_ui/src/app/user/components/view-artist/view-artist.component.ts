import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { UserService } from '../../user.service';
import { AVATAR_FALLBACK } from '../../../shared/avatar';

/**
 * An artist's public profile, seen by a booker.
 *
 * Was the CLI stub. Reads the artist through the existing user endpoint and
 * their published availability, so a booker can see who they are and when they
 * can play before raising a request.
 */
@Component({
  selector: 'app-view-artist',
  standalone: false,
  templateUrl: './view-artist.component.html',
})
export class ViewArtistComponent implements OnInit {
  readonly fallbackAvatar = AVATAR_FALLBACK;

  artistId: number | null = null;
  artist: any = null;
  availability: any[] = [];

  loading = true;
  error = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: UserService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.params['id']);
    this.artistId = Number.isNaN(id) ? null : id;

    if (this.artistId === null) {
      this.error = 'No artist was specified.';
      this.loading = false;
      return;
    }

    this.service.getUserById(this.artistId).subscribe({
      next: (res: any) => {
        this.artist = res?.data ?? null;
        this.loading = false;
        this.loadAvailability();
      },
      error: err => {
        console.error('Failed to load artist', err);
        this.error = 'Could not load this artist.';
        this.loading = false;
      },
    });
  }

  /**
   * Availability is keyed by stage name on the API. It is best-effort: an
   * artist who has not published a schedule is not an error, just an empty
   * section.
   */
  private loadAvailability(): void {
    const stageName = this.artist?.stageName;
    if (!stageName) {
      return;
    }
    this.service.getArtistAvailability(stageName).subscribe({
      next: (res: any) => (this.availability = res?.data?.availabilities ?? []),
      error: () => (this.availability = []),
    });
  }

  book(): void {
    this.router.navigate(['/user/artists']);
  }

  get genres(): string[] {
    const raw = this.artist?.genre ?? this.artist?.genres ?? [];
    return raw.map((g: any) => g?.name ?? g).filter(Boolean);
  }

  get stars(): number[] {
    return [1, 2, 3, 4, 5];
  }

  starClass(position: number): string {
    const rating = this.artist?.rating ?? 0;
    if (rating >= position) return 'bi-star-fill';
    if (rating >= position - 0.5) return 'bi-star-half';
    return 'bi-star';
  }

  shortTime(time: string | null | undefined): string {
    if (!time) return '';
    const [hourStr, minute] = time.split(':');
    const hour = parseInt(hourStr, 10);
    if (Number.isNaN(hour)) return time;
    const suffix = hour >= 12 ? 'PM' : 'AM';
    const display = hour % 12 === 0 ? 12 : hour % 12;
    return `${display}:${minute ?? '00'} ${suffix}`;
  }

  pretty(value: string | undefined): string {
    return (value || '')
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, c => c.toUpperCase());
  }
}
