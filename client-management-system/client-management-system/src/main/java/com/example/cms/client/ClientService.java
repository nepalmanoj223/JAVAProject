package com.example.cms.client;

import com.example.cms.common.ObjectIds;
import com.example.cms.exception.BadRequestException;
import com.example.cms.exception.ConflictException;
import com.example.cms.exception.ResourceNotFoundException;
import com.example.cms.interaction.InteractionRepository;
import com.example.cms.project.ProjectRepository;
import com.example.cms.project.ProjectStatus;
import com.example.cms.project.ProjectStatusRules;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Business logic for clients. */
@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final InteractionRepository interactionRepository;
    private final MongoTemplate mongoTemplate;

    public ClientService(ClientRepository clientRepository,
                         ProjectRepository projectRepository,
                         InteractionRepository interactionRepository,
                         MongoTemplate mongoTemplate) {
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.interactionRepository = interactionRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /** Rule 1: a client created without a status becomes LEAD. */
    public ClientResponse create(ClientRequest request) {
        String email = normalizeEmail(request.email());
        validateCompanyName(request);
        ensureEmailIsFree(email, null);

        Client client = new Client();
        copyFields(request, email, client);
        client.setStatus(request.status() != null ? request.status() : ClientStatus.LEAD);

        Instant now = Instant.now();
        client.setCreatedAt(now);
        client.setUpdatedAt(now);

        return ClientResponse.from(saveWithUniqueEmail(client));
    }

    /**
     * Lists clients, newest first. All parameters are optional and can be combined.
     * "search" matches first name, last name, email or company name (case-insensitive);
     * with several words, every word must match one of those fields ("alex smith").
     */
    public List<ClientResponse> findAll(String search, ClientStatus status, ClientType clientType) {
        Query query = new Query();
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        if (clientType != null) {
            query.addCriteria(Criteria.where("clientType").is(clientType));
        }
        if (search != null && !search.isBlank()) {
            List<Criteria> wordCriteria = new ArrayList<>();
            for (String word : search.trim().split("\\s+")) {
                Pattern pattern = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE);
                wordCriteria.add(new Criteria().orOperator(
                        Criteria.where("firstName").regex(pattern),
                        Criteria.where("lastName").regex(pattern),
                        Criteria.where("email").regex(pattern),
                        Criteria.where("companyName").regex(pattern)));
            }
            query.addCriteria(new Criteria().andOperator(wordCriteria));
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));

        return mongoTemplate.find(query, Client.class).stream()
                .map(ClientResponse::from)
                .toList();
    }

    public ClientResponse findById(String id) {
        return ClientResponse.from(getExistingClient(id));
    }

    public ClientResponse update(String id, ClientRequest request) {
        Client client = getExistingClient(id);
        String email = normalizeEmail(request.email());
        validateCompanyName(request);
        ensureEmailIsFree(email, client.getId());

        copyFields(request, email, client);
        if (request.status() != null) {
            client.setStatus(request.status());
        }
        client.setUpdatedAt(Instant.now()); // Rule 9

        return ClientResponse.from(saveWithUniqueEmail(client));
    }

    /** Rule 6: a client with projects or interactions cannot be deleted. */
    public void delete(String id) {
        Client client = getExistingClient(id);
        long projects = projectRepository.countByClientId(id);
        long interactions = interactionRepository.countByClientId(id);

        if (projects > 0 || interactions > 0) {
            throw new ConflictException("Cannot delete client '" + client.getFullName() + "' because it still has "
                    + projects + " project(s) and " + interactions + " interaction(s). "
                    + "Delete those records first, or change the client's status to CLOSED instead.");
        }
        clientRepository.delete(client);
    }

    /** Rule 7: client details plus project and interaction counts. */
    public ClientSummaryResponse getSummary(String id) {
        Client client = getExistingClient(id);
        LocalDate today = LocalDate.now();

        return new ClientSummaryResponse(
                ClientResponse.from(client),
                projectRepository.countByClientId(id),
                projectRepository.countByClientIdAndStatus(id, ProjectStatus.IN_PROGRESS),
                projectRepository.countByClientIdAndStatus(id, ProjectStatus.COMPLETED),
                projectRepository.countByClientIdAndDueDateBeforeAndStatusNotIn(
                        id, today, ProjectStatusRules.FINISHED_STATUSES),
                interactionRepository.countByClientId(id));
    }

    /** Loads a client or throws 400 (bad ID format) / 404 (not found). */
    public Client getExistingClient(String id) {
        ObjectIds.requireValid(id, "client ID");
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client", id));
    }

    // ---------------------------------------------------------------- helpers

    private void copyFields(ClientRequest request, String normalizedEmail, Client client) {
        client.setFirstName(request.firstName().trim());
        client.setLastName(request.lastName().trim());
        client.setEmail(normalizedEmail);
        client.setPhone(request.phone().trim());
        client.setCompanyName(blankToNull(request.companyName()));
        client.setAddress(blankToNull(request.address()));
        client.setClientType(request.clientType());
        client.setNotes(blankToNull(request.notes()));
    }

    /** A BUSINESS client must have a company name. */
    private void validateCompanyName(ClientRequest request) {
        if (request.clientType() == ClientType.BUSINESS && blankToNull(request.companyName()) == null) {
            throw new BadRequestException("Company name is required for BUSINESS clients.");
        }
    }

    /** Emails are compared in lower case, so duplicates are detected regardless of letter case. */
    private void ensureEmailIsFree(String email, String currentClientId) {
        clientRepository.findByEmail(email).ifPresent(existing -> {
            if (!existing.getId().equals(currentClientId)) {
                throw new ConflictException("A client with email '" + email + "' already exists.");
            }
        });
    }

    /** The unique index is the final guard (for example, two requests at the same moment). */
    private Client saveWithUniqueEmail(Client client) {
        try {
            return clientRepository.save(client);
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("A client with email '" + client.getEmail() + "' already exists.");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
