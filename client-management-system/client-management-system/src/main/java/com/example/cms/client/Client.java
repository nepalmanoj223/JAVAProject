package com.example.cms.client;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A client stored in the "clients" collection.
 * The email is always stored in lower case, and the unique index on it
 * therefore prevents duplicates such as "Alex@Example.com" and "alex@example.com".
 */
@Document(collection = "clients")
public class Client {

    @Id
    private String id;

    private String firstName;
    private String lastName;

    @Indexed(name = "uk_clients_email", unique = true)
    private String email;

    private String phone;
    private String companyName;
    private String address;

    @Indexed(name = "ix_clients_status")
    private ClientStatus status;

    private ClientType clientType;
    private String notes;

    @Indexed(name = "ix_clients_created_at")
    private Instant createdAt;

    private Instant updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public ClientStatus getStatus() { return status; }
    public void setStatus(ClientStatus status) { this.status = status; }

    public ClientType getClientType() { return clientType; }
    public void setClientType(ClientType clientType) { this.clientType = clientType; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
