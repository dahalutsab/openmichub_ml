package com.brogrammers.open_mic_hub_service.security.oauth;

/** Who vouches for an account's identity. */
public enum AuthProvider {

    /** Registered here, authenticates with a password this application stores. */
    LOCAL,

    GOOGLE,
    FACEBOOK;

    /**
     * Maps a Spring registration id ("google", "facebook") onto a provider.
     *
     * @throws IllegalArgumentException if the registration is not one this application supports,
     *         which means someone added a provider to the configuration without teaching the user
     *         provisioning about it.
     */
    public static AuthProvider fromRegistrationId(String registrationId) {
        for (AuthProvider provider : values()) {
            if (provider != LOCAL && provider.name().equalsIgnoreCase(registrationId)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unsupported sign-in provider: " + registrationId);
    }
}
