package com.ahicode.bidflow.bidding.controllers;

import com.ahicode.bidflow.bidding.exceptions.*;
import com.ahicode.bidflow.common.dtos.ErrorResponse;
import com.ahicode.bidflow.common.exceptions.UserNotAllowedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({BidTooLowException.class, LotNotActiveException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(
            RuntimeException ex, HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    @ExceptionHandler({
            UserNotAllowedException.class,
            OwnerCannotBidException.class,
            OwnerCannotCancelException.class
    })
    public ResponseEntity<ErrorResponse> handleForbidden(
            RuntimeException ex, HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request);
    }

    @ExceptionHandler(LotNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            LotNotFoundException ex, HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request
    ) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternalException(
            Exception ex, HttpServletRequest request
    ) {
        log.error("[InternalServerError] Unhandled exception {}: {}", ex.getMessage(), ex);

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An expected error occurred. Please try again later", request
        );
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus httpStatus, String error, String message, HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                httpStatus.value(),
                error,
                message,
                request.getRequestURI()
        );

        return ResponseEntity.status(httpStatus).body(response);
    }
}
