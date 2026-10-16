package com.example.cms.interaction;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Interaction history endpoints. */
@RestController
@RequestMapping("/api")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping("/clients/{clientId}/interactions")
    public ResponseEntity<InteractionResponse> create(@PathVariable("clientId") String clientId,
                                                      @Valid @RequestBody InteractionRequest request) {
        InteractionResponse created = interactionService.create(clientId, request);
        return ResponseEntity.created(URI.create("/api/interactions/" + created.id())).body(created);
    }

    @GetMapping("/clients/{clientId}/interactions")
    public List<InteractionResponse> findByClient(@PathVariable("clientId") String clientId) {
        return interactionService.findByClient(clientId);
    }

    @GetMapping("/interactions/{id}")
    public InteractionResponse findById(@PathVariable("id") String id) {
        return interactionService.findById(id);
    }

    @PutMapping("/interactions/{id}")
    public InteractionResponse update(@PathVariable("id") String id, @Valid @RequestBody InteractionRequest request) {
        return interactionService.update(id, request);
    }

    @DeleteMapping("/interactions/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        interactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
