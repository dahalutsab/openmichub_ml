package com.brogrammers.open_mic_hub_service.discovery;

import com.brogrammers.open_mic_hub_service.discovery.entity.InteractionKind;
import com.brogrammers.open_mic_hub_service.discovery.entity.UserInteraction;
import com.brogrammers.open_mic_hub_service.discovery.repository.UserInteractionRepository;
import com.brogrammers.open_mic_hub_service.discovery.service.InteractionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What gets written to the interaction log, and what deliberately does not.
 *
 * <p>This log is the input to personalised ranking, so the cases worth pinning are the ones where
 * a wrong row would quietly distort someone's taste profile rather than fail: a page that reloads
 * counting as renewed interest, an empty front page counting as a preference, and anything at all
 * being recorded for a visitor who is not signed in.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InteractionServiceImplTest {

    @Mock private UserInteractionRepository interactions;

    @InjectMocks private InteractionServiceImpl service;

    private UserInteraction saved() {
        ArgumentCaptor<UserInteraction> captor = ArgumentCaptor.forClass(UserInteraction.class);
        verify(interactions).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("a search is recorded with its words and its filters")
    void recordsASearch() {
        service.recordSearch(7L, "  jazz trio for a dinner  ", "Jazz", "Kathmandu", "Corporate", 5000.0);

        UserInteraction row = saved();
        assertThat(row.getUserId()).isEqualTo(7L);
        assertThat(row.getKind()).isEqualTo(InteractionKind.SEARCH);
        assertThat(row.getSearchQuery()).isEqualTo("jazz trio for a dinner");
        assertThat(row.getGenre()).isEqualTo("Jazz");
        assertThat(row.getOccasion()).isEqualTo("Corporate");
        assertThat(row.getBudgetPerHour()).isEqualTo(5000.0);
        assertThat(row.getCreatedDate()).isNotNull();
    }

    @Test
    @DisplayName("nothing is recorded for a visitor who is not signed in")
    void ignoresAnonymousVisitors() {
        service.recordSearch(null, "jazz trio", null, null, null, null);
        service.recordBrowse(null, "Jazz", null, null, null);
        service.recordProfileView(null, 12L);

        verify(interactions, never()).save(any());
    }

    @Test
    @DisplayName("an empty query is not a search")
    void ignoresABlankQuery() {
        service.recordSearch(7L, "   ", null, null, null, null);

        verify(interactions, never()).save(any());
    }

    @Test
    @DisplayName("browsing with no filters set is the front page loading, not a preference")
    void ignoresAnUnfilteredBrowse() {
        service.recordBrowse(7L, null, null, null, null);
        service.recordBrowse(7L, "  ", null, "  ", null);

        verify(interactions, never()).save(any());

        // A city on its own is a filter rather than a taste, and the ML service treats it that way
        // too - it does not start a profile on its own.
        service.recordBrowse(7L, null, "Pokhara", null, null);
        verify(interactions, never()).save(any());
    }

    @Test
    @DisplayName("a stated genre or budget is a preference, and is kept")
    void recordsAFilteredBrowse() {
        service.recordBrowse(7L, "Jazz", "Pokhara", null, 3000.0);

        UserInteraction row = saved();
        assertThat(row.getKind()).isEqualTo(InteractionKind.BROWSE);
        assertThat(row.getGenre()).isEqualTo("Jazz");
        assertThat(row.getCity()).isEqualTo("Pokhara");
        assertThat(row.getArtistId()).isNull();
    }

    @Test
    @DisplayName("a profile seen again within the window is not counted twice")
    void deDuplicatesProfileViews() {
        when(interactions.existsByUserIdAndArtistIdAndKindAndCreatedDateAfter(
                eq(7L), eq(12L), eq(InteractionKind.PROFILE_VIEW), any())).thenReturn(true);

        service.recordProfileView(7L, 12L);

        verify(interactions, never()).save(any());
    }

    @Test
    @DisplayName("a first look at a profile is recorded")
    void recordsAFirstProfileView() {
        when(interactions.existsByUserIdAndArtistIdAndKindAndCreatedDateAfter(
                anyLong(), anyLong(), any(), any())).thenReturn(false);

        service.recordProfileView(7L, 12L);

        UserInteraction row = saved();
        assertThat(row.getKind()).isEqualTo(InteractionKind.PROFILE_VIEW);
        assertThat(row.getArtistId()).isEqualTo(12L);
        assertThat(row.getSearchQuery()).isNull();
    }

    @Test
    @DisplayName("a long query is cut to the column rather than failing the insert")
    void trimsAnOverlongQuery() {
        service.recordSearch(7L, "x".repeat(900), null, null, null, null);

        assertThat(saved().getSearchQuery()).hasSize(500);
    }

    @Test
    @DisplayName("a failure to record never reaches the person searching")
    void swallowsWriteFailures() {
        when(interactions.save(any())).thenThrow(new RuntimeException("connection reset"));
        when(interactions.existsByUserIdAndArtistIdAndKindAndCreatedDateAfter(
                anyLong(), anyLong(), any(), any()))
                .thenThrow(new RuntimeException("connection reset"));

        assertThatCode(() -> {
            service.recordSearch(7L, "jazz trio", null, null, null, null);
            service.recordBrowse(7L, "Jazz", null, null, null);
            service.recordProfileView(7L, 12L);
        }).doesNotThrowAnyException();
    }
}
