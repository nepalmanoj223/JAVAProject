package com.example.cms.dashboard;

import com.example.cms.client.ClientRepository;
import com.example.cms.client.ClientResponse;
import com.example.cms.client.ClientStatus;
import com.example.cms.interaction.InteractionRepository;
import com.example.cms.project.ProjectRepository;
import com.example.cms.project.ProjectResponse;
import com.example.cms.project.ProjectService;
import com.example.cms.project.ProjectStatus;
import com.example.cms.project.ProjectStatusRules;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** Rule 8: dashboard statistics calculated from the database on every request. */
@Service
public class DashboardService {

    private static final int OVERDUE_LIST_LIMIT = 5;

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final InteractionRepository interactionRepository;
    private final ProjectService projectService;

    public DashboardService(ClientRepository clientRepository,
                            ProjectRepository projectRepository,
                            InteractionRepository interactionRepository,
                            ProjectService projectService) {
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.interactionRepository = interactionRepository;
        this.projectService = projectService;
    }

    public DashboardSummaryResponse getSummary() {
        long inactive = clientRepository.countByStatus(ClientStatus.INACTIVE);
        long closed = clientRepository.countByStatus(ClientStatus.CLOSED);

        List<ClientResponse> recentClients = clientRepository.findTop5ByOrderByCreatedAtDesc().stream()
                .map(ClientResponse::from)
                .toList();
        List<ProjectResponse> overdueList = projectService.findOverdue().stream()
                .limit(OVERDUE_LIST_LIMIT)
                .toList();

        return new DashboardSummaryResponse(
                clientRepository.count(),
                clientRepository.countByStatus(ClientStatus.ACTIVE),
                clientRepository.countByStatus(ClientStatus.LEAD),
                inactive,
                closed,
                inactive + closed,
                projectRepository.count(),
                projectRepository.countByStatus(ProjectStatus.IN_PROGRESS),
                projectRepository.countByStatus(ProjectStatus.COMPLETED),
                projectRepository.countByDueDateBeforeAndStatusNotIn(LocalDate.now(),
                        ProjectStatusRules.FINISHED_STATUSES),
                interactionRepository.count(),
                recentClients,
                overdueList);
    }
}
