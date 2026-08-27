package com.brogrammers.open_mic_hub_service.user_management.artist.artist;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Turns a stage name into the identifier that appears in a public URL.
 *
 * <p>Kept deliberately narrow: lowercase ASCII letters, digits and single hyphens. Anything else —
 * accents, punctuation, emoji, the scripts a Nepali stage name may well be written in — either
 * folds to ASCII or disappears. A slug is not a display name and does not have to preserve one; it
 * only has to be stable, readable and safe in a path segment.
 */
public final class Slugs {

    /** Comfortably inside the column, and long enough that no real stage name is cut short. */
    private static final int MAX_LENGTH = 120;

    private Slugs() {
    }

    /**
     * The slug for a name, ignoring whether anything already uses it.
     *
     * <p>Returns an empty string when the name has nothing sluggable in it, so callers can fall
     * back rather than silently publishing an empty path segment.
     */
    public static String slugify(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }

        // Decompose accents into base letter plus mark, then drop the marks, so "Café" becomes
        // "cafe" rather than losing the letter entirely.
        String folded = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        String slug = folded.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug;
    }

    /**
     * A slug for a name that nothing else is using.
     *
     * <p>{@code taken} answers whether a candidate is already claimed. The first free candidate
     * wins: the bare slug, then {@code -2}, {@code -3} and so on. Counting up rather than appending
     * the id keeps the URL readable, and matters because the id is not known until the row is
     * written.
     *
     * @param name  the stage name to derive from
     * @param taken whether a candidate slug is already in use
     */
    public static String uniqueSlug(String name, Predicate<String> taken) {
        String base = slugify(name);
        if (base.isEmpty()) {
            // Nothing sluggable — "artist" alone would collide immediately, so go straight to
            // counting.
            base = "artist";
        }

        if (!taken.test(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 10_000; suffix++) {
            String candidate = base + "-" + suffix;
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find a free slug for: " + name);
    }
}
