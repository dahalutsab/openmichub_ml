import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import {Genre, GenreResponse, LandingService} from '../landing.service';
import { interval, Subscription } from 'rxjs';
import { AVATAR_FALLBACK } from '../shared/avatar';

@Component({
  selector: 'app-landing-page',
  standalone: false,
  templateUrl: './landing-page.component.html',
  styleUrls: ['./landing-page.component.scss']
})
export class LandingPageComponent implements OnInit, OnDestroy {
  readonly fallbackAvatar = AVATAR_FALLBACK;

  artists: any[] = [];
  featuredArtists: any[] = [];
  counts = { users: 0, artists: 0, bookings: 0 };
  loading = false;
  error: string | null = null;
  currentPage = 0;
  totalPages = 1;

  // New properties for enhanced features
  currentTestimonialIndex = 0;
  animatedCounts = { users: 0, artists: 0, bookings: 0 };
  countSubscription?: Subscription;
  testimonialInterval?: Subscription;

  /**
   * The three rows in the hero mock-up.
   *
   * A worked example of a real search beats a stock photograph: it shows what
   * the product does in the space a hero image would have occupied. Static on
   * purpose — the hero must render instantly and identically every visit,
   * rather than waiting on the ranking service.
   */
  heroSamples = [
    {
      name: 'Kalo Assembly',
      meta: 'Kathmandu · Bebop, Fusion',
      rate: '6,050',
      match: 62,
      sleeve: 'linear-gradient(145deg, #3E1D2C, #B04A57 165%)',
    },
    {
      name: 'The Ember Affair',
      meta: 'Kathmandu · Bebop, Swing',
      rate: '1,590',
      match: 60,
      sleeve: 'linear-gradient(145deg, #14232E, #356F8C 165%)',
    },
    {
      name: 'Trio Static Avenue',
      meta: 'Kathmandu · Swing',
      rate: '3,010',
      match: 58,
      sleeve: 'linear-gradient(145deg, #402E12, #C08A2E 165%)',
    },
  ];

  /** What the platform actually does, in the order it matters to a newcomer. */
  features = [
    {
      title: 'Search in plain language',
      body: 'Describe the mood, the room and the occasion. No genre dropdowns, ' +
            'no guessing what an act is called.',
    },
    {
      title: 'Ranked for your brief',
      body: 'Results are ordered by a trained model weighing relevance, budget, ' +
            'location, rating and track record together.',
    },
    {
      title: 'Real availability',
      body: 'Artists keep their own calendar and blackout dates, so what you see ' +
            'is what you can actually book.',
    },
    {
      title: 'Money held safely',
      body: 'Pay through Khalti. Earnings sit in the artist\'s wallet and are ' +
            'released on withdrawal, with every movement on a ledger.',
    },
    {
      title: 'Talk before you commit',
      body: 'Message an act directly to agree set length, equipment and the ' +
            'first-dance song.',
    },
    {
      title: 'Reviews that mean something',
      body: 'Only the organizer who made a booking can review it, once. ' +
            'Ratings you can rely on.',
    },
  ];

  /** Three steps, because that is genuinely how long it takes. */
  steps = [
    {
      title: 'Describe the night',
      body: 'Tell us the occasion, the city and roughly what you want to spend.',
    },
    {
      title: 'Compare the shortlist',
      body: 'See rates, ratings and past gigs side by side, ranked for your brief.',
    },
    {
      title: 'Book and pay',
      body: 'Confirm the date, pay in full or half up front, and get it in writing.',
    },
  ];

  // Sample recent performances data
  recentPerformances = [
    {
      artist: 'Aashish Rana',
      venue: 'Thamel Jazz Club',
      date: '2025-06-25',
      image: 'assets/performance1.jpg',
      rating: 4.8
    },
    {
      artist: 'Priya Shrestha',
      venue: 'Blue Note Café',
      date: '2025-06-24',
      image: 'assets/performance2.jpg',
      rating: 4.9
    },
    {
      artist: 'Raj Comedy Club',
      venue: 'Laughter Lounge',
      date: '2025-06-23',
      image: 'assets/performance3.jpg',
      rating: 4.7
    }
  ];

  // Enhanced testimonials with images
  testimonials = [
    {
      text: "OpenMicHub made booking performers for our venue so easy! The coin system is a game-changer.",
      name: "Sita R.",
      role: "Venue Owner",
      venue: "Thamel Jazz Club",
      image: "assets/testimonial1.jpg",
      rating: 5
    },
    {
      text: "As a comedian, I love how I can showcase my videos and manage my gigs all in one place.",
      name: "Ram K.",
      role: "Comedian",
      venue: "Laughter Lounge",
      image: "assets/testimonial2.jpg",
      rating: 5
    },
    {
      text: "The scheduling tools helped me avoid conflicts and focus on performing.",
      name: "Lila M.",
      role: "Singer",
      venue: "Blue Note Café",
      image: "assets/testimonial3.jpg",
      rating: 5
    },
    {
      text: "Finding quality artists for our events has never been easier. Highly recommended!",
      name: "Bikash T.",
      role: "Event Organizer",
      venue: "Kathmandu Events",
      image: "assets/testimonial4.jpg",
      rating: 5
    }
  ];

