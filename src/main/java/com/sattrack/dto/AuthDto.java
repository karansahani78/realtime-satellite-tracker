package com.sattrack.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;

public class AuthDto {

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password
    ) {}

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    @Builder
    public record AuthResponse(
            String token,
            String tokenType,
            long expiresIn,
            String username,
            String role
    ) {}

    public record UpdatePreferencesRequest(
            Double defaultLatitude,
            Double defaultLongitude,
            Double defaultAltitudeMeters,
            String timezoneId
    ) {}

    @Builder
    public record UserProfile(
            String username,
            String email,
            Double defaultLatitude,
            Double defaultLongitude,
            Double defaultAltitudeMeters,
            String timezoneId,
            java.util.List<String> favoriteSatelliteIds
    ) {}
}
