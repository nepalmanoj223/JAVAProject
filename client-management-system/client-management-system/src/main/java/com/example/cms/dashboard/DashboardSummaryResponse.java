package com.example.cms.dashboard;

import com.example.cms.client.ClientResponse;
import com.example.cms.project.ProjectResponse;

import java.util.List;

/** Returned by GET /api/dashboard/summary. Every number is counted from MongoDB. */
public record DashboardSummaryResponse(
        long totalClients,
        long activeClients,
        long leads,
        long inactiveClients,
        long closedClients,
        long inactiveAndClosedClients,
        long totalProjects,
        long projectsInProgress,
        long completedProjects,
        long overdueProjects,
        long totalInteractions,
        List<ClientResponse> recentClients,
        List<ProjectResponse> overdueProjectList
) {
}
