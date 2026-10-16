package com.example.cms.config;

import com.example.cms.client.Client;
import com.example.cms.interaction.Interaction;
import com.example.cms.project.Project;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs once when the application starts:
 *  1. Pings MongoDB and prints a clear success or failure message in the Run console.
 *  2. Creates the indexes declared on the documents (@Indexed, @CompoundIndex),
 *     including the UNIQUE index on clients.email.
 *
 * If MongoDB is not running, the web server still starts (so you can see the page),
 * but every API call returns HTTP 503 with an explanation until MongoDB is started.
 * Restart the application after starting MongoDB so the indexes are created.
 */
@Component
public class MongoStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoStartupCheck.class);

    private final MongoTemplate mongoTemplate;
    private final MongoMappingContext mappingContext;

    public MongoStartupCheck(MongoTemplate mongoTemplate, MongoMappingContext mappingContext) {
        this.mongoTemplate = mongoTemplate;
        this.mappingContext = mappingContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        String databaseName = mongoTemplate.getDb().getName();
        try {
            mongoTemplate.executeCommand(new Document("ping", 1));
            createIndexes();
            log.info("==============================================================");
            log.info(" Connected to MongoDB database '{}'. Indexes are ready.", databaseName);
            log.info(" Open http://localhost:8080 in your browser.");
            log.info("==============================================================");
        } catch (Exception ex) {
            log.error("==============================================================");
            log.error(" COULD NOT CONNECT TO MONGODB (database '{}').", databaseName);
            log.error(" Reason: {}", ex.getMessage());
            log.error(" 1. Start the MongoDB server (Windows: Services > MongoDB Server > Start).");
            log.error(" 2. Check spring.data.mongodb.uri in application.properties");
            log.error("    or the MONGODB_URI environment variable in Run > Edit Configurations.");
            log.error(" 3. Stop and Run the application again in IntelliJ IDEA.");
            log.error(" Until then, API requests will fail with HTTP 503.");
            log.error("==============================================================");
        }
    }

    private void createIndexes() {
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);
        for (Class<?> documentClass : List.of(Client.class, Project.class, Interaction.class)) {
            IndexOperations indexOps = mongoTemplate.indexOps(documentClass);
            resolver.resolveIndexFor(documentClass).forEach(indexOps::ensureIndex);
        }
    }
}
