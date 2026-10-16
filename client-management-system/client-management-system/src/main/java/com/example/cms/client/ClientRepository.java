package com.example.cms.client;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends MongoRepository<Client, String> {

    Optional<Client> findByEmail(String email);

    long countByStatus(ClientStatus status);

    List<Client> findTop5ByOrderByCreatedAtDesc();
}