  // Pricing plans
  pricingPlans = [
    {
      name: 'Basic',
      price: 'Free',
      features: [
        'Create artist profile',
        'Browse available gigs',
        'Basic messaging',
        'Standard support'
      ],
      recommended: false
    },
    {
      name: 'Professional',
      price: '₹999/month',
      features: [
        'Everything in Basic',
        'Priority listing',
        'Advanced analytics',
        'Video portfolio uploads',
        'Priority support',
        'Custom branding'
      ],
      recommended: true
    },
    {
      name: 'Venue',
      price: '₹1499/month',
      features: [
        'Post unlimited gigs',
        'Artist management tools',
        'Booking calendar',
        'Payment processing',
        'Event promotion',
        'Dedicated account manager'
      ],
      recommended: false
    }
  ];

  genres: Genre[] = [];
  selectedGenre: string = '';
  searchTerm: string = '';
  searchTimeout: any;
  constructor(private router: Router, private landingService: LandingService) {}

  ngOnInit() {
    this.fetchGenres();
    this.fetchCounts();
    this.fetchArtists();
    this.startCountAnimation();
    this.startTestimonialRotation();
  }

  ngOnDestroy() {
    if (this.countSubscription) {
      this.countSubscription.unsubscribe();
    }
    if (this.testimonialInterval) {
      this.testimonialInterval.unsubscribe();
    }
  }

  fetchCounts() {
    this.loading = true;
    this.landingService.getCounts().subscribe({
      next: (response) => {
        this.counts = response.data;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load counts';
        this.loading = false;
        console.error(err);
      }
    });
  }

  fetchGenres() {
    this.landingService.getGenres().subscribe({
      next: (response) => {
        this.genres = response.data;
      },
      error: (err) => {
        console.error('Failed to load genres:', err);
      }
    });
  }

  // Update fetchArtists to include search and genre filter
  fetchArtists(page: number = 0) {
    this.loading = true;
    this.currentPage = page;
    this.landingService.getArtists(
      page,
      10,
      this.searchTerm,
      this.selectedGenre
    ).subscribe({
      next: (response) => {
        this.artists = response.data.content;
        this.featuredArtists = this.artists.slice(0, 3);
        this.totalPages = response.data.totalPages;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load artists';
        this.loading = false;
        console.error(err);
      }
    });
  }

  // Add search handler with debounce
  onSearch(event: any) {
    if (this.searchTimeout) {
      clearTimeout(this.searchTimeout);
    }

    this.searchTimeout = setTimeout(() => {
      this.searchTerm = event.target.value;
      this.currentPage = 0; // Reset to first page
      this.fetchArtists();
    }, 300);
  }

  // Add genre filter handler
  onGenreChange(event: any) {
    this.selectedGenre = event.target.value;
    this.currentPage = 0; // Reset to first page
    this.fetchArtists();
  }



  // Animate numbers counting up
  startCountAnimation() {
    const duration = 2000; // 2 seconds
    const steps = 60;
    const increment = duration / steps;

    this.countSubscription = interval(increment).subscribe(step => {
      const progress = Math.min(step / steps, 1);
      this.animatedCounts.users = Math.floor(this.counts.users * progress);
      this.animatedCounts.artists = Math.floor(this.counts.artists * progress);
      this.animatedCounts.bookings = Math.floor(this.counts.bookings * progress);

      if (progress >= 1 && this.countSubscription) {
        this.countSubscription.unsubscribe();
      }
    });
  }

  // Auto-rotate testimonials
  startTestimonialRotation() {
    this.testimonialInterval = interval(5000).subscribe(() => {
      this.currentTestimonialIndex = (this.currentTestimonialIndex + 1) % this.testimonials.length;
    });
  }

  // Manual testimonial navigation
  setTestimonial(index: number) {
    this.currentTestimonialIndex = index;
  }

  navigateTo(path: string) {
    this.router.navigate([`/${path}`]);
  }

  scrollToSection(sectionId: string) {
    const element = document.getElementById(sectionId);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' });
    }
  }

  prevPage() {
    if (this.currentPage > 0) {
      this.fetchArtists(this.currentPage - 1);
    }
  }

  nextPage() {
    if (this.currentPage < this.totalPages - 1) {
      this.fetchArtists(this.currentPage + 1);
    }
  }

  // New methods for enhanced features
  getStarArray(rating: number): number[] {
    return Array(Math.floor(rating)).fill(0);
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });
  }
}
