/**
 * Shared shapes and geometry helpers for the chart components.
 *
 * The charts are hand-drawn SVG rather than a charting library. They render a
 * small, fixed set of forms, all of which need to sit exactly on the design
 * system's tokens and respond to the theme toggle — which is most of what a
 * library's configuration surface is spent fighting. Drawing them directly is
 * less code here than the theme adapters would have been.
 */

/** One point of a time series, as returned by the analytics API. */
export interface SeriesPoint {
  period: string;
  date: string;
  primary: number;
  secondary: number;
}

/** A labelled portion: booking statuses, payment methods, event types. */
export interface Slice {
  label: string;
  count: number;
  amount: number;
}

/** One cell of the week/hour grid. `dayOfWeek` is 1 (Mon) to 7 (Sun). */
export interface HeatCell {
  dayOfWeek: number;
  hour: number;
  count: number;
}

/** A figure with its previous-period comparison. */
export interface Metric {
  value: number;
  previousValue: number;
  changePercent: number;
}

/**
 * Builds an SVG path through points using a monotone cubic interpolation.
 *
 * A plain Catmull-Rom or cardinal spline overshoots: given a run of equal
 * values followed by a spike it dips *below* the flat run before climbing,
 * inventing a decline the data never had. Monotone interpolation constrains
 * the tangents so the curve never leaves the range of the points it joins,
 * which for revenue and booking counts is the difference between a smooth
 * chart and a wrong one.
 */
export function smoothPath(points: Array<{ x: number; y: number }>): string {
  if (points.length === 0) return '';
  if (points.length === 1) return `M ${points[0].x} ${points[0].y}`;
  if (points.length === 2) {
    return `M ${points[0].x} ${points[0].y} L ${points[1].x} ${points[1].y}`;
  }

  const n = points.length;

  // Secant slopes between consecutive points.
  const dx: number[] = [];
  const dy: number[] = [];
  const slope: number[] = [];
  for (let i = 0; i < n - 1; i++) {
    dx[i] = points[i + 1].x - points[i].x;
    dy[i] = points[i + 1].y - points[i].y;
    slope[i] = dx[i] === 0 ? 0 : dy[i] / dx[i];
  }

  // Tangents, initialised to the average of the neighbouring secants.
  const tangent: number[] = new Array(n);
  tangent[0] = slope[0];
  tangent[n - 1] = slope[n - 2];
  for (let i = 1; i < n - 1; i++) {
    if (slope[i - 1] * slope[i] <= 0) {
      // A local extremum: flatten, so the curve turns without overshooting.
      tangent[i] = 0;
    } else {
      tangent[i] = (slope[i - 1] + slope[i]) / 2;
    }
  }

  // Fritsch-Carlson limiter: keeps each tangent inside three times the
  // neighbouring secant, which is what guarantees monotonicity.
  for (let i = 0; i < n - 1; i++) {
    if (slope[i] === 0) {
      tangent[i] = 0;
      tangent[i + 1] = 0;
      continue;
    }
    const a = tangent[i] / slope[i];
    const b = tangent[i + 1] / slope[i];
    const h = Math.hypot(a, b);
    if (h > 3) {
      const t = 3 / h;
      tangent[i] = t * a * slope[i];
      tangent[i + 1] = t * b * slope[i];
    }
  }

  let d = `M ${round(points[0].x)} ${round(points[0].y)}`;
  for (let i = 0; i < n - 1; i++) {
    const third = dx[i] / 3;
    const c1x = points[i].x + third;
    const c1y = points[i].y + third * tangent[i];
    const c2x = points[i + 1].x - third;
    const c2y = points[i + 1].y - third * tangent[i + 1];
    d += ` C ${round(c1x)} ${round(c1y)}, ${round(c2x)} ${round(c2y)}, ` +
         `${round(points[i + 1].x)} ${round(points[i + 1].y)}`;
  }
  return d;
}

/** SVG coordinates carry no meaning past two decimals; trimming shrinks the DOM. */
export function round(value: number): number {
  return Math.round(value * 100) / 100;
}

/**
 * A "nice" upper bound for an axis — 1, 2, 2.5 or 5 times a power of ten.
 *
 * Scaling to the raw maximum puts the tallest bar flush against the ceiling and
 * produces gridline labels like 3,847. Rounding outward gives round numbers and
 * a little headroom.
 */
export function niceCeiling(max: number): number {
  if (!isFinite(max) || max <= 0) return 1;
  const magnitude = Math.pow(10, Math.floor(Math.log10(max)));
  const normalised = max / magnitude;
  const step = normalised <= 1 ? 1
    : normalised <= 2 ? 2
    : normalised <= 2.5 ? 2.5
    : normalised <= 5 ? 5
    : 10;
  return step * magnitude;
}

/**
 * Compact number formatting for axis ticks and tile figures.
 *
 * Charts are read at a glance, so 1.2M beats 1,238,400 on an axis — but the
 * exact figure still belongs in the tooltip, which formats separately.
 */
export function compact(value: number): string {
  const abs = Math.abs(value);
  if (abs >= 1_000_000_000) return trim(value / 1_000_000_000) + 'B';
  if (abs >= 1_000_000) return trim(value / 1_000_000) + 'M';
  if (abs >= 1_000) return trim(value / 1_000) + 'K';
  return trim(value);
}

function trim(value: number): string {
  const rounded = Math.round(value * 10) / 10;
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1);
}

/** Day labels for the heatmap axis, indexed so 1 = Monday. */
export const DAY_LABELS = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

/** The categorical palette, as CSS variable references resolved per theme. */
export const CATEGORICAL = [
  'rgb(var(--omh-cat-1))',
  'rgb(var(--omh-cat-2))',
  'rgb(var(--omh-cat-3))',
  'rgb(var(--omh-cat-4))',
  'rgb(var(--omh-cat-5))',
  'rgb(var(--omh-cat-6))',
  'rgb(var(--omh-cat-7))',
  'rgb(var(--omh-cat-8))',
];

/** Stable colour for a series index, wrapping if there are more than eight. */
export function categorical(index: number): string {
  return CATEGORICAL[index % CATEGORICAL.length];
}

/**
 * Colour for a known status or type label, so the same state is the same colour
 * on every screen — a CONFIRMED booking is green in the donut, in the table and
 * in the legend without each caller deciding for itself.
 */
export function statusColor(label: string): string {
  switch ((label || '').toUpperCase()) {
    case 'CONFIRMED':
    case 'COMPLETED':
    case 'COMPLETE':
    case 'APPROVED':
    case 'SUCCESS':
    case 'CREDIT':
      return 'rgb(var(--omh-positive))';
    case 'PENDING':
    case 'INITIATED':
      return 'rgb(var(--omh-warning))';
    case 'CANCELLED':
    case 'CANCELED':
    case 'DECLINED':
    case 'REJECTED':
    case 'FAILED':
    case 'ABORTED':
    case 'NO_SHOW':
    case 'DEBIT':
      return 'rgb(var(--omh-critical))';
    default:
      return 'rgb(var(--omh-muted))';
  }
}
