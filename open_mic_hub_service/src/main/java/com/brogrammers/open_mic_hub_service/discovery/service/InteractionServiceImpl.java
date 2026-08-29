package com.brogrammers.open_mic_hub_service.discovery.service;

import com.brogrammers.open_mic_hub_service.discovery.entity.InteractionKind;
import com.brogrammers.open_mic_hub_service.discovery.entity.UserInteraction;
import com.brogrammers.open_mic_hub_service.discovery.repository.UserInteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

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

    private final UserInteractionRepository interactions;

    @Async
    @Override
    public void recordSearch(Long userId, String query, String genre, String city,
                             String occasion, Double budgetPerHour) {
        String text = trimmed(query, MAX_QUERY_LENGTH);
        if (userId == null || text == null) {
            return;
        }
        save(UserInteraction.builder()
                .userId(userId)
                .kind(InteractionKind.SEARCH)
                .searchQuery(text)
                .genre(trimmed(genre, 160))
                .city(trimmed(city, 160))
                .occasion(trimmed(occasion, 160))
                .budgetPerHour(budgetPerHour)
                .createdDate(LocalDateTime.now())
                .build());
    }

    @Async
    @Override
    public void recordBrowse(Long userId, String genre, String city,
                             String occasion, Double budgetPerHour) {
        // An unfiltered browse is the front page loading. It is not a preference, and logging it
        // would give every user a history made mostly of noise.
        boolean statedSomething = trimmed(genre, 160) != null
                || trimmed(occasion, 160) != null
                || budgetPerHour != null;
        if (userId == null || !statedSomething) {
            return;
        }
        save(UserInteraction.builder()
                .userId(userId)
                .kind(InteractionKind.BROWSE)
                .genre(trimmed(genre, 160))
                .city(trimmed(city, 160))
                .occasion(trimmed(occasion, 160))
                .budgetPerHour(budgetPerHour)
                .createdDate(LocalDateTime.now())
                .build());
    }

    @Async
    @Override
    public void recordProfileView(Long userId, Long artistId) {
        if (userId == null || artistId == null) {
            return;
        }
        try {
            LocalDateTime since = LocalDateTime.now().minus(VIEW_DEDUPE_WINDOW);
            if (interactions.existsByUserIdAndArtistIdAndKindAndCreatedDateAfter(
                    userId, artistId, InteractionKind.PROFILE_VIEW, since)) {
                return;
            }
        } catch (Exception e) {
            log.warn("Could not check for a recent profile view; recording it anyway: {}",
                    e.getMessage());
        }
        save(UserInteraction.builder()
                .userId(userId)
                .artistId(artistId)
                .kind(InteractionKind.PROFILE_VIEW)
                .createdDate(LocalDateTime.now())
                .build());
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
