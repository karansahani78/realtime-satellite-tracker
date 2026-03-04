package com.sattrack.service;

import com.sattrack.dto.AuthDto;
import com.sattrack.entity.User;
import com.sattrack.exception.ConflictException;
import com.sattrack.repository.UserRepository;
import com.sattrack.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // ---------- REGISTER ----------
    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {

        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered: " + request.email());
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role("ROLE_USER")
                .enabled(true)
                .build();

        userRepository.save(user);

        log.info("Registered new user: {}", request.username());

        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return buildAuthResponse(token, user);
    }

    // ---------- LOGIN ----------
    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() ->
                        new IllegalStateException("User not found after authentication"));

        String token = jwtService.generateToken(user.getUsername(), user.getRole());

        log.info("User logged in: {}", request.username());
        return buildAuthResponse(token, user);
    }

    // ---------- UPDATE PREFERENCES ----------
    @Transactional
    public AuthDto.UserProfile updatePreferences(
            String username,
            AuthDto.UpdatePreferencesRequest req) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalStateException("User not found: " + username));

        if (req.defaultLatitude() != null)
            user.setDefaultLatitude(req.defaultLatitude());

        if (req.defaultLongitude() != null)
            user.setDefaultLongitude(req.defaultLongitude());

        if (req.defaultAltitudeMeters() != null)
            user.setDefaultAltitudeMeters(req.defaultAltitudeMeters());

        if (req.timezoneId() != null)
            user.setTimezoneId(req.timezoneId());

        userRepository.save(user);
        return toProfile(user);
    }

    // ---------- GET PROFILE (🔥 FIXED 🔥) ----------
    @Transactional(readOnly = true)
    public AuthDto.UserProfile getProfile(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalStateException("User not found: " + username));

        return toProfile(user);
    }

    // ---------- AUTH RESPONSE ----------
    private AuthDto.AuthResponse buildAuthResponse(String token, User user) {
        return AuthDto.AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }

    // ---------- USER PROFILE DTO (🔥 FIXED 🔥) ----------
    private AuthDto.UserProfile toProfile(User user) {

        return AuthDto.UserProfile.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .defaultLatitude(user.getDefaultLatitude())
                .defaultLongitude(user.getDefaultLongitude())
                .defaultAltitudeMeters(user.getDefaultAltitudeMeters())
                .timezoneId(user.getTimezoneId())
                .favoriteSatelliteIds(
                        user.getFavoriteSatelliteIds() == null
                                ? List.of()
                                : List.copyOf(user.getFavoriteSatelliteIds())
                )
                .build();
    }
}