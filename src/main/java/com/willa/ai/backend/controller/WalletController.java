package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.request.TokenAdjustRequest;
import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.dto.response.WalletResponse;
import com.willa.ai.backend.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "User Token & Wallet Management APIs")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/my")
    public ResponseEntity<ApiResponse> getMyWallet(Authentication authentication) {
        String email = authentication.getName();
        WalletResponse wallet = walletService.getMyWallet(email);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Wallet retrieved successfully")
                .data(wallet)
                .build());
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> getWalletByUserId(@PathVariable Long userId) {
        WalletResponse wallet = walletService.getWalletByUserId(userId);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("User wallet retrieved successfully")
                .data(wallet)
                .build());
    }

    @PostMapping("/user/{userId}/add")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> addTokens(
            @PathVariable Long userId,
            @Valid @RequestBody TokenAdjustRequest request) {
        WalletResponse wallet = walletService.addTokens(userId, request.getAmount());
        return ResponseEntity.ok(ApiResponse.builder()
                .status(true)
                .message("Tokens added successfully")
                .data(wallet)
                .build());
    }

    // Endpoint deducting tokens manually for testing/admin usage 
    // In production, deduction happens via Image Gen / Chat Backend Service automatically.
    @PostMapping("/deduct")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> testDeductTokens(
            @Valid @RequestBody TokenAdjustRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        boolean success = walletService.deductTokens(email, request.getAmount());
        return ResponseEntity.ok(ApiResponse.builder()
                .status(success)
                .message("Tokens deducted successfully")
                .build());
    }
}
