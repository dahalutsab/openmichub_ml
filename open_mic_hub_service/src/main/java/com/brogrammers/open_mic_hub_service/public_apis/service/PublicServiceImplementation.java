package com.brogrammers.open_mic_hub_service.public_apis.service;

import com.brogrammers.open_mic_hub_service.booking.repository.BookingRepository;
import com.brogrammers.open_mic_hub_service.public_apis.response.CountsResponse;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.user_management.genere.dto.response.GenreResponse;
import com.brogrammers.open_mic_hub_service.user_management.genere.repository.GenreRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublicServiceImplementation implements PublicService {
    private final UserInfoRepository userInfoRepository;
    private final ArtistRepository artistRepository;
    private final BookingRepository bookingRepository;
    private final GenreRepository genreRepository;

    @Override
    public CountsResponse getCounts() {
        log.info("Fetching counts for users, artists, and bookings");

        long userCount = userInfoRepository.count();
        long artistCount = artistRepository.count();
        long bookingCount = bookingRepository.count();

        log.info("Counts fetched - Users: {}, Artists: {}, Bookings: {}", userCount, artistCount, bookingCount);

        return new CountsResponse(userCount, artistCount, bookingCount);
    }

    @Override
    public List<GenreResponse> getAllGenres() {
        log.info("Fetching all genres");
        List<GenreResponse> genres = genreRepository.findAll().stream()
                .map(GenreResponse::new)
                .toList();
        log.info("Fetched {} genres", genres.size());
        return genres;
    }
}
