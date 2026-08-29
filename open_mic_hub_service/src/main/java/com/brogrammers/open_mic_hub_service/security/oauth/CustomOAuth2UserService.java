package com.brogrammers.open_mic_hub_service.security.oauth;

import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Sign-in for providers that are plain OAuth2 rather than OpenID Connect — Facebook here.
 *
 * <p>Google does not come through this path. It asks for the {@code openid} scope, so Spring
 * Security runs the OIDC flow and calls {@link CustomOidcUserService} instead. Both do the same
 * thing and delegate the account work to {@link OAuth2AccountService}.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuth2AccountService accountService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);
        AuthProvider provider = AuthProvider.fromRegistrationId(
                request.getClientRegistration().getRegistrationId());

        UserEntity user = accountService.resolveAccount(
                provider, OAuth2UserDetails.from(provider, oAuth2User));

        // Rebuilt rather than passed through. The principal name becomes the JWT subject, and
        // every other part of this application identifies a user by email — the access-token
        // filter looks the caller up by it on each request. The authorities have to be this
        // platform's roles too, not the provider's scopes.
        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());
        attributes.put("email", user.getEmailId());

        return new DefaultOAuth2User(accountService.authorities(user), attributes, "email");
    }
}
