package com.brogrammers.open_mic_hub_service.security.jwt_auth;

import com.brogrammers.open_mic_hub_service.config.RSAKeyRecord;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Interceptor for validating JWT tokens during WebSocket handshake and extracting the username.
 *
 * @author Utsab Dahal
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenUtils jwtTokenUtils;
    private final JwtTokenDecoder jwtTokenDecoder;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            log.debug("Extracted JWT from Authorization header");
        } else {
            String query = request.getURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && pair[0].equals("access_token")) {
                        token = pair[1];
                        log.debug("Extracted JWT from access_token query parameter");
                        break;
                    }
                }
            }
        }

        if (token == null) {
            log.warn("Missing Authorization header or access_token query parameter");
            return false;
        }

        try {
            JWTClaimsSet claims = jwtTokenDecoder.decodeAndVerify(token);

            String username = jwtTokenUtils.getUserName(claims);
            if (username == null || username.isBlank()) {
                log.warn("Invalid token: username missing or empty in JWT claims. Claims: {}", claims.toJSONObject());
                return false;
            }

            if (!jwtTokenUtils.isTokenValid(claims, jwtTokenUtils.userDetails(username))) {
                log.warn("Token validation failed for user: {}", username);
                return false;
            }

            attributes.put("username", username);
            if (request instanceof org.springframework.http.server.ServletServerHttpRequest servletRequest) {
                String sessionId = servletRequest.getServletRequest().getSession().getId();
                log.info("WebSocket handshake successful for user: {}, session ID: {}", username, sessionId);
            }
            return true;

        } catch (Exception e) {
            // Deliberately does not log the token itself.
            log.warn("WebSocket handshake rejected: {}", e.getMessage());
            return false;
        }
    }


    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
                               @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler appointedHandler,
                               Exception exception) {
        // No action needed after handshake
    }
}