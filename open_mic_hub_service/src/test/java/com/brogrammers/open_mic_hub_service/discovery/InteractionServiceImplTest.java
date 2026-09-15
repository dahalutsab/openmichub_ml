package com.brogrammers.open_mic_hub_service.discovery;

import com.brogrammers.open_mic_hub_service.discovery.dto.DiscoveryActor;
import com.brogrammers.open_mic_hub_service.discovery.entity.InteractionKind;
import com.brogrammers.open_mic_hub_service.discovery.entity.UserInteraction;
import com.brogrammers.open_mic_hub_service.discovery.repository.ImpressionRepository;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What gets written to the interaction log, and what deliberately does not.
 *
 * <p>This log is the input to personalised ranking, so the cases worth pinning are the ones where
 * a wrong row would quietly distort someone's taste profile rather than fail: a page that reloads
 * counting as renewed interest, an empty front page counting as a preference, a click credited
 * against a list nobody was shown, and anything at all being recorded for a request that carries
 * neither an account nor a visitor id.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InteractionServiceImplTest {

    private static final String VISITOR = "3f2b8c1e-9a4d-4e7b-8c21-0d5e6f7a8b9c";
    private static final DiscoveryActor USER = DiscoveryActor.of(7L, null);
    private static final DiscoveryActor BROWSER = DiscoveryActor.of(null, VISITOR);

    @Mock private UserInteractionRepository interactions;
    @Mock private ImpressionRepository impressions;

    @InjectMocks private InteractionServiceImpl service;

    private UserInteraction saved() {
        ArgumentCaptor<UserInteraction> captor = ArgumentCaptor.forClass(UserInteraction.class);
        verify(interactions).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("a search is recorded with its words and its filters")
    void recordsASearch() {
        service.recordSearch(USER, "  jazz trio for a dinner  ", "Jazz", "Kathmandu", "Corporate", 5000.0);

        UserInteraction row = saved();
        assertThat(row.getUserId()).isEqualTo(7L);
        assertThat(row.getVisitorId()).isNull();
        assertThat(row.getKind()).isEqualTo(InteractionKind.SEARCH);
        assertThat(row.getSearchQuery()).isEqualTo("jazz trio for a dinner");
        assertThat(row.getGenre()).isEqualTo("Jazz");
        assertThat(row.getOccasion()).isEqualTo("Corporate");
        assertThat(row.getBudgetPerHour()).isEqualTo(5000.0);
        assertThat(row.getCreatedDate()).isNotNull();
    }

    @Test
    @DisplayName("a visitor who has not signed in is recorded against their browser's id")
    void recordsAVisitorSearch() {
        service.recordSearch(BROWSER, "dj for a club night", null, null, null, null);

        UserInteraction row = saved();
        assertThat(row.getUserId()).isNull();
        assertThat(row.getVisitorId()).isEqualTo(VISITOR);
    }

    @Test
    @DisplayName("nothing is recorded for a request with neither an account nor a visitor id")
    void ignoresUnidentifiedRequests() {
        DiscoveryActor nobody = DiscoveryActor.of(null, null);
        service.recordSearch(nobody, "jazz trio", null, null, null, null);
        service.recordBrowse(nobody, "Jazz", null, null, null);
        service.recordProfileView(nobody, 12L);
        service.recordImpressions(nobody, UUID.randomUUID(), "HOME", null, List.of(1L), "x");

        verify(interactions, never()).save(any());
        verify(impressions, never()).insert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a malformed visitor id is no visitor id")
    void rejectsMalformedVisitorIds() {
        assertThat(DiscoveryActor.of(null, "'; DROP TABLE users; --").isKnown()).isFalse();
        assertThat(DiscoveryActor.of(null, "not-a-uuid").isKnown()).isFalse();
        assertThat(DiscoveryActor.of(null, VISITOR.toUpperCase()).visitorId()).isEqualTo(VISITOR);
    }

    @Test
    @DisplayName("a signed-in person is their account, never also their browser")
    void accountWinsOverBrowser() {
        DiscoveryActor both = DiscoveryActor.of(7L, VISITOR);

        assertThat(both.userId()).isEqualTo(7L);
        assertThat(both.visitorId()).isNull();
    }

    @Test
    @DisplayName("an empty query is not a search")
    void ignoresABlankQuery() {
        service.recordSearch(USER, "   ", null, null, null, null);

        verify(interactions, never()).save(any());
    }

    @Test
    @DisplayName("browsing with no filters set is the front page loading, not a preference")
    void ignoresAnUnfilteredBrowse() {
        service.recordBrowse(USER, null, null, null, null);
        service.recordBrowse(USER, "  ", null, "  ", null);

        verify(interactions, never()).save(any());

        // A city on its own is a filter rather than a taste, and the ML service treats it that way
        // too - it does not start a profile on its own.
        service.recordBrowse(USER, null, "Pokhara", null, null);
        verify(interactions, never()).save(any());
    }

    @Test
    @DisplayName("a stated genre or budget is a preference, and is kept")
    void recordsAFilteredBrowse() {
        service.recordBrowse(USER, "Jazz", "Pokhara", null, 3000.0);

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
        when(interactions.existsByVisitorIdAndArtistIdAndKindAndCreatedDateAfter(
                eq(VISITOR), eq(12L), eq(InteractionKind.PROFILE_VIEW), any())).thenReturn(true);

        service.recordProfileView(USER, 12L);
        service.recordProfileView(BROWSER, 12L);

        verify(interactions, never()).save(any());
    }

    @Test
    @DisplayName("a first look at a profile is recorded")
    void recordsAFirstProfileView() {
        when(interactions.existsByUserIdAndArtistIdAndKindAndCreatedDateAfter(
                anyLong(), anyLong(), any(), any())).thenReturn(false);

        service.recordProfileView(USER, 12L);

        UserInteraction row = saved();
        assertThat(row.getKind()).isEqualTo(InteractionKind.PROFILE_VIEW);
        assertThat(row.getArtistId()).isEqualTo(12L);
        assertThat(row.getSearchQuery()).isNull();
    }

    @Test
    @DisplayName("a served list is logged in the order it was shown")
    void recordsImpressions() {
        UUID request = UUID.randomUUID();

        service.recordImpressions(BROWSER, request, "HOME", null, List.of(5L, 3L, 9L), "lightgbm");

        verify(impressions).insert(eq(request), isNull(), eq(VISITOR), eq("HOME"), isNull(),
                eq(List.of(5L, 3L, 9L)), eq("lightgbm"));
    }

    @Test
    @DisplayName("a click is kept with its list and position when that list was served to this caller")
    void recordsAClickOnAServedList() {
        UUID request = UUID.randomUUID();
        when(impressions.servedTo(request, null, VISITOR, 9L)).thenReturn(true);

        assertThat(service.recordClick(BROWSER, request, 9L, 2)).isTrue();

        UserInteraction row = saved();
        assertThat(row.getKind()).isEqualTo(InteractionKind.CLICK);
        assertThat(row.getRequestId()).isEqualTo(request);
        assertThat(row.getPosition()).isEqualTo(2);
        assertThat(row.getArtistId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("a click against a list this caller was not served is dropped")
    void dropsAClickOnSomeoneElsesList() {
        when(impressions.servedTo(any(), any(), anyString(), anyLong())).thenReturn(false);

        assertThat(service.recordClick(BROWSER, UUID.randomUUID(), 9L, 2)).isFalse();
        assertThat(service.recordClick(BROWSER, UUID.randomUUID(), 9L, -1)).isFalse();
        assertThat(service.recordClick(BROWSER, null, 9L, 0)).isFalse();

        verify(interactions, never()).save(any());
    }

    @Test
    @DisplayName("signing in moves the browser's history and its served lists onto the account")
    void claimsVisitorHistory() {
        when(interactions.claimVisitorHistory(VISITOR, 7L)).thenReturn(4);
        when(impressions.claimVisitorLists(VISITOR, 7L)).thenReturn(3);

        assertThat(service.claimVisitorHistory(VISITOR.toUpperCase(), 7L)).isEqualTo(7);
        assertThat(service.claimVisitorHistory("garbage", 7L)).isZero();
        assertThat(service.claimVisitorHistory(VISITOR, null)).isZero();
    }

    @Test
    @DisplayName("a long query is cut to the column rather than failing the insert")
    void trimsAnOverlongQuery() {
        service.recordSearch(USER, "x".repeat(900), null, null, null, null);

        assertThat(saved().getSearchQuery()).hasSize(500);
    }

    @Test
    @DisplayName("a failure to record never reaches the person searching")
    void swallowsWriteFailures() {
        when(interactions.save(any())).thenThrow(new RuntimeException("connection reset"));
        when(interactions.existsByUserIdAndArtistIdAndKindAndCreatedDateAfter(
                anyLong(), anyLong(), any(), any()))
                .thenThrow(new RuntimeException("connection reset"));
        when(impressions.servedTo(any(), any(), any(), anyLong()))
                .thenThrow(new RuntimeException("connection reset"));

        assertThatCode(() -> {
            service.recordSearch(USER, "jazz trio", null, null, null, null);
            service.recordBrowse(USER, "Jazz", null, null, null);
            service.recordProfileView(USER, 12L);
            service.recordClick(USER, UUID.randomUUID(), 12L, 0);
        }).doesNotThrowAnyException();
    }
}
