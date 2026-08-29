package com.brogrammers.open_mic_hub_service.security.jwt_auth;

import com.brogrammers.open_mic_hub_service.config.RSAKeyRecord;
import com.brogrammers.open_mic_hub_service.security.WHITE_LIST_URLS;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Enumeration;

@RequiredArgsConstructor
@Slf4j
public class JwtAccessTokenFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final RSAKeyRecord rsaKeyRecord;
    private final JwtTokenUtils jwtTokenUtils;
    private final JwtTokenDecoder jwtTokenDecoder;


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
//        logRequestDetails(request);

        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isWhitelisted(request)) {
            // Public, but not necessarily anonymous.
            //
            // These paths never require a token and this filter used to stop here, which meant a
            // signed-in person browsing the public pages arrived at the controller as nobody. That
            // is fine for reading an artist profile and wrong for everything that wants to know
            // who is asking - personalised ranking, and recording what someone looked at.
            //
            // So a token that happens to be there is honoured, and one that is absent, expired or
            // forged is ignored rather than refused: the endpoint is public, and a stale token in
            // an old tab must not turn browsing into a 401.
            try {
                authenticate(request);
            } catch (Exception e) {
                log.debug("[JwtAccessTokenFilter] Ignoring an unusable token on the public path {}: {}",
                        requestURI, e.getMessage());
            }
            filterChain.doFilter(request, response);
            return;
        }

        log.info("[JwtAccessTokenFilter] Filtering request: {}", requestURI);

        try {
            authenticate(request);
        } catch (JwtTokenDecoder.InvalidAccessTokenException e) {
            log.warn("[JwtAccessTokenFilter] {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Puts the bearer token's user into the security context, if there is a usable one.
     *
     * <p>Does nothing when no bearer token is present. A token that is present but not valid raises
     * {@link JwtTokenDecoder.InvalidAccessTokenException}, which a protected path turns into a 401
     * and a public path ignores.
     */
    private void authenticate(HttpServletRequest request) {
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }

        // Decrypt AND verify the signature. Decryption alone would let anyone holding the
        // public key mint a token for any subject.
        JWTClaimsSet claims = jwtTokenDecoder.decodeAndVerify(authHeader.substring(7));

        final String userName = jwtTokenUtils.getUserName(claims);
        if (userName.isEmpty() || isAlreadyAuthenticated()) {
            return;
        }

        UserDetails userDetails = jwtTokenUtils.userDetails(userName);
        if (jwtTokenUtils.isTokenValid(claims, userDetails)) {
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            UsernamePasswordAuthenticationToken createdToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            createdToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            securityContext.setAuthentication(createdToken);
            SecurityContextHolder.setContext(securityContext);
        }
    }

    private boolean isAlreadyAuthenticated() {
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    /**
     * Whether this request is on the public allow-list.
     *
     * <p>Matches on method as well as path, and uses Spring's Ant matcher rather than treating the
     * pattern as a regex. The previous version ignored the method entirely, so a path allow-listed
     * for GET also skipped token processing for POST and DELETE.
     */
    private boolean isWhitelisted(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        return Arrays.stream(WHITE_LIST_URLS.values())
                .filter(entry -> PATH_MATCHER.match(entry.getUrl(), uri))
                .anyMatch(entry -> Arrays.stream(entry.getMethods())
                        .anyMatch(allowed -> allowed.matches(method)));
    }


    private void logRequestDetails(HttpServletRequest request) {
        log.info("Request URI: {}", request.getRequestURI());
        log.info("HTTP Method: {}", request.getMethod());
        log.info("Headers:");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            log.info("{}: {}", headerName, headerValue);
        }
    }
}