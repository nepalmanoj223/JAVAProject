package com.example.cms.dashboard;

import com.example.cms.support.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rule 8: every dashboard number comes from the database. */
class DashboardIntegrationTest extends AbstractMongoIntegrationTest {

    @Test
    void emptyDatabaseGivesZeros() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClients").value(0))
                .andExpect(jsonPath("$.totalProjects").value(0))
                .andExpect(jsonPath("$.overdueProjects").value(0))
                .andExpect(jsonPath("$.totalInteractions").value(0))
                .andExpect(jsonPath("$.recentClients", hasSize(0)))
                .andExpect(jsonPath("$.overdueProjectList", hasSize(0)));
    }

    @Test
    void statisticsReflectTheRecordsInTheDatabase() throws Exception {
        LocalDate today = LocalDate.now();

        createClient("Lena", "Lead", "lead@example.com", null);                  // LEAD
        String active = createClient("Alex", "Smith", "alex@example.com", null);  // becomes ACTIVE below
        createClient("Ivan", "Inactive", "inactive@example.com", "INACTIVE");
        createClient("Cara", "Closed", "closed@example.com", "CLOSED");

        createProject(active, "Overdue", "IN_PROGRESS", today.minusDays(20), today.minusDays(1));
        String done = createProject(active, "Done", "IN_PROGRESS", today.minusDays(20), today.plusDays(10));
        changeProjectStatus(done, active, "COMPLETED", today.minusDays(20), today.plusDays(10));
        createProject(active, "Planned", "PLANNED", today, today.plusDays(30));

        createInteraction(active, "CALL", "Call 1", today);
        createInteraction(active, "EMAIL", "Email 1", today.minusDays(1));

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClients").value(4))
                .andExpect(jsonPath("$.activeClients").value(1))
                .andExpect(jsonPath("$.leads").value(1))
                .andExpect(jsonPath("$.inactiveClients").value(1))
                .andExpect(jsonPath("$.closedClients").value(1))
                .andExpect(jsonPath("$.inactiveAndClosedClients").value(2))
                .andExpect(jsonPath("$.totalProjects").value(3))
                .andExpect(jsonPath("$.projectsInProgress").value(1))
                .andExpect(jsonPath("$.completedProjects").value(1))
                .andExpect(jsonPath("$.overdueProjects").value(1))
                .andExpect(jsonPath("$.totalInteractions").value(2))
                .andExpect(jsonPath("$.recentClients", hasSize(4)))
                .andExpect(jsonPath("$.overdueProjectList", hasSize(1)))
                .andExpect(jsonPath("$.overdueProjectList[0].name").value("Overdue"));
    }
}
