package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.response.WalletResponse;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.Wallet;
import com.willa.ai.backend.exception.ResourceNotFoundException;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.repository.WalletRepository;
import com.willa.ai.backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public WalletResponse getMyWallet(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return mapToResponse(getOrCreateWallet(user));
    }

    @Override
    @Transactional
    public WalletResponse getWalletByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        return mapToResponse(getOrCreateWallet(user));
    }

    @Override
    @Transactional
    public WalletResponse addTokens(Long userId, Long amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        Wallet wallet = getOrCreateWallet(user);
        wallet.setTokenBalance(wallet.getTokenBalance() + amount);
        wallet.setTotalRecharged(wallet.getTotalRecharged() + amount);
        
        Wallet savedWallet = walletRepository.save(wallet);
        return mapToResponse(savedWallet);
    }

    @Override
    @Transactional
    public boolean deductTokens(String email, Long amount) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        Wallet wallet = getOrCreateWallet(user);
        
        if (wallet.getTokenBalance() < amount) {
            throw new IllegalArgumentException("Insufficient token balance. Please upgrade your plan or recharge your wallet.");
        }
        
        wallet.setTokenBalance(wallet.getTokenBalance() - amount);
        walletRepository.save(wallet);
        return true;
    }

    // Helper method to auto-create wallet if physical record doesn't exist
    private Wallet getOrCreateWallet(User user) {
        return walletRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Wallet newWallet = Wallet.builder()
                            .user(user)
                            .tokenBalance(0L)
                            .totalRecharged(0L)
                            .build();
                    return walletRepository.save(newWallet);
                });
    }

    private WalletResponse mapToResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUser().getId())
                .tokenBalance(wallet.getTokenBalance())
                .totalRecharged(wallet.getTotalRecharged())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }
}
