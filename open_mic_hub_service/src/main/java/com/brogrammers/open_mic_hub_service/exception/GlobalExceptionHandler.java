package com.brogrammers.open_mic_hub_service.exception;



import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalErrorResponse;
import com.brogrammers.open_mic_hub_service.exception.custom.*;
import com.brogrammers.open_mic_hub_service.payment.gateway.PaymentVerificationException;
import com.brogrammers.open_mic_hub_service.security.ratelimit.RateLimitExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import com.brogrammers.open_mic_hub_service.payment.gateway.PaymentGatewayException;
import com.brogrammers.open_mic_hub_service.payment.gateway.PaymentVerificationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler extends BaseController {
    private final ObjectMapper objectMapper;

    private static final String EXCEPTION = "Exception: ";

    @ExceptionHandler(GeneralException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<GlobalErrorResponse> handlingGeneralException(GeneralException exception){
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }


    /**
     * A payment gateway that refuses or cannot answer is a bad gateway, not an auth failure.
     *
     * <p>Khalti replies 401 to an unconfigured secret key. Letting that status through made the API
     * answer 401 on a valid session, and the browser's interceptor cleared the token and redirected
     * to the login screen — so a server misconfiguration read to the user as "payment failed, and
     * also you are logged out".
     */
    @ExceptionHandler(PaymentGatewayException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ResponseEntity<GlobalErrorResponse> handlingPaymentGatewayException(PaymentGatewayException exception) {
        return errorResponse(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<GlobalErrorResponse> handlingEntityNotFoundException(EntityNotFoundException exception){
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    }

    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<GlobalErrorResponse> handleInvalidTokenException (InvalidTokenException exception) {
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), exception);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<GlobalErrorResponse> handlingDataIntegrityViolationException(DataIntegrityViolationException exception){
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.BAD_REQUEST, "Data integrity violation occurred", exception);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<GlobalErrorResponse> handlingAccessDeniedException(AccessDeniedException exception){
        log.warn("Access denied: {}", exception.getMessage());
        return errorResponse(HttpStatus.FORBIDDEN, "Access denied", exception);
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<GlobalErrorResponse> handlingIOException(IOException exception){
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Input/output error", exception);
    }

    /**
     * Services raise this for rules the caller broke — "you have already reviewed this booking",
     * "insufficient balance". The message is written for the user, so it is passed through rather
     * than replaced with the word "Illegal argument", which told them nothing.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<GlobalErrorResponse> handlingIllegalArgumentException (IllegalArgumentException exception) {
        log.warn("Rejected request: {}", exception.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }

    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<GlobalErrorResponse> handlingNullPointerException (NullPointerException exception) {
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Null pointer exception", exception);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<GlobalErrorResponse> handlingConstraintViolationException (ConstraintViolationException exception) {
        log.error("Constraint violation", exception);
        return errorResponse(HttpStatus.BAD_REQUEST, "Constraint violation", exception);
    }

    @ExceptionHandler(HttpClientErrorException.MethodNotAllowed.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResponseEntity<GlobalErrorResponse> handlingMethodNotAllowedException (HttpClientErrorException.MethodNotAllowed exception) {
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", exception);
    }

    @ExceptionHandler(HttpClientErrorException.Unauthorized.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<GlobalErrorResponse> handleUnauthorizedException (HttpClientErrorException.Unauthorized exception) {
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", exception);
    }

    @ExceptionHandler(InvalidEmailException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<GlobalErrorResponse> handleInvalidEmailException (InvalidEmailException exception) {
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.BAD_REQUEST, "Invalid Email", exception);
    }

    @ExceptionHandler(InvalidPasswordException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<GlobalErrorResponse> handleInvalidPasswordException (InvalidPasswordException exception) {
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.BAD_REQUEST, "Invalid Email", exception);
    }

    @ExceptionHandler(InvalidPhoneFormat.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<GlobalErrorResponse> handleInvalidPhoneFormat (InvalidPhoneFormat exception) {
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }

    /*
     * The four below all used to fall through to the catch-all and come back as
     * 500 "An unexpected error occurred". A caller could not tell a mistake of their own from a
     * broken server, and every malformed request landed in the logs looking like an incident.
     */

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<GlobalErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
        log.warn("Missing request parameter: {}", exception.getParameterName());
        return errorResponse(HttpStatus.BAD_REQUEST,
                "Required parameter '" + exception.getParameterName() + "' is missing.", exception);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<GlobalErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        // The cause carries the useful detail - which field, and what could not be parsed - while
        // the top-level message leaks the deserializer's class names.
        Throwable cause = exception.getMostSpecificCause();
        log.warn("Unreadable request body: {}", cause.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST,
                "The request body could not be read: " + cause.getMessage(), exception);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<GlobalErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("Parameter type mismatch on '{}': {}", exception.getName(), exception.getValue());
        return errorResponse(HttpStatus.BAD_REQUEST,
                "Parameter '" + exception.getName() + "' has the wrong type.", exception);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResponseEntity<GlobalErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        log.warn("Method not supported: {}", exception.getMethod());
        return errorResponse(HttpStatus.METHOD_NOT_ALLOWED,
                "The " + exception.getMethod() + " method is not supported on this endpoint.", exception);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<GlobalErrorResponse> handleNoResourceFound(NoResourceFoundException exception) {
        log.warn("No handler for {}", exception.getResourcePath());
        return errorResponse(HttpStatus.NOT_FOUND, "No such endpoint.", exception);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<GlobalErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        // A rejected field is the caller's mistake, not an incident. Logged at warn, and without
        // the stack trace that used to accompany every mistyped form.
        List<String> problems = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        log.warn("Rejected request: {}", problems);

        // The first message is the headline; the rest are listed so a caller fixing a form does
        // not have to submit it once per invalid field. `exception.getMessage()` is deliberately
        // not used - it dumps the controller signature and every field's message codes.
        String headline = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(org.springframework.validation.FieldError::getDefaultMessage)
                .orElse("The request is not valid.");

        return errorResponse(HttpStatus.BAD_REQUEST, headline, String.join("; ", problems));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<GlobalErrorResponse> handleUsernameNotFoundException (UsernameNotFoundException exception) {
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), exception);
    }

    @ExceptionHandler(MessagingException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<GlobalErrorResponse> handleMessagingException (MessagingException exception) {
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Messaging exception", exception);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<GlobalErrorResponse> handleAuthenticationFailedException (AuthenticationFailedException exception) {
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.UNAUTHORIZED, "Authentication failed", exception);
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public void handleExpiredJwtException(ExpiredJwtException e, HttpServletResponse response) throws IOException {
        handleException(response, e, HttpStatus.UNAUTHORIZED.value());
    }

    /**
     * Preserves the status a service deliberately chose. Without this these fell through to the
     * catch-all below, so a rejected login answered 500 instead of 401.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<GlobalErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        log.warn("{} - {}", exception.getStatusCode(), exception.getReason());
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return errorResponse(status, exception.getReason(), exception);
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<GlobalErrorResponse> handleAuthentication(AuthenticationException exception) {
        log.warn("Authentication failed: {}", exception.getMessage());
        return errorResponse(HttpStatus.UNAUTHORIZED, "Invalid credentials", exception);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ResponseEntity<GlobalErrorResponse> handleRateLimit(RateLimitExceededException exception) {
        return errorResponse(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), exception);
    }

    @ExceptionHandler(PaymentVerificationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ResponseEntity<GlobalErrorResponse> handlePaymentVerification(PaymentVerificationException exception) {
        log.error(EXCEPTION, exception);
        return errorResponse(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
    }

    /**
     * Last-resort handler. Deliberately does not echo {@code e.getMessage()} to the caller — it
     * used to, which leaked stack-level detail (SQL fragments, upstream URLs) to anyone who could
     * trigger an error. The full exception still goes to the log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalErrorResponse> handleException(Exception e) {
        log.error(EXCEPTION, e);
        GlobalErrorResponse errorResponse = new GlobalErrorResponse(
                LocalDateTime.now(),
                "An unexpected error occurred",
                "Please try again. If the problem persists, contact support.",
                String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value())
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void handleException(HttpServletResponse response, Exception e, int status) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType("application/json");
        GlobalErrorResponse exceptionResponse = new GlobalErrorResponse(
                LocalDateTime.now(),
                "JWT token has expired",
                e.getMessage(),
                String.valueOf(status)
        );
        response.getWriter().write(objectMapper.writeValueAsString(exceptionResponse));
        response.getWriter().flush();
        response.getWriter().close();
    }

    @ExceptionHandler(BadCredentialsException.class)
    public void handleBadCredentialsException(BadCredentialsException e, HttpServletResponse response) throws IOException {
        handleException(response, e, HttpStatus.UNAUTHORIZED.value());
    }

}

