package com.brogrammers.open_mic_hub_service.user_management.artist.availability.service.implementation;

import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.dto.request.AvailabilityRequest;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.dto.response.ArtistCalendarResponse;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.dto.response.AvailabilityResponse;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.ArtistAvailability;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.entity.AvailabilityTime;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.repository.ArtistAvailabilityRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.availability.service.ArtistAvailabilityService;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.dto.response.UnAvailabilityResponse;
import com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.entity.ArtistUnavailability;
import com.brogrammers.open_mic_hub_service.user_management.artist.unavailability.repository.ArtistUnavailabilityRepository;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ArtistAvailabilityServiceImplementation implements ArtistAvailabilityService {
    private final ArtistAvailabilityRepository artistAvailabilityRepository;
    private final ArtistUnavailabilityRepository artistUnavailabilityRepository;
    private final LoggedInUserUtil loggedInUserUtil;
    private final ArtistRepository artistRepository;

    @Override
    @Transactional
    public AvailabilityResponse addAvailability(AvailabilityRequest request) {
        Artist artist = loggedInUserUtil.getLoggedInArtist();

        // Check for overlapping times
        List<ArtistAvailability> existing = artistAvailabilityRepository.findAll()
                .stream()
                .filter(a -> a.getArtist().getId().equals(artist.getId()) && a.getDayOfWeek() == request.getDayOfWeek())
                .toList();

        for (AvailabilityTime newTime : mapToAvailabilityTimes(request)) {
            for (ArtistAvailability avail : existing) {
                for (AvailabilityTime existingTime : avail.getAvailabilityTimes()) {
                    if (isOverlapping(existingTime.getStartTime(), existingTime.getEndTime(), newTime.getStartTime(), newTime.getEndTime())) {
                        throw new IllegalArgumentException("Overlapping time slot detected");
                    }
                }
            }
        }

        ArtistAvailability availability = new ArtistAvailability();
        availability.setArtist(artist);
        availability.setDayOfWeek(request.getDayOfWeek());
        availability.setAvailabilityTimes(mapToAvailabilityTimes(request));
        return new AvailabilityResponse(artistAvailabilityRepository.save(availability));
    }

    @Override
    @Transactional
    public AvailabilityResponse updateAvailability(Long availabilityId, AvailabilityRequest request) {
        ArtistAvailability availability = artistAvailabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new IllegalArgumentException("Availability not found"));

        Artist artist = loggedInUserUtil.getLoggedInArtist();

        // Check for overlapping times (excluding current)
        List<ArtistAvailability> existing = artistAvailabilityRepository.findAll()
                .stream()
                .filter(a -> a.getArtist().getId().equals(artist.getId())
                        && a.getDayOfWeek() == request.getDayOfWeek()
                        && !a.getId().equals(availabilityId))
                .toList();

        for (AvailabilityTime newTime : mapToAvailabilityTimes(request)) {
            for (ArtistAvailability avail : existing) {
                for (AvailabilityTime existingTime : avail.getAvailabilityTimes()) {
                    if (isOverlapping(existingTime.getStartTime(), existingTime.getEndTime(), newTime.getStartTime(), newTime.getEndTime())) {
                        throw new IllegalArgumentException("Overlapping time slot detected");
                    }
                }
            }
        }

        availability.setDayOfWeek(request.getDayOfWeek());
        availability.setAvailabilityTimes(mapToAvailabilityTimes(request));
        return new AvailabilityResponse(artistAvailabilityRepository.save(availability));
    }

    @Override
    @Transactional
    public void deleteAvailability(Long availabilityId) {
        artistAvailabilityRepository.deleteById(availabilityId);
    }

    @Override
    public List<AvailabilityResponse> getAvailabilities() {
        Artist artist = loggedInUserUtil.getLoggedInArtist();
        return artistAvailabilityRepository.findAll()
                .stream()
                .filter(a -> a.getArtist().getId().equals(artist.getId()))
                .map(AvailabilityResponse::new)
                .toList();
    }

    @Override
    @Transactional
    public UnAvailabilityResponse addUnavailability(ArtistUnavailability unavailability) {
        Artist artist = loggedInUserUtil.getLoggedInArtist();
        unavailability.setArtist(artist);
        return new UnAvailabilityResponse(artistUnavailabilityRepository.save(unavailability));
    }

    @Override
    @Transactional
    public void deleteUnavailability(Long unavailabilityId) {
        artistUnavailabilityRepository.deleteById(unavailabilityId);
    }

    @Override
    public List<UnAvailabilityResponse> getUnavailabilities() {
        Artist artist = loggedInUserUtil.getLoggedInArtist();
        return artistUnavailabilityRepository.findAll()
                .stream()
                .filter(u -> u.getArtist().getId().equals(artist.getId()))
                .map(UnAvailabilityResponse::new)
                .toList();
    }

    @Override
    public List<AvailabilityResponse> getAvailabilitiesByStageName(String stageName) {
        return artistAvailabilityRepository.findAll()
                .stream()
                .filter(a -> a.getArtist().getStageName().equalsIgnoreCase(stageName))
                .map(AvailabilityResponse::new)
                .toList();
    }

    @Override
    public List<UnAvailabilityResponse> getUnavailabilitiesByStageName(String stageName) {
        return artistUnavailabilityRepository.findAll()
                .stream()
                .filter(u -> u.getArtist().getStageName().equalsIgnoreCase(stageName))
                .map(UnAvailabilityResponse::new)
                .toList();
    }

    @Override
    public ArtistCalendarResponse getArtistCalendarByStageName(String stageName) {
        Artist artist = artistRepository.findByStageName(stageName)
                .orElseThrow(() -> new IllegalArgumentException("Artist with stage name " + stageName + " not found"));
        return new ArtistCalendarResponse(
                artist.getId(),
                getAvailabilitiesByStageName(stageName),
                getUnavailabilitiesByStageName(stageName)
        );
    }

    // Helper methods
    private List<AvailabilityTime> mapToAvailabilityTimes(AvailabilityRequest request) {
        List<AvailabilityTime> times = new ArrayList<>();
        if (request.getAvailabilityTimes() != null) {
            request.getAvailabilityTimes().forEach(req -> {
                times.add(new AvailabilityTime(null, req.getStartTime(), req.getEndTime()));
            });
        }
        return times;
    }

    private boolean isOverlapping(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return !start1.isAfter(end2) && !start2.isAfter(end1);
    }
}