package com.example.cms.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for POST /api/clients and PUT /api/clients/{id}.
 * "status" is optional: a new client without a status becomes LEAD,
 * and an update without a status keeps the current one.
 */
public record ClientRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name must be at most 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name must be at most 50 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 120, message = "Email must be at most 120 characters")
        String email,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?[0-9 ()\\-]{7,20}$",
                message = "Phone must be 7-20 characters: digits, spaces, (), - and an optional leading +")
        String phone,

        @Size(max = 120, message = "Company name must be at most 120 characters")
        String companyName,

        @Size(max = 255, message = "Address must be at most 255 characters")
        String address,

        @NotNull(message = "Client type is required (INDIVIDUAL or BUSINESS)")
        ClientType clientType,

        ClientStatus status,

        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes
) {
}
