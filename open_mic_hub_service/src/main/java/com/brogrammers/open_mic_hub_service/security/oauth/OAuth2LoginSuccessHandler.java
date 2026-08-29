package com.brogrammers.open_mic_hub_service.security.oauth;

import com.brogrammers.open_mic_hub_service.security.jwt_auth.JwtTokenGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.stream.Collectors;

/**
 * Issues this application's own token once a provider has vouched for the person, and hands it to
 * the client.
 *
 * <p>The token travels in the URL <em>fragment</em>, not the query string. A fragment is never
 * sent to a server, so it stays out of access logs, out of any proxy in between, and out of the
 * Referer header of whatever the page loads next. The client reads it, stores it, and clears it
 * from the address bar.
 *
 * <p>After this point the session is worthless: every subsequent request authenticates with the
 * bearer token like any other, and nothing here holds server-side login state.
 */
@Component
@Slf4j
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenGenerator jwtTokenGenerator;
    private final String redirectUri;

    public OAuth2LoginSuccessHandler(JwtTokenGenerator jwtTokenGenerator,
                                     @Value("${frontend.domain}") String frontendDomain,
                                     @Value("${frontend.oauth_redirect}") String oauthRedirectPath) {
        this.jwtTokenGenerator = jwtTokenGenerator;
        this.redirectUri = frontendDomain + oauthRedirectPath;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (response.isCommitted()) {
            log.warn("Response already committed; cannot complete the sign-in redirect");
            return;
        }

        String accessToken = jwtTokenGenerator.generateAccessToken(authentication);
        log.info("Social sign-in completed for {}", authentication.getName());

        // The handshake needed a session to hold the authorization request across the round trip
        // to the provider. It has served its purpose; leaving it would be a second way to
        // authenticate that nothing else in this stateless application expects.
        request.getSession().invalidate();

        // The client routes on role the moment it lands, exactly as it does after a password
        // login. It cannot read them from the token — that is encrypted, not merely signed — and
        // there is no current-user endpoint to ask, so they travel alongside it. They are the same
        // roles the password login already returns in its response body, and the token, not this
        // list, is what the server trusts.
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(Collectors.joining(","));

        String target = UriComponentsBuilder.fromUriString(redirectUri)
                .fragment("token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                          + "&roles=" + URLEncoder.encode(roles, StandardCharsets.UTF_8)
                          + "&expiresIn=" + (150 * 60))
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
