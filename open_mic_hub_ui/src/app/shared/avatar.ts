/**
 * Placeholder shown when a profile picture is missing or fails to load.
 *
 * Two components each carried their own ~4KB base64 PNG of a grey avatar,
 * inlined into the TypeScript. This is the same picture as a 300-byte SVG,
 * drawn with the design system's neutrals so it sits inside an `omh-avatar-*`
 * ring without looking pasted on.
 */
export const AVATAR_FALLBACK =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 80 80">` +
      `<rect width="80" height="80" fill="#eeedf3"/>` +
      `<circle cx="40" cy="32" r="13" fill="#b6b2c6"/>` +
      `<path d="M14 74c0-14.4 11.6-24 26-24s26 9.6 26 24z" fill="#b6b2c6"/>` +
    `</svg>`
  );
