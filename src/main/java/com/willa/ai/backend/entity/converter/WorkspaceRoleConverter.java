package com.willa.ai.backend.entity.converter;

import com.willa.ai.backend.entity.enums.WorkspaceRole;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Map legacy roles (ADMIN, EDITOR, VIEWER) to MEMBER when reading from DB. */
@Converter(autoApply = true)
public class WorkspaceRoleConverter implements AttributeConverter<WorkspaceRole, String> {

    @Override
    public String convertToDatabaseColumn(WorkspaceRole role) {
        return role == null ? WorkspaceRole.MEMBER.name() : role.name();
    }

    @Override
    public WorkspaceRole convertToEntityAttribute(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) {
            return WorkspaceRole.MEMBER;
        }
        return switch (dbValue.toUpperCase()) {
            case "OWNER" -> WorkspaceRole.OWNER;
            case "MEMBER" -> WorkspaceRole.MEMBER;
            case "ADMIN", "EDITOR", "VIEWER" -> WorkspaceRole.MEMBER;
            default -> WorkspaceRole.MEMBER;
        };
    }
}
