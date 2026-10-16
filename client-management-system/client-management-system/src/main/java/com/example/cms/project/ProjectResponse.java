package com.example.cms.project;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * The project data returned by the API.
 * "overdue" and "allowedNextStatuses" are calculated, not stored.
 */
public record ProjectResponse(
        String id,
        String name,
        String description,
        String clientId,
        String clientName,
        ProjectStatus status,
        ProjectPriority priority,
        LocalDate startDate,
        LocalDate dueDate,
        BigDecimal budget,
        boolean overdue,
        Set<ProjectStatus> allowedNextStatuses,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project project, String clientName, LocalDate today) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getClientId(),
                clientName,
                project.getStatus(),
                project.getPriority(),
                project.getStartDate(),
                project.getDueDate(),
                project.getBudget(),
                ProjectStatusRules.isOverdue(project.getDueDate(), project.getStatus(), today),
                ProjectStatusRules.allowedNextStatuses(project.getStatus()),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
