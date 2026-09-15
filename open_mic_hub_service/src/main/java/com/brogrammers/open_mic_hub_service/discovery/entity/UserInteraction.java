package com.brogrammers.open_mic_hub_service.discovery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One thing a person did while looking for an artist.
 *
 * <p>Holds ids rather than {@code @ManyToOne} associations on purpose. This is an append-only log
 * written on the way out of a request that has already done its work; resolving a user and an
 * artist entity to write one row would add two selects to every search and every profile view, and
 * nothing here ever navigates to either.
 *
 * <p>Belongs to an account or to a browser, never to neither. A visitor's rows move to their account
 * when they sign in.
 */
@Entity
@Table(name = "user_interaction")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for a visitor who has not signed in. */
    @Column(name = "user_id")
    private Long userId;

    /** The browser's random id, for a visitor who has not signed in. Null once claimed. */
    @Column(name = "visitor_id", length = 64)
    private String visitorId;

    /** Null for a search or a browse, which are about a requirement rather than one artist. */
    @Column(name = "artist_id")
    private Long artistId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InteractionKind kind;

    @Column(name = "search_query")
    private String searchQuery;

    @Column(length = 160)
    private String genre;

    @Column(length = 160)
    private String city;

    /** Wedding, Corporate, Festival — the occasion, not the sort of interaction. */
    @Column(length = 160)
    private String occasion;

    @Column(name = "budget_per_hour")
    private Double budgetPerHour;

    /** For a CLICK: the served list it came from. See {@code discovery_impression}. */
    @Column(name = "request_id")
    private UUID requestId;

    /** For a CLICK: where in that list the artist was, from 0. */
    @Column(name = "position")
    private Integer position;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;
}
