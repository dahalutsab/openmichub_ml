import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ArtistService } from '../../artist.service';

export interface ChecklistItem {
  key: string;
  label: string;
  hint: string;
  action: string;
  weight: number;
  done: boolean;
  blocking: boolean;
}

export interface Completeness {
  score: number;
  complete: boolean;
  bookable: boolean;
  items: ChecklistItem[];
}

/**
 * What an artist still has to do, on the board they open first.
 *
 * A bare list of empty fields would be busywork, so each item says what it changes: which ones
 * feed search, which one decides whether a booking can be accepted at all. The blocking item is
 * called out separately because it is a different kind of gap — an artist with no published hours
 * is not merely less visible, they cannot be booked, and nothing else on the list compensates.
 *
 * Disappears once everything is done. A checklist that stays on screen after it is finished is
 * just clutter on the page an artist looks at every day.
 */
@Component({
  selector: 'app-profile-completeness',
  standalone: false,
  templateUrl: './profile-completeness.component.html',
})
export class ProfileCompletenessComponent implements OnInit {
  private readonly artistService = inject(ArtistService);

  readonly loading = signal(true);
  readonly data = signal<Completeness | null>(null);

  /** Only what is left to do; finished items are counted in the score, not listed again. */
  readonly remaining = computed(() => this.data()?.items.filter(item => !item.done) ?? []);

  readonly blocker = computed(() => this.remaining().find(item => item.blocking) ?? null);

  /** Everything else, heaviest first, so the most worthwhile step reads as the next one. */
  readonly suggestions = computed(() =>
    this.remaining().filter(item => !item.blocking).sort((a, b) => b.weight - a.weight));

  readonly show = computed(() => {
    const data = this.data();
    return !this.loading() && !!data && !data.complete;
  });

  ngOnInit(): void {
    this.artistService.getProfileCompleteness().subscribe({
      next: (response: any) => {
        this.data.set(response?.data ?? null);
        this.loading.set(false);
      },
      error: () => {
        // A checklist is an aid, not part of the dashboard's job. If it cannot be fetched the
        // card stays hidden rather than showing an error where advice should be.
        this.loading.set(false);
      },
    });
  }
}
