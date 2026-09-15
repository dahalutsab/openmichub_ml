package com.brogrammers.open_mic_hub_service.discovery.service;

import com.brogrammers.open_mic_hub_service.discovery.dto.DiscoveryActor;
import com.brogrammers.open_mic_hub_service.discovery.entity.InteractionKind;
import com.brogrammers.open_mic_hub_service.discovery.entity.UserInteraction;
import com.brogrammers.open_mic_hub_service.discovery.repository.ImpressionRepository;
import com.brogrammers.open_mic_hub_service.discovery.repository.UserInteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InteractionServiceImpl implements InteractionService {

    /**
     * How long a profile view stands for.
     *
     * <p>Reopening a profile an hour later is a second look and worth counting; the four requests a
     * single page visit produces, and a reload while comparing two acts, are not.
     */
    private static final Duration VIEW_DEDUPE_WINDOW = Duration.ofMinutes(30);

    /** Long enough for anything a person types, short enough that a paste is not stored whole. */
    private static final int MAX_QUERY_LENGTH = 500;

    /** No served list is longer than the API's page cap; a position past it is not a real one. */
    private static final int MAX_POSITION = 100;

    private final UserInteractionRepository interactions;
    private final ImpressionRepository impressions;

    @Async
    @Override
    public void recordSearch(DiscoveryActor actor, String query, String genre, String city,
                             String occasion, Double budgetPerHour) {
        String text = trimmed(query, MAX_QUERY_LENGTH);
        if (!known(actor) || text == null) {
            return;
        }
        save(row(actor, InteractionKind.SEARCH)
                .searchQuery(text)
                .genre(trimmed(genre, 160))
                .city(trimmed(city, 160))
                .occasion(trimmed(occasion, 160))
                .budgetPerHour(budgetPerHour)
                .build());
    }

    @Async
    @Override
    public void recordBrowse(DiscoveryActor actor, String genre, String city,
                             String occasion, Double budgetPerHour) {
        // An unfiltered browse is the front page loading. It is not a preference, and logging it
        // would give every visitor a history made mostly of noise. What they were *shown* on that
        // front page is still kept, as an impression.
        boolean statedSomething = trimmed(genre, 160) != null
                || trimmed(occasion, 160) != null
                || budgetPerHour != null;
        if (!known(actor) || !statedSomething) {
            return;
        }
        save(row(actor, InteractionKind.BROWSE)
                .genre(trimmed(genre, 160))
                .city(trimmed(city, 160))
                .occasion(trimmed(occasion, 160))
                .budgetPerHour(budgetPerHour)
                .build());
    }

    @Async
    @Override
    public void recordProfileView(DiscoveryActor actor, Long artistId) {
        if (!known(actor) || artistId == null) {
            return;
        }
        try {
            LocalDateTime since = LocalDateTime.now().minus(VIEW_DEDUPE_WINDOW);
            boolean seen = actor.userId() != null
                    ? interactions.existsByUserIdAndArtistIdAndKindAndCreatedDateAfter(
                            actor.userId(), artistId, InteractionKind.PROFILE_VIEW, since)
                    : interactions.existsByVisitorIdAndArtistIdAndKindAndCreatedDateAfter(
                            actor.visitorId(), artistId, InteractionKind.PROFILE_VIEW, since);
            if (seen) {
                return;
            }
        } catch (Exception e) {
            log.warn("Could not check for a recent profile view; recording it anyway: {}",
                    e.getMessage());
        }
        save(row(actor, InteractionKind.PROFILE_VIEW).artistId(artistId).build());
    }

    /**
     * Written on the request thread, unlike the other recordings. A click is checked against this
     * row, and written asynchronously it could lose the race to a click posted straight after the
     * list arrived - a single indexed insert is cheaper than a click that is silently dropped.
     * Failures are still swallowed.
     */
    @Override
    public void recordImpressions(DiscoveryActor actor, UUID requestId, String surface, String query,
                                  List<Long> artistIds, String strategy) {
        if (!known(actor) || requestId == null || artistIds == null || artistIds.isEmpty()) {
            return;
        }
        try {
            impressions.insert(requestId, actor.userId(), actor.visitorId(), surface,
                    trimmed(query, MAX_QUERY_LENGTH), artistIds, trimmed(strategy, 160));
        } catch (Exception e) {
            log.warn("Could not record a served list: {}", e.getMessage());
        }
    }

    @Override
    public boolean recordClick(DiscoveryActor actor, UUID requestId, Long artistId, Integer position) {
        if (!known(actor) || requestId == null || artistId == null
                || position == null || position < 0 || position > MAX_POSITION) {
            return false;
        }
        try {
            if (!impressions.servedTo(requestId, actor.userId(), actor.visitorId(), artistId)) {
                return false;
            }
        } catch (Exception e) {
            log.warn("Could not check a click against its list: {}", e.getMessage());
            return false;
        }
        save(row(actor, InteractionKind.CLICK)
                .artistId(artistId)
                .requestId(requestId)
                .position(position)
                .build());
        return true;
    }

    @Override
    @Transactional
    public int claimVisitorHistory(String visitorId, Long userId) {
        String visitor = DiscoveryActor.normalisedVisitorId(visitorId);
        if (visitor == null || userId == null) {
            return 0;
        }
        int moved = interactions.claimVisitorHistory(visitor, userId);
        moved += impressions.claimVisitorLists(visitor, userId);
        if (moved > 0) {
            log.info("Moved {} discovery rows from a browser onto user {}", moved, userId);
        }
        return moved;
    }

    private static boolean known(DiscoveryActor actor) {
        return actor != null && actor.isKnown();
    }

    private static UserInteraction.UserInteractionBuilder row(DiscoveryActor actor, InteractionKind kind) {
        return UserInteraction.builder()
                .userId(actor.userId())
                .visitorId(actor.visitorId())
                .kind(kind)
                .createdDate(LocalDateTime.now());
    }

    /**
     * Writes the row, and swallows anything that goes wrong.
     *
     * <p>The caller's request has already been answered by the time this runs. A full disk or a
     * deleted artist is a reason to lose one signal, not to log a stack trace per search.
     */
    private void save(UserInteraction interaction) {
        try {
            interactions.save(interaction);
        } catch (Exception e) {
            log.warn("Could not record a {} interaction: {}", interaction.getKind(), e.getMessage());
        }
    }

    /** Null for blank, trimmed otherwise, cut to the column's width. */
    private String trimmed(String value, int limit) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > limit ? text.substring(0, limit) : text;
    }
}
