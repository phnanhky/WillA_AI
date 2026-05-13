package com.willa.ai.backend.dto.request;

import com.willa.ai.backend.entity.enums.WorkspaceRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteMemberRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email is invalid")
    private String email;
    
    private WorkspaceRole role;
}
