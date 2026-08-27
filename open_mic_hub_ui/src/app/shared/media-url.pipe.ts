import { Pipe, PipeTransform } from '@angular/core';
import { environment } from '../environment/environment';

/**
 * Turns whatever an API gave us for an image into a URL the browser can fetch.
 *
 * The platform is inconsistent about this, and legitimately so: `ArtistResponse`
 * runs the stored path through `FileUrlUtil` and hands back a full URL, while
 * the ML service and several list endpoints return the raw `users.profile`
 * value — `profiles/anup-thapa.jpg`. A relative path resolves against whatever
 * origin the page is on, so every one of those requested
 * `localhost:4200/profiles/anup-thapa.jpg` and 404ed. Nobody noticed until
 * artists had pictures at all.
 *
 * Rather than chase every producer, this normalises at the point of use:
 * anything already absolute is left alone, anything else is resolved against
 * the API's media root.
 */
@Pipe({ name: 'media', standalone: true })
export class MediaUrlPipe implements PipeTransform {
  transform(value: string | null | undefined, fallback = ''): string {
    if (!value) {
      return fallback;
    }
    const path = value.trim();
    if (!path) {
      return fallback;
    }
    // Absolute URLs and inline data stay as they are.
    if (/^(https?:)?\/\//i.test(path) || path.startsWith('data:') || path.startsWith('blob:')) {
      return path;
    }
    const clean = path.replace(/^\/+/, '');
    // Some producers already include the media segment, some do not.
    const suffix = clean.startsWith('media/') ? clean : `media/${clean}`;
    return `${environment.host.replace(/\/+$/, '')}/${suffix}`;
  }
}
