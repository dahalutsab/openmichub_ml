package com.brogrammers.open_mic_hub_service.config.web_socket;

import com.brogrammers.open_mic_hub_service.security.jwt_auth.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final WebSocketAuthChannelInterceptorAdapter webSocketAuthChannelInterceptorAdapter;

    /**
     * The same origins the REST API accepts.
     *
     * <p>This used to be the literal {@code http://localhost:4200}, so chat only ever worked on a
     * developer's default dev-server port: any other port, and every deployed environment, had its
     * SockJS handshake rejected with 403 while the rest of the API worked normally.
     */
    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthChannelInterceptorAdapter);
    }
}