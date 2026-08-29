package com.brogrammers.open_mic_hub_service.util.logged_in_user;

import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.user_management.user.repository.UserInfoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoggedInUserUtil {

    private final UserInfoRepository userInfoRepository;
    private final ArtistRepository artistRepository;

    public UserEntity getLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = getUsername(authentication);

            return userInfoRepository.findByEmailId(username)
                    .orElseThrow(() -> new EntityNotFoundException("No user found with username: " + username));
        }
        throw new IllegalStateException("No authenticated user found in SecurityContext");
    }

    /**
     * The signed-in user, or empty when there is nobody.
     *
     * <p>For the public pages, which anyone can reach and a signed-in person often reaches while
     * carrying a token. {@link #getLoggedInUser()} is the right call when an account is required
     * and its absence is an error; this one is for the places where knowing who is asking changes
     * the answer but is not needed to produce one — personalised ranking, and recording what
     * someone searched for.
     *
     * <p>Never throws. An anonymous request, a token for an account that has since been deleted,
     * and an unreachable database all read the same way here: nobody is signed in.
     */
    public Optional<UserEntity> findLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        try {
            return userInfoRepository.findByEmailId(getUsername(authentication));
        } catch (Exception e) {
            log.debug("Could not resolve the signed-in user: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** The signed-in user's id, or null. The shape the discovery calls want. */
    public Long currentUserIdOrNull() {
        return findLoggedInUser().map(UserEntity::getId).orElse(null);
    }

    private String getUsername(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        String username;

        switch (principal) {
            case UserDetails userDetails -> username = userDetails.getUsername(); // Extract username from UserDetails
            case String user -> username = user;
            default -> throw new IllegalStateException("Authentication principal is not of expected type: " + principal.getClass());
        }
        return username;
    }

    public Artist getLoggedInArtist() {
        UserEntity user = getLoggedInUser(); // gets user from token
        return artistRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("No artist found for the logged-in user: " + user.getEmailId()));
    }





}