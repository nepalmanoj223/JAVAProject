package com.example.cms.client;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Client endpoints. The controller only translates HTTP to service calls;
 * all rules live in {@link ClientService}.
 */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientRequest request) {
        ClientResponse created = clientService.create(request);
        return ResponseEntity.created(URI.create("/api/clients/" + created.id())).body(created);
    }

    /** Example: GET /api/clients?search=alex&status=ACTIVE&clientType=BUSINESS */
    @GetMapping
    public List<ClientResponse> findAll(@RequestParam(name = "search", required = false) String search,
                                        @RequestParam(name = "status", required = false) ClientStatus status,
                                        @RequestParam(name = "clientType", required = false) ClientType clientType) {
        return clientService.findAll(search, status, clientType);
    }

    @GetMapping("/{id}")
    public ClientResponse findById(@PathVariable("id") String id) {
        return clientService.findById(id);
    }

    @PutMapping("/{id}")
    public ClientResponse update(@PathVariable("id") String id, @Valid @RequestBody ClientRequest request) {
        return clientService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/summary")
    public ClientSummaryResponse summary(@PathVariable("id") String id) {
        return clientService.getSummary(id);
    }
}
