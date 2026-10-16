package com.example.cms.client;

import java.time.Instant;

/** The client data returned by the API. */
public record ClientResponse(
        String id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phone,
        String companyName,
        String address,
        ClientType clientType,
        ClientStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static ClientResponse from(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getFirstName(),
                client.getLastName(),
                client.getFullName(),
                client.getEmail(),
                client.getPhone(),
                client.getCompanyName(),
                client.getAddress(),
                client.getClientType(),
                client.getStatus(),
                client.getNotes(),
                client.getCreatedAt(),
                client.getUpdatedAt());
    }
}
