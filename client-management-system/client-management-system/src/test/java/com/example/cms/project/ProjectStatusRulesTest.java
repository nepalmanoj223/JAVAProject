package com.example.cms.project;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.example.cms.project.ProjectStatus.CANCELLED;
import static com.example.cms.project.ProjectStatus.COMPLETED;
import static com.example.cms.project.ProjectStatus.IN_PROGRESS;
import static com.example.cms.project.ProjectStatus.ON_HOLD;
import static com.example.cms.project.ProjectStatus.PLANNED;
import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests (no database needed) for Rule 4 and Rule 5. */
class ProjectStatusRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Test
    void plannedCanOnlyMoveToInProgressOrCancelled() {
        assertThat(ProjectStatusRules.canTransition(PLANNED, IN_PROGRESS)).isTrue();
        assertThat(ProjectStatusRules.canTransition(PLANNED, CANCELLED)).isTrue();
        assertThat(ProjectStatusRules.canTransition(PLANNED, ON_HOLD)).isFalse();
        assertThat(ProjectStatusRules.canTransition(PLANNED, COMPLETED)).isFalse();
    }

    @Test
    void inProgressCanMoveToOnHoldCompletedOrCancelled() {
        assertThat(ProjectStatusRules.canTransition(IN_PROGRESS, ON_HOLD)).isTrue();
        assertThat(ProjectStatusRules.canTransition(IN_PROGRESS, COMPLETED)).isTrue();
        assertThat(ProjectStatusRules.canTransition(IN_PROGRESS, CANCELLED)).isTrue();
        assertThat(ProjectStatusRules.canTransition(IN_PROGRESS, PLANNED)).isFalse();
    }

    @Test
    void onHoldCanMoveToInProgressOrCancelled() {
        assertThat(ProjectStatusRules.canTransition(ON_HOLD, IN_PROGRESS)).isTrue();
        assertThat(ProjectStatusRules.canTransition(ON_HOLD, CANCELLED)).isTrue();
        assertThat(ProjectStatusRules.canTransition(ON_HOLD, COMPLETED)).isFalse();
        assertThat(ProjectStatusRules.canTransition(ON_HOLD, PLANNED)).isFalse();
    }

    @Test
    void completedAndCancelledAreFinal() {
        for (ProjectStatus target : ProjectStatus.values()) {
            if (target != COMPLETED) {
                assertThat(ProjectStatusRules.canTransition(COMPLETED, target)).isFalse();
            }
            if (target != CANCELLED) {
                assertThat(ProjectStatusRules.canTransition(CANCELLED, target)).isFalse();
            }
        }
        assertThat(ProjectStatusRules.allowedNextStatuses(COMPLETED)).isEmpty();
        assertThat(ProjectStatusRules.allowedNextStatuses(CANCELLED)).isEmpty();
    }

    @Test
    void keepingTheSameStatusIsAllowed() {
        for (ProjectStatus status : ProjectStatus.values()) {
            assertThat(ProjectStatusRules.canTransition(status, status)).isTrue();
        }
    }

    @Test
    void allowedNextStatusesMatchTheRules() {
        assertThat(ProjectStatusRules.allowedNextStatuses(PLANNED)).containsExactlyInAnyOrder(IN_PROGRESS, CANCELLED);
        assertThat(ProjectStatusRules.allowedNextStatuses(IN_PROGRESS))
                .containsExactlyInAnyOrder(ON_HOLD, COMPLETED, CANCELLED);
        assertThat(ProjectStatusRules.allowedNextStatuses(ON_HOLD)).containsExactlyInAnyOrder(IN_PROGRESS, CANCELLED);
    }

    @Test
    void projectIsOverdueOnlyWhenDueDatePassedAndNotFinished() {
        LocalDate yesterday = TODAY.minusDays(1);

        assertThat(ProjectStatusRules.isOverdue(yesterday, IN_PROGRESS, TODAY)).isTrue();
        assertThat(ProjectStatusRules.isOverdue(yesterday, PLANNED, TODAY)).isTrue();
        assertThat(ProjectStatusRules.isOverdue(yesterday, ON_HOLD, TODAY)).isTrue();

        assertThat(ProjectStatusRules.isOverdue(yesterday, COMPLETED, TODAY)).isFalse();
        assertThat(ProjectStatusRules.isOverdue(yesterday, CANCELLED, TODAY)).isFalse();

        assertThat(ProjectStatusRules.isOverdue(TODAY, IN_PROGRESS, TODAY)).as("due today is not overdue yet").isFalse();
        assertThat(ProjectStatusRules.isOverdue(TODAY.plusDays(3), IN_PROGRESS, TODAY)).isFalse();
        assertThat(ProjectStatusRules.isOverdue(null, IN_PROGRESS, TODAY)).isFalse();
    }
}
