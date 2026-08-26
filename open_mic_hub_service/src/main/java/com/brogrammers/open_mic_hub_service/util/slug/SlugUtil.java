package com.brogrammers.open_mic_hub_service.util.slug;

public class SlugUtil {
    public static String toSlug(String input) {
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "") // Remove special chars
                .replaceAll("\\s+", "-")        // Replace spaces with dashes
                .replaceAll("-{2,}", "-")        // Replace multiple dashes
                .replaceAll("^-|-$", "");        // Trim dashes from ends
    }
}
