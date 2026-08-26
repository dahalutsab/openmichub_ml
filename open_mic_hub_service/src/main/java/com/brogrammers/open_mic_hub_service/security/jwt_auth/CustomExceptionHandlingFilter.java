package com.brogrammers.open_mic_hub_service.security.jwt_auth;

import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
@Component
public class CustomExceptionHandlingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (ResponseStatusException ex) {
            log.error("ResponseStatusException caught in filter: {}", ex.getMessage(), ex);
            setErrorResponse(response, HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Exception caught in filter: {}", ex.getMessage(), ex);
            setErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", ex.getMessage());
        }
    }

    private void setErrorResponse(HttpServletResponse response, HttpStatus status, String message, String details) throws IOException {

        GlobalApiResponse apiResponse = new GlobalApiResponse();
        apiResponse.setStatus("success");
        apiResponse.setMessage(message);

        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}