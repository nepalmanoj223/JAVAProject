package com.example.cms.interaction;

import com.example.cms.support.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Interaction history: create, list newest first, get, update, delete. */
class InteractionApiIntegrationTest extends AbstractMongoIntegrationTest {

    private final LocalDate today = LocalDate.now();

    private Map<String, Object> interactionBody(String type, String subject, LocalDate date) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("subject", subject);
        map.put("description", "Discussed the client's website requirements.");
        map.put("interactionDate", date.toString());
        return map;
    }

    @Test
    void historyIsSortedNewestFirst() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", null);
        createInteraction(clientId, "NOTE", "Oldest", today.minusDays(5));
        createInteraction(clientId, "MEETING", "Newest", today);
        createInteraction(clientId, "CALL", "Middle", today.minusDays(2));

        mockMvc.perform(get("/api/clients/" + clientId + "/interactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].subject").value("Newest"))
                .andExpect(jsonPath("$[1].subject").value("Middle"))
                .andExpect(jsonPath("$[2].subject").value("Oldest"));
    }

    @Test
    void interactionsBelongToTheirClientOnly() throws Exception {
        String alex = createClient("Alex", "Smith", "alex@example.com", null);
        String sara = createClient("Sara", "Lee", "sara@example.com", null);
        createInteraction(alex, "CALL", "Alex call", today);
        createInteraction(sara, "EMAIL", "Sara email", today);

        mockMvc.perform(get("/api/clients/" + alex + "/interactions"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].subject").value("Alex call"));
    }

    @Test
    void interactionCrud() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", null);

        String id = body(mockMvc.perform(post("/api/clients/" + clientId + "/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(interactionBody("MEETING", "Initial requirements discussion", today))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andExpect(jsonPath("$.type").value("MEETING"))
                .andExpect(jsonPath("$.interactionDate").value(today.toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn()).get("id").asText();

        mockMvc.perform(get("/api/interactions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Initial requirements discussion"));

        mockMvc.perform(put("/api/interactions/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(interactionBody("CALL", "Follow-up call", today.minusDays(1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CALL"))
                .andExpect(jsonPath("$.subject").value("Follow-up call"))
                .andExpect(jsonPath("$.clientId").value(clientId));

        mockMvc.perform(delete("/api/interactions/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/interactions/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void clientMustExistBeforeCreatingInteraction() throws Exception {
        mockMvc.perform(post("/api/clients/0123456789abcdef01234567/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(interactionBody("CALL", "Hello", today))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/clients/not-an-id/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(interactionBody("CALL", "Hello", today))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidInteractionIsRejected() throws Exception {
        String clientId = createClient("Alex", "Smith", "alex@example.com", null);

        mockMvc.perform(post("/api/clients/" + clientId + "/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(interactionBody("CALL", "Future", today.plusDays(3)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.interactionDate").exists());

        mockMvc.perform(post("/api/clients/" + clientId + "/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(interactionBody("SMOKE_SIGNAL", "Bad type", today))))
                .andExpect(status().isBadRequest());
    }
}
