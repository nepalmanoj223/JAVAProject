package com.example.cms.interaction;

import java.time.Instant;
import java.time.LocalDate;

/** The interaction data returned by the API. */
public record InteractionResponse(
        String id,
        String clientId,
        InteractionType type,
        String subject,
        String description,
        LocalDate interactionDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static InteractionResponse from(Interaction interaction) {
        return new InteractionResponse(
                interaction.getId(),
                interaction.getClientId(),
                interaction.getType(),
                interaction.getSubject(),
                interaction.getDescription(),
                interaction.getInteractionDate(),
                interaction.getCreatedAt(),
                interaction.getUpdatedAt());
    }
}
