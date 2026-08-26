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
        stage: 'rgb(var(--omh-stage) / <alpha-value>)',
        positive: 'rgb(var(--omh-positive) / <alpha-value>)',
        warning: 'rgb(var(--omh-warning) / <alpha-value>)',
        critical: 'rgb(var(--omh-critical) / <alpha-value>)',
      },
      fontFamily: {
        display: ['Sora', 'system-ui', 'sans-serif'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        card: '14px',
        pill: '999px',
      },
      boxShadow: {
        soft: '0 1px 2px rgb(16 12 30 / 0.04), 0 4px 16px -4px rgb(16 12 30 / 0.08)',
        lift: '0 2px 4px rgb(16 12 30 / 0.05), 0 12px 32px -8px rgb(16 12 30 / 0.16)',
      },
      keyframes: {
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        shimmer: {
          '100%': { transform: 'translateX(100%)' },
        },
      },
      animation: {
        'fade-up': 'fade-up .35s cubic-bezier(.2,.7,.3,1) both',
        shimmer: 'shimmer 1.6s infinite',
      },
    },
  },
  corePlugins: {
    // Bootstrap and Angular Material already own the base layer. Tailwind's
    // preflight would reset both out from under every existing screen, so this
    // adds utilities alongside them rather than replacing them.
    preflight: false,
  },
  plugins: [],
};
