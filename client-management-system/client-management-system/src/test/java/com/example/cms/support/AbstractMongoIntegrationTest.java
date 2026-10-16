package com.example.cms.support;

import com.example.cms.client.Client;
import com.example.cms.interaction.Interaction;
import com.example.cms.project.Project;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for integration tests.
 *
 * ISOLATION: the tests use a SEPARATE database, "client_management_test", on your local
 * MongoDB server. Your development database "client_management" is never touched.
 * Before every test the three test collections are emptied (only in the test database,
 * which is double-checked below).
 *
 * If MongoDB is not running, these tests are SKIPPED (not failed) with a message.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.data.mongodb.uri=mongodb://localhost:27017/" + AbstractMongoIntegrationTest.TEST_DATABASE)
public abstract class AbstractMongoIntegrationTest {

    public static final String TEST_DATABASE = "client_management_test";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeAll
    static void requireLocalMongo() {
        Assumptions.assumeTrue(isMongoReachable(),
                "MongoDB is not running on localhost:27017 - integration tests are skipped. Start MongoDB and run again.");
    }

    @BeforeEach
    void cleanTestDatabase() {
        assertThat(mongoTemplate.getDb().getName())
                .as("Integration tests must only ever use the test database")
                .isEqualTo(TEST_DATABASE);
        mongoTemplate.remove(new Query(), Client.class);
        mongoTemplate.remove(new Query(), Project.class);
        mongoTemplate.remove(new Query(), Interaction.class);
    }

    private static boolean isMongoReachable() {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString("mongodb://localhost:27017"))
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(2, TimeUnit.SECONDS))
                .build();
        try (MongoClient client = MongoClients.create(settings)) {
            client.getDatabase("admin").runCommand(new Document("ping", 1));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ helpers

    protected String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected Map<String, Object> clientBody(String firstName, String lastName, String email, String status) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("firstName", firstName);
        map.put("lastName", lastName);
        map.put("email", email);
        map.put("phone", "+19876543210");
        map.put("companyName", "Smith Technologies");
        map.put("address", "New York");
        map.put("clientType", "BUSINESS");
        map.put("notes", "Test client");
        if (status != null) {
            map.put("status", status);
        }
        return map;
    }

    /** Creates a client through the API and returns its ID. */
    protected String createClient(String firstName, String lastName, String email, String status) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(clientBody(firstName, lastName, email, status))))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asText();
    }

    protected Map<String, Object> projectBody(String clientId, String name, String status,
                                              LocalDate startDate, LocalDate dueDate) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("description", "Test project");
        map.put("clientId", clientId);
        if (status != null) {
            map.put("status", status);
        }
        map.put("priority", "HIGH");
        map.put("startDate", startDate.toString());
        map.put("dueDate", dueDate.toString());
        map.put("budget", 2500.00);
        return map;
    }

    /** Creates a project through the API and returns its ID. */
    protected String createProject(String clientId, String name, String status,
                                   LocalDate startDate, LocalDate dueDate) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(projectBody(clientId, name, status, startDate, dueDate))))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asText();
    }

    /** Changes only the status of a project (PUT with the other fields unchanged). */
    protected MvcResult changeProjectStatus(String projectId, String clientId, String newStatus,
                                            LocalDate startDate, LocalDate dueDate) throws Exception {
        return mockMvc.perform(put("/api/projects/" + projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(projectBody(clientId, "Project", newStatus, startDate, dueDate))))
                .andReturn();
    }

    /** Creates an interaction through the API and returns its ID. */
    protected String createInteraction(String clientId, String type, String subject, LocalDate date) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("subject", subject);
        map.put("description", "Details of " + subject);
        map.put("interactionDate", date.toString());
        MvcResult result = mockMvc.perform(post("/api/clients/" + clientId + "/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(map)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asText();
    }
}
