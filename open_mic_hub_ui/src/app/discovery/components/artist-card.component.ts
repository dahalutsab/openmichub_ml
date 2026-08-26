import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

import { ArtistHit } from '../discovery.service';

/**
 * One artist in a result list.
 *
 * Kept separate so search, recommendations and any future "similar artists"
 * strip render an artist identically rather than each growing its own card.
 */
@Component({
  selector: 'omh-artist-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './artist-card.component.html',
})
export class ArtistCardComponent {
  @Input({ required: true }) artist!: ArtistHit;

  /** Position in the list, 1-based. Only the top few are badged. */
  @Input() rank?: number;

  get initials(): string {
    return (this.artist.stageName || '?')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map(word => word[0]?.toUpperCase() ?? '')
      .join('');
  }

  /** Whole stars to fill, so the row reads at a glance. */
  get stars(): boolean[] {
    const rounded = Math.round(this.artist.rating);
    return Array.from({ length: 5 }, (_, index) => index < rounded);
  }

  /**
   * How closely the artist matched the words of the query.
   *
   * Only shown for text searches. Deliberately not shown as the ranking score:
   * that number is a model output on an arbitrary scale and means nothing to
   * someone booking a band.
   */
  get matchPercent(): number | null {
    if (this.artist.similarity == null) return null;
    return Math.round(Math.min(1, Math.max(0, this.artist.similarity)) * 100);
  }

  get genres(): string[] {
    const subs = this.artist.subGenres?.filter(Boolean) ?? [];
    return subs.length ? subs.slice(0, 3) : (this.artist.parentGenres ?? []).slice(0, 3);
  }
}
