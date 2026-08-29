package com.brogrammers.open_mic_hub_service.security.oauth;

import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.user_management.user.repository.UserInfoRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.Roles;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import com.brogrammers.open_mic_hub_service.user_management.user.role.repository.RolesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deciding which account a social profile belongs to.
 *
 * <p>The linking rules are the security-sensitive part of social sign-in. Attaching a provider to
 * an existing account on the strength of a matching email hands that account to whoever controls
 * the address at the provider — so an address the provider has not verified must be refused. These
 * cases exist to keep that guard from being relaxed by accident.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuth2AccountServiceTest {

    @Mock private UserInfoRepository userInfoRepository;
    @Mock private RolesRepository rolesRepository;

    @InjectMocks private OAuth2AccountService accountService;

    private Roles organizerRole;

    @BeforeEach
    void setUp() {
        organizerRole = new Roles();
        organizerRole.setName(UserRole.ORGANIZER.name());
        when(rolesRepository.findByName(UserRole.ORGANIZER.name())).thenReturn(Optional.of(organizerRole));
        when(userInfoRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(userInfoRepository.findByAuthProviderAndProviderId(any(), any())).thenReturn(Optional.empty());
        when(userInfoRepository.findByEmailId(any())).thenReturn(Optional.empty());
    }

    private OAuth2UserDetails details(String sub, String email, boolean verified) {
        return new OAuth2UserDetails(sub, email, "Aastha Sharma", "https://pic", verified);
    }

    private UserEntity localAccount(String email) {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setEmailId(email);
        user.setPassword("$2a$10$hashed");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setVerified(true);
        return user;
    }

    @Nested
    @DisplayName("a first sign-in")
    class NewAccount {

        @Test
        @DisplayName("creates an organizer with no password, already verified")
        void createsAccount() {
            UserEntity user = accountService.resolveAccount(
                    AuthProvider.GOOGLE, details("sub-1", "aastha@example.com", true));

            assertThat(user.getEmailId()).isEqualTo("aastha@example.com");
            assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
            assertThat(user.getProviderId()).isEqualTo("sub-1");
            // The provider authenticates this account; there is nothing here to store or check.
            assertThat(user.getPassword()).isNull();
            // The provider already confirmed the address, so there is no second email to send.
            assertThat(user.isVerified()).isTrue();
            assertThat(user.getRoles()).containsExactly(organizerRole);
        }

        @Test
        @DisplayName("is asked what it is here for")
        void marksOnboardingRequired() {
            UserEntity user = accountService.resolveAccount(
                    AuthProvider.GOOGLE, details("sub-1", "aastha@example.com", true));

            assertThat(user.isOnboardingRequired()).isTrue();
        }

        @Test
        @DisplayName("takes the name and picture the provider supplied")
        void copiesProfileFields() {
            UserEntity user = accountService.resolveAccount(
                    AuthProvider.GOOGLE, details("sub-1", "aastha@example.com", true));

            assertThat(user.getFullName()).isEqualTo("Aastha Sharma");
            assertThat(user.getProfileImage()).isEqualTo("https://pic");
        }
    }

    @Nested
    @DisplayName("a returning sign-in")
    class ReturningAccount {

        @Test
        @DisplayName("is matched on the provider's subject id, not the address")
        void matchesOnProviderId() {
            UserEntity existing = new UserEntity();
            existing.setId(3L);
            existing.setEmailId("old-address@example.com");
            existing.setAuthProvider(AuthProvider.GOOGLE);
            existing.setProviderId("sub-1");
            existing.setFullName("Aastha Sharma");
            when(userInfoRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "sub-1"))
                    .thenReturn(Optional.of(existing));

            // The address at Google has changed since last time; the account must not fork.
            UserEntity user = accountService.resolveAccount(
                    AuthProvider.GOOGLE, details("sub-1", "new-address@example.com", true));

            assertThat(user.getId()).isEqualTo(3L);
            assertThat(user.getEmailId()).isEqualTo("old-address@example.com");
        }

        @Test
        @DisplayName("is not asked to set up again")
        void doesNotReOnboard() {
            UserEntity existing = new UserEntity();
            existing.setId(3L);
            existing.setEmailId("aastha@example.com");
            existing.setAuthProvider(AuthProvider.GOOGLE);
            existing.setProviderId("sub-1");
            existing.setOnboardingRequired(false);
            when(userInfoRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "sub-1"))
                    .thenReturn(Optional.of(existing));

            UserEntity user = accountService.resolveAccount(
                    AuthProvider.GOOGLE, details("sub-1", "aastha@example.com", true));

            assertThat(user.isOnboardingRequired()).isFalse();
        }

        @Test
        @DisplayName("does not overwrite a picture uploaded here")
        void keepsLocallyUploadedPicture() {
            UserEntity existing = new UserEntity();
            existing.setId(3L);
            existing.setEmailId("aastha@example.com");
            existing.setAuthProvider(AuthProvider.GOOGLE);
            existing.setProviderId("sub-1");
            existing.setProfileImage("/media/uploaded-by-the-user.jpg");
            when(userInfoRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "sub-1"))
                    .thenReturn(Optional.of(existing));

            UserEntity user = accountService.resolveAccount(
                    AuthProvider.GOOGLE, details("sub-1", "aastha@example.com", true));

            assertThat(user.getProfileImage()).isEqualTo("/media/uploaded-by-the-user.jpg");
        }
    }

    @Nested
    @DisplayName("linking to an account that already exists")
    class Linking {

        @Test
        @DisplayName("links when the provider has verified the address")
        void linksVerifiedAddress() {
            when(userInfoRepository.findByEmailId("aastha@example.com"))
                    .thenReturn(Optional.of(localAccount("aastha@example.com")));

            UserEntity user = accountService.resolveAccount(
                    AuthProvider.GOOGLE, details("sub-1", "aastha@example.com", true));

            assertThat(user.getId()).isEqualTo(7L);
            assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
            assertThat(user.getProviderId()).isEqualTo("sub-1");
        }

        @Test
        @DisplayName("refuses an address the provider has not verified")
        void refusesUnverifiedAddress() {
            // Without this, anyone able to set an arbitrary unverified address on a provider
            // account could sign in as an existing user here.
            when(userInfoRepository.findByEmailId("aastha@example.com"))
                    .thenReturn(Optional.of(localAccount("aastha@example.com")));

            assertThatThrownBy(() -> accountService.resolveAccount(
                    AuthProvider.GOOGLE, details("attacker-sub", "aastha@example.com", false)))
                    .isInstanceOf(OAuth2AuthenticationException.class)
                    .hasMessageContaining("Verify your email");

            verify(userInfoRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses an address already claimed by a different provider")
        void refusesCrossProviderTakeover() {
            UserEntity heldByFacebook = localAccount("aastha@example.com");
            heldByFacebook.setAuthProvider(AuthProvider.FACEBOOK);
            heldByFacebook.setProviderId("fb-1");
            when(userInfoRepository.findByEmailId("aastha@example.com"))
                    .thenReturn(Optional.of(heldByFacebook));

            assertThatThrownBy(() -> accountService.resolveAccount(
                    AuthProvider.GOOGLE, details("sub-1", "aastha@example.com", true)))
                    .isInstanceOf(OAuth2AuthenticationException.class)
                    .hasMessageContaining("already registered with");
        }
    }

    @Nested
    @DisplayName("what the provider must supply")
    class RequiredClaims {

        @Test
        @DisplayName("an address, because every account here is keyed by one")
        void requiresEmail() {
            // Facebook withholds it when the person declines the email permission.
            assertThatThrownBy(() -> accountService.resolveAccount(
                    AuthProvider.FACEBOOK, details("fb-1", null, true)))
                    .isInstanceOf(OAuth2AuthenticationException.class)
                    .hasMessageContaining("did not share an email address");
        }

        @Test
        @DisplayName("a subject id, or every sign-in would look like a new person")
        void requiresSubject() {
            assertThatThrownBy(() -> accountService.resolveAccount(
                    AuthProvider.GOOGLE, details("  ", "aastha@example.com", true)))
                    .isInstanceOf(OAuth2AuthenticationException.class)
                    .hasMessageContaining("did not identify the account");
        }
    }

    @Test
    @DisplayName("authorities are this platform's roles, not the provider's scopes")
    void mapsRolesToAuthorities() {
        UserEntity user = accountService.resolveAccount(
                AuthProvider.GOOGLE, details("sub-1", "aastha@example.com", true));

        assertThat(accountService.authorities(user))
                .extracting("authority")
                .containsExactly("ROLE_ORGANIZER");
    }
}
