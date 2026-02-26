package com.sattrack.exception;

public class SatelliteNotFoundException extends RuntimeException {
    public SatelliteNotFoundException(String identifier) {
        super("Satellite not found: " + identifier);
    }
}
