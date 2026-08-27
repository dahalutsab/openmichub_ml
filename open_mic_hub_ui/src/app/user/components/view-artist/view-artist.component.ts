import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ToastrService } from 'ngx-toastr';
import { switchMap, tap } from 'rxjs';
import { UserService } from '../../user.service';
import { environment } from '../../../environment/environment';
import { AVATAR_FALLBACK } from '../../../shared/avatar';
import { ReviewListComponent } from '../../../shared/reviews';
import { BookingDialogComponent } from '../../../shared/booking/booking-dialog.component';
import { canBook, isSignedIn } from '../../../shared/session';

/**
 * An artist's public profile.
 *
 * Standalone and routed publicly, because browsing is what brings organizers to
 * the platform — it used to live behind the booker guard, so a visitor
 * following a link from discovery was bounced to the login screen before seeing
 * anything. Signing in is required to book, not to look.
 */
@Component({
  selector: 'app-view-artist',
  standalone: true,
  imports: [CommonModule, RouterModule, ReviewListComponent, BookingDialogComponent],
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
  booking = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: UserService,
    private http: HttpClient,
    private toast: ToastrService
  ) {}

  ngOnInit(): void {
    /**
     * Reacts to the id changing, not just to the component being created.
     *
     * The similar-artists strip links to this same route with a different id.
     * Angular reuses the component for that, so reading a snapshot in ngOnInit
     * navigated the URL and left the previous artist on screen.
     */
    this.route.paramMap
      .pipe(
        tap(() => {
          this.loading = true;
          this.error = '';
          // Cleared so the previous artist is not briefly shown under the new
          // one's heading while the request is in flight.
          this.artist = null;
          this.availability = [];
          this.similar = [];
          this.booking = false;
        }),
        switchMap(params => {
          const id = Number(params.get('id'));
          this.artistId = Number.isNaN(id) ? null : id;
          return this.http.get<any>(`${environment.baseUrl}/public/artists/${this.artistId}`);
        })
      )
      .subscribe({
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
   * Availability is keyed by stage name on the API. Best-effort: an artist who
   * has not published a schedule is not an error, just an empty section.
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

  /**
   * Opens the booking dialog, or sends an unauthenticated visitor to sign in
   * and come straight back here.
   */
  book(): void {
    if (!isSignedIn()) {
      this.toast.info('Sign in to request a booking.');
      this.router.navigate(['/auth/login'], {
        queryParams: { returnUrl: this.router.url },
      });
      return;
    }

    if (!canBook()) {
      this.toast.error('Only organizer accounts can raise a booking.');
      return;
    }

    this.booking = true;
  }

  onBooked(): void {
    this.booking = false;
  }

  /**
   * The artist's photo.
   *
   * The public DTO calls this `profilePictureUrl`; the template used to read
   * `profilePicture`/`profileImage`, so every profile silently fell back to the
   * placeholder. The other two keys stay because discovery spells it differently.
   */
  get avatar(): string {
    return (
      this.artist?.profilePictureUrl ||
      this.artist?.profilePicture ||
      this.artist?.profileImage ||
      this.fallbackAvatar
    );
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

  /** Whether there is a real rating, as opposed to an artist nobody has rated. */
  get hasRating(): boolean {
    return typeof this.artist?.rating === 'number' && this.artist.rating > 0;
  }

  /** The rating to one decimal, rounded exactly as Angular's number pipe rounds it. */
  get ratingLabel(): string {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    }).format(this.artist?.rating ?? 0);
  }

  /**
   * The credibility strip under the name.
   *
   * Assembled here rather than in the template so an artist missing a stat gets
   * a shorter strip instead of a row of dashes: someone new should read as new,
   * not as broken.
   */
  get proof(): { icon: string; value: string; label: string }[] {
    const out: { icon: string; value: string; label: string }[] = [];
    const a = this.artist;
    if (!a) return out;

    if (this.hasRating) {
      // Formatted the way Angular's number pipe formats it elsewhere. toFixed(1)
      // rounds 3.65 down to "3.6" where the pipe gives "3.7", so the hero and the
      // reviews section disagreed about the same artist's rating.
      out.push({ icon: 'bi-star-fill', value: this.ratingLabel, label: 'rating' });
    }
    if (a.completedBookings > 0) {
      out.push({
        icon: 'bi-music-note-beamed',
        value: String(a.completedBookings),
        label: a.completedBookings === 1 ? 'gig played' : 'gigs played',
      });
    }
    if (typeof a.responseRate === 'number') {
      out.push({ icon: 'bi-reply-fill', value: Math.round(a.responseRate) + '%', label: 'responds' });
    }
    if (a.city) {
      out.push({ icon: 'bi-geo-alt-fill', value: a.city, label: 'based in' });
    }
    return out;
  }

  /** "Mar 2024", or empty when the join date is unknown. */
  get memberSince(): string {
    const raw = this.artist?.memberSince;
    if (!raw) return '';
    const date = new Date(raw);
    if (Number.isNaN(date.getTime())) return '';
    return date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
  }

  /** Two letters for the avatar when there is no photo. */
  get initials(): string {
    const name = this.artist?.stageName || this.artist?.fullName || '';
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((word: string) => word[0])
      .join('')
      .toUpperCase();
  }

  /** Total published slots, so the schedule can be summarised in its header. */
  get slotCount(): number {
    return this.availability.reduce(
      (total, day) => total + (day?.availabilityTimes?.length ?? 0),
      0
    );
  }

  /** "18:30:00" as "6:30 PM". The API sends a bare LocalTime, not a date. */
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
