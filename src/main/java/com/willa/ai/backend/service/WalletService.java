package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.response.WalletResponse;

public interface WalletService {
    // Get wallet for the currently authenticated user
    WalletResponse getMyWallet(String email);
    
    // Get wallet for a specific user (Used by Admin)
    WalletResponse getWalletByUserId(Long userId);
    
    // Add tokens manually (Used by Admin, or internal logic like rewards/promos)
    WalletResponse addTokens(Long userId, Long amount);
    
    // Deduct tokens (Used internally when User chats with AI or generates Images)
    // Returns boolean indicating success. Will throw Exception if insufficient funds.
    boolean deductTokens(String email, Long amount);
}
