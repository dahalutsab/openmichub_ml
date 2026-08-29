package com.brogrammers.open_mic_hub_service.security.oauth;

import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

/**
 * The handful of fields this application needs out of a provider's profile.
 *
 * <p>Google and Facebook do not agree on much: Google returns OpenID Connect claims ({@code sub},
 * {@code email_verified}), Facebook returns Graph API fields ({@code id}, a nested {@code picture}
 * object) and no verification flag at all. Normalising here keeps that difference in one readable
 * place instead of spread through the provisioning logic.
 */
public record OAuth2UserDetails(
        String providerId,
        String email,
        String name,
        String pictureUrl,
        boolean emailVerified) {

    public static OAuth2UserDetails from(AuthProvider provider, OAuth2User user) {
        Map<String, Object> attributes = user.getAttributes();
        return switch (provider) {
            case GOOGLE -> google(attributes);
            case FACEBOOK -> facebook(attributes);
            case LOCAL -> throw new IllegalArgumentException("LOCAL is not a social provider.");
        };
    }

    private static OAuth2UserDetails google(Map<String, Object> attributes) {
        return new OAuth2UserDetails(
                string(attributes, "sub"),
                string(attributes, "email"),
                string(attributes, "name"),
                string(attributes, "picture"),
                // Google states this explicitly. A Google account can carry an unverified address,
                // and that address must not be trusted to identify anyone.
                Boolean.TRUE.equals(attributes.get("email_verified")));
    }

    @SuppressWarnings("unchecked")
    private static OAuth2UserDetails facebook(Map<String, Object> attributes) {
        String picture = null;
        // picture -> data -> url, present only when the picture field is requested.
        if (attributes.get("picture") instanceof Map<?, ?> outer
                && outer.get("data") instanceof Map<?, ?> data) {
            picture = data.get("url") instanceof String url ? url : null;
        }
        return new OAuth2UserDetails(
                string(attributes, "id"),
                string(attributes, "email"),
                string(attributes, "name"),
                picture,
                // The Graph API exposes no verification flag. Facebook requires a confirmed
                // address to create an account, so the address is treated as verified — but that
                // is an assumption about Facebook's own policy, not something it tells us here.
                true);
    }

    private static String string(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
