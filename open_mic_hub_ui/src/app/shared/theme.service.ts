import { Inject, Injectable, PLATFORM_ID, effect, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

export type ThemeChoice = 'dark' | 'light' | 'system';

const STORAGE_KEY = 'omh-theme';

/**
 * Light/dark theme for the dashboard.
 *
 * The choice is a signal, and an effect writes `data-theme` onto <html>; every
 * colour in the product resolves through tokens defined for both themes, so
 * nothing else has to react to a change.
 *
 * Three states rather than two: `system` follows the OS and is the default, so
 * a first-time visitor gets whichever they already prefer. Only an explicit
 * pick is persisted.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly choice = signal<ThemeChoice>('system');

  /** What is actually painted right now — `system` resolved against the OS. */
  readonly resolved = signal<'dark' | 'light'>('dark');

  private media?: MediaQueryList;

  constructor(@Inject(PLATFORM_ID) private platformId: Object) {
    if (isPlatformBrowser(this.platformId)) {
      this.choice.set(this.readStored());

      this.media = window.matchMedia('(prefers-color-scheme: light)');
      // Re-resolve when the OS flips, but only while following it.
      this.media.addEventListener('change', () => {
        if (this.choice() === 'system') {
          this.apply();
        }
      });

      effect(() => {
        this.choice();
        this.apply();
      });
    }
  }

  /** Cycles dark → light → system, which is what the navbar control does. */
  toggle(): void {
    const next: Record<ThemeChoice, ThemeChoice> = {
      dark: 'light',
      light: 'system',
      system: 'dark',
    };
    this.set(next[this.choice()]);
  }

  set(choice: ThemeChoice): void {
    this.choice.set(choice);
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    try {
      if (choice === 'system') {
        localStorage.removeItem(STORAGE_KEY);
      } else {
        localStorage.setItem(STORAGE_KEY, choice);
      }
    } catch {
      // Private browsing and blocked site data both throw here. The theme still
      // applies for this page; it just will not be remembered.
    }
  }

  /** Icon for the current choice, so the control shows what is actually set. */
  get icon(): string {
    switch (this.choice()) {
      case 'dark': return 'bi-moon-stars-fill';
      case 'light': return 'bi-sun-fill';
      default: return 'bi-circle-half';
    }
  }

  get label(): string {
    switch (this.choice()) {
      case 'dark': return 'Dark';
      case 'light': return 'Light';
      default: return 'System';
    }
  }

  private apply(): void {
    const choice = this.choice();
    const resolved: 'dark' | 'light' =
      choice === 'system' ? (this.media?.matches ? 'light' : 'dark') : choice;

    this.resolved.set(resolved);
    document.documentElement.setAttribute('data-theme', resolved);
    document.documentElement.style.colorScheme = resolved;
  }

  private readStored(): ThemeChoice {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      return stored === 'dark' || stored === 'light' ? stored : 'system';
    } catch {
      return 'system';
    }
  }
}
