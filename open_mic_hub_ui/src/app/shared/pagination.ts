/**
 * The page numbers a pager should actually render.
 *
 * Three screens were rendering one button per page. At 392 pages of payments
 * that is 392 buttons filling most of the viewport, and it grows with the data
 * — so the more successful the platform gets, the worse the control behaves.
 *
 * Four other screens each carried their own near-identical copy of this window
 * logic. One implementation now, so a fix lands everywhere.
 *
 * @param current    zero-based index of the page being shown
 * @param total      total number of pages
 * @param maxVisible how many numbered buttons to render at most
 * @returns zero-based page indices, always contiguous and clamped to range
 */
export function visiblePages(current: number, total: number, maxVisible = 5): number[] {
  if (total <= 0) {
    return [];
  }
  if (total <= maxVisible) {
    return Array.from({ length: total }, (_, i) => i);
  }

  // Centre the window on the current page, then push it back inside the range.
  // Clamping after centring keeps the window a constant width at both ends —
  // sliding it instead would show fewer buttons on the first and last pages.
  const half = Math.floor(maxVisible / 2);
  let start = current - half;

  if (start < 0) {
    start = 0;
  } else if (start + maxVisible > total) {
    start = total - maxVisible;
  }

  return Array.from({ length: maxVisible }, (_, i) => start + i);
}
