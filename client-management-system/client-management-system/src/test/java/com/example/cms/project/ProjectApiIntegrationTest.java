package com.example.cms.project;

import com.example.cms.support.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Project CRUD, client activation, dates, status transitions and overdue projects. */
class ProjectApiIntegrationTest extends AbstractMongoIntegrationTest {

    private final LocalDate today = LocalDate.now();

    // ---------------------------------------------------------------- CRUD

    @Test
    void projectCrud() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", "ACTIVE");

        // create
        String projectId = body(mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(projectBody(clientId, "Company Website", null, today, today.plusDays(60)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.clientName").value("Alex Smith"))
                .andExpect(jsonPath("$.budget").value(2500.0))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn()).get("id").asText();

        // read
        mockMvc.perform(get("/api/projects/" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Company Website"))
                .andExpect(jsonPath("$.startDate").value(today.toString()));
        mockMvc.perform(get("/api/projects"))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/clients/" + clientId + "/projects"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(projectId));

        // update
        Map<String, Object> update = projectBody(clientId, "Company Website v2", "IN_PROGRESS", today, today.plusDays(90));
        update.put("budget", 3000.50);
        update.put("priority", "LOW");
        mockMvc.perform(put("/api/projects/" + projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Company Website v2"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.budget").value(3000.5));

        // delete
        mockMvc.perform(delete("/api/projects/" + projectId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/projects/" + projectId)).andExpect(status().isNotFound());
    }

    @Test
    void projectNeedsAnExistingClient() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(projectBody("0123456789abcdef01234567", "Orphan", null, today, today.plusDays(5)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("does not exist")));

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(projectBody("bad-id", "Orphan", null, today, today.plusDays(5)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid client ID")));
    }

    @Test
    void negativeBudgetIsRejected() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", null);
        Map<String, Object> request = projectBody(clientId, "Website", null, today, today.plusDays(5));
        request.put("budget", -10);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.budget").exists());
    }

    @Test
    void invalidProjectIdReturns400() throws Exception {
        mockMvc.perform(get("/api/projects/12345")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- Rule 2: client activation

    @Test
    void creatingProjectForLeadClientMakesClientActive() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", null);
        mockMvc.perform(get("/api/clients/" + clientId)).andExpect(jsonPath("$.status").value("LEAD"));

        createProject(clientId, "Website", null, today, today.plusDays(30));

        mockMvc.perform(get("/api/clients/" + clientId)).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void inactiveAndClosedClientsAreNotReactivated() throws Exception {
        for (String status : new String[]{"INACTIVE", "CLOSED"}) {
            String clientId = createClient("Client", status, status.toLowerCase() + "@example.com", status);

            mockMvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(projectBody(clientId, "Website", null, today, today.plusDays(30)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString(status)));

            mockMvc.perform(get("/api/clients/" + clientId)).andExpect(jsonPath("$.status").value(status));
        }
        assertThat(mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(), Project.class))
                .isZero();
    }

    // ---------------------------------------------------------------- Rule 3: dates

    @Test
    void dueDateCannotBeBeforeStartDate() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", null);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(projectBody(clientId, "Website", null, today, today.minusDays(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("cannot be earlier than start date")));
    }

    @Test
    void projectCannotBeCompletedBeforeItsStartDate() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", null);
        LocalDate start = today.plusDays(10);
        String projectId = createProject(clientId, "Future project", "IN_PROGRESS", start, start.plusDays(20));

        var result = changeProjectStatus(projectId, clientId, "COMPLETED", start, start.plusDays(20));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).get("message").asText()).contains("before its start date");
    }

    @Test
    void newProjectMustStartPlannedOrInProgress() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", null);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(projectBody(clientId, "Website", "COMPLETED", today, today.plusDays(5)))))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- Rule 4: status transitions

    @Test
    void validTransitionsAreAccepted() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", null);
        LocalDate start = today.minusDays(5);
        LocalDate due = today.plusDays(30);
        String projectId = createProject(clientId, "Website", "PLANNED", start, due);

        for (String next : new String[]{"IN_PROGRESS", "ON_HOLD", "IN_PROGRESS", "COMPLETED"}) {
            var result = changeProjectStatus(projectId, clientId, next, start, due);
            assertThat(result.getResponse().getStatus()).as("move to " + next).isEqualTo(200);
            assertThat(body(result).get("status").asText()).isEqualTo(next);
        }
    }

    @Test
    void invalidTransitionsAreRejectedWithMessage() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", null);
        LocalDate start = today.minusDays(5);
        LocalDate due = today.plusDays(30);
        String projectId = createProject(clientId, "Website", "PLANNED", start, due);

        var skip = changeProjectStatus(projectId, clientId, "COMPLETED", start, due);
        assertThat(skip.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(skip).get("message").asText())
                .contains("Cannot change status from PLANNED to COMPLETED")
                .contains("IN_PROGRESS");

        assertThat(changeProjectStatus(projectId, clientId, "CANCELLED", start, due).getResponse().getStatus())
                .isEqualTo(200);

        var reopen = changeProjectStatus(projectId, clientId, "IN_PROGRESS", start, due);
        assertThat(reopen.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(reopen).get("message").asText()).contains("final status");
    }

    // ---------------------------------------------------------------- Rule 5: overdue

    @Test
    void overdueEndpointReturnsOnlyOpenProjectsPastTheirDueDate() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", null);

        String overdueId = createProject(clientId, "Late and open", "IN_PROGRESS", today.minusDays(30), today.minusDays(1));

        createProject(clientId, "Due in future", "IN_PROGRESS", today.minusDays(5), today.plusDays(5));
        createProject(clientId, "Due today", "PLANNED", today, today);

        String completed = createProject(clientId, "Late but completed", "IN_PROGRESS", today.minusDays(30), today.minusDays(2));
        changeProjectStatus(completed, clientId, "COMPLETED", today.minusDays(30), today.minusDays(2));

        String cancelled = createProject(clientId, "Late but cancelled", "PLANNED", today.minusDays(30), today.minusDays(3));
        changeProjectStatus(cancelled, clientId, "CANCELLED", today.minusDays(30), today.minusDays(3));

        mockMvc.perform(get("/api/projects/overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(overdueId))
                .andExpect(jsonPath("$[0].overdue").value(true));
    }

    // ---------------------------------------------------------------- filters

    @Test
    void projectsCanBeFilteredByStatusPriorityAndClient() throws Exception {
        String alex = createClient("Alex", "Smith", "alex@example.com", null);
        String sara = createClient("Sara", "Lee", "sara@example.com", null);

        createProject(alex, "A1", "PLANNED", today, today.plusDays(10));
        createProject(alex, "A2", "IN_PROGRESS", today, today.plusDays(10));
        Map<String, Object> low = projectBody(sara, "S1", "IN_PROGRESS", today, today.plusDays(10));
        low.put("priority", "LOW");
        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(toJson(low)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/projects").param("status", "IN_PROGRESS"))
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/projects").param("priority", "LOW"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("S1"));
        mockMvc.perform(get("/api/projects").param("clientId", alex))
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/projects").param("clientId", alex).param("status", "IN_PROGRESS"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("A2"));
        mockMvc.perform(get("/api/projects").param("priority", "URGENT"))
                .andExpect(status().isBadRequest());
    }
}
