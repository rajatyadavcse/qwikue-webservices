package com.kitchen.order.exception;
 
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
 
import java.time.LocalDateTime;
import java.util.stream.Collectors;
 
@ControllerAdvice(basePackages = "com.kitchen.order.controller", name = "orderGlobalExceptionHandler")
public class GlobalExceptionHandler {
 
    @Autowired
    private HttpServletRequest servletRequest;
 
    @Autowired
    private ObjectMapper objectMapper;
 
    // ── 404 Not Found ─────────────────────────────────────────────────────────
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        return buildResponse(ex.getMessage(), HttpStatus.NOT_FOUND, request);
    }
 
    // ── 422 Unprocessable Entity ───────────────────────────────────────────────
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<?> handleInvalidStatusTransition(
            InvalidStatusTransitionException ex, WebRequest request) {
        return buildResponse(ex.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY, request);
    }
 
    // ── 400 Bad Request (bean validation failures) ────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return buildResponse(message, HttpStatus.BAD_REQUEST, request);
    }
 
    // ── 400 Bad Request (manual validation in service layer) ──────────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        return buildResponse(ex.getMessage(), HttpStatus.BAD_REQUEST, request);
    }
 
    // ── 503 Service Unavailable (restaurant-service down) ─────────────────────
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<?> handleExternalServiceException(
            ExternalServiceException ex, WebRequest request) {
        return buildResponse(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE, request);
    }
 
    // ── 500 Internal Server Error (catch-all) ─────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGlobalException(Exception ex, WebRequest request) {
        return buildResponse(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }
 
    // ── Helper ────────────────────────────────────────────────────────────────
    private ResponseEntity<?> buildResponse(String message, HttpStatus status, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getDescription(false).replace("uri=", "")
        );
 
        String acceptHeader = servletRequest.getHeader("Accept");
        if (acceptHeader != null && acceptHeader.contains("text/event-stream")) {
            try {
                String json = objectMapper.writeValueAsString(errorResponse);
                return ResponseEntity.status(status)
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(json);
            } catch (Exception e) {
                // Fallback to regular JSON response if serialization fails
            }
        }
 
        return new ResponseEntity<>(errorResponse, status);
    }
}
