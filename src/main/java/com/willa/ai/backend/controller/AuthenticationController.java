package com.willa.ai.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.willa.ai.backend.dto.request.ForgotPasswordRequest;
import com.willa.ai.backend.dto.request.FacebookLoginRequest;
import com.willa.ai.backend.dto.request.GoogleLoginRequest;
import com.willa.ai.backend.dto.request.LoginRequest;
import com.willa.ai.backend.dto.request.RefreshTokenRequest;
import com.willa.ai.backend.dto.request.RegisterRequest;
import com.willa.ai.backend.dto.request.ResetPasswordRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.dto.response.AuthResponse;
import com.willa.ai.backend.service.AuthenticationService;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Authentication", description = "APIs for Authentication")
public class AuthenticationController {

    @Autowired
    private AuthenticationService authenticationService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authenticationService.register(request);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("User registered successfully")
                    .data(response)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AuthResponse response = authenticationService.login(request);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Login successful")
                    .data(response)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            var response = authenticationService.refreshToken(request);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Token refreshed successfully")
                    .data(response)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequest request) {
        try {
            AuthResponse response = authenticationService.googleLogin(request);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Google login successful")
                    .data(response)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/facebook-login")
    public ResponseEntity<?> facebookLogin(@RequestBody FacebookLoginRequest request) {
        try {
            AuthResponse response = authenticationService.facebookLogin(request);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Facebook login successful")
                    .data(response)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        try {
            if (authorizationHeader == null || authorizationHeader.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.builder()
                                .status(false)
                                .message("Missing authorization header")
                                .build());
            }

            String trimmedHeader = authorizationHeader.trim();
            if (!trimmedHeader.toLowerCase().startsWith("bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.builder()
                                .status(false)
                                .message("Invalid authorization header format")
                                .build());
            }

            String token = trimmedHeader.substring(7).trim(); // Remove "Bearer " prefix
            authenticationService.logout(token);
            
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Logout successful")
                    .build());
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Invalid or expired token")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.builder()
                                .status(false)
                                .message(e.getMessage())
                                .build());
            }
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            authenticationService.forgotPassword(request);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Password reset email sent")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            authenticationService.resetPassword(request);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Password reset successful")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam String email, @RequestParam String token) {
        try {
            AuthResponse response = authenticationService.verifyEmail(email, token);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Email verified successfully")
                    .data(response)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }
}
