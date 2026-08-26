package com.brogrammers.open_mic_hub_service.config.web_socket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.security.Principal;
import java.util.Collections;
@Component
@Slf4j
public class WebSocketAuthChannelInterceptorAdapter implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        String sessionId = accessor.getSessionId();

        log.info("Intercepting STOMP command: {}, session ID: {}", command, sessionId);

        if (command == StompCommand.CONNECT || command == StompCommand.SEND || command == StompCommand.SUBSCRIBE) {
            String username = (String) accessor.getSessionAttributes().get("username");

            if (username == null || username.isBlank()) {
                log.warn("No username found in session attributes for STOMP {}, session ID: {}", command, sessionId);
                return message;
            }

            if (accessor.getUser() == null) {
                Principal userPrincipal = new UsernamePasswordAuthenticationToken(
                        username, null, Collections.emptyList()
                );
                accessor.setUser(userPrincipal);
                log.info("Set Principal for user: {} in session ID: {}", username, sessionId);
            }
        }

        // Return a new message with the updated accessor
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());    }
}