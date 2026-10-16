package com.example.cms.project;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Plain Java rules for project statuses (no database access, easy to unit test).
 *
 * Rule 4 - allowed status changes:
 *   PLANNED     -> IN_PROGRESS, CANCELLED
 *   IN_PROGRESS -> ON_HOLD, COMPLETED, CANCELLED
 *   ON_HOLD     -> IN_PROGRESS, CANCELLED
 *   COMPLETED   -> (final)
 *   CANCELLED   -> (final)
 *
 * Rule 5 - a project is overdue when its due date is before today
 * and it is neither COMPLETED nor CANCELLED.
 */
public final class ProjectStatusRules {

    /** Statuses that end a project. Finished projects are never overdue. */
    public static final Set<ProjectStatus> FINISHED_STATUSES =
            Collections.unmodifiableSet(EnumSet.of(ProjectStatus.COMPLETED, ProjectStatus.CANCELLED));

    /** A new project starts either PLANNED or already IN_PROGRESS. */
    public static final Set<ProjectStatus> INITIAL_STATUSES =
            Collections.unmodifiableSet(EnumSet.of(ProjectStatus.PLANNED, ProjectStatus.IN_PROGRESS));

    private static final Map<ProjectStatus, Set<ProjectStatus>> TRANSITIONS = new EnumMap<>(ProjectStatus.class);

    static {
        TRANSITIONS.put(ProjectStatus.PLANNED, EnumSet.of(ProjectStatus.IN_PROGRESS, ProjectStatus.CANCELLED));
        TRANSITIONS.put(ProjectStatus.IN_PROGRESS,
                EnumSet.of(ProjectStatus.ON_HOLD, ProjectStatus.COMPLETED, ProjectStatus.CANCELLED));
        TRANSITIONS.put(ProjectStatus.ON_HOLD, EnumSet.of(ProjectStatus.IN_PROGRESS, ProjectStatus.CANCELLED));
        TRANSITIONS.put(ProjectStatus.COMPLETED, EnumSet.noneOf(ProjectStatus.class));
        TRANSITIONS.put(ProjectStatus.CANCELLED, EnumSet.noneOf(ProjectStatus.class));
    }

    private ProjectStatusRules() {
    }

    /** Keeping the same status is always allowed. */
    public static boolean canTransition(ProjectStatus from, ProjectStatus to) {
        return from == to || TRANSITIONS.get(from).contains(to);
    }

    public static Set<ProjectStatus> allowedNextStatuses(ProjectStatus from) {
        EnumSet<ProjectStatus> result = EnumSet.noneOf(ProjectStatus.class);
        result.addAll(TRANSITIONS.get(from));
        return Collections.unmodifiableSet(result);
    }

    public static boolean isFinal(ProjectStatus status) {
        return FINISHED_STATUSES.contains(status);
    }

    public static boolean isOverdue(LocalDate dueDate, ProjectStatus status, LocalDate today) {
        return dueDate != null && dueDate.isBefore(today) && !isFinal(status);
    }
}
