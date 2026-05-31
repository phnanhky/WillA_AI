package com.willa.ai.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.willa.ai.backend.dto.request.PersonaSettingsRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.dto.response.PersonaResponse;
import com.willa.ai.backend.service.PersonaService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Persona chỉ truy cập qua {@code /me} — không có endpoint theo userId để tránh IDOR.
 */
@RestController
@RequestMapping("/api/users/me/persona")
@Validated
@RequiredArgsConstructor
@Tag(name = "Persona", description = "User design persona (owner-only)")
@SecurityRequirement(name = "bearerAuth")
public class PersonaController {

    private final PersonaService personaService;

    @GetMapping
    public ResponseEntity<ApiResponse> getMyPersona(Authentication authentication) {
        String email = requireAuthenticatedEmail(authentication);
        PersonaResponse persona = personaService.getMyPersona(email);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Persona retrieved successfully")
                .data(persona)
                .build());
    }

    @PatchMapping("/settings")
    public ResponseEntity<ApiResponse> updateSettings(
            Authentication authentication,
            @Valid @RequestBody PersonaSettingsRequest request) {
        String email = requireAuthenticatedEmail(authentication);
        PersonaResponse persona = personaService.updateMySettings(email, request);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Persona settings updated")
                .data(persona)
                .build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse> refreshPersona(Authentication authentication) {
        String email = requireAuthenticatedEmail(authentication);
        PersonaResponse persona = personaService.refreshMyPersona(email, true);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Persona refreshed successfully")
                .data(persona)
                .build());
    }

    private static String requireAuthenticatedEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
        return authentication.getName();
    }
}
