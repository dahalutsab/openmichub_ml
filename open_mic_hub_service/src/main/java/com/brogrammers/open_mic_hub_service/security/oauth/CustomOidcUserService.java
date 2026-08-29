package com.brogrammers.open_mic_hub_service.security.oauth;

import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Sign-in for OpenID Connect providers — Google here.
 *
 * <p>This exists because {@code userInfoEndpoint().userService(...)} does not cover OIDC. A
 * provider that requests the {@code openid} scope goes through {@code OidcUserService}, a separate
 * extension point, and configuring only the plain one fails silently in the worst way: the
 * handshake completes, Spring builds a principal out of the raw ID token, and no account is ever
 * created. A token then gets issued for a subject with no row behind it, so every request made
 * with it is rejected — while the sign-in itself looked like it worked.
 */
@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final OAuth2AccountService accountService;

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(request);
        AuthProvider provider = AuthProvider.fromRegistrationId(
                request.getClientRegistration().getRegistrationId());

        UserEntity user = accountService.resolveAccount(
                provider, OAuth2UserDetails.from(provider, oidcUser));

        // "email" as the name attribute, so the principal name — and therefore the JWT subject —
        // is the address this application knows the person by, not the provider's opaque subject
        // id. The authorities are this platform's roles rather than the OIDC scopes.
        return new DefaultOidcUser(
                accountService.authorities(user),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "email");
    }
}
