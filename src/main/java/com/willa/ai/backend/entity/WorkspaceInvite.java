package com.willa.ai.backend.entity;

import com.willa.ai.backend.entity.converter.WorkspaceRoleConverter;
import com.willa.ai.backend.entity.enums.InviteStatus;
import com.willa.ai.backend.entity.enums.WorkspaceRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_invites", indexes = {
        @Index(name = "idx_workspace_invite_workspace", columnList = "workspace_id"),
        @Index(name = "idx_workspace_invite_email", columnList = "email"),
        @Index(name = "idx_workspace_invite_token", columnList = "token", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false)
    private String email;

    @Convert(converter = WorkspaceRoleConverter.class)
    @Column(nullable = false)
    @Builder.Default
    private WorkspaceRole role = WorkspaceRole.MEMBER;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InviteStatus status = InviteStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    private User invitedBy;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
