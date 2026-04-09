package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.*;
import com.willa.ai.backend.dto.response.AuthResponse;
import com.willa.ai.backend.dto.response.TokenResponse;

public interface AuthenticationService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    TokenResponse refreshToken(RefreshTokenRequest request);
    AuthResponse googleLogin(GoogleLoginRequest request);
    AuthResponse facebookLogin(FacebookLoginRequest request);
    void logout(String token);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    void verifyEmail(String email, String token);
}
