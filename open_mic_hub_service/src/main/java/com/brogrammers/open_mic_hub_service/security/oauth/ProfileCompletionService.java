package com.brogrammers.open_mic_hub_service.security.oauth;

import com.brogrammers.open_mic_hub_service.auth.dto.request.CompleteProfileRequest;
import com.brogrammers.open_mic_hub_service.auth.dto.request.artist.ArtistGenreRequest;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Category;
import com.brogrammers.open_mic_hub_service.user_management.genere.repository.CategoryRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.user_management.user.repository.UserInfoRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.Roles;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import com.brogrammers.open_mic_hub_service.user_management.user.role.repository.RolesRepository;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.Slugs;
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity.VirtualCoin;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.repository.VirtualCoinRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Finishes an account created through a social provider.
 *
 * <p>Runs exactly once per account. The window is the {@code onboardingRequired} flag, and closing
 * it is what stops this becoming a way to change your own role whenever you like. Choosing to
 * perform is not an escalation — anyone can register as an artist through the public form — but a
 * one-time question that quietly stayed answerable forever would be a surprise, and surprises in
 * role assignment are worth designing out.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileCompletionService {

    private final UserInfoRepository userInfoRepository;
    private final RolesRepository rolesRepository;
    private final ArtistRepository artistRepository;
    private final CategoryRepository categoryRepository;
    private final VirtualCoinRepository virtualCoinRepository;
    private final LoggedInUserUtil loggedInUserUtil;

    /** Whether the caller still owes an answer, for the client to decide where to send them. */
    public boolean isPending() {
        return loggedInUserUtil.getLoggedInUser().isOnboardingRequired();
    }

    @Transactional
    public Map<String, Object> complete(CompleteProfileRequest request) {
        UserEntity user = userInfoRepository.findById(loggedInUserUtil.getLoggedInUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));

        if (!user.isOnboardingRequired()) {
            // Already answered. Not an error worth alarming anyone about, but not something to
            // silently redo either: roles are not self-service after this point.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This account has already been set up.");
        }

        if (request.getLocation() != null && !request.getLocation().isBlank()) {
            user.setLocation(request.getLocation().trim());
        }

        if (Boolean.TRUE.equals(request.getPerforming())) {
            becomeArtist(user, request);
        } else {
            log.info("{} completed setup as an organizer", user.getEmailId());
        }

        user.setOnboardingRequired(false);
        userInfoRepository.save(user);

        // The access token already in the browser stays valid: authorities are read from the
        // database on every request, not from the token, so a role changed here takes effect on
        // the very next call without anyone signing in again.
        return Map.of(
                "roles", user.getRoles().stream().map(Roles::getName).toList(),
                "onboardingRequired", false);
    }

    private void becomeArtist(UserEntity user, CompleteProfileRequest request) {
        String stageName = request.getStageName() == null ? "" : request.getStageName().trim();
        if (stageName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A stage name is required to perform.");
        }

        List<Long> categoryIds = request.getGenres() == null ? List.of()
                : request.getGenres().stream()
                        .filter(genre -> genre != null && genre.getSubGenreIds() != null)
                        .flatMap(genre -> genre.getSubGenreIds().stream())
                        .toList();
        if (categoryIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose at least one style you perform.");
        }
        List<Category> categories = categoryRepository.findAllById(categoryIds);
        if (categories.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Those styles are not recognised.");
        }

        Roles artistRole = rolesRepository.findByName(UserRole.ARTIST.name())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));

        // Replaces rather than adds. The ORGANIZER role was provisional, given only so the
        // account had one while the question was outstanding; leaving both would hand this person
        // two dashboards and make every role check ambiguous.
        user.getRoles().clear();
        user.getRoles().add(artistRole);

        Artist artist = new Artist();
        artist.setUser(user);
        artist.setStageName(stageName);
        // Assigned once and never rewritten, matching the registration path: a shared profile link
        // keeps working after a rename.
        artist.setSlug(Slugs.uniqueSlug(stageName, artistRepository::existsBySlug));
        artist.setBio(request.getBio());
        artist.setGenres(categories);
        if (request.getHourlyRate() != null) {
            artist.setHourlyRate(request.getHourlyRate());
        }
        Artist savedArtist = artistRepository.save(artist);

        // Every artist needs a wallet from the outset, or their first payout has nowhere to land.
        VirtualCoin wallet = new VirtualCoin();
        wallet.setArtist(savedArtist);
        wallet.setBalance(0.0);
        virtualCoinRepository.save(wallet);

        log.info("{} completed setup as the artist '{}'", user.getEmailId(), stageName);
    }
}
