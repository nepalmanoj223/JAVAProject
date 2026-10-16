package com.example.cms.interaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body for POST /api/clients/{clientId}/interactions and PUT /api/interactions/{id}.
 * The client is taken from the URL, so it is not part of the body.
 */
public record InteractionRequest(

        @NotNull(message = "Type is required (CALL, EMAIL, MEETING or NOTE)")
        InteractionType type,

        @NotBlank(message = "Subject is required")
        @Size(max = 200, message = "Subject must be at most 200 characters")
        String subject,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @NotNull(message = "Interaction date is required (YYYY-MM-DD)")
        @PastOrPresent(message = "Interaction date cannot be in the future")
        LocalDate interactionDate
) {
}
