package com.brogrammers.open_mic_hub_service.user_management.artist.artist.service.implementation;

import com.brogrammers.open_mic_hub_service.booking.repository.BookingRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.service.ArtistService;
import com.brogrammers.open_mic_hub_service.user_management.artist.dto.response.ArtistResponse;
import com.brogrammers.open_mic_hub_service.user_management.genere.dto.response.GenreResponse;
import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Category;
import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Genre;
import com.brogrammers.open_mic_hub_service.user_management.genere.repository.GenreRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.repository.UserInfoRepository;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArtistServiceImplementation implements ArtistService {
    private final ArtistRepository artistRepository;
    private final UserInfoRepository userInfoRepository;
    private final GenreRepository genreRepository;
    private final BookingRepository bookingRepository;
    private final LoggedInUserUtil loggedInUserUtil;
    /**
     * A page of verified artists, each with their categories grouped under the parent genre.
     *
     * <p>Read-only transactional because {@code Artist.genres} is a lazy {@code @ManyToMany} and the
     * application runs with {@code open-in-view: false} — without it, touching the collection here
     * threw {@code LazyInitializationException} and the endpoint answered 500. That took the public
     * artist listing, the landing page and the discovery fallback down with it.
     *
     * <p>The category-to-genre lookup is also resolved once for the whole page. It used to run
     * {@code findByCategoriesContaining} per category per artist, so a page of twelve artists with
     * three categories each issued thirty-six queries to answer one request.
     */
    @Transactional(readOnly = true)
    @Override
    public Page<ArtistResponse> getAllVerifiedArtists(Pageable pageable, String searchTerm, String genreName) {
        Page<Artist> artists;
        if ((searchTerm == null || searchTerm.isEmpty()) && (genreName == null || genreName.isEmpty())) {
            artists = artistRepository.findByUserVerifiedTrue(pageable);
        } else if (genreName != null && !genreName.isEmpty()) {
            artists = artistRepository.findByUserVerifiedTrueAndGenres_NameIgnoreCaseContaining(genreName, pageable);
        } else {
            artists = artistRepository.findByUserVerifiedTrueAndStageNameIgnoreCaseContaining(searchTerm, pageable);
        }

        Map<Long, Genre> genreByCategoryId = genreByCategoryId();

        Map<Long, long[]> stats = bookingStatsFor(
                artists.getContent().stream().map(Artist::getId).toList());

        return artists.map(artist -> {
            List<Category> artistCategories = artist.getGenres() == null ? List.of() : artist.getGenres();

            Map<Genre, List<Category>> genreToCategories = new LinkedHashMap<>();
            for (Category category : artistCategories) {
                Genre parent = genreByCategoryId.get(category.getId());
                if (parent != null) {
                    genreToCategories.computeIfAbsent(parent, k -> new ArrayList<>()).add(category);
                }
            }

            List<GenreResponse> genreResponses = genreToCategories.entrySet().stream()
                    .map(entry -> new GenreResponse(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            ArtistResponse response = new ArtistResponse(artist, genreResponses);
            applyStats(response, stats.get(artist.getId()));
            return response;
        });
    }

    /**
     * Booking counts for a set of artists, keyed by id.
     *
     * <p>One query for the whole page. An artist with no bookings simply has no entry, which
     * {@link #applyStats} reads as "nothing yet" rather than as a zero response rate.
     */
    private Map<Long, long[]> bookingStatsFor(List<Long> artistIds) {
        if (artistIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, long[]> stats = new HashMap<>();
        for (Object[] row : bookingRepository.findBookingStatsFor(artistIds)) {
            stats.put(
                    ((Number) row[0]).longValue(),
                    new long[]{
                            ((Number) row[1]).longValue(),   // went ahead
                            ((Number) row[2]).longValue(),   // answered
                            ((Number) row[3]).longValue(),   // total requests
                    });
        }
        return stats;
    }

    /**
     * Copies the counts onto a response.
     *
     * <p>Response rate stays null when there have been no requests at all: showing 0% for a new
     * artist reads as "ignores people" rather than "has not been asked yet".
     */
    private void applyStats(ArtistResponse response, long[] row) {
        if (row == null) {
            response.setCompletedBookings(0);
            response.setResponseRate(null);
            return;
        }
        response.setCompletedBookings(row[0]);
        response.setResponseRate(row[2] == 0 ? null : Math.round((row[1] * 1000d) / row[2]) / 10d);
    }

    /** Every category id mapped to the genre that owns it, in one query. */
    private Map<Long, Genre> genreByCategoryId() {
        Map<Long, Genre> index = new HashMap<>();
        for (Genre genre : genreRepository.findByActiveTrue()) {
            if (genre.getCategories() == null) {
                continue;
            }
            for (Category category : genre.getCategories()) {
                index.put(category.getId(), genre);
            }
        }
        return index;
    }

    /**
     * One artist, with their categories grouped by parent genre.
     *
     * <p>Artist ids and user ids are separate sequences, so the profile page has to look up by
     * artist id — passing a user id here finds the wrong person or nobody at all.
     */
    @Transactional(readOnly = true)
    @Override
    public ArtistResponse getArtistById(Long artistId) {
        return toResponse(artistRepository.findById(artistId)
                .orElseThrow(() -> new EntityNotFoundException("No artist found with id " + artistId)));
    }

    /**
     * The same profile, addressed by its public URL segment.
     *
     * <p>Both lookups end in the same mapper so a profile cannot come out differently depending on
     * which kind of link the reader followed.
     */
    @Override
    @Transactional(readOnly = true)
    public ArtistResponse getArtistBySlug(String slug) {
        return toResponse(artistRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("No artist found at /" + slug)));
    }

    /** Genres regrouped under their parents, plus the booking stats. */
    private ArtistResponse toResponse(Artist artist) {
        Map<Long, Genre> genreByCategoryId = genreByCategoryId();
        Map<Genre, List<Category>> genreToCategories = new LinkedHashMap<>();
        for (Category category : artist.getGenres() == null ? List.<Category>of() : artist.getGenres()) {
            Genre parent = genreByCategoryId.get(category.getId());
            if (parent != null) {
                genreToCategories.computeIfAbsent(parent, k -> new ArrayList<>()).add(category);
            }
        }

        List<GenreResponse> genreResponses = genreToCategories.entrySet().stream()
                .map(entry -> new GenreResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        ArtistResponse response = new ArtistResponse(artist, genreResponses);
        applyStats(response, bookingStatsFor(List.of(artist.getId())).get(artist.getId()));
        return response;
    }

    @Override
    public ArtistResponse getLoggedInArtist() {
        Artist artist = loggedInUserUtil.getLoggedInArtist();
        // Group artist's categories by their parent genre
        List<Category> artistCategories = artist.getGenres();
        Map<Genre, List<Category>> genreToCategories = new HashMap<>();
        for (Category category : artistCategories) {
            Optional<Genre> genreOpt = genreRepository.findByCategoriesContaining(category);
            genreOpt.ifPresent(genre ->
                    genreToCategories.computeIfAbsent(genre, k -> new ArrayList<>()).add(category)
            );
        }

        // Build GenreResponse list
        List<GenreResponse> genreResponses = genreToCategories.entrySet().stream()
                .map(entry -> new GenreResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        ArtistResponse response = new ArtistResponse(artist, genreResponses);
        applyStats(response, bookingStatsFor(List.of(artist.getId())).get(artist.getId()));
        return response;
    }

    @Override
    public ArtistResponse updateArtistHourlyRate(double hourlyRate) {
        Artist artist = loggedInUserUtil.getLoggedInArtist();
        artist.setHourlyRate(hourlyRate);
        Artist updatedArtist = artistRepository.save(artist);

        // Group artist's categories by their parent genre
        List<Category> artistCategories = artist.getGenres();
        Map<Genre, List<Category>> genreToCategories = new HashMap<>();
        for (Category category : artistCategories) {
            Optional<Genre> genreOpt = genreRepository.findByCategoriesContaining(category);
            genreOpt.ifPresent(genre ->
                    genreToCategories.computeIfAbsent(genre, k -> new ArrayList<>()).add(category)
            );
        }

        // Build GenreResponse list
        List<GenreResponse> genreResponses = genreToCategories.entrySet().stream()
                .map(entry -> new GenreResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        return new ArtistResponse(artist, genreResponses);

    }
}