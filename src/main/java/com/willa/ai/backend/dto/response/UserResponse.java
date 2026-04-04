package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String role;
    private Boolean isEnabled;
    private Boolean isActive;
    private Boolean isStudent;
    private Boolean requiresReview;
    private LocalDateTime studentVerifiedAt;
    private String firebaseUid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
