package com.example.cms.interaction;

import com.example.cms.client.ClientRepository;
import com.example.cms.common.ObjectIds;
import com.example.cms.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Business logic for the client interaction history. */
@Service
public class InteractionService {

    private final InteractionRepository interactionRepository;
    private final ClientRepository clientRepository;

    public InteractionService(InteractionRepository interactionRepository, ClientRepository clientRepository) {
        this.interactionRepository = interactionRepository;
        this.clientRepository = clientRepository;
    }

    /** The client must exist before an interaction can be recorded. */
    public InteractionResponse create(String clientId, InteractionRequest request) {
        requireExistingClient(clientId);

        Interaction interaction = new Interaction();
        interaction.setClientId(clientId);
        copyFields(request, interaction);
        Instant now = Instant.now();
        interaction.setCreatedAt(now);
        interaction.setUpdatedAt(now);

        return InteractionResponse.from(interactionRepository.save(interaction));
    }

    /** History of one client, newest interaction date first. */
    public List<InteractionResponse> findByClient(String clientId) {
        requireExistingClient(clientId);
        return interactionRepository.findByClientIdOrderByInteractionDateDescCreatedAtDesc(clientId).stream()
                .map(InteractionResponse::from)
                .toList();
    }

    public InteractionResponse findById(String id) {
        return InteractionResponse.from(getExistingInteraction(id));
    }

    public InteractionResponse update(String id, InteractionRequest request) {
        Interaction interaction = getExistingInteraction(id);
        copyFields(request, interaction);
        interaction.setUpdatedAt(Instant.now()); // Rule 9
        return InteractionResponse.from(interactionRepository.save(interaction));
    }

    public void delete(String id) {
        interactionRepository.delete(getExistingInteraction(id));
    }

    private void requireExistingClient(String clientId) {
        ObjectIds.requireValid(clientId, "client ID");
        if (!clientRepository.existsById(clientId)) {
            throw new ResourceNotFoundException("Client", clientId);
        }
    }

    private Interaction getExistingInteraction(String id) {
        ObjectIds.requireValid(id, "interaction ID");
        return interactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interaction", id));
    }

    private void copyFields(InteractionRequest request, Interaction interaction) {
        interaction.setType(request.type());
        interaction.setSubject(request.subject().trim());
        interaction.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        interaction.setInteractionDate(request.interactionDate());
    }
}
