package com.restaurant.service.exception;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;

@ControllerAdvice(basePackages = "com.restaurant.service.controller", name = "restaurantGlobalExceptionHandler")
public class GlobalExceptionHandler {

    @Autowired
    private HttpServletRequest servletRequest;

    @Autowired
    private ObjectMapper objectMapper;

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleResourceNotFoundException(ResourceNotFoundException ex,
            WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getDescription(false).replace("uri=", ""));
        return buildResponse(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGlobalException(Exception ex, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                ex.getMessage(),
                request.getDescription(false).replace("uri=", ""));
        return buildResponse(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<?> handleResourceAlreadyExistsException(ResourceAlreadyExistsException ex,
            WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getDescription(false).replace("uri=", ""));
        return buildResponse(errorResponse, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<?> buildResponse(ErrorResponse errorResponse, HttpStatus status) {
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

