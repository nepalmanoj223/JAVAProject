package com.example.cms.interaction;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InteractionRepository extends MongoRepository<Interaction, String> {

    /** History for one client, newest first. */
    List<Interaction> findByClientIdOrderByInteractionDateDescCreatedAtDesc(String clientId);

    long countByClientId(String clientId);
}
