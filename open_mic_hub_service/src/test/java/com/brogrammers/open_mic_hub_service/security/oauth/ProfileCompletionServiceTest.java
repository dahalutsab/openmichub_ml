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
import com.brogrammers.open_mic_hub_service.util.logged_in_user.LoggedInUserUtil;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity.VirtualCoin;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.repository.VirtualCoinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The one-time question a social sign-in leaves open, and the fact that it stays one-time.
 *
 * <p>The window closing is the part worth pinning down. Choosing to perform is not an escalation —
 * anyone can register as an artist through the public form — but an endpoint that quietly stayed
 * answerable would be a standing way to change your own role, which is not what a setup step is.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileCompletionServiceTest {

    @Mock private UserInfoRepository userInfoRepository;
    @Mock private RolesRepository rolesRepository;
    @Mock private ArtistRepository artistRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private VirtualCoinRepository virtualCoinRepository;
    @Mock private LoggedInUserUtil loggedInUserUtil;

    @InjectMocks private ProfileCompletionService profileCompletionService;

    private UserEntity account;
    private Roles organizerRole;
    private Roles artistRole;

    @BeforeEach
    void setUp() {
        organizerRole = new Roles();
        organizerRole.setName(UserRole.ORGANIZER.name());
        artistRole = new Roles();
        artistRole.setName(UserRole.ARTIST.name());

        account = new UserEntity();
        account.setId(11L);
        account.setEmailId("aastha@example.com");
        account.setAuthProvider(AuthProvider.GOOGLE);
        account.setProviderId("sub-1");
        account.setOnboardingRequired(true);
        account.getRoles().add(organizerRole);

        when(loggedInUserUtil.getLoggedInUser()).thenReturn(account);
        when(userInfoRepository.findById(11L)).thenReturn(Optional.of(account));
        when(userInfoRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(rolesRepository.findByName(UserRole.ARTIST.name())).thenReturn(Optional.of(artistRole));
        when(artistRepository.save(any(Artist.class))).thenAnswer(i -> i.getArgument(0));
        when(artistRepository.existsBySlug(any())).thenReturn(false);
    }

    private CompleteProfileRequest performing(String stageName, List<Long> subGenreIds) {
        CompleteProfileRequest request = new CompleteProfileRequest();
        request.setPerforming(true);
        request.setStageName(stageName);
        request.setBio("Two decades of bebop.");
        request.setHourlyRate(3500.0);
        request.setGenres(List.of(new ArtistGenreRequest(1L, subGenreIds)));
        return request;
    }

    private void categoriesExist(List<Long> ids) {
        List<Category> categories = ids.stream().map(id -> {
            Category category = new Category();
            category.setId(id);
            category.setName("Bebop");
            return category;
        }).toList();
        when(categoryRepository.findAllById(anyList())).thenReturn(categories);
    }

    @Test
    @DisplayName("booking: keeps the organizer role and closes the question")
    void completesAsOrganizer() {
        CompleteProfileRequest request = new CompleteProfileRequest();
        request.setPerforming(false);
        request.setLocation("Pokhara");

        var result = profileCompletionService.complete(request);

        assertThat(account.isOnboardingRequired()).isFalse();
        assertThat(account.getLocation()).isEqualTo("Pokhara");
        assertThat(result.get("roles")).isEqualTo(List.of(UserRole.ORGANIZER.name()));
        verify(artistRepository, never()).save(any());
    }

    @Test
    @DisplayName("performing: creates the artist profile and closes the question")
    void completesAsArtist() {
        categoriesExist(List.of(4L, 5L));

        var result = profileCompletionService.complete(performing("Bibek Subedi", List.of(4L, 5L)));

        ArgumentCaptor<Artist> saved = ArgumentCaptor.forClass(Artist.class);
        verify(artistRepository).save(saved.capture());
        assertThat(saved.getValue().getStageName()).isEqualTo("Bibek Subedi");
        assertThat(saved.getValue().getHourlyRate()).isEqualTo(3500.0);
        assertThat(saved.getValue().getSlug()).isNotBlank();
        assertThat(saved.getValue().getGenres()).hasSize(2);

        assertThat(account.isOnboardingRequired()).isFalse();
        assertThat(result.get("roles")).isEqualTo(List.of(UserRole.ARTIST.name()));
    }

    @Test
    @DisplayName("performing: replaces the provisional organizer role rather than adding to it")
    void replacesProvisionalRole() {
        // Holding both would give this person two dashboards and make every role check ambiguous.
        categoriesExist(List.of(4L));

        profileCompletionService.complete(performing("Bibek Subedi", List.of(4L)));

        assertThat(account.getRoles()).containsExactly(artistRole);
    }

    @Test
    @DisplayName("performing: opens a wallet, so the first payout has somewhere to land")
    void opensAWallet() {
        categoriesExist(List.of(4L));

        profileCompletionService.complete(performing("Bibek Subedi", List.of(4L)));

        ArgumentCaptor<VirtualCoin> wallet = ArgumentCaptor.forClass(VirtualCoin.class);
        verify(virtualCoinRepository).save(wallet.capture());
        assertThat(wallet.getValue().getBalance()).isZero();
    }

    @Test
    @DisplayName("performing: a stage name is required")
    void requiresStageName() {
        categoriesExist(List.of(4L));

        assertThatThrownBy(() -> profileCompletionService.complete(performing("   ", List.of(4L))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("stage name is required");

        assertThat(account.isOnboardingRequired()).isTrue();
    }

    @Test
    @DisplayName("performing: at least one style is required")
    void requiresAtLeastOneStyle() {
        CompleteProfileRequest request = performing("Bibek Subedi", List.of());

        assertThatThrownBy(() -> profileCompletionService.complete(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at least one style");

        verify(artistRepository, never()).save(any());
    }

    @Test
    @DisplayName("answered once, and not again")
    void refusesASecondAnswer() {
        // Otherwise this is a standing self-service role change rather than a setup step.
        account.setOnboardingRequired(false);
        categoriesExist(List.of(4L));

        assertThatThrownBy(() -> profileCompletionService.complete(performing("Sneaky", List.of(4L))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already been set up");

        verify(artistRepository, never()).save(any());
        assertThat(account.getRoles()).containsExactly(organizerRole);
    }

    @Test
    @DisplayName("reports whether the question is still outstanding")
    void reportsPendingState() {
        assertThat(profileCompletionService.isPending()).isTrue();

        account.setOnboardingRequired(false);
        assertThat(profileCompletionService.isPending()).isFalse();
    }
}
