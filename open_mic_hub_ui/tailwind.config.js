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
        hot: {
          DEFAULT: 'rgb(var(--omh-hot) / <alpha-value>)',
          soft: 'rgb(var(--omh-hot-soft) / <alpha-value>)',
        },
        'stage-ink': 'rgb(var(--omh-stage-ink) / <alpha-value>)',
        'stage-ink-2': 'rgb(var(--omh-stage-ink-2) / <alpha-value>)',
        'on-stage': 'rgb(var(--omh-on-stage) / <alpha-value>)',
        'on-stage-dim': 'rgb(var(--omh-on-stage-dim) / <alpha-value>)',
        positive: 'rgb(var(--omh-positive) / <alpha-value>)',
        warning: 'rgb(var(--omh-warning) / <alpha-value>)',
        critical: 'rgb(var(--omh-critical) / <alpha-value>)',
      },
      fontFamily: {
        display: ['Archivo', 'system-ui', 'sans-serif'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        card: '6px',
        pill: '999px',
      },
      boxShadow: {
        soft: '0 1px 2px rgb(16 12 30 / 0.04), 0 4px 16px -4px rgb(16 12 30 / 0.08)',
        lift: '0 2px 4px rgb(16 12 30 / 0.05), 0 12px 32px -8px rgb(16 12 30 / 0.16)',
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
        shimmer: { '100%': { transform: 'translateX(100%)' } },
        'sweep': {
          '0%': { transform: 'translateX(-120%) skewX(-12deg)' },
          '100%': { transform: 'translateX(320%) skewX(-12deg)' },
        },
      },
      animation: {
        'rise-in': 'rise-in .5s cubic-bezier(.16,.84,.32,1) both',
        'slide-up': 'slide-up .3s cubic-bezier(.2,.7,.3,1) both',
        shimmer: 'shimmer 1.5s infinite',
        sweep: 'sweep 1.1s cubic-bezier(.4,0,.2,1)',
      },
    },
  },
  // Bootstrap's utility API emits `!important` on everything it generates
  // (.opacity-0, .d-none, .w-100 and dozens more). Any Tailwind utility whose
  // name collides therefore loses, silently and only in some states — a
  // group-hover:opacity-100 that never fires because .opacity-0 outranks it.
  //
  // Marking Tailwind's utilities important puts them back on top. It is the
  // documented way to coexist with a framework that owns the same names, and
  // the blast radius is small here: Tailwind classes appear only in the newer
  // components, and utilities are meant to have the final word anyway.
  important: true,

  corePlugins: {
    // Bootstrap and Angular Material already own the base layer. Tailwind's
    // preflight would reset both out from under every existing screen, so this
    // adds utilities alongside them rather than replacing them.
    preflight: false,
  },
  plugins: [],
};
