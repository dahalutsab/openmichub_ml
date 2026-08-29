package com.brogrammers.open_mic_hub_service.user_management.artist;

import com.brogrammers.open_mic_hub_service.social_feed.repository.PostRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.ArtistAvailability;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.AvailabilityTime;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.repository.ArtistAvailabilityRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.completeness.ProfileChecklistItem;
import com.brogrammers.open_mic_hub_service.user_management.artist.completeness.ProfileCompleteness;
import com.brogrammers.open_mic_hub_service.user_management.artist.completeness.ProfileCompletenessService;
import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Category;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * What the profile checklist tells an artist to do next.
 *
 * <p>The score is a judgement about what gets someone booked, so the cases worth pinning are the
 * ones where the advice would otherwise be wrong: availability being the only item that decides
 * whether a booking can happen at all, and a bio that exists but says nothing not counting as
 * written.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileCompletenessServiceTest {

    @Mock private ArtistRepository artistRepository;
    @Mock private ArtistAvailabilityRepository availabilityRepository;
    @Mock private PostRepository postRepository;
    @Mock private LoggedInUserUtil loggedInUserUtil;

    @InjectMocks private ProfileCompletenessService service;

    private Artist artist;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(5L);
        user.setEmailId("bibek@example.com");

        artist = new Artist();
        artist.setId(9L);
        artist.setUser(user);
        artist.setStageName("Bibek Subedi");

        when(loggedInUserUtil.getLoggedInUser()).thenReturn(user);
        when(artistRepository.findByUser(user)).thenReturn(Optional.of(artist));
        when(availabilityRepository.findAllByArtist(any())).thenReturn(List.of());
        when(postRepository.countByArtist_Id(anyLong())).thenReturn(0L);
    }

    private void withAvailability() {
        AvailabilityTime slot = new AvailabilityTime();
        slot.setStartTime(LocalTime.of(20, 0));
        slot.setEndTime(LocalTime.of(23, 0));
        ArtistAvailability thursday = new ArtistAvailability();
        thursday.setArtist(artist);
        thursday.setDayOfWeek(DayOfWeek.THURSDAY);
        thursday.setAvailabilityTimes(List.of(slot));
        when(availabilityRepository.findAllByArtist(any())).thenReturn(List.of(thursday));
    }

    private void fillEverythingExceptAvailability() {
        Category bebop = new Category();
        bebop.setId(4L);
        bebop.setName("Bebop");
        artist.setGenres(List.of(bebop));
        artist.setBio("Two decades of bebop across Kathmandu's late-night rooms and hotel bars.");
        artist.setHourlyRate(3500);
        user.setProfileImage("/media/bibek.jpg");
        user.setLocation("Kathmandu");
        when(postRepository.countByArtist_Id(anyLong())).thenReturn(3L);
    }

    private ProfileChecklistItem item(ProfileCompleteness result, String key) {
        return result.items().stream().filter(i -> i.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("an empty profile scores nothing and lists everything")
    void emptyProfile() {
        ProfileCompleteness result = service.forCurrentArtist();

        assertThat(result.score()).isZero();
        assertThat(result.complete()).isFalse();
        assertThat(result.items()).noneMatch(ProfileChecklistItem::done);
    }

    @Test
    @DisplayName("a finished profile scores 100 and has nothing left")
    void finishedProfile() {
        fillEverythingExceptAvailability();
        withAvailability();

        ProfileCompleteness result = service.forCurrentArtist();

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.complete()).isTrue();
        assertThat(result.bookable()).isTrue();
        assertThat(result.items()).allMatch(ProfileChecklistItem::done);
    }

    @Test
    @DisplayName("without published hours the profile is not bookable, however complete it looks")
    void availabilityDecidesBookability() {
        // Everything else done: this is the case where a percentage on its own would mislead.
        fillEverythingExceptAvailability();

        ProfileCompleteness result = service.forCurrentArtist();

        assertThat(result.score()).isEqualTo(75);
        assertThat(result.bookable()).isFalse();
        assertThat(item(result, "availability").blocking()).isTrue();
        assertThat(item(result, "availability").done()).isFalse();
    }

    @Test
    @DisplayName("availability is the only blocking item")
    void onlyAvailabilityBlocks() {
        ProfileCompleteness result = service.forCurrentArtist();

        assertThat(result.items().stream().filter(ProfileChecklistItem::blocking))
                .extracting(ProfileChecklistItem::key)
                .containsExactly("availability");
    }

    @Test
    @DisplayName("a day with no hours on it is not availability")
    void emptyDayDoesNotCount() {
        // A row exists for Thursday but carries no times, so nothing can be booked against it.
        ArtistAvailability emptyDay = new ArtistAvailability();
        emptyDay.setArtist(artist);
        emptyDay.setDayOfWeek(DayOfWeek.THURSDAY);
        emptyDay.setAvailabilityTimes(List.of());
        when(availabilityRepository.findAllByArtist(any())).thenReturn(List.of(emptyDay));

        assertThat(service.forCurrentArtist().bookable()).isFalse();
    }

    @Test
    @DisplayName("a bio too short to describe anything does not count as written")
    void placeholderBioDoesNotCount() {
        artist.setBio("Musician.");

        assertThat(item(service.forCurrentArtist(), "bio").done()).isFalse();
    }

    @Test
    @DisplayName("a rate of zero reads as unstated, not as free")
    void zeroRateIsUnstated() {
        artist.setHourlyRate(0);

        assertThat(item(service.forCurrentArtist(), "rate").done()).isFalse();
    }

    @Test
    @DisplayName("blank strings do not pass for a photo or a location")
    void blankFieldsDoNotCount() {
        user.setProfileImage("   ");
        user.setLocation("");

        ProfileCompleteness result = service.forCurrentArtist();

        assertThat(item(result, "photo").done()).isFalse();
        assertThat(item(result, "location").done()).isFalse();
    }

    @Test
    @DisplayName("the weights add up to a hundred, so the score is a percentage")
    void weightsFormAPercentage() {
        assertThat(service.forCurrentArtist().items())
                .extracting(ProfileChecklistItem::weight)
                .satisfies(weights -> assertThat(weights.stream().mapToInt(Integer::intValue).sum())
                        .isEqualTo(100));
    }

    @Test
    @DisplayName("every item says where to go and why it is worth doing")
    void everyItemIsActionable() {
        // A checklist that only names a gap is busywork; the hint is the part that makes it advice.
        assertThat(service.forCurrentArtist().items()).allSatisfy(entry -> {
            assertThat(entry.action()).startsWith("/artist/");
            assertThat(entry.hint()).isNotBlank();
            assertThat(entry.label()).isNotBlank();
        });
    }
}
