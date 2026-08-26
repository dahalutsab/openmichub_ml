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
        if (isWhitelisted(request)) {
            log.info("Skipping JWT filter for: {}", requestURI);
            filterChain.doFilter(request, response);
            return;
        }

        if (requestURI.startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }

        log.info("[JwtAccessTokenFilter] Filtering request: {}", requestURI);

        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.error("[JwtAccessTokenFilter] Invalid or missing Authorization header");
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        try {
            // Decrypt AND verify the signature. Decryption alone would let anyone holding the
            // public key mint a token for any subject.
            JWTClaimsSet claims = jwtTokenDecoder.decodeAndVerify(token);

            final String userName = jwtTokenUtils.getUserName(claims);
            if (!userName.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) {
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
        } catch (JwtTokenDecoder.InvalidAccessTokenException e) {
            log.warn("[JwtAccessTokenFilter] {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        filterChain.doFilter(request, response);
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