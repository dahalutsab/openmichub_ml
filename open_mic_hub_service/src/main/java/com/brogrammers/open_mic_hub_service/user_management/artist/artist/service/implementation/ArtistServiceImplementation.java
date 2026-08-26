package com.brogrammers.open_mic_hub_service.user_management.artist.artist.service.implementation;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArtistServiceImplementation implements ArtistService {
    private final ArtistRepository artistRepository;
    private final UserInfoRepository userInfoRepository;
    private final GenreRepository genreRepository;
    private final LoggedInUserUtil loggedInUserUtil;
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
            return artists.map(artist -> {
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
        });
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

        return new ArtistResponse(artist, genreResponses);
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