package com.brogrammers.open_mic_hub_service.user_management.user.role.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Roles recognised by the platform.
 *
 * <p>Authorities are stored under these names and exposed to Spring Security with a {@code ROLE_}
 * prefix, so {@code hasRole('SUPER_ADMIN')} matches {@link #SUPER_ADMIN}.
 */
@Getter
@RequiredArgsConstructor
public enum UserRole {

    /** Platform owner. The only role that can disburse money or change other people's roles. */
    SUPER_ADMIN("Platform owner: payouts, refunds, role assignment and configuration"),

    /** Platform staff. Can moderate and read financial records, but cannot move money. */
    ADMIN("Platform staff: moderation and read-only access to financial records"),

    /** A performer: manages their profile, availability, posts and earnings wallet. */
    ARTIST("Performer: profile, availability, posts and earnings"),

    /** Books and pays for artists. Venues, event companies and individuals hiring talent. */
    ORGANIZER("Books and pays for artists"),

    /** Audience account: browses artists and reviews bookings they made. */
    USER("Audience: browses artists and reviews their own bookings");

    private final String description;

    /** Roles permitted to read platform-wide administrative data. */
    public static final String ANY_ADMIN = "hasAnyRole('SUPER_ADMIN','ADMIN')";

    /** Roles permitted to raise and pay for a booking. */
    public static final String ANY_BOOKER = "hasAnyRole('ORGANIZER','USER')";
}
