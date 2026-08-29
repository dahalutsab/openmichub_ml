package com.brogrammers.open_mic_hub_service.security.oauth;

import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.user_management.user.repository.UserInfoRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.Roles;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import com.brogrammers.open_mic_hub_service.user_management.user.role.repository.RolesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a provider's profile into an account on this platform.
 *
 * <p>Runs after the authorization code has been exchanged, so the profile is one the provider
 * vouches for rather than anything the browser supplied.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserInfoRepository userInfoRepository;
    private final RolesRepository rolesRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);
        AuthProvider provider = AuthProvider.fromRegistrationId(
                request.getClientRegistration().getRegistrationId());
        OAuth2UserDetails details = OAuth2UserDetails.from(provider, oAuth2User);

        if (details.providerId() == null || details.providerId().isBlank()) {
            throw error("no_subject", provider + " did not identify the account.");
        }
        if (details.email() == null || details.email().isBlank()) {
            // Facebook can withhold the address when the person declines the email permission,
            // and every account here is keyed by one.
            throw error("no_email", "Your " + provider + " account did not share an email address.");
        }

        UserEntity user = resolve(provider, details);

        // Rebuilt rather than passed through, for two reasons. The principal name becomes the JWT
        // subject, and every other part of this application identifies a user by email — the
        // access-token filter looks the caller up by it on each request. And the authorities have
        // to be this platform's roles, not the provider's scopes, or the token's scope claim comes
        // out describing Google's permissions instead of what the person may do here.
        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());
        attributes.put("email", user.getEmailId());

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .toList();

        return new DefaultOAuth2User(authorities, attributes, "email");
    }

    private UserEntity resolve(AuthProvider provider, OAuth2UserDetails details) {
        // Seen before under this provider: the ordinary returning-user case. Matched on the
        // provider's subject id, so it still works if the person has since changed their address.
        var byProvider = userInfoRepository.findByAuthProviderAndProviderId(provider, details.providerId());
        if (byProvider.isPresent()) {
            return refresh(byProvider.get(), details);
        }

        var byEmail = userInfoRepository.findByEmailId(details.email());
        if (byEmail.isPresent()) {
            return link(byEmail.get(), provider, details);
        }

        return create(provider, details);
    }

    /**
     * Attaches a provider to an account that already exists under the same address.
     *
     * <p>This is the dangerous path, and the guard is the reason it is written out. Linking on a
     * matching email means whoever controls that address at the provider gets the existing
     * account — so an address the provider has not verified is refused outright. Without that
     * check, anyone able to set an arbitrary unverified address on a provider account could sign
     * in as an existing user here.
     */
    private UserEntity link(UserEntity existing, AuthProvider provider, OAuth2UserDetails details) {
        if (!details.emailVerified()) {
            log.warn("Refused to link {} to {}: the provider has not verified the address",
                    provider, existing.getEmailId());
            throw error("email_unverified",
                    "Verify your email address with " + provider + " before signing in with it.");
        }
        if (existing.getAuthProvider() != AuthProvider.LOCAL) {
            // Already claimed by a different provider. Overwriting would move the account from
            // one identity to another on the strength of a shared address.
            log.warn("Refused to link {} to {}: already held by {}",
                    provider, existing.getEmailId(), existing.getAuthProvider());
            throw error("provider_conflict",
                    "This email is already registered with " + existing.getAuthProvider()
                            + ". Sign in with that instead.");
        }

        log.info("Linking {} sign-in to the existing account {}", provider, existing.getEmailId());
        existing.setAuthProvider(provider);
        existing.setProviderId(details.providerId());
        // The provider has confirmed the address, which is what this flag records.
        existing.setVerified(true);
        return userInfoRepository.save(refresh(existing, details));
    }

    private UserEntity create(AuthProvider provider, OAuth2UserDetails details) {
        Roles role = rolesRepository.findByName(UserRole.ORGANIZER.name())
                .orElseThrow(() -> error("role_missing", "The ORGANIZER role is not configured."));

        UserEntity user = new UserEntity();
        user.setEmailId(details.email());
        user.setFullName(details.name() != null ? details.name() : details.email());
        user.setProfileImage(details.pictureUrl());
        user.setAuthProvider(provider);
        user.setProviderId(details.providerId());
        // No password: the provider authenticates this account, and the database constraint
        // permits a null one only for exactly this case.
        user.setPassword(null);
        // Signing up here means booking artists, which is what self-registration already assumes.
        user.getRoles().add(role);
        // The provider has already confirmed the address, so there is no second email to send.
        user.setVerified(true);
        user.setActive(true);

        log.info("Creating an account for {} via {}", details.email(), provider);
        return userInfoRepository.save(user);
    }

    /** Keeps the display name and picture current without touching identity or roles. */
    private UserEntity refresh(UserEntity user, OAuth2UserDetails details) {
        boolean changed = false;
        if (details.name() != null && !details.name().equals(user.getFullName())) {
            user.setFullName(details.name());
            changed = true;
        }
        // Only fills a gap. Overwriting would undo a picture the person uploaded here.
        if (user.getProfileImage() == null && details.pictureUrl() != null) {
            user.setProfileImage(details.pictureUrl());
            changed = true;
        }
        return changed ? userInfoRepository.save(user) : user;
    }

    private OAuth2AuthenticationException error(String code, String message) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, message, null), message);
    }
}
