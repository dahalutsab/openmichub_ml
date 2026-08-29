package com.brogrammers.open_mic_hub_service.security.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Which social providers are actually usable right now.
 *
 * <p>The client cannot work this out for itself, and asking it to be told separately was a
 * mistake: configuring the backend then left a second switch to flip in the front end, so a
 * correctly configured Google client showed no button, and a Facebook button appeared whether or
 * not Facebook had ever been set up. One source of truth removes both.
 */
@Service
@RequiredArgsConstructor
public class SocialProviderService {

    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    /** Registration ids ("google", "facebook") with complete credentials, in a stable order. */
    public List<String> enabledProviders() {
        ClientRegistrationRepository repository = clientRegistrations.getIfAvailable();
        // Absent when no provider is configured at all — the repository bean is only defined then.
        if (!(repository instanceof InMemoryClientRegistrationRepository registrations)) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(registrations.spliterator(), false)
                .map(ClientRegistration::getRegistrationId)
                .sorted()
                .toList();
    }
}
