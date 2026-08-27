/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      // Mapped onto the CSS custom properties in styles.scss, so a token is
      // defined once and reachable from both a utility class and hand-written CSS.
      colors: {
        ink: 'rgb(var(--omh-ink) / <alpha-value>)',
        'ink-soft': 'rgb(var(--omh-ink-soft) / <alpha-value>)',
        muted: 'rgb(var(--omh-muted) / <alpha-value>)',
        surface: 'rgb(var(--omh-surface) / <alpha-value>)',
        'surface-2': 'rgb(var(--omh-surface-2) / <alpha-value>)',
        ground: 'rgb(var(--omh-ground) / <alpha-value>)',
        line: 'rgb(var(--omh-line) / <alpha-value>)',
        brand: {
          DEFAULT: 'rgb(var(--omh-brand) / <alpha-value>)',
          soft: 'rgb(var(--omh-brand-soft) / <alpha-value>)',
          strong: 'rgb(var(--omh-brand-strong) / <alpha-value>)',
        },
        stage: {
          DEFAULT: 'rgb(var(--omh-stage) / <alpha-value>)',
          soft: 'rgb(var(--omh-stage-soft) / <alpha-value>)',
        },
        hot: {
          DEFAULT: 'rgb(var(--omh-hot) / <alpha-value>)',
          soft: 'rgb(var(--omh-hot-soft) / <alpha-value>)',
        },
        // The dashboard's single warm accent. Defined per theme in styles.scss.
        accent: {
          DEFAULT: 'rgb(var(--omh-accent) / <alpha-value>)',
          soft: 'rgb(var(--omh-accent-soft) / <alpha-value>)',
          ink: 'rgb(var(--omh-accent-ink) / <alpha-value>)',
        },
        // Categorical series colours, for charts and legends.
        cat: {
          1: 'rgb(var(--omh-cat-1) / <alpha-value>)',
          2: 'rgb(var(--omh-cat-2) / <alpha-value>)',
          3: 'rgb(var(--omh-cat-3) / <alpha-value>)',
          4: 'rgb(var(--omh-cat-4) / <alpha-value>)',
          5: 'rgb(var(--omh-cat-5) / <alpha-value>)',
          6: 'rgb(var(--omh-cat-6) / <alpha-value>)',
          7: 'rgb(var(--omh-cat-7) / <alpha-value>)',
          8: 'rgb(var(--omh-cat-8) / <alpha-value>)',
        },
        'stage-ink': 'rgb(var(--omh-stage-ink) / <alpha-value>)',
        'stage-ink-2': 'rgb(var(--omh-stage-ink-2) / <alpha-value>)',
        'on-stage': 'rgb(var(--omh-on-stage) / <alpha-value>)',
        'on-stage-dim': 'rgb(var(--omh-on-stage-dim) / <alpha-value>)',
        positive: {
          DEFAULT: 'rgb(var(--omh-positive) / <alpha-value>)',
          soft: 'rgb(var(--omh-positive-soft) / <alpha-value>)',
        },
        warning: {
          DEFAULT: 'rgb(var(--omh-warning) / <alpha-value>)',
          soft: 'rgb(var(--omh-warning-soft) / <alpha-value>)',
        },
        critical: {
          DEFAULT: 'rgb(var(--omh-critical) / <alpha-value>)',
          soft: 'rgb(var(--omh-critical-soft) / <alpha-value>)',
        },
      },
      fontFamily: {
        display: ['Archivo', 'system-ui', 'sans-serif'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      // The one type scale for the product. Components pick a step from this
      // list; nothing writes `text-[13.5px]` any more. Every step carries its
      // own line-height so vertical rhythm does not drift between screens.
      fontSize: {
        micro: ['0.6875rem', { lineHeight: '1rem', letterSpacing: '0.02em' }], // 11
        xs: ['0.75rem', { lineHeight: '1.125rem' }], // 12
        sm: ['0.8125rem', { lineHeight: '1.25rem' }], // 13
        base: ['0.875rem', { lineHeight: '1.375rem' }], // 14
        md: ['0.9375rem', { lineHeight: '1.5rem' }], // 15
        lg: ['1.0625rem', { lineHeight: '1.5rem' }], // 17
        xl: ['1.25rem', { lineHeight: '1.75rem' }], // 20
        '2xl': ['1.5rem', { lineHeight: '1.875rem', letterSpacing: '-0.02em' }],
        '3xl': ['1.875rem', { lineHeight: '2.25rem', letterSpacing: '-0.025em' }],
        '4xl': ['2.375rem', { lineHeight: '2.625rem', letterSpacing: '-0.03em' }],
        '5xl': ['3rem', { lineHeight: '3.125rem', letterSpacing: '-0.032em' }],
        '6xl': ['3.75rem', { lineHeight: '3.875rem', letterSpacing: '-0.035em' }],
      },
      borderRadius: {
        card: '6px',
        pill: '999px',
      },
      boxShadow: {
        soft: '0 1px 1px rgb(16 12 30 / 0.03)',
        lift: '0 1px 2px rgb(16 12 30 / 0.06), 0 18px 36px -14px rgb(16 12 30 / 0.28)',
      },
      // Ordered so each layer sits above what it is meant to cover. The scrim
      // dims the content and the topbar but stays under the rail and the
      // drawer — it used to outrank the drawer, which dimmed the very panel it
      // was opened to reveal.
      zIndex: {
        topbar: '30',
        scrim: '35',
        rail: '45',
        sidebar: '46',
        overlay: '50',
        modal: '60',
      },
      keyframes: {
        'rise-in': {
          '0%': { opacity: '0', transform: 'translateY(14px) scale(.985)' },
          '100%': { opacity: '1', transform: 'translateY(0) scale(1)' },
        },
        'slide-up': {
          '0%': { opacity: '0', transform: 'translateY(6px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'fade-in': {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        'menu-in': {
          '0%': { opacity: '0', transform: 'translateY(-4px) scale(.97)' },
          '100%': { opacity: '1', transform: 'translateY(0) scale(1)' },
        },
        shimmer: { '100%': { transform: 'translateX(100%)' } },
        sweep: {
          '0%': { transform: 'translateX(-120%) skewX(-12deg)' },
          '100%': { transform: 'translateX(320%) skewX(-12deg)' },
        },
      },
      animation: {
        'rise-in': 'rise-in .5s cubic-bezier(.16,.84,.32,1) both',
        'slide-up': 'slide-up .3s cubic-bezier(.2,.7,.3,1) both',
        'fade-in': 'fade-in .2s ease both',
        'menu-in': 'menu-in .14s cubic-bezier(.2,.7,.3,1) both',
        shimmer: 'shimmer 1.5s infinite',
        sweep: 'sweep 1.1s cubic-bezier(.4,0,.2,1)',
      },
    },
  },
  plugins: [],
};
