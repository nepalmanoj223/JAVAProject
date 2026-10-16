package com.example.cms.project;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ProjectRepository extends MongoRepository<Project, String> {

    List<Project> findByClientIdOrderByDueDateAsc(String clientId);

    long countByClientId(String clientId);

    long countByClientIdAndStatus(String clientId, ProjectStatus status);

    long countByStatus(ProjectStatus status);

    /** Overdue = due date before today and status not in (COMPLETED, CANCELLED). */
    List<Project> findByDueDateBeforeAndStatusNotInOrderByDueDateAsc(LocalDate today,
                                                                     Collection<ProjectStatus> finishedStatuses);

    long countByDueDateBeforeAndStatusNotIn(LocalDate today, Collection<ProjectStatus> finishedStatuses);

    long countByClientIdAndDueDateBeforeAndStatusNotIn(String clientId, LocalDate today,
                                                       Collection<ProjectStatus> finishedStatuses);
}
