package com.sattrack.exception;

public class TleNotFoundException extends RuntimeException {
    public TleNotFoundException(String message) {
        super(message);
    }
}
