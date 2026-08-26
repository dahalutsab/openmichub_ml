import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

import { ArtistHit } from '../discovery.service';

/**
 * One act in the lineup.
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

  /** Position in the list, 1-based. Drives the numeral on the artwork. */
  @Input() rank?: number;

  /** 'grid' shows artwork; 'list' is a dense row for scanning many at once. */
  @Input() layout: 'grid' | 'list' = 'grid';

  /** Raised when the card is clicked or activated from the keyboard. */
  @Output() open = new EventEmitter<ArtistHit>();

  /**
   * The card is a control, so it answers to Enter and Space as well as a click.
   * It carries tabindex already; without this the keyboard could focus it and
   * then do nothing with it.
   */
  activate(event?: Event): void {
    event?.preventDefault();
    this.open.emit(this.artist);
  }

  get initials(): string {
    return (this.artist.stageName || '?')
      .split(/\s+/)
      .filter(word => /[a-z0-9]/i.test(word[0] ?? ''))
      .slice(0, 2)
      .map(word => word[0].toUpperCase())
      .join('');
  }

  /**
   * Cover artwork, derived from the artist id.
   *
   * Real profile photos are rare on a new platform, and a wall of identical grey
   * monograms makes the page look unfinished. Every act gets a stable sleeve
   * instead — the same artist is always the same colour, across sessions and
   * across screens.
   *
   * Drawn from a fixed palette rather than a hashed hue. Free hue rotation puts
   * lime next to magenta and the grid reads as noise; a curated set of duotones
   * held to a similar depth reads as a designed series, the way a label's back
   * catalogue does.
   */
  private static readonly SLEEVES: [string, string][] = [
    ['#3B2E6E', '#8B3A62'],  // indigo → plum
    ['#12363F', '#2B7A6F'],  // deep teal → sea
    ['#4A2418', '#A85434'],  // umber → rust
    ['#1E2A4A', '#4F6DA8'],  // navy → steel
    ['#3E1D2C', '#B04A57'],  // maroon → clay
    ['#233524', '#5C8A4A'],  // forest → moss
    ['#2C2440', '#6B5AA8'],  // aubergine → violet
    ['#402E12', '#C08A2E'],  // bronze → amber
    ['#1A2C36', '#3F7E96'],  // slate → cyan
    ['#361F3B', '#8E4B95'],  // mulberry → orchid
    ['#14232E', '#356F8C'],  // ink → petrol
    ['#43231E', '#96453B'],  // oxblood → brick
  ];

  get coverStyle(): Record<string, string> {
    const sleeves = ArtistCardComponent.SLEEVES;
    const id = this.artist.artistId ?? 0;
    const [dark, light] = sleeves[id % sleeves.length];
    // Two ids apart in the palette also differ in light direction, so a run of
    // consecutive artists does not look like one repeated tile.
    const flipped = Math.floor(id / sleeves.length) % 2 === 1;

    return {
      'background-image': [
        `radial-gradient(105% 105% at ${flipped ? '82% 88%' : '18% 12%'}, ${light} 0%, transparent 62%)`,
        `linear-gradient(${flipped ? '215deg' : '145deg'}, ${dark}, ${light} 165%)`,
      ].join(','),
      'background-color': dark,
    };
  }

  get stars(): boolean[] {
    const rounded = Math.round(this.artist.rating);
    return Array.from({ length: 5 }, (_, index) => index < rounded);
  }

  /**
   * How closely the act matched the words of the query.
   *
   * Deliberately not the ranking score: that is a model output on an arbitrary
   * scale and means nothing to someone booking a band.
   */
  get matchPercent(): number | null {
    if (this.artist.similarity == null) return null;
    return Math.round(Math.min(1, Math.max(0, this.artist.similarity)) * 100);
  }

  get genres(): string[] {
    const subs = this.artist.subGenres?.filter(Boolean) ?? [];
    return subs.length ? subs.slice(0, 2) : (this.artist.parentGenres ?? []).slice(0, 2);
  }
}
