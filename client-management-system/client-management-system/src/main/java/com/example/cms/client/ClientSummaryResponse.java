package com.example.cms.client;

/** Returned by GET /api/clients/{id}/summary. */
public record ClientSummaryResponse(
        ClientResponse client,
        long totalProjects,
        long projectsInProgress,
        long completedProjects,
        long overdueProjects,
        long totalInteractions
) {
}
