package com.example.cms.project;

import com.example.cms.client.Client;
import com.example.cms.client.ClientRepository;
import com.example.cms.client.ClientStatus;
import com.example.cms.common.ObjectIds;
import com.example.cms.exception.BadRequestException;
import com.example.cms.exception.ResourceNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Business logic for projects (rules 2, 3, 4 and 5). */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;
    private final MongoTemplate mongoTemplate;

    public ProjectService(ProjectRepository projectRepository,
                          ClientRepository clientRepository,
                          MongoTemplate mongoTemplate) {
        this.projectRepository = projectRepository;
        this.clientRepository = clientRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public ProjectResponse create(ProjectRequest request) {
        Client client = findReferencedClient(request.clientId());

        ProjectStatus status = request.status() != null ? request.status() : ProjectStatus.PLANNED;
        if (!ProjectStatusRules.INITIAL_STATUSES.contains(status)) {
            throw new BadRequestException("A new project must start as PLANNED or IN_PROGRESS, not " + status + ".");
        }
        validateDates(request.startDate(), request.dueDate(), status);

        // Every check passed: now apply Rule 2 (LEAD client becomes ACTIVE).
        activateClientForProject(client);

        Project project = new Project();
        copyFields(request, project);
        project.setStatus(status);
        project.setPriority(request.priority() != null ? request.priority() : ProjectPriority.MEDIUM);

        Instant now = Instant.now();
        project.setCreatedAt(now);
        project.setUpdatedAt(now);

        return toResponse(projectRepository.save(project), client.getFullName());
    }

    /** Lists projects, newest first. All filters are optional and can be combined. */
    public List<ProjectResponse> findAll(ProjectStatus status, ProjectPriority priority, String clientId) {
        Query query = new Query();
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        if (priority != null) {
            query.addCriteria(Criteria.where("priority").is(priority));
        }
        if (clientId != null && !clientId.isBlank()) {
            ObjectIds.requireValid(clientId, "client ID");
            query.addCriteria(Criteria.where("clientId").is(clientId));
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return toResponses(mongoTemplate.find(query, Project.class));
    }

    public ProjectResponse findById(String id) {
        Project project = getExistingProject(id);
        return toResponse(project, clientNameOf(project.getClientId()));
    }

    /** GET /api/clients/{clientId}/projects - the client must exist. */
    public List<ProjectResponse> findByClient(String clientId) {
        ObjectIds.requireValid(clientId, "client ID");
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));
        LocalDate today = LocalDate.now();
        return projectRepository.findByClientIdOrderByDueDateAsc(clientId).stream()
                .map(p -> ProjectResponse.from(p, client.getFullName(), today))
                .toList();
    }

    /** Rule 5: due date before today and not COMPLETED / CANCELLED. Oldest due date first. */
    public List<ProjectResponse> findOverdue() {
        return toResponses(projectRepository.findByDueDateBeforeAndStatusNotInOrderByDueDateAsc(
                LocalDate.now(), ProjectStatusRules.FINISHED_STATUSES));
    }

    public ProjectResponse update(String id, ProjectRequest request) {
        Project project = getExistingProject(id);

        // Moving the project to another client: the new client must exist and be allowed to receive projects.
        Client client;
        boolean clientChanged = !project.getClientId().equals(request.clientId());
        if (clientChanged) {
            client = findReferencedClient(request.clientId());
        } else {
            client = clientRepository.findById(project.getClientId()).orElse(null);
        }

        // Rule 4: status transitions
        ProjectStatus newStatus = request.status() != null ? request.status() : project.getStatus();
        if (!ProjectStatusRules.canTransition(project.getStatus(), newStatus)) {
            throw new BadRequestException(transitionErrorMessage(project.getStatus(), newStatus));
        }

        // Rule 3: dates
        validateDates(request.startDate(), request.dueDate(), newStatus);

        if (clientChanged) {
            activateClientForProject(client);
        }

        copyFields(request, project);
        project.setStatus(newStatus);
        if (request.priority() != null) {
            project.setPriority(request.priority());
        }
        project.setUpdatedAt(Instant.now()); // Rule 9

        return toResponse(projectRepository.save(project), client != null ? client.getFullName() : null);
    }

    public void delete(String id) {
        projectRepository.delete(getExistingProject(id));
    }

    // ---------------------------------------------------------------- rules

    /**
     * Rule 2: creating a project for a LEAD client makes the client ACTIVE.
     * INACTIVE and CLOSED clients are never reactivated automatically.
     */
    private void activateClientForProject(Client client) {
        if (client.getStatus() == ClientStatus.INACTIVE || client.getStatus() == ClientStatus.CLOSED) {
            throw new BadRequestException("Cannot add a project for client '" + client.getFullName()
                    + "' because the client's status is " + client.getStatus()
                    + ". Change the client's status to ACTIVE first.");
        }
        if (client.getStatus() == ClientStatus.LEAD) {
            client.setStatus(ClientStatus.ACTIVE);
            client.setUpdatedAt(Instant.now());
            clientRepository.save(client);
        }
    }

    /** Rule 3: due date not before start date; not COMPLETED before the start date. */
    private void validateDates(LocalDate startDate, LocalDate dueDate, ProjectStatus status) {
        if (dueDate.isBefore(startDate)) {
            throw new BadRequestException("Due date (" + dueDate + ") cannot be earlier than start date ("
                    + startDate + ").");
        }
        if (status == ProjectStatus.COMPLETED && LocalDate.now().isBefore(startDate)) {
            throw new BadRequestException("A project cannot be marked COMPLETED before its start date ("
                    + startDate + ").");
        }
    }

    private static String transitionErrorMessage(ProjectStatus from, ProjectStatus to) {
        if (ProjectStatusRules.isFinal(from)) {
            return "Cannot change status from " + from + " to " + to + ". " + from
                    + " is a final status and cannot be changed.";
        }
        String allowed = ProjectStatusRules.allowedNextStatuses(from).stream()
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        return "Cannot change status from " + from + " to " + to + ". Allowed next statuses: " + allowed + ".";
    }

    // ---------------------------------------------------------------- helpers

    private Project getExistingProject(String id) {
        ObjectIds.requireValid(id, "project ID");
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    /** A clientId inside a request body must point to an existing client (400 otherwise). */
    private Client findReferencedClient(String clientId) {
        ObjectIds.requireValid(clientId, "client ID");
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new BadRequestException(
                        "Client with id '" + clientId + "' does not exist. Choose an existing client."));
    }

    private void copyFields(ProjectRequest request, Project project) {
        project.setName(request.name().trim());
        project.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        project.setClientId(request.clientId());
        project.setStartDate(request.startDate());
        project.setDueDate(request.dueDate());
        project.setBudget(request.budget());
    }

    private String clientNameOf(String clientId) {
        return clientRepository.findById(clientId).map(Client::getFullName).orElse(null);
    }

    private ProjectResponse toResponse(Project project, String clientName) {
        return ProjectResponse.from(project, clientName, LocalDate.now());
    }

    /** Converts many projects, loading all their clients with one query (no joins in MongoDB). */
    private List<ProjectResponse> toResponses(List<Project> projects) {
        Set<String> clientIds = new HashSet<>();
        projects.forEach(p -> clientIds.add(p.getClientId()));
        Map<String, Client> clients = clientRepository.findAllById(clientIds).stream()
                .collect(Collectors.toMap(Client::getId, Function.identity()));

        LocalDate today = LocalDate.now();
        return projects.stream()
                .map(p -> {
                    Client c = clients.get(p.getClientId());
                    return ProjectResponse.from(p, c != null ? c.getFullName() : null, today);
                })
                .toList();
    }
}
