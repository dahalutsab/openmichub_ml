import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { UserService } from '../../user.service';
import { environment } from '../../../environment/environment';
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
  similar: any[] = [];

  loading = true;
  error = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: UserService,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.params['id']);
    this.artistId = Number.isNaN(id) ? null : id;

    if (this.artistId === null) {
      this.error = 'No artist was specified.';
      this.loading = false;
      return;
    }

    // Artist ids and user ids are separate sequences, so this reads the public
    // artist endpoint rather than getUserById — passing an artist id there
    // fetches a different person entirely.
    this.http.get<any>(`${environment.baseUrl}/public/artists/${this.artistId}`).subscribe({
      next: (res: any) => {
        this.artist = res?.data ?? null;
        this.loading = false;
        this.loadAvailability();
        this.loadSimilar();
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

  /**
   * Nearest neighbours from the segmentation model, via the API's proxy.
   *
   * Best-effort: the strip is hidden when the ML service is unavailable rather
   * than surfacing an error, since the profile itself is unaffected.
   */
  private loadSimilar(): void {
    if (this.artistId === null) {
      return;
    }
    this.http
      .get<any>(`${environment.baseUrl}/discover/artists/${this.artistId}/similar`, {
        params: { limit: 6 },
      })
      .subscribe({
        next: res => (this.similar = res?.data?.similar ?? []),
        error: () => (this.similar = []),
      });
  }

  book(): void {
    this.router.navigate(['/user/artists']);
  }

  get genres(): string[] {
    const raw = this.artist?.genre ?? this.artist?.genres ?? [];
    return raw.map((g: any) => g?.name ?? g).filter(Boolean);
  }

  /** Sub-genres across every parent genre, for the at-a-glance panel. */
  get styles(): string[] {
    const raw = this.artist?.genre ?? this.artist?.genres ?? [];
    return raw.flatMap((g: any) => (g?.categories ?? []).map((c: any) => c?.name))
      .filter(Boolean)
      .slice(0, 6);
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
