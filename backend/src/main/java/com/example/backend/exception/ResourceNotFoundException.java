package com.example.backend.exception;

/**
 * Thrown when a requested resource (user, post, friendship, etc.) does not exist.
 * Maps to HTTP 404 NOT FOUND in {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
