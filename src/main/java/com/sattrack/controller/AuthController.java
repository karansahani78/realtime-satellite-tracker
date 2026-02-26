package com.sattrack.controller;

import com.sattrack.dto.AuthDto;
import com.sattrack.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthDto.AuthResponse> register(
            @Valid @RequestBody AuthDto.RegisterRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.AuthResponse> login(
            @Valid @RequestBody AuthDto.LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/profile")
    public ResponseEntity<AuthDto.UserProfile> getProfile(
            Authentication authentication) {

        String username = authentication.getName(); // ✅ ALWAYS WORKS
        return ResponseEntity.ok(authService.getProfile(username));
    }

    @PatchMapping("/preferences")
    public ResponseEntity<AuthDto.UserProfile> updatePreferences(
            Authentication authentication,
            @RequestBody AuthDto.UpdatePreferencesRequest request) {

        String username = authentication.getName(); // ✅
        return ResponseEntity.ok(authService.updatePreferences(username, request));
    }
}