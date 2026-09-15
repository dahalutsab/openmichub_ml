package com.brogrammers.open_mic_hub_service.discovery.dto;

import java.util.regex.Pattern;

/**
 * Who is using discovery: an account, a browser, or nobody we can tell apart from anyone else.
 *
 * <p>A signed-in person is their account and nothing more - their browser's visitor id is dropped,
 * so one person's history is never split across two keys. A visitor who has not signed in is the
 * random id their browser generated. A request carrying neither is served normally and recorded
 * nowhere.
 *
 * @param userId    the account, or null
 * @param visitorId the browser's id, or null; always null when {@code userId} is set
 */
public record DiscoveryActor(Long userId, String visitorId) {

    /** The header the client sends its visitor id in. */
    public static final String VISITOR_HEADER = "X-Visitor-Id";

    /**
     * What a visitor id may look like: the UUID the client generates.
     *
     * <p>Checked rather than trusted, because it is written straight into the interaction log. An id
     * that does not match is treated as absent, not as an error - the endpoints are public, and an
     * odd header must not turn browsing into a failure.
     */
    private static final Pattern VISITOR_ID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public static final DiscoveryActor NOBODY = new DiscoveryActor(null, null);

    public static DiscoveryActor of(Long userId, String rawVisitorId) {
        if (userId != null) {
            return new DiscoveryActor(userId, null);
        }
        String visitor = normalisedVisitorId(rawVisitorId);
        return visitor == null ? NOBODY : new DiscoveryActor(null, visitor);
    }

    /** The id in canonical lower case, or null when it is missing or malformed. */
    public static String normalisedVisitorId(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return VISITOR_ID.matcher(trimmed).matches() ? trimmed.toLowerCase() : null;
    }

    /** Whether there is anyone to record anything against. */
    public boolean isKnown() {
        return userId != null || visitorId != null;
    }
}
