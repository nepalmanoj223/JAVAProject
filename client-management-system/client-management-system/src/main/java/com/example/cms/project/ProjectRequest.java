package com.example.cms.project;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body for POST /api/projects and PUT /api/projects/{id}.
 * status and priority are optional: new projects default to PLANNED / MEDIUM,
 * and an update without them keeps the current values.
 */
public record ProjectRequest(

        @NotBlank(message = "Project name is required")
        @Size(max = 120, message = "Project name must be at most 120 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @NotBlank(message = "Client is required")
        String clientId,

        ProjectStatus status,

        ProjectPriority priority,

        @NotNull(message = "Start date is required (YYYY-MM-DD)")
        LocalDate startDate,

        @NotNull(message = "Due date is required (YYYY-MM-DD)")
        LocalDate dueDate,

        @DecimalMin(value = "0.00", message = "Budget cannot be negative")
        @Digits(integer = 12, fraction = 2, message = "Budget can have at most 12 digits and 2 decimal places")
        BigDecimal budget
) {
}
