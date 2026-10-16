package com.example.cms.project;

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
 * Project endpoints.
 * Note: Spring always prefers the fixed path /api/projects/overdue over the
 * pattern /api/projects/{id}, so "overdue" is never treated as an ID.
 */
@RestController
@RequestMapping("/api")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping("/projects")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        ProjectResponse created = projectService.create(request);
        return ResponseEntity.created(URI.create("/api/projects/" + created.id())).body(created);
    }

    /** Example: GET /api/projects?status=IN_PROGRESS&priority=HIGH&clientId=... */
    @GetMapping("/projects")
    public List<ProjectResponse> findAll(@RequestParam(name = "status", required = false) ProjectStatus status,
                                         @RequestParam(name = "priority", required = false) ProjectPriority priority,
                                         @RequestParam(name = "clientId", required = false) String clientId) {
        return projectService.findAll(status, priority, clientId);
    }

    @GetMapping("/projects/overdue")
    public List<ProjectResponse> findOverdue() {
        return projectService.findOverdue();
    }

    @GetMapping("/projects/{id}")
    public ProjectResponse findById(@PathVariable("id") String id) {
        return projectService.findById(id);
    }

    @PutMapping("/projects/{id}")
    public ProjectResponse update(@PathVariable("id") String id, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/clients/{clientId}/projects")
    public List<ProjectResponse> findByClient(@PathVariable("clientId") String clientId) {
        return projectService.findByClient(clientId);
    }
}
