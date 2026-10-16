package com.example.cms.client;

import com.example.cms.support.AbstractMongoIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
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

/** Client CRUD, duplicate emails, search/filters, deletion rules and summary. */
class ClientApiIntegrationTest extends AbstractMongoIntegrationTest {

    // ---------------------------------------------------------------- CRUD

    @Test
    void createClientWithoutStatusBecomesLeadWithTimestamps() throws Exception {
        Map<String, Object> request = clientBody("Alex", "Smith", "Alex@Example.com", null);

        MvcResult result = mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("LEAD"))
                .andExpect(jsonPath("$.email").value("alex@example.com"))
                .andExpect(jsonPath("$.fullName").value("Alex Smith"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getHeader("Location")).startsWith("/api/clients/");
    }

    @Test
    void createClientKeepsExplicitStatus() throws Exception {
        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(clientBody("Sara", "Lee", "sara@example.com", "ACTIVE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getAllAndGetById() throws Exception {
        String id = createClient("Alex", "Smith", "alex@example.com", null);
        createClient("Sara", "Lee", "sara@example.com", null);

        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/clients/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.firstName").value("Alex"));
    }

    @Test
    void updateClientChangesFieldsAndUpdatedAtButKeepsCreatedAt() throws Exception {
        String id = createClient("Alex", "Smith", "alex@example.com", null);
        JsonNode before = body(mockMvc.perform(get("/api/clients/" + id)).andReturn());

        Thread.sleep(20); // make sure the clock moves on
        Map<String, Object> update = clientBody("Alexander", "Smith", "alex@example.com", null);
        update.put("phone", "+977 9800000000");

        JsonNode after = body(mockMvc.perform(put("/api/clients/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alexander"))
                .andExpect(jsonPath("$.phone").value("+977 9800000000"))
                .andExpect(jsonPath("$.status").value("LEAD")) // status omitted -> unchanged
                .andReturn());

        Instant createdBefore = Instant.parse(before.get("createdAt").asText());
        Instant createdAfter = Instant.parse(after.get("createdAt").asText());
        Instant updatedAfter = Instant.parse(after.get("updatedAt").asText());
        assertThat(createdAfter).isEqualTo(createdBefore);
        assertThat(updatedAfter).isAfter(Instant.parse(before.get("updatedAt").asText()));
    }

    @Test
    void deleteClientWithoutDependencies() throws Exception {
        String id = createClient("Alex", "Smith", "alex@example.com", null);

        mockMvc.perform(delete("/api/clients/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/clients/" + id)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- duplicate email

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        createClient("Alex", "Smith", "alex@example.com", null);

        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(clientBody("Other", "Person", "ALEX@EXAMPLE.COM", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    void updatingToAnotherClientsEmailIsRejected() throws Exception {
        createClient("Alex", "Smith", "alex@example.com", null);
        String saraId = createClient("Sara", "Lee", "sara@example.com", null);

        mockMvc.perform(put("/api/clients/" + saraId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(clientBody("Sara", "Lee", "Alex@example.com", null))))
                .andExpect(status().isConflict());
    }

    @Test
    void uniqueIndexOnEmailExistsInMongo() {
        boolean uniqueEmailIndex = mongoTemplate.indexOps(Client.class).getIndexInfo().stream()
                .anyMatch(index -> index.isUnique() && index.isIndexForFields(java.util.List.of("email")));
        assertThat(uniqueEmailIndex).isTrue();
    }

    // ---------------------------------------------------------------- validation

    @Test
    void invalidRequestReturns400WithFieldErrors() throws Exception {
        Map<String, Object> request = clientBody("", "Smith", "not-an-email", null);

        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void invalidEnumValueReturns400() throws Exception {
        Map<String, Object> request = clientBody("Alex", "Smith", "alex@example.com", "SLEEPING");

        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Allowed values")));
    }

    @Test
    void businessClientRequiresCompanyName() throws Exception {
        Map<String, Object> request = clientBody("Alex", "Smith", "alex@example.com", null);
        request.remove("companyName");

        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Company name")));
    }

    @Test
    void invalidIdReturns400AndUnknownIdReturns404() throws Exception {
        mockMvc.perform(get("/api/clients/not-a-valid-id"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/clients/0123456789abcdef01234567"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- search and filters

    @Test
    void searchAndFiltersCanBeCombined() throws Exception {
        createClient("Alex", "Smith", "alex@example.com", "ACTIVE");
        createClient("Alexa", "Brown", "alexa@example.com", "LEAD");
        createClient("John", "Doe", "john@example.com", "ACTIVE");

        mockMvc.perform(get("/api/clients").param("search", "alex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/clients").param("search", "alex").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].firstName").value("Alex"));

        mockMvc.perform(get("/api/clients").param("status", "ACTIVE").param("clientType", "BUSINESS"))
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/clients").param("clientType", "INDIVIDUAL"))
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/clients").param("search", "smith technologies"))
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(get("/api/clients").param("status", "WRONG"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- Rule 6: deletion restrictions

    @Test
    void clientWithProjectsCannotBeDeleted() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", null);
        createProject(clientId, "Website", "PLANNED", LocalDate.now(), LocalDate.now().plusDays(30));

        mockMvc.perform(delete("/api/clients/" + clientId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("1 project(s)")));

        mockMvc.perform(get("/api/clients/" + clientId)).andExpect(status().isOk());
    }

    @Test
    void clientWithInteractionsCannotBeDeleted() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", null);
        createInteraction(clientId, "CALL", "Intro call", LocalDate.now());

        mockMvc.perform(delete("/api/clients/" + clientId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("1 interaction(s)")));
    }

    // ---------------------------------------------------------------- Rule 7: summary

    @Test
    void clientSummaryCountsProjectsAndInteractions() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", null);
        LocalDate today = LocalDate.now();

        createProject(clientId, "In progress, overdue", "IN_PROGRESS", today.minusDays(20), today.minusDays(2));
        String toComplete = createProject(clientId, "Will complete", "IN_PROGRESS", today.minusDays(10), today.plusDays(5));
        changeProjectStatus(toComplete, clientId, "COMPLETED", today.minusDays(10), today.plusDays(5));
        createProject(clientId, "Planned", "PLANNED", today, today.plusDays(10));
        createInteraction(clientId, "EMAIL", "Proposal sent", today);

        mockMvc.perform(get("/api/clients/" + clientId + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.id").value(clientId))
                .andExpect(jsonPath("$.client.status").value("ACTIVE"))
                .andExpect(jsonPath("$.totalProjects").value(3))
                .andExpect(jsonPath("$.projectsInProgress").value(1))
                .andExpect(jsonPath("$.completedProjects").value(1))
                .andExpect(jsonPath("$.overdueProjects").value(1))
                .andExpect(jsonPath("$.totalInteractions").value(1));
    }
}
